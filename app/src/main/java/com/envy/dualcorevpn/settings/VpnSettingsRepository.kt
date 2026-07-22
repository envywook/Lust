package com.envy.dualcorevpn.settings

import android.content.Context
import com.envy.dualcorevpn.core.EngineKind

class VpnSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)

    fun load(): VpnSettings = VpnSettings(
        mtu = preferences.getInt(KEY_MTU, VpnSettings.DEFAULT_MTU),
        dnsServer = preferences.getString(KEY_DNS, VpnSettings.DEFAULT_DNS) ?: VpnSettings.DEFAULT_DNS,
        ipv6Enabled = preferences.getBoolean(KEY_IPV6, true),
        engine = runCatching {
            EngineKind.valueOf(preferences.getString(KEY_ENGINE, EngineKind.XRAY.name) ?: EngineKind.XRAY.name)
        }.getOrDefault(EngineKind.XRAY),
    )

    fun save(settings: VpnSettings) {
        preferences.edit()
            .putInt(KEY_MTU, settings.mtu)
            .putString(KEY_DNS, settings.dnsServer)
            .putBoolean(KEY_IPV6, settings.ipv6Enabled)
            .putString(KEY_ENGINE, settings.engine.name)
            .apply()
    }

    private companion object {
        const val KEY_MTU = "mtu"
        const val KEY_DNS = "dns_server"
        const val KEY_IPV6 = "ipv6_enabled"
        const val KEY_ENGINE = "engine"
    }
}
