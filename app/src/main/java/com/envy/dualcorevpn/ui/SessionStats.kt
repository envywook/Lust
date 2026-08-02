package com.envy.dualcorevpn.ui

import java.util.Locale
import java.util.Base64

internal data class SessionDetailItem(
    val label: String,
    val value: String,
    val weight: Float = 1f,
)

internal data class SessionDetailsLayout(
    val cardHeightDp: Int,
    val items: List<SessionDetailItem>,
)

internal fun sessionDetailsLayout(
    total: String = "—",
    duration: String = "—",
    ping: String = "—",
    engine: String = "—",
    protocol: String = "—",
    endpoint: String = "—",
    totalLabel: String = "Session",
    durationLabel: String = "Duration",
    pingLabel: String = "Ping",
    engineLabel: String = "Engine",
    protocolLabel: String = "Protocol",
    endpointLabel: String = "Endpoint",
): SessionDetailsLayout = SessionDetailsLayout(
    cardHeightDp = 132,
    items = listOf(
        SessionDetailItem(totalLabel, total),
        SessionDetailItem(durationLabel, duration),
        SessionDetailItem(pingLabel, ping),
        SessionDetailItem(engineLabel, engine, .8f),
        SessionDetailItem(protocolLabel, protocol, .8f),
        SessionDetailItem(endpointLabel, endpoint, 1.5f),
    ),
)

internal fun formatSessionDuration(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}

internal fun safeEndpointLabel(address: String, port: Int): String {
    if (port !in 1..65_535) return "—"
    val raw = address.trim().takeIf(String::isNotEmpty) ?: return "—"
    if (raw.contains("://") || raw.any { it.isWhitespace() || it.isISOControl() }) return "—"
    if (raw.any { it in "@/?#\\" }) return "—"
    val candidate = raw.removeSurrounding("[", "]")
    if (candidate.looksLikeCredentialToken()) return "—"
    val valid = when {
        candidate.count { it == ':' } > 1 -> candidate.matches(Regex("[0-9A-Fa-f:.%]+"))
        candidate == "localhost" -> true
        candidate.contains('.') -> candidate
            .split('.')
            .all { label ->
                label.isNotEmpty() &&
                    label.length <= 63 &&
                    label.first() != '-' &&
                    label.last() != '-' &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }
        else -> false
    }
    if (!valid || candidate.length > 253) return "—"
    val host = if (candidate.count { it == ':' } > 1) "[$candidate]" else candidate
    return "$host:$port"
}

private fun String.looksLikeCredentialToken(): Boolean {
    val labels = split('.')
    for (endExclusive in labels.size downTo 1) {
        val prefix = labels.take(endExclusive)
        val compactUuid = prefix.joinToString("").replace("-", "")
        if (compactUuid.length == 32 && compactUuid.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return true
        }

        val compactBase64 = prefix.joinToString("")
        if (compactBase64.length < 16 || !compactBase64.matches(Regex("[A-Za-z0-9_-]+"))) continue
        val padded = compactBase64 + "=".repeat((4 - compactBase64.length % 4) % 4)
        val decoded = runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull() ?: continue
        if (decoded.isEmpty() || decoded.any { it.toInt() !in 0x20..0x7e }) continue
        if (decoded.toString(Charsets.UTF_8).any { it in ":@{}\"" }) return true
    }
    return false
}
