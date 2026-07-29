package com.envy.dualcorevpn.server

import com.envy.dualcorevpn.subscription.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartConnectPlannerTest {
    private fun server(id: String, name: String) = ServerProfile(id, "sub", name, "vless", "$id.example", 443, "{}")
    private fun ok(ms: Long) = ServerLatencyResult(ms, null)
    private val failed = ServerLatencyResult(null, "timeout")

    @Test
    fun `keeps pinned server before third consecutive failure`() {
        val pinned = server("de1", "🇩🇪 Berlin 1")
        val other = server("fi1", "🇫🇮 Helsinki")
        assertEquals(pinned, SmartConnectPlanner.choose(pinned, listOf(pinned, other), mapOf("de1" to failed, "fi1" to ok(10)), 2))
    }

    @Test
    fun `prefers same country after third failure even when another country is faster`() {
        val pinned = server("de1", "🇩🇪 Berlin 1")
        val sameCountry = server("de2", "Germany Frankfurt")
        val other = server("fi1", "🇫🇮 Helsinki")
        val chosen = SmartConnectPlanner.choose(
            pinned,
            listOf(pinned, sameCountry, other),
            mapOf("de1" to failed, "de2" to ok(80), "fi1" to ok(10)),
            3,
        )
        assertEquals(sameCountry, chosen)
    }

    @Test
    fun `changes country only when no endpoint in pinned country is reachable`() {
        val pinned = server("de1", "🇩🇪 Berlin")
        val sameCountry = server("de2", "Germany Frankfurt")
        val other = server("fi1", "🇫🇮 Helsinki")
        val chosen = SmartConnectPlanner.choose(
            pinned,
            listOf(pinned, sameCountry, other),
            mapOf("de1" to failed, "de2" to failed, "fi1" to ok(30)),
            3,
        )
        assertEquals(other, chosen)
    }

    @Test
    fun `never switches when every server is unreachable`() {
        val pinned = server("de1", "🇩🇪 Berlin")
        val other = server("fi1", "🇫🇮 Helsinki")
        assertEquals(pinned, SmartConnectPlanner.choose(pinned, listOf(pinned, other), mapOf("de1" to failed, "fi1" to failed), 3))
    }
}
