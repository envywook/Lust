package com.envy.dualcorevpn.ui

import com.envy.dualcorevpn.subscription.ServerProfile
import com.envy.dualcorevpn.subscription.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSectionsPlannerTest {
    private fun server(id: String, subscriptionId: String, name: String) = ServerProfile(
        id = id,
        subscriptionId = subscriptionId,
        name = name,
        protocol = "vless",
        address = "$id.example.invalid",
        port = 443,
        config = "{}",
    )

    @Test
    fun `managed subscription is ordered into base plus and others`() {
        val subscriptions = listOf(
            Subscription("ours", "MaxSpeed", "https://sub.maxspeed.example/list"),
            Subscription("other", "Imported", "https://other.example/list"),
        )
        val base = server("base", "ours", "Canada")
        val plus = server("plus", "ours", "[Plus] Ireland")
        val other = server("other", "other", "Germany")

        val result = planServerSections(listOf(other, plus, base), subscriptions, "sub.maxspeed.example")

        assertTrue(result.managed)
        assertEquals(listOf(base), result.base)
        assertEquals(listOf(plus), result.plus)
        assertEquals(listOf(other), result.others)
    }

    @Test
    fun `without a managed subscription all servers remain in one list`() {
        val servers = listOf(server("one", "external", "Canada"), server("two", "external", "[Plus] Ireland"))

        val result = planServerSections(
            servers,
            listOf(Subscription("external", "Imported", "https://external.example/list")),
            "sub.maxspeed.example",
        )

        assertFalse(result.managed)
        assertEquals(servers, result.base)
        assertTrue(result.plus.isEmpty())
        assertTrue(result.others.isEmpty())
    }
}
