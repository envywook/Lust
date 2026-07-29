package com.envy.dualcorevpn.server

import com.envy.dualcorevpn.subscription.ServerProfile

/** Keeps a server and country stable unless the pinned endpoint repeatedly fails. */
object SmartConnectPlanner {
    const val FAILURE_THRESHOLD = 3

    fun choose(
        pinned: ServerProfile,
        servers: List<ServerProfile>,
        results: Map<String, ServerLatencyResult>,
        consecutiveFailures: Int,
    ): ServerProfile {
        val pinnedReachable = results[pinned.id]?.latencyMillis != null
        if (pinnedReachable || consecutiveFailures < FAILURE_THRESHOLD) return pinned

        val reachable = servers.filter { it.id != pinned.id && results[it.id]?.latencyMillis != null }
        if (reachable.isEmpty()) return pinned
        val country = serverCountryKey(pinned.name)
        val sameCountry = reachable.filter { serverCountryKey(it.name) == country }
        return (sameCountry.ifEmpty { reachable }).minBy { results.getValue(it.id).latencyMillis!! }
    }

    fun serverCountryKey(name: String): String {
        val value = name.lowercase()
        val aliases = linkedMapOf(
            "AT" to listOf("🇦🇹", "austria", "австрия", "vienna", "вена"),
            "CA" to listOf("🇨🇦", "canada", "канада", "toronto", "торонто"),
            "DE" to listOf("🇩🇪", "germany", "германия", "deutschland", "berlin", "берлин"),
            "IE" to listOf("🇮🇪", "ireland", "ирландия", "dublin", "дублин"),
            "CN" to listOf("🇨🇳", "china", "китай", "beijing", "пекин"),
            "US" to listOf("🇺🇸", "united states", "usa", "сша", "new york", "нью-йорк"),
            "RU" to listOf("🇷🇺", "russia", "россия", "moscow", "москва"),
            "GB" to listOf("🇬🇧", "united kingdom", "great britain", "uk", "london", "лондон"),
            "NL" to listOf("🇳🇱", "netherlands", "нидерланды", "amsterdam", "амстердам"),
            "FR" to listOf("🇫🇷", "france", "франция", "paris", "париж"),
            "FI" to listOf("🇫🇮", "finland", "финляндия", "helsinki", "хельсинки"),
            "PL" to listOf("🇵🇱", "poland", "польша", "warsaw", "варшава"),
        )
        return aliases.entries.firstOrNull { (_, names) -> names.any(value::contains) }?.key
            ?: "UNKNOWN:${value.replace(Regex("[^\\p{L}]+"), " ").trim().substringBefore(' ')}"
    }
}
