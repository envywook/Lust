package com.envy.dualcorevpn.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONObject

data class MieruImportRequest(
    val profile: ServerProfile,
    val username: String,
    val transport: String,
    val mtu: Int,
    val multiplexing: String,
) {
    override fun toString(): String = "MieruImportRequest(profile=<redacted>, transport=$transport, mtu=$mtu, multiplexing=$multiplexing)"
}

object MieruDeepLink {
    private const val LOCAL_SUBSCRIPTION_ID = "local-mieru-import"
    private val allowedParameters = setOf("v", "transport", "mtu", "mux")
    private val multiplexing = mapOf(
        "default" to "MULTIPLEXING_DEFAULT",
        "off" to "MULTIPLEXING_OFF",
        "low" to "MULTIPLEXING_LOW",
        "middle" to "MULTIPLEXING_MIDDLE",
        "high" to "MULTIPLEXING_HIGH",
    )

    fun parse(value: String?): MieruImportRequest {
        val source = value ?: throw IllegalArgumentException("Mieru URI is missing")
        require(source.length <= 4_096) { "Mieru URI is too long" }
        val uri = URI(source)
        require(uri.scheme.equals("mieru", ignoreCase = true)) { "Unsupported Mieru URI scheme" }
        require(uri.rawPath.isNullOrEmpty()) { "Mieru URI path is not supported" }
        val host = uri.host?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Mieru host is required")
        val port = uri.port
        require(port in 1..65535) { "Mieru port must be between 1 and 65535" }

        val credentials = uri.rawUserInfo?.split(':', limit = 2).orEmpty()
        require(credentials.size == 2) { "Mieru username and password are required" }
        val username = decode(credentials[0]).also { require(it.isNotBlank()) { "Mieru username is required" } }
        val password = decode(credentials[1]).also { require(it.isNotBlank()) { "Mieru password is required" } }
        require(username.length <= 256 && password.length <= 256) { "Mieru credentials are too long" }

        val query = parseQuery(uri.rawQuery)
        require(query.keys.all { it in allowedParameters }) { "Mieru URI contains unsupported parameters" }
        val version = query["v"]?.ifBlank { "1" } ?: "1"
        require(version == "1") { "Unsupported Mieru URI version" }
        val transport = query["transport"]?.ifBlank { "tcp" }?.uppercase() ?: "TCP"
        require(transport == "TCP" || transport == "UDP") { "Mieru transport must be tcp or udp" }
        val mtu = (query["mtu"]?.ifBlank { "1400" } ?: "1400").toIntOrNull()
            ?: throw IllegalArgumentException("Mieru MTU must be an integer")
        require(mtu in 1280..1500) { "Mieru MTU must be between 1280 and 1500" }
        val mux = multiplexing[query["mux"]?.ifBlank { "default" }?.lowercase() ?: "default"]
            ?: throw IllegalArgumentException("Unsupported Mieru multiplexing")
        val name = decode(uri.rawFragment.orEmpty()).ifBlank { "$host:$port" }
        require(name.length <= 128 && name.none(Char::isISOControl)) { "Invalid Mieru profile name" }

        val outbound = JSONObject().apply {
            put("type", "mieru")
            put("server", host)
            put("server_port", port)
            put("transport", transport)
            put("username", username)
            put("password", password)
            put("mtu", mtu)
            put("multiplexing", mux)
        }
        val config = JSONObject().put("lust_format", "sing-box").put("outbound", outbound).toString()
        val id = UUID.nameUUIDFromBytes("$LOCAL_SUBSCRIPTION_ID:$host:$port:$username:$transport".toByteArray()).toString()
        return MieruImportRequest(
            profile = ServerProfile(id, LOCAL_SUBSCRIPTION_ID, name, "mieru", host, port, config),
            username = username,
            transport = transport,
            mtu = mtu,
            multiplexing = mux,
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        val segments = rawQuery?.split('&').orEmpty()
        require(segments.none(String::isBlank)) { "Mieru URI contains an empty query parameter" }
        val pairs = segments.map { part ->
            val pair = part.split('=', limit = 2)
            require(pair.size == 2 && pair[0].isNotBlank()) { "Invalid Mieru query parameter" }
            decode(pair[0]).lowercase() to decode(pair[1])
        }
        require(pairs.map { it.first }.distinct().size == pairs.size) { "Duplicate Mieru query parameter" }
        val result = pairs.toMap()
        return result
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}
