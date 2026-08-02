package com.envy.dualcorevpn.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.envy.dualcorevpn.MainActivity
import com.envy.dualcorevpn.R
import com.envy.dualcorevpn.core.VpnSessionState
import com.envy.dualcorevpn.core.VpnSessionStore
import com.envy.dualcorevpn.subscription.SubscriptionRepository

class LustTileService : TileService() {
    override fun onStartListening() = updateTile()

    override fun onClick() {
        super.onClick()
        when (VpnSessionStore.state.value) {
            is VpnSessionState.Connected, is VpnSessionState.Connecting ->
                startService(Intent(this, DualCoreVpnService::class.java).setAction(DualCoreVpnService.ACTION_DISCONNECT))
            else -> connectOrOpenApp()
        }
        updateTile()
    }

    private fun connectOrOpenApp() {
        val server = SubscriptionRepository(this).let { repository ->
            repository.servers().firstOrNull { it.id == repository.selectedServerId() }
        }
        if (server == null || VpnService.prepare(this) != null) {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                startActivityAndCollapseCompat(intent)
            }
            return
        }
        val intent = Intent(this, DualCoreVpnService::class.java)
            .setAction(DualCoreVpnService.ACTION_CONNECT)
            .putExtra(DualCoreVpnService.EXTRA_XRAY_CONFIG, server.config)
            .putExtra(DualCoreVpnService.EXTRA_SERVER_NAME, server.name)
            .putExtra(DualCoreVpnService.EXTRA_SERVER_ID, server.id)
            .putExtra(DualCoreVpnService.EXTRA_SERVER_PROTOCOL, server.protocol)
            .putExtra(DualCoreVpnService.EXTRA_SERVER_ADDRESS, server.address)
            .putExtra(DualCoreVpnService.EXTRA_SERVER_PORT, server.port)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun updateTile() {
        val connected = VpnSessionStore.state.value is VpnSessionState.Connected
        qsTile?.apply {
            label = getString(R.string.quick_tile_label)
            state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (connected) R.string.status_connected else R.string.connection_not_connected)
            }
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) = startActivityAndCollapse(intent)
}
