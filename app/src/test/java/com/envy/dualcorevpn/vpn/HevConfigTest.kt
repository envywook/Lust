package com.envy.dualcorevpn.vpn

import kotlin.test.assertFailsWith
import org.junit.Assert.assertTrue
import org.junit.Test

class HevConfigTest {
    @Test
    fun `renders matching tunnel and socks endpoint`() {
        val yaml = HevConfig("127.0.0.1", 10808, 1500).toYaml()

        assertTrue(yaml.contains("address: '127.0.0.1'"))
        assertTrue(yaml.contains("port: 10808"))
        assertTrue(yaml.contains("mtu: 1500"))
        assertTrue(yaml.contains("ipv4: 198.18.0.1"))
        assertTrue(yaml.contains("ipv6: 'fc00::1'"))
        assertTrue(yaml.contains("tcp-read-write-timeout: 300000"))
        assertTrue(yaml.contains("udp-read-write-timeout: 60000"))
        assertTrue(!yaml.contains("\nmapdns:"))
        assertTrue(!yaml.contains("\n  read-write-timeout:"))
        assertTrue(!yaml.contains("task-stack-size:"))
        assertTrue(!yaml.contains("connect-timeout:"))
    }

    @Test
    fun `omits ipv6 tunnel address when disabled`() {
        val yaml = HevConfig(ipv6Enabled = false).toYaml()

        assertTrue(!yaml.contains("ipv6:"))
    }

    @Test fun `rejects invalid port`() {
        assertFailsWith<IllegalArgumentException> { HevConfig(socksPort = 0) }
    }

    @Test fun `rejects unsafe mtu`() {
        assertFailsWith<IllegalArgumentException> { HevConfig(mtu = 500) }
    }
}
