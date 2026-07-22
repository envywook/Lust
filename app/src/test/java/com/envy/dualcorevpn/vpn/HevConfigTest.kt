package com.envy.dualcorevpn.vpn

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class HevConfigTest {
    @Test fun `renders socks endpoint and tunnel parameters`() {
        val yaml = HevConfig("127.0.0.1", 10808, 1500).toYaml()
        assertTrue(yaml.contains("address: 127.0.0.1"))
        assertTrue(yaml.contains("port: 10808"))
        assertTrue(yaml.contains("mtu: 1500"))
        assertTrue(yaml.contains("ipv4: 198.18.0.1"))
    }

    @Test fun `rejects invalid port`() {
        assertFailsWith<IllegalArgumentException> { HevConfig(socksPort = 0) }
    }

    @Test fun `rejects unsafe mtu`() {
        assertFailsWith<IllegalArgumentException> { HevConfig(mtu = 1000) }
    }
}
