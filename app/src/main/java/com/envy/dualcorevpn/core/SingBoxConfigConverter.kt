package com.envy.dualcorevpn.core

import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigConverter {
    const val SOCKS_PORT = 10808

    fun convert(xrayConfig: String): String {
        val root = JSONObject(xrayConfig)
        val routing = root.optJSONObject("routing")
        require(routing == null || routing.length() == 0) {
            "Sing-box conversion does not support custom Xray routing rules"
        }
        val outbounds = root.optJSONArray("outbounds") ?: error("At least one outbound is required")
        val candidates = (0 until outbounds.length()).map(outbounds::getJSONObject)
        val proxyCandidates = candidates.filterNot { it.optString("protocol") in setOf("freedom", "blackhole", "dns") }
        require(proxyCandidates.size <= 1) { "Sing-box conversion supports exactly one proxy outbound" }
        val source = candidates.firstOrNull { it.optString("tag") == "proxy" }
            ?: candidates.firstOrNull()
            ?: error("At least one outbound is required")
        val outbound = convertOutbound(source)
        return JSONObject().apply {
            put("log", JSONObject().put("level", "fatal").put("timestamp", true))
            put("inbounds", JSONArray().put(JSONObject().apply {
                put("type", "socks")
                put("tag", "socks-in")
                put("listen", "127.0.0.1")
                put("listen_port", SOCKS_PORT)
            }))
            put("outbounds", JSONArray().put(outbound))
            put("route", JSONObject().put("final", "proxy"))
        }.toString()
    }

    private fun convertOutbound(source: JSONObject): JSONObject {
        val protocol = source.getString("protocol")
        if (protocol == "freedom") return JSONObject().put("type", "direct").put("tag", "proxy")
        val settings = source.optJSONObject("settings") ?: JSONObject()
        val endpoint = when (protocol) {
            "vless", "vmess" -> settings.getJSONArray("vnext").getJSONObject(0)
            "trojan", "shadowsocks" -> settings.getJSONArray("servers").getJSONObject(0)
            else -> error("Sing-box не поддерживает Xray outbound $protocol")
        }
        val outbound = JSONObject().apply {
            put("type", protocol)
            put("tag", "proxy")
            put("server", endpoint.getString("address"))
            put("server_port", endpoint.getInt("port"))
        }
        when (protocol) {
            "vless" -> endpoint.getJSONArray("users").getJSONObject(0).also { user ->
                outbound.put("uuid", user.getString("id"))
                user.optString("flow").takeIf(String::isNotBlank)?.let { outbound.put("flow", it) }
            }
            "vmess" -> endpoint.getJSONArray("users").getJSONObject(0).also { user ->
                outbound.put("uuid", user.getString("id"))
                outbound.put("security", user.optString("security", "auto"))
                outbound.put("alter_id", user.optInt("alterId", 0))
            }
            "trojan" -> outbound.put("password", endpoint.getString("password"))
            "shadowsocks" -> {
                outbound.put("method", endpoint.getString("method"))
                outbound.put("password", endpoint.getString("password"))
            }
        }
        applyStream(source.optJSONObject("streamSettings"), outbound)
        return outbound
    }

    private fun JSONObject.requireOnlySupported(allowed: Set<String>, label: String) {
        val unsupported = keys().asSequence().filterNot(allowed::contains).filter { key ->
            when (val value = opt(key)) {
                null, JSONObject.NULL, false, "" -> false
                is JSONArray -> value.length() > 0
                is JSONObject -> value.length() > 0
                is Number -> value.toDouble() != 0.0
                else -> true
            }
        }.toList()
        require(unsupported.isEmpty()) {
            "Sing-box conversion does not support $label fields: ${unsupported.joinToString()}"
        }
    }

    private fun applyStream(stream: JSONObject?, outbound: JSONObject) {
        if (stream == null) return
        val security = stream.optString("security", "none")
        require(security in setOf("", "none", "tls", "reality")) {
            "Sing-box security $security пока не поддерживается"
        }
        if (security == "tls" || security == "reality") {
            val source = if (security == "reality") stream.optJSONObject("realitySettings") else stream.optJSONObject("tlsSettings")
            source?.requireOnlySupported(
                if (security == "reality") setOf("serverName", "fingerprint", "publicKey", "shortId")
                else setOf("serverName", "fingerprint", "allowInsecure", "alpn"),
                "$security settings",
            )
            outbound.put("tls", JSONObject().apply {
                put("enabled", true)
                source?.optString("serverName")?.takeIf(String::isNotBlank)?.let { put("server_name", it) }
                if (source?.has("allowInsecure") == true) put("insecure", source.optBoolean("allowInsecure"))
                source?.optJSONArray("alpn")?.let { put("alpn", it) }
                source?.optString("fingerprint")?.takeIf(String::isNotBlank)?.let {
                    put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
                }
                if (security == "reality") put("reality", JSONObject().apply {
                    put("enabled", true)
                    source?.optString("publicKey")?.takeIf(String::isNotBlank)?.let { put("public_key", it) }
                    source?.optString("shortId")?.takeIf(String::isNotBlank)?.let { put("short_id", it) }
                })
            })
        }
        when (stream.optString("network", "tcp")) {
            "ws" -> {
                val ws = requireNotNull(stream.optJSONObject("wsSettings")) {
                    "WebSocket transport requires wsSettings"
                }
                outbound.put("transport", JSONObject().apply {
                    put("type", "ws")
                    ws.optString("path").takeIf(String::isNotBlank)?.let { put("path", it) }
                    ws.optJSONObject("headers")?.let { put("headers", it) }
                })
            }
            "grpc" -> {
                val grpc = requireNotNull(stream.optJSONObject("grpcSettings")) {
                    "gRPC transport requires grpcSettings"
                }
                outbound.put("transport", JSONObject().put("type", "grpc")
                    .put("service_name", grpc.optString("serviceName")))
            }
            "tcp", "raw", "" -> Unit
            else -> error("Sing-box transport ${stream.optString("network")} пока не поддерживается")
        }
    }
}
