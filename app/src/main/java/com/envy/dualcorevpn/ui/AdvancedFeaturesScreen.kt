package com.envy.dualcorevpn.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envy.dualcorevpn.R
import com.envy.dualcorevpn.settings.SplitTunnelMode
import com.envy.dualcorevpn.settings.VpnSettings
import com.envy.dualcorevpn.vpn.LustTileService

internal data class LaunchableApp(val label: String, val packageName: String)

@Composable
internal fun AdvancedFeaturesScreen(
    initial: VpnSettings,
    onBack: () -> Unit,
    onSave: (VpnSettings) -> Unit,
    onScanQr: () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember(initial) { mutableStateOf(initial) }
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .map { LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .filterNot { it.packageName == context.packageName }
            .distinctBy(LaunchableApp::packageName)
            .sortedBy { it.label.lowercase() }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("‹") }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.features_title), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.features_subtitle), color = Color(0xFFA0A5A2), fontSize = 11.sp)
                }
            }
        }
        item {
            FeatureCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_connect_title), color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.smart_connect_description), color = Color(0xFFA0A5A2), fontSize = 11.sp)
                    }
                    Switch(checked = settings.smartConnectEnabled, onCheckedChange = { settings = settings.copy(smartConnectEnabled = it) })
                }
            }
        }
        item {
            FeatureCard {
                Text(stringResource(R.string.auto_refresh_title), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.auto_refresh_description), color = Color(0xFFA0A5A2), fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(0, 6, 12, 24).forEach { hours ->
                        OutlinedButton(
                            onClick = { settings = settings.copy(subscriptionRefreshHours = hours) },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (hours == 0) stringResource(R.string.off) else "${hours}h", color = if (settings.subscriptionRefreshHours == hours) Color(0xFFA6F3D1) else Color.White) }
                    }
                }
            }
        }
        item {
            FeatureCard {
                Text(stringResource(R.string.split_tunnel_title), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.split_tunnel_description), color = Color(0xFFA0A5A2), fontSize = 11.sp)
                SplitTunnelMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { settings = settings.copy(splitTunnelMode = mode) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.splitTunnelMode == mode, onClick = { settings = settings.copy(splitTunnelMode = mode) })
                        Text(stringResource(mode.labelRes()), color = Color.White)
                    }
                }
            }
        }
        if (settings.splitTunnelMode != SplitTunnelMode.OFF) {
            item { Text(stringResource(R.string.split_tunnel_apps), color = Color(0xFFA6F3D1), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(apps, key = LaunchableApp::packageName) { app ->
                val selected = app.packageName in settings.splitTunnelPackages
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF171B19), RoundedCornerShape(14.dp))
                        .clickable {
                            settings = settings.copy(splitTunnelPackages = if (selected) settings.splitTunnelPackages - app.packageName else settings.splitTunnelPackages + app.packageName)
                        }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName,
                            color = Color(0xFFA0A5A2),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item {
            FeatureCard {
                Text(stringResource(R.string.system_controls_title), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.system_controls_description), color = Color(0xFFA0A5A2), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScanQr, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_qr)) }
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                ComponentName(context, LustTileService::class.java),
                                context.getString(R.string.quick_tile_label),
                                Icon.createWithResource(context, R.drawable.ic_stat_vpn),
                                context.mainExecutor,
                            ) { }
                        } else context.startActivity(Intent("android.settings.QUICK_SETTINGS"))
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.add_quick_tile)) }
                }
            }
        }
        item {
            Button(
                onClick = { onSave(settings) },
                enabled = settings.splitTunnelMode != SplitTunnelMode.ONLY_SELECTED || settings.splitTunnelPackages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6F3D1), contentColor = Color(0xFF08110D)),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FeatureCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF171B19), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun SplitTunnelMode.labelRes(): Int = when (this) {
    SplitTunnelMode.OFF -> R.string.split_tunnel_off
    SplitTunnelMode.ONLY_SELECTED -> R.string.split_tunnel_only
    SplitTunnelMode.EXCLUDE_SELECTED -> R.string.split_tunnel_exclude
}
