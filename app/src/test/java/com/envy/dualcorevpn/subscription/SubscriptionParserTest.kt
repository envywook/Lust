package com.envy.dualcorevpn.subscription

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {
    @Test
    fun `generated profile exposes local socks inbound for HEV`() {
        val link = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#test"

        val profile = SubscriptionParser.parse("subscription", link).single()
        val inbounds = JSONObject(profile.config).getJSONArray("inbounds")
        val socks = (0 until inbounds.length())
            .map(inbounds::getJSONObject)
            .single { it.getString("protocol") == "socks" }

        assertEquals("127.0.0.1", socks.getString("listen"))
        assertEquals(10808, socks.getInt("port"))
        assertEquals("socks-in", socks.getString("tag"))
        assertTrue(socks.getJSONObject("settings").getBoolean("udp"))
    }
}
