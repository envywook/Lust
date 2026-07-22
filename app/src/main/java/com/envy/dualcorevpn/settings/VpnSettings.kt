package com.envy.dualcorevpn.settings

import com.envy.dualcorevpn.core.EngineKind

data class VpnSettings(
    val mtu: Int = DEFAULT_MTU,
    val dnsServer: String = DEFAULT_DNS,
    val ipv6Enabled: Boolean = true,
    val engine: EngineKind = EngineKind.XRAY,
) {
    companion object {
        const val DEFAULT_MTU = 1500
        const val DEFAULT_DNS = "1.1.1.1"

        fun validate(mtu: String, dnsServer: String, ipv6Enabled: Boolean, engine: EngineKind = EngineKind.XRAY): VpnSettings {
            val parsedMtu = mtu.trim().toIntOrNull()
                ?: throw IllegalArgumentException("MTU должен быть числом")
            require(parsedMtu in 576..9000) { "MTU должен быть от 576 до 9000" }
            val dns = dnsServer.trim()
            require(dns.isNotEmpty()) { "DNS-сервер не указан" }
            require(dns.length <= 253 && dns.all { it.isLetterOrDigit() || it in ".:-_%" }) {
                "Некорректный адрес DNS-сервера"
            }
            return VpnSettings(parsedMtu, dns, ipv6Enabled, engine)
        }
    }
}
