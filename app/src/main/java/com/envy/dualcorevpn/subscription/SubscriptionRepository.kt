package com.envy.dualcorevpn.subscription

import android.content.Context
import android.util.Base64
import com.envy.dualcorevpn.server.ServerFavoritesCodec
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = 0L,
    val usage: SubscriptionUsage? = null,
)

data class ServerProfile(
    val id: String,
    val subscriptionId: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val config: String,
)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

class SubscriptionRepository(context: Context) {
    private val preferences = context.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)

    fun subscriptions(): List<Subscription> = runCatching {
        val values = JSONArray(preferences.getString(KEY_SUBSCRIPTIONS, "[]"))
        List(values.length()) { index ->
            values.getJSONObject(index).let {
                Subscription(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    url = it.getString("url"),
                    updatedAt = it.optLong("updatedAt"),
                    usage = if (it.has("usage")) it.getJSONObject("usage").let { usage ->
                        SubscriptionUsage(
                            uploadBytes = usage.optNullableLong("upload"),
                            downloadBytes = usage.optNullableLong("download"),
                            totalBytes = usage.optNullableLong("total"),
                            expiresAtEpochSeconds = usage.optNullableLong("expire"),
                        )
                    } else null,
                )
            }
        }
    }.getOrDefault(emptyList())

    fun servers(): List<ServerProfile> = runCatching {
        val values = JSONArray(preferences.getString(KEY_SERVERS, "[]"))
        List(values.length()) { index ->
            values.getJSONObject(index).let {
                ServerProfile(
                    id = it.getString("id"),
                    subscriptionId = it.getString("subscriptionId"),
                    name = it.getString("name"),
                    protocol = it.getString("protocol"),
                    address = it.getString("address"),
                    port = it.getInt("port"),
                    config = it.getString("config"),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun selectedServerId(): String? = preferences.getString(KEY_SELECTED, null)

    fun favoriteServerIds(): Set<String> = ServerFavoritesCodec.decode(preferences.getString(KEY_FAVORITES, ""))

    fun toggleFavorite(serverId: String): Set<String> {
        val updated = favoriteServerIds().toMutableSet().apply {
            if (!add(serverId)) remove(serverId)
        }
        check(preferences.edit().putString(KEY_FAVORITES, ServerFavoritesCodec.encode(updated)).commit()) {
            "Не удалось сохранить избранные серверы"
        }
        return updated
    }

    fun select(serverId: String?) {
        preferences.edit().putString(KEY_SELECTED, serverId).apply()
    }

    fun importProfile(profile: ServerProfile) {
        val updated = servers().filterNot { it.id == profile.id } + profile
        saveServers(updated)
        select(profile.id)
    }

    suspend fun addAndUpdate(name: String, url: String): SubscriptionUpdateResult {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "Ссылка подписки должна начинаться с https:// или http://"
        }
        val current = subscriptions()
        val existing = current.firstOrNull { it.url == url }
        val subscription = (existing ?: Subscription(UUID.randomUUID().toString(), name.ifBlank { hostName(url) }, url))
            .copy(name = name.ifBlank { existing?.name ?: hostName(url) })
        val fetched = fetch(subscription)
        val enrichedSubscription = subscription.copy(usage = fetched.usage ?: subscription.usage)
        val plan = SubscriptionRefreshPlanner.plan(
            subscriptions = current,
            servers = servers(),
            selectedServerId = selectedServerId(),
            subscription = enrichedSubscription,
            report = fetched.report,
            updatedAt = System.currentTimeMillis(),
        )
        persist(plan)
        return plan.result
    }

    suspend fun update(subscription: Subscription): SubscriptionUpdateResult {
        val fetched = fetch(subscription)
        val plan = SubscriptionRefreshPlanner.plan(
            subscriptions = subscriptions(),
            servers = servers(),
            selectedServerId = selectedServerId(),
            subscription = subscription.copy(usage = fetched.usage ?: subscription.usage),
            report = fetched.report,
            updatedAt = System.currentTimeMillis(),
        )
        persist(plan)
        return plan.result
    }

    fun remove(subscription: Subscription) {
        val remaining = servers().filterNot { it.subscriptionId == subscription.id }
        saveSubscriptions(subscriptions().filterNot { it.id == subscription.id })
        saveServers(remaining)
        if (remaining.none { it.id == selectedServerId() }) select(remaining.firstOrNull()?.id)
    }

    private data class FetchedSubscription(
        val report: SubscriptionParser.ParseReport,
        val usage: SubscriptionUsage?,
    )

    private suspend fun fetch(subscription: Subscription): FetchedSubscription {
        val connection = URL(subscription.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Lust/0.1 Android")
        return try {
            require(connection.responseCode in 200..299) { "Сервер подписки ответил HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            FetchedSubscription(
                report = SubscriptionParser.parseReport(subscription.id, body),
                usage = SubscriptionUsageParser.parse(connection.getHeaderField("subscription-userinfo")),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun persist(plan: SubscriptionRefreshPlan) {
        val committed = preferences.edit()
            .putString(KEY_SUBSCRIPTIONS, subscriptionsJson(plan.subscriptions).toString())
            .putString(KEY_SERVERS, serversJson(plan.servers).toString())
            .putString(KEY_SELECTED, plan.selectedServerId)
            .commit()
        check(committed) { "Не удалось атомарно сохранить обновление подписки" }
    }

    private fun saveSubscriptions(items: List<Subscription>) {
        preferences.edit().putString(KEY_SUBSCRIPTIONS, subscriptionsJson(items).toString()).apply()
    }

    private fun subscriptionsJson(items: List<Subscription>): JSONArray {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("url", item.url); put("updatedAt", item.updatedAt)
            item.usage?.let { usage ->
                put("usage", JSONObject().apply {
                    usage.uploadBytes?.let { put("upload", it) }
                    usage.downloadBytes?.let { put("download", it) }
                    usage.totalBytes?.let { put("total", it) }
                    usage.expiresAtEpochSeconds?.let { put("expire", it) }
                })
            }
        }) }
        return array
    }

    private fun saveServers(items: List<ServerProfile>) {
        preferences.edit().putString(KEY_SERVERS, serversJson(items).toString()).apply()
    }

    private fun serversJson(items: List<ServerProfile>): JSONArray {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("subscriptionId", item.subscriptionId); put("name", item.name)
            put("protocol", item.protocol); put("address", item.address); put("port", item.port); put("config", item.config)
        }) }
        return array
    }

    private fun hostName(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { "Подписка" }

    private companion object {
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_SERVERS = "servers"
        const val KEY_SELECTED = "selected_server"
        const val KEY_FAVORITES = "favorite_servers"
    }
}

object SubscriptionParser {
    data class ParseReport(
        val profiles: List<ServerProfile>,
        val unsupportedCount: Int,
        val invalidCount: Int,
        val duplicateCount: Int,
    )

    fun parse(subscriptionId: String, body: String): List<ServerProfile> =
        parseReport(subscriptionId, body).profiles

    fun parseReport(subscriptionId: String, body: String): ParseReport {
        val content = decodeMaybeBase64(body.trim())
        val profiles = linkedMapOf<String, ServerProfile>()
        var unsupported = 0
        var invalid = 0
        var duplicates = 0
        content.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val supported = line.startsWith("vmess://") || line.startsWith("vless://") ||
                    line.startsWith("trojan://") || line.startsWith("ss://") ||
                    line.startsWith("hysteria2://") || line.startsWith("hy2://") ||
                    line.startsWith("tuic://") || line.startsWith("naive+https://") ||
                    line.startsWith("mierus://") || line.startsWith("mieru://")
                if (!supported) {
                    unsupported++
                    return@forEach
                }
                val parsed = runCatching { parseLine(subscriptionId, line) }.getOrNull()
                if (parsed.isNullOrEmpty()) {
                    invalid++
                    return@forEach
                }
                parsed.forEach { profile ->
                    val key = "${profile.protocol}:${profile.address}:${profile.port}:${profile.name}"
                    if (profiles.putIfAbsent(key, profile) != null) duplicates++
                }
            }
        return ParseReport(profiles.values.toList(), unsupported, invalid, duplicates)
    }

    private fun parseLine(subscriptionId: String, line: String): List<ServerProfile> = when {
        line.startsWith("vmess://") -> listOf(parseVmess(subscriptionId, line.removePrefix("vmess://")))
        line.startsWith("vless://") -> listOf(parseStandardUri(subscriptionId, line, "vless"))
        line.startsWith("trojan://") -> listOf(parseStandardUri(subscriptionId, line, "trojan"))
        line.startsWith("ss://") -> listOf(parseStandardUri(subscriptionId, line, "shadowsocks"))
        line.startsWith("hysteria2://") || line.startsWith("hy2://") -> listOf(parseHysteria2(subscriptionId, line))
        line.startsWith("tuic://") -> listOf(parseTuic(subscriptionId, line))
        line.startsWith("naive+https://") -> listOf(parseNaive(subscriptionId, line))
        line.startsWith("mierus://") -> parseMieruSimple(subscriptionId, line)
        line.startsWith("mieru://") -> error("Binary Mieru URI is not supported")
        else -> emptyList()
    }

    private fun parseVmess(subscriptionId: String, encoded: String): ServerProfile {
        val json = JSONObject(decodeBase64(encoded))
        val address = json.getString("add")
        val port = json.getString("port").toInt()
        val name = json.optString("ps").ifBlank { "$address:$port" }
        val user = JSONObject().apply {
            put("id", json.getString("id")); put("alterId", json.optInt("aid", 0)); put("security", json.optString("scy", "auto"))
        }
        val stream = streamSettings(
            network = json.optString("net", "tcp"),
            security = json.optString("tls"),
            host = json.optString("host"),
            path = json.optString("path"),
            sni = json.optString("sni"),
            fingerprint = json.optString("fp"),
            publicKey = "",
            shortId = "",
        )
        return profile(subscriptionId, name, "vmess", address, port, JSONObject().apply {
            put("vnext", JSONArray().put(JSONObject().apply {
                put("address", address); put("port", port); put("users", JSONArray().put(user))
            }))
        }, stream)
    }

    private fun parseStandardUri(subscriptionId: String, source: String, protocol: String): ServerProfile {
        val uri = URI(source)
        val address = uri.host ?: error("В ссылке отсутствует адрес сервера")
        val port = if (uri.port > 0) uri.port else 443
        val query = parseQuery(uri.rawQuery)
        val name = decode(uri.rawFragment ?: "$address:$port")
        val settings = when (protocol) {
            "vless" -> {
                val userId = uri.userInfo?.takeIf(String::isNotBlank)
                    ?: error("В VLESS-ссылке отсутствует UUID пользователя")
                JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                put("address", address); put("port", port)
                put("users", JSONArray().put(JSONObject().apply {
                    put("id", userId); put("encryption", query["encryption"] ?: "none")
                    put("flow", query["flow"] ?: "")
                }))
            }))
            }
            "trojan" -> {
                val password = uri.userInfo?.takeIf(String::isNotBlank)
                    ?: error("В Trojan-ссылке отсутствует пароль")
                JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                    put("address", address); put("port", port); put("password", decode(password))
                }))
            }
            else -> parseShadowsocksSettings(source, address, port)
        }
        return profile(subscriptionId, name, protocol, address, port, settings, streamSettings(
            network = query["type"] ?: "tcp", security = query["security"] ?: if (protocol == "trojan") "tls" else "",
            host = query["host"] ?: "", path = query["path"] ?: "", sni = query["sni"] ?: query["serverName"] ?: "",
            fingerprint = query["fp"] ?: "", publicKey = query["pbk"] ?: "", shortId = query["sid"] ?: "",
            xhttpMode = query["mode"] ?: "",
            xhttpPadding = parseXhttpPadding(query["extra"]),
        ))
    }

    private fun parseHysteria2(subscriptionId: String, source: String): ServerProfile {
        val uri = URI(source)
        val address = uri.host ?: error("В Hysteria2-ссылке отсутствует адрес сервера")
        val port = uri.port.takeIf { it > 0 } ?: 443
        val password = uri.rawUserInfo?.let(::decode)?.takeIf(String::isNotBlank)
            ?: error("В Hysteria2-ссылке отсутствует пароль")
        val query = parseQuery(uri.rawQuery)
        val outbound = nativeOutbound("hysteria2", address, port).apply {
            put("password", password)
            query["upmbps"]?.toIntOrNull()?.let { put("up_mbps", it) }
            query["downmbps"]?.toIntOrNull()?.let { put("down_mbps", it) }
            val obfsType = query["obfs"].orEmpty()
            val obfsPassword = query["obfs-password"] ?: query["obfsPassword"]
            if (obfsType.isNotBlank()) put("obfs", JSONObject().put("type", obfsType).apply {
                if (!obfsPassword.isNullOrBlank()) put("password", obfsPassword)
            })
            put("tls", tlsOptions(address, query))
        }
        return nativeProfile(subscriptionId, uri, "hysteria2", address, port, outbound)
    }

    private fun parseTuic(subscriptionId: String, source: String): ServerProfile {
        val uri = URI(source)
        val address = uri.host ?: error("В TUIC-ссылке отсутствует адрес сервера")
        val port = uri.port.takeIf { it > 0 } ?: 443
        val credentials = uri.rawUserInfo?.let(::decode)?.split(':', limit = 2).orEmpty()
        require(credentials.size == 2 && credentials.all(String::isNotBlank)) {
            "TUIC-ссылка должна содержать UUID и пароль"
        }
        val query = parseQuery(uri.rawQuery)
        val udpOverStream = query.boolean("udp_over_stream", "udp-over-stream")
        val udpRelayMode = query["udp_relay_mode"] ?: query["udp-relay-mode"]
        require(!(udpOverStream && !udpRelayMode.isNullOrBlank())) {
            "TUIC udp_over_stream несовместим с udp_relay_mode"
        }
        val outbound = nativeOutbound("tuic", address, port).apply {
            put("uuid", credentials[0])
            put("password", credentials[1])
            put("congestion_control", query["congestion_control"] ?: query["congestion-control"] ?: "cubic")
            if (!udpRelayMode.isNullOrBlank()) put("udp_relay_mode", udpRelayMode)
            if (udpOverStream) put("udp_over_stream", true)
            if (query.boolean("zero_rtt_handshake", "reduce_rtt")) put("zero_rtt_handshake", true)
            (query["heartbeat_interval"] ?: query["heartbeat-interval"])?.takeIf(String::isNotBlank)?.let {
                put("heartbeat", it)
            }
            put("tls", tlsOptions(address, query))
        }
        return nativeProfile(subscriptionId, uri, "tuic", address, port, outbound)
    }

    private fun parseNaive(subscriptionId: String, source: String): ServerProfile {
        val uri = URI(source)
        val address = uri.host ?: error("В Naive-ссылке отсутствует адрес сервера")
        val port = uri.port.takeIf { it > 0 } ?: 443
        val credentials = uri.rawUserInfo?.let(::decode)?.split(':', limit = 2).orEmpty()
        require(credentials.size == 2) { "Naive-ссылка должна содержать имя пользователя и пароль" }
        val query = parseQuery(uri.rawQuery)
        require(!query.boolean("insecure", "allowInsecure")) {
            "NaiveProxy не поддерживает отключение проверки TLS-сертификата"
        }
        require(query["alpn"].isNullOrBlank()) { "NaiveProxy не поддерживает пользовательский ALPN" }
        val outbound = nativeOutbound("naive", address, port).apply {
            put("username", credentials[0])
            put("password", credentials[1])
            put("tls", tlsOptions(address, query))
        }
        return nativeProfile(subscriptionId, uri, "naive", address, port, outbound)
    }

    private fun nativeOutbound(type: String, address: String, port: Int) = JSONObject().apply {
        put("type", type)
        put("tag", "proxy")
        put("server", address)
        put("server_port", port)
    }

    private fun parseMieruSimple(subscriptionId: String, source: String): List<ServerProfile> {
        val uri = URI(source)
        val address = uri.host ?: error("В Mieru-ссылке отсутствует адрес сервера")
        require(uri.port == -1) { "Mieru ports must be specified as query parameters" }
        val credentials = uri.rawUserInfo?.split(':', limit = 2)?.map(::decodeUriComponent).orEmpty()
        require(credentials.size == 2 && credentials.all(String::isNotBlank)) {
            "Mieru-ссылка должна содержать имя пользователя и пароль"
        }
        val pairs = parseQueryPairs(uri.rawQuery)
        val allowed = setOf("profile", "port", "protocol", "multiplexing")
        require(pairs.all { it.first in allowed }) {
            "Mieru-ссылка содержит неподдерживаемые параметры"
        }
        val profileName = pairs.singleValue("profile").takeIf(String::isNotBlank)
            ?: error("Mieru profile is required")
        val ports = pairs.filter { it.first == "port" }.map { it.second }
        val transports = pairs.filter { it.first == "protocol" }.map { it.second.uppercase() }
        require(ports.isNotEmpty() && ports.size == transports.size) {
            "Mieru port and protocol counts must match"
        }
        require(transports.all { it == "TCP" || it == "UDP" }) { "Unsupported Mieru transport" }
        ports.forEach(::validateMieruPortRange)
        val multiplexing = pairs.singleValueOrNull("multiplexing")?.uppercase()
            ?.takeUnless { it == "MULTIPLEXING_DEFAULT" }
        require(multiplexing == null || multiplexing in setOf(
            "MULTIPLEXING_OFF", "MULTIPLEXING_LOW", "MULTIPLEXING_MIDDLE", "MULTIPLEXING_HIGH",
        )) { "Unsupported Mieru multiplexing" }

        val grouped = ports.zip(transports).groupBy({ it.second }, { it.first })
        return grouped.map { (transport, transportPorts) ->
            val name = if (grouped.size == 1) profileName else "$profileName [$transport]"
            val first = transportPorts.first()
            val outbound = JSONObject().apply {
                put("type", "mieru")
                put("server", address)
                if (transportPorts.size == 1 && '-' !in first) {
                    put("server_port", first.toInt())
                } else {
                    put("server_ports", JSONArray(transportPorts.map { if ('-' in it) it else "$it-$it" }))
                }
                put("transport", transport)
                put("username", credentials[0])
                put("password", credentials[1])
                multiplexing?.let { put("multiplexing", it) }

            }
            val displayPort = first.substringBefore('-').toInt()
            val config = JSONObject().put("lust_format", "sing-box").put("outbound", outbound).toString()
            val id = UUID.nameUUIDFromBytes(
                "$subscriptionId:mieru:$address:$transport:${transportPorts.joinToString()}:$name".toByteArray(),
            ).toString()
            ServerProfile(id, subscriptionId, name, "mieru", address, displayPort, config)
        }
    }

    private fun validateMieruPortRange(value: String) {
        val bounds = value.split('-', limit = 2).map { it.toIntOrNull() ?: error("Invalid Mieru port") }
        require(bounds.size in 1..2 && bounds.all { it in 1..65535 }) { "Invalid Mieru port" }
        require(bounds.size == 1 || bounds[0] <= bounds[1]) { "Invalid Mieru port range" }
    }

    private fun List<Pair<String, String>>.singleValue(key: String): String =
        filter { it.first == key }.single().second

    private fun List<Pair<String, String>>.singleValueOrNull(key: String): String? =
        filter { it.first == key }.let { values -> if (values.isEmpty()) null else values.single().second }

    private fun nativeProfile(
        subscriptionId: String,
        uri: URI,
        protocol: String,
        address: String,
        port: Int,
        outbound: JSONObject,
    ): ServerProfile {
        val name = decode(uri.rawFragment ?: "$address:$port")
        val config = JSONObject().put("lust_format", "sing-box").put("outbound", outbound).toString()
        val id = UUID.nameUUIDFromBytes("$subscriptionId:$protocol:$address:$port:$name".toByteArray()).toString()
        return ServerProfile(id, subscriptionId, name, protocol, address, port, config)
    }

    private fun tlsOptions(address: String, query: Map<String, String>) = JSONObject().apply {
        put("enabled", true)
        put("server_name", query["sni"] ?: query["peer"] ?: address)
        if (query.boolean("insecure", "allowInsecure")) put("insecure", true)
        query["alpn"]?.split(',')?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)?.let {
            put("alpn", JSONArray(it))
        }
    }

    private fun Map<String, String>.boolean(vararg keys: String): Boolean = keys.any { key ->
        this[key]?.lowercase() in setOf("1", "true", "yes")
    }

    private fun parseShadowsocksSettings(source: String, address: String, port: Int): JSONObject {
        val uri = URI(source)
        val userInfo = uri.rawUserInfo?.let(::decode) ?: decodeBase64(source.removePrefix("ss://").substringBefore('@'))
        val parts = userInfo.split(':', limit = 2)
        require(parts.size == 2) { "Некорректная Shadowsocks-ссылка" }
        return JSONObject().put("servers", JSONArray().put(JSONObject().apply {
            put("address", address); put("port", port); put("method", parts[0]); put("password", parts[1])
        }))
    }

    private fun profile(subscriptionId: String, name: String, protocol: String, address: String, port: Int, settings: JSONObject, stream: JSONObject): ServerProfile {
        val outbound = JSONObject().apply {
            put("tag", "proxy"); put("protocol", protocol); put("settings", settings)
            if (stream.length() > 0) put("streamSettings", stream)
        }
        val config = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("inbounds", JSONArray().put(JSONObject().apply {
                put("tag", "socks-in")
                put("listen", "127.0.0.1")
                put("port", 10808)
                put("protocol", "socks")
                put("settings", JSONObject().put("udp", true))
                put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls").put("quic")))
            }))
            put("outbounds", JSONArray().put(outbound).put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
        }.toString()
        return ServerProfile(UUID.nameUUIDFromBytes("$subscriptionId:$protocol:$address:$port:$name".toByteArray()).toString(), subscriptionId, name, protocol, address, port, config)
    }

    private fun streamSettings(
        network: String,
        security: String,
        host: String,
        path: String,
        sni: String,
        fingerprint: String,
        publicKey: String,
        shortId: String,
        xhttpMode: String = "",
        xhttpPadding: String = "",
    ): JSONObject = JSONObject().apply {
        put("network", network.ifBlank { "tcp" })
        if (security.isNotBlank() && security != "none") {
            put("security", security)
            val key = if (security == "reality") "realitySettings" else "tlsSettings"
            put(key, JSONObject().apply {
                if (sni.isNotBlank()) put("serverName", sni)
                if (fingerprint.isNotBlank()) put("fingerprint", fingerprint)
                if (publicKey.isNotBlank()) put("publicKey", publicKey)
                if (shortId.isNotBlank()) put("shortId", shortId)
            })
        }
        if (network == "ws") put("wsSettings", JSONObject().apply {
            if (path.isNotBlank()) put("path", path)
            if (host.isNotBlank()) put("headers", JSONObject().put("Host", host))
        })
        if (network == "grpc") put("grpcSettings", JSONObject().put("serviceName", path.removePrefix("/")))
        if (network == "xhttp") put("xhttpSettings", JSONObject().apply {
            if (host.isNotBlank()) put("host", host)
            if (path.isNotBlank()) put("path", path)
            if (xhttpMode.isNotBlank()) put("mode", xhttpMode)
            if (xhttpPadding.isNotBlank()) put("xPaddingBytes", xhttpPadding)
        })
    }

    private fun parseXhttpPadding(extra: String?): String {
        if (extra.isNullOrBlank()) return ""
        val normalized = extra.trim().replace('-', '+').replace('_', '/').let {
            it + "=".repeat((4 - it.length % 4) % 4)
        }
        val decoded = String(java.util.Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8)
        val options = JSONObject(decoded)
        val unsupported = options.keys().asSequence().filterNot { it == "xPaddingBytes" }.toList()
        require(unsupported.isEmpty()) {
            "Неподдерживаемые параметры XHTTP extra: ${unsupported.joinToString(", ")}"
        }
        return options.optString("xPaddingBytes")
    }

    private fun parseQueryPairs(query: String?): List<Pair<String, String>> = query.orEmpty().split('&').mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair[0].isBlank()) null else decode(pair[0]) to decode(pair.getOrElse(1) { "" })
    }

    private fun parseQuery(query: String?): Map<String, String> = parseQueryPairs(query).toMap()

    private fun decodeMaybeBase64(value: String): String = if (value.contains("://")) value else runCatching { decodeBase64(value) }.getOrDefault(value)
    private fun decodeBase64(value: String): String {
        val normalized = value.trim().replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return String(Base64.decode(normalized, Base64.DEFAULT), StandardCharsets.UTF_8)
    }
    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun decodeUriComponent(value: String): String = decode(value.replace("+", "%2B"))
}
