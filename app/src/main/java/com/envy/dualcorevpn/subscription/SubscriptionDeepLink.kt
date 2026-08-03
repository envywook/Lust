package com.envy.dualcorevpn.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class SubscriptionImportRequest(val url: String, val name: String = "")

object SubscriptionClipboard {
    fun parse(value: CharSequence?): SubscriptionImportRequest? {
        val text = value?.toString()?.trim().orEmpty()
        if (text.startsWith("lust://", ignoreCase = true) || text.startsWith("maxspeedvpn://", ignoreCase = true)) {
            return SubscriptionDeepLink.parse(text)
        }
        return text.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let(::SubscriptionImportRequest)
    }
}

object SubscriptionDeepLink {
    fun parse(value: String?): SubscriptionImportRequest? = runCatching {
        val uri = URI(value ?: return null)
        if (uri.scheme?.lowercase() !in setOf("lust", "maxspeedvpn")) return null
        if (uri.host !in setOf("add", "subscription")) return null
        val query = parseQuery(uri.rawQuery)
        val rawUrl = query["url"] ?: uri.rawPath.orEmpty().removePrefix("/").takeIf(String::isNotBlank) ?: return null
        val url = decode(rawUrl)
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        SubscriptionImportRequest(url = url, name = query["name"]?.let(::decode).orEmpty())
    }.getOrNull()

    private fun parseQuery(query: String?): Map<String, String> = query.orEmpty().split('&').mapNotNull { part ->
        val pair = part.split('=', limit = 2)
        pair.firstOrNull()?.takeIf(String::isNotBlank)?.let { it to pair.getOrElse(1) { "" } }
    }.toMap()

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
