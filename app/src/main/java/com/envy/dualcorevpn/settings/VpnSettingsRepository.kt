package com.envy.dualcorevpn.settings

import android.content.Context

class VpnSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)

    fun load(): VpnSettings = VpnSettings(
        mtu = preferences.getInt(KEY_MTU, VpnSettings.DEFAULT_MTU),
        dnsServer = preferences.getString(KEY_DNS, VpnSettings.DEFAULT_DNS) ?: VpnSettings.DEFAULT_DNS,
        ipv6Enabled = preferences.getBoolean(KEY_IPV6, true),
    )

    fun save(settings: VpnSettings) {
        preferences.edit()
            .putInt(KEY_MTU, settings.mtu)
            .putString(KEY_DNS, settings.dnsServer)
            .putBoolean(KEY_IPV6, settings.ipv6Enabled)
            .apply()
    }

    private companion object {
        const val KEY_MTU = "mtu"
        const val KEY_DNS = "dns_server"
        const val KEY_IPV6 = "ipv6_enabled"
    }
}
