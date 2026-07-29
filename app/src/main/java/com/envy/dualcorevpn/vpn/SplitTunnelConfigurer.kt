package com.envy.dualcorevpn.vpn

import android.content.pm.PackageManager
import android.net.VpnService
import com.envy.dualcorevpn.settings.SplitTunnelMode
import com.envy.dualcorevpn.settings.VpnSettings
import com.envy.dualcorevpn.logging.AppLog

internal fun VpnService.Builder.applySplitTunnel(
    settings: VpnSettings,
    ownPackage: String,
): VpnService.Builder {
    val packages = settings.splitTunnelPackages.filterNot { it == ownPackage }.sorted()
    when (settings.splitTunnelMode) {
        SplitTunnelMode.OFF -> addDisallowedApplication(ownPackage)
        SplitTunnelMode.ONLY_SELECTED -> {
            require(packages.isNotEmpty()) { "Split tunneling requires at least one installed application" }
            packages.forEach { packageName ->
                runCatching { addAllowedApplication(packageName) }
                    .onFailure { if (it is PackageManager.NameNotFoundException) AppLog.info("VPN", "Split app was removed: $packageName") else throw it }
            }
        }
        SplitTunnelMode.EXCLUDE_SELECTED -> (packages + ownPackage).forEach { packageName ->
            runCatching { addDisallowedApplication(packageName) }
                .onFailure { if (it is PackageManager.NameNotFoundException) AppLog.info("VPN", "Split app was removed: $packageName") else throw it }
        }
    }
    return this
}
