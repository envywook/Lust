package com.envy.dualcorevpn.settings

import android.content.Context
import com.envy.dualcorevpn.core.EngineKind
import com.envy.dualcorevpn.routing.RoutingMode

class VpnSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)

    fun load(): VpnSettings = VpnSettings(
        mtu = preferences.getInt(KEY_MTU, VpnSettings.DEFAULT_MTU),
        dnsServer = preferences.getString(KEY_DNS, VpnSettings.DEFAULT_DNS) ?: VpnSettings.DEFAULT_DNS,
        ipv6Enabled = preferences.getBoolean(KEY_IPV6, true),
        engine = runCatching {
            EngineKind.valueOf(preferences.getString(KEY_ENGINE, EngineKind.XRAY.name) ?: EngineKind.XRAY.name)
        }.getOrDefault(EngineKind.XRAY),
        routingMode = runCatching {
            RoutingMode.valueOf(preferences.getString(KEY_ROUTING_MODE, RoutingMode.ALL.name) ?: RoutingMode.ALL.name)
        }.getOrDefault(RoutingMode.ALL),
        routingRules = preferences.getString(KEY_ROUTING_RULES, "") ?: "",
        smartConnectEnabled = preferences.getBoolean(KEY_SMART_CONNECT, false),
        subscriptionRefreshHours = preferences.getInt(KEY_SUBSCRIPTION_REFRESH_HOURS, 0),
        splitTunnelMode = runCatching {
            SplitTunnelMode.valueOf(preferences.getString(KEY_SPLIT_TUNNEL_MODE, SplitTunnelMode.OFF.name) ?: SplitTunnelMode.OFF.name)
        }.getOrDefault(SplitTunnelMode.OFF),
        splitTunnelPackages = preferences.getStringSet(KEY_SPLIT_TUNNEL_PACKAGES, emptySet())?.toSet() ?: emptySet(),
    )

    fun save(settings: VpnSettings) {
        check(preferences.edit()
            .putInt(KEY_MTU, settings.mtu)
            .putString(KEY_DNS, settings.dnsServer)
            .putBoolean(KEY_IPV6, settings.ipv6Enabled)
            .putString(KEY_ENGINE, settings.engine.name)
            .putString(KEY_ROUTING_MODE, settings.routingMode.name)
            .putString(KEY_ROUTING_RULES, settings.routingRules)
            .putBoolean(KEY_SMART_CONNECT, settings.smartConnectEnabled)
            .putInt(KEY_SUBSCRIPTION_REFRESH_HOURS, settings.subscriptionRefreshHours)
            .putString(KEY_SPLIT_TUNNEL_MODE, settings.splitTunnelMode.name)
            .putStringSet(KEY_SPLIT_TUNNEL_PACKAGES, settings.splitTunnelPackages)
            .commit()) { "VPN settings could not be persisted" }
    }

    private companion object {
        const val KEY_MTU = "mtu"
        const val KEY_DNS = "dns_server"
        const val KEY_IPV6 = "ipv6_enabled"
        const val KEY_ENGINE = "engine"
        const val KEY_ROUTING_MODE = "routing_mode"
        const val KEY_ROUTING_RULES = "routing_rules"
        const val KEY_SMART_CONNECT = "smart_connect"
        const val KEY_SUBSCRIPTION_REFRESH_HOURS = "subscription_refresh_hours"
        const val KEY_SPLIT_TUNNEL_MODE = "split_tunnel_mode"
        const val KEY_SPLIT_TUNNEL_PACKAGES = "split_tunnel_packages"
    }
}
