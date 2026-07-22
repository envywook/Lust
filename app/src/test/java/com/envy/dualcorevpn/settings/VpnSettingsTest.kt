package com.envy.dualcorevpn.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class VpnSettingsTest {
    @Test
    fun `accepts normal mtu dns and ipv6 preference`() {
        assertEquals(
            VpnSettings(mtu = 1400, dnsServer = "9.9.9.9", ipv6Enabled = false),
            VpnSettings.validate("1400", "9.9.9.9", false),
        )
    }

    @Test
    fun `rejects mtu outside supported range`() {
        assertFailsWith<IllegalArgumentException> { VpnSettings.validate("575", "1.1.1.1", true) }
        assertFailsWith<IllegalArgumentException> { VpnSettings.validate("9001", "1.1.1.1", true) }
    }

    @Test
    fun `rejects blank or malformed dns host`() {
        assertFailsWith<IllegalArgumentException> { VpnSettings.validate("1500", "", true) }
        assertFailsWith<IllegalArgumentException> { VpnSettings.validate("1500", "not a host!", true) }
    }
}
