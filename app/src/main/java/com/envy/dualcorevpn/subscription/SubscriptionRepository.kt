package com.envy.dualcorevpn.subscription

import android.content.Context
import android.util.Base64
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

    fun select(serverId: String?) {
        preferences.edit().putString(KEY_SELECTED, serverId).apply()
    }

    suspend fun addAndUpdate(name: String, url: String): Subscription {
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "Ссылка подписки должна начинаться с https:// или http://"
        }
        val current = subscriptions().toMutableList()
        val existing = current.firstOrNull { it.url == url }
        val subscription = (existing ?: Subscription(UUID.randomUUID().toString(), name.ifBlank { hostName(url) }, url))
            .copy(name = name.ifBlank { existing?.name ?: hostName(url) })
        val profiles = fetch(subscription)
        require(profiles.isNotEmpty()) { "В подписке не найдено поддерживаемых конфигов" }
        current.removeAll { it.id == subscription.id }
        val updated = subscription.copy(updatedAt = System.currentTimeMillis())
        current += updated
        saveSubscriptions(current)
        saveServers(servers().filterNot { it.subscriptionId == subscription.id } + profiles)
        if (selectedServerId() == null) select(profiles.first().id)
        return updated
    }

    suspend fun update(subscription: Subscription) {
        val profiles = fetch(subscription)
        require(profiles.isNotEmpty()) { "В подписке не найдено поддерживаемых конфигов" }
        saveServers(servers().filterNot { it.subscriptionId == subscription.id } + profiles)
        saveSubscriptions(subscriptions().map {
            if (it.id == subscription.id) it.copy(updatedAt = System.currentTimeMillis()) else it
        })
        if (selectedServerId()?.let { id -> profiles.none { it.id == id } } == true) select(profiles.first().id)
    }

    fun remove(subscription: Subscription) {
        val remaining = servers().filterNot { it.subscriptionId == subscription.id }
        saveSubscriptions(subscriptions().filterNot { it.id == subscription.id })
        saveServers(remaining)
        if (remaining.none { it.id == selectedServerId() }) select(remaining.firstOrNull()?.id)
    }

    private fun fetch(subscription: Subscription): List<ServerProfile> {
        val connection = URL(subscription.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Lust/0.1 Android")
        return try {
            require(connection.responseCode in 200..299) { "Сервер подписки ответил HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            SubscriptionParser.parse(subscription.id, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun saveSubscriptions(items: List<Subscription>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("url", item.url); put("updatedAt", item.updatedAt)
        }) }
        preferences.edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply()
    }

    private fun saveServers(items: List<ServerProfile>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("subscriptionId", item.subscriptionId); put("name", item.name)
            put("protocol", item.protocol); put("address", item.address); put("port", item.port); put("config", item.config)
        }) }
        preferences.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    private fun hostName(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { "Подписка" }

    private companion object {
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_SERVERS = "servers"
        const val KEY_SELECTED = "selected_server"
    }
}

object SubscriptionParser {
    fun parse(subscriptionId: String, body: String): List<ServerProfile> {
        val content = decodeMaybeBase64(body.trim())
        return content.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line -> runCatching { parseLine(subscriptionId, line) }.getOrNull() }
            .distinctBy { "${it.protocol}:${it.address}:${it.port}:${it.name}" }
            .toList()
    }

    private fun parseLine(subscriptionId: String, line: String): ServerProfile? = when {
        line.startsWith("vmess://") -> parseVmess(subscriptionId, line.removePrefix("vmess://"))
        line.startsWith("vless://") -> parseStandardUri(subscriptionId, line, "vless")
        line.startsWith("trojan://") -> parseStandardUri(subscriptionId, line, "trojan")
        line.startsWith("ss://") -> parseStandardUri(subscriptionId, line, "shadowsocks")
        else -> null
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
            "vless" -> JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                put("address", address); put("port", port)
                put("users", JSONArray().put(JSONObject().apply {
                    put("id", uri.userInfo); put("encryption", query["encryption"] ?: "none")
                    put("flow", query["flow"] ?: "")
                }))
            }))
            "trojan" -> JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                put("address", address); put("port", port); put("password", decode(uri.userInfo ?: ""))
            }))
            else -> parseShadowsocksSettings(source, address, port)
        }
        return profile(subscriptionId, name, protocol, address, port, settings, streamSettings(
            network = query["type"] ?: "tcp", security = query["security"] ?: if (protocol == "trojan") "tls" else "",
            host = query["host"] ?: "", path = query["path"] ?: "", sni = query["sni"] ?: query["serverName"] ?: "",
            fingerprint = query["fp"] ?: "", publicKey = query["pbk"] ?: "", shortId = query["sid"] ?: "",
        ))
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

    private fun streamSettings(network: String, security: String, host: String, path: String, sni: String, fingerprint: String, publicKey: String, shortId: String): JSONObject = JSONObject().apply {
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
    }

    private fun parseQuery(query: String?): Map<String, String> = query.orEmpty().split('&').mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair[0].isBlank()) null else decode(pair[0]) to decode(pair.getOrElse(1) { "" })
    }.toMap()

    private fun decodeMaybeBase64(value: String): String = if (value.contains("://")) value else runCatching { decodeBase64(value) }.getOrDefault(value)
    private fun decodeBase64(value: String): String {
        val normalized = value.trim().replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return String(Base64.decode(normalized, Base64.DEFAULT), StandardCharsets.UTF_8)
    }
    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
