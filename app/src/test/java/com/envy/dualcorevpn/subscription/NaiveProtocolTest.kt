package com.envy.dualcorevpn.subscription

import com.envy.dualcorevpn.core.SingBoxConfigConverter
import com.envy.dualcorevpn.core.XrayConfigValidator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiveProtocolTest {
    @Test
    fun `parses encoded credentials and builds sing-box outbound`() {
        val profile = SubscriptionParser.parse(
            "fixture",
            "naive+https://user%3Aname:p%40ss@naive.example:443#Naive",
        ).single()
        assertEquals("naive", profile.protocol)
        val outbound = JSONObject(profile.config).getJSONObject("outbound")
        assertEquals("user:name", outbound.getString("username"))
        assertEquals("p@ss", outbound.getString("password"))
        val runtime = JSONObject(SingBoxConfigConverter.convert(profile.config))
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals("naive", runtime.getString("type"))
        assertEquals("proxy", runtime.getString("tag"))
    }

    @Test
    fun `rejects insecure TLS custom ALPN and empty credentials`() {
        listOf(
            "naive+https://u:p@n.example?insecure=true",
            "naive+https://u:p@n.example?alpn=h2",
            "naive+https://:p@n.example",
        ).forEach { uri ->
            val report = SubscriptionParser.parseReport("fixture", uri)
            assertTrue(report.profiles.isEmpty())
            assertEquals(1, report.invalidCount)
        }
    }

    @Test
    fun `Xray explicitly rejects Naive sing-box profile without leaking credentials`() {
        val profile = SubscriptionParser.parse("fixture", "naive+https://user:password@n.example").single()
        val validation = XrayConfigValidator.validate(profile.config)
        assertTrue(validation is com.envy.dualcorevpn.core.ValidationResult.Invalid)
        assertFalse(validation.toString().contains("password"))
    }
}
