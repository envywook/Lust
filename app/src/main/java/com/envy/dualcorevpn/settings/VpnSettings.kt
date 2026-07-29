package com.envy.dualcorevpn.settings

import com.envy.dualcorevpn.core.EngineKind
import com.envy.dualcorevpn.routing.RoutingMode
import com.envy.dualcorevpn.routing.RoutingPolicy

data class VpnSettings(
    val mtu: Int = DEFAULT_MTU,
    val dnsServer: String = DEFAULT_DNS,
    val ipv6Enabled: Boolean = true,
    val engine: EngineKind = EngineKind.XRAY,
    val routingMode: RoutingMode = RoutingMode.ALL,
    val routingRules: String = "",
    val smartConnectEnabled: Boolean = false,
    val subscriptionRefreshHours: Int = 0,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.OFF,
    val splitTunnelPackages: Set<String> = emptySet(),
) {
    val routingPolicy: RoutingPolicy
        get() = RoutingPolicy.parse(routingMode, routingRules)
    companion object {
        const val DEFAULT_MTU = 1500
        const val DEFAULT_DNS = "1.1.1.1"

        fun validate(
            mtu: String,
            dnsServer: String,
            ipv6Enabled: Boolean,
            engine: EngineKind = EngineKind.XRAY,
            routingMode: RoutingMode = RoutingMode.ALL,
            routingRules: String = "",
            smartConnectEnabled: Boolean = false,
            subscriptionRefreshHours: Int = 0,
            splitTunnelMode: SplitTunnelMode = SplitTunnelMode.OFF,
            splitTunnelPackages: Set<String> = emptySet(),
        ): VpnSettings {
            val parsedMtu = mtu.trim().toIntOrNull()
                ?: throw IllegalArgumentException("MTU должен быть числом")
            require(parsedMtu in 576..9000) { "MTU должен быть от 576 до 9000" }
            val dns = dnsServer.trim()
            require(dns.isNotEmpty()) { "DNS-сервер не указан" }
            require(dns.length <= 253 && dns.all { it.isLetterOrDigit() || it in ".:-_%" }) {
                "Некорректный адрес DNS-сервера"
            }
            val normalizedRules = if (routingMode == RoutingMode.CUSTOM) {
                routingRules.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
            } else {
                ""
            }
            RoutingPolicy.parse(routingMode, normalizedRules)
            require(subscriptionRefreshHours in setOf(0, 6, 12, 24)) { "Некорректный интервал обновления подписок" }
            return VpnSettings(
                parsedMtu,
                dns,
                ipv6Enabled,
                engine,
                routingMode,
                normalizedRules,
                smartConnectEnabled,
                subscriptionRefreshHours,
                splitTunnelMode,
                splitTunnelPackages.filter(String::isNotBlank).toSet(),
            )
        }
    }
}
