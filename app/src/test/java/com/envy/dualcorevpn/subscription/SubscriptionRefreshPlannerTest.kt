package com.envy.dualcorevpn.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionRefreshPlannerTest {
    private val old = Subscription("sub", "Main", "https://example.test/sub", 10L)
    private val oldServer = ServerProfile("old", "sub", "Old", "vless", "old.example", 443, "{}")
    private val otherServer = ServerProfile("other", "other-sub", "Other", "vless", "other.example", 443, "{}")
    private val freshServer = ServerProfile("fresh", "sub", "Fresh", "vless", "fresh.example", 443, "{}")

    @Test
    fun `successful refresh replaces only servers owned by subscription`() {
        val plan = SubscriptionRefreshPlanner.plan(
            subscriptions = listOf(old),
            servers = listOf(oldServer, otherServer),
            selectedServerId = oldServer.id,
            subscription = old,
            report = SubscriptionParser.ParseReport(listOf(freshServer), 2, 1, 3),
            updatedAt = 20L,
        )

        assertEquals(listOf(otherServer, freshServer), plan.servers)
        assertEquals(20L, plan.subscriptions.single().updatedAt)
        assertEquals(freshServer.id, plan.selectedServerId)
        assertEquals(1, plan.result.importedCount)
        assertEquals(2, plan.result.unsupportedCount)
        assertEquals(1, plan.result.invalidCount)
        assertEquals(3, plan.result.duplicateCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty refresh is rejected before old snapshot can be replaced`() {
        SubscriptionRefreshPlanner.plan(
            subscriptions = listOf(old),
            servers = listOf(oldServer),
            selectedServerId = oldServer.id,
            subscription = old,
            report = SubscriptionParser.ParseReport(emptyList(), 1, 1, 0),
            updatedAt = 20L,
        )
    }

    @Test
    fun `renamed selected server keeps identical endpoint`() {
        val renamed = ServerProfile("renamed", "sub", "Renamed", "vless", oldServer.address, oldServer.port, "new")
        val unrelated = ServerProfile("unrelated", "sub", "Other", "vless", "other-country.example", 443, "new")
        val plan = SubscriptionRefreshPlanner.plan(
            listOf(old), listOf(oldServer), oldServer.id, old,
            SubscriptionParser.ParseReport(listOf(unrelated, renamed), 0, 0, 0), 20L,
        )
        assertEquals(renamed.id, plan.selectedServerId)
    }

    @Test
    fun `removed selected endpoint stays in same country`() {
        val previous = oldServer.copy(name = "🇩🇪 Berlin")
        val foreign = freshServer.copy(id = "fi", name = "🇫🇮 Helsinki")
        val sameCountry = freshServer.copy(id = "de", name = "Germany Frankfurt")
        val plan = SubscriptionRefreshPlanner.plan(
            listOf(old), listOf(previous), previous.id, old,
            SubscriptionParser.ParseReport(listOf(foreign, sameCountry), 0, 0, 0), 20L,
        )
        assertEquals(sameCountry.id, plan.selectedServerId)
    }
}
