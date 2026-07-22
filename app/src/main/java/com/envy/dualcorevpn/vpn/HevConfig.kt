package com.envy.dualcorevpn.vpn

data class HevConfig(
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 10808,
    val mtu: Int = 1500,
    val ipv6Enabled: Boolean = true,
) {
    init {
        require(socksHost.isNotBlank()) { "SOCKS host is blank" }
        require(socksPort in 1..65535) { "SOCKS port is out of range" }
        require(mtu in 576..9000) { "MTU is out of range" }
    }

    fun toYaml(): String = """
        tunnel:
          mtu: $mtu
          ipv4: 198.18.0.1
        ${if (ipv6Enabled) "  ipv6: 'fc00::1'" else ""}
        socks5:
          address: '$socksHost'
          port: $socksPort
          udp: 'udp'
        misc:
          tcp-read-write-timeout: 300000
          udp-read-write-timeout: 60000
          log-file: stderr
          log-level: warn
    """.trimIndent()
}
