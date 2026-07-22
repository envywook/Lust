package com.envy.dualcorevpn.vpn

data class HevConfig(
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 10808,
    val mtu: Int = 1500,
) {
    init {
        require(socksHost.isNotBlank()) { "SOCKS host is blank" }
        require(socksPort in 1..65535) { "SOCKS port is out of range" }
        require(mtu in 576..9000) { "MTU is out of range" }
    }

    fun toYaml(): String = """
        tunnel:
          mtu: $mtu
        socks5:
          address: '$socksHost'
          port: $socksPort
          udp: 'udp'
        misc:
          task-stack-size: 20480
          connect-timeout: 5000
          read-write-timeout: 60000
          log-level: warn
        mapdns:
          address: '198.18.0.0'
          network: 15
          netmask: '255.254.0.0'
    """.trimIndent()
}
