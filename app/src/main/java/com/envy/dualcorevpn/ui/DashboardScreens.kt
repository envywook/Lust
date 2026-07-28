package com.envy.dualcorevpn.ui

import android.content.Intent
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Process
import androidx.compose.animation.AnimatedContent

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.envy.dualcorevpn.R
import com.envy.dualcorevpn.server.ServerLatencyResult
import com.envy.dualcorevpn.speed.NetworkSpeedTester
import com.envy.dualcorevpn.speed.SpeedTestPhase
import com.envy.dualcorevpn.speed.SpeedTestSnapshot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envy.dualcorevpn.BuildConfig
import com.envy.dualcorevpn.core.VpnSessionState
import com.envy.dualcorevpn.subscription.ServerProfile
import com.envy.dualcorevpn.subscription.Subscription
import java.net.URI
import java.util.Locale
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF080A09)
private val Panel = Color(0xFF151817)
private val PanelHigh = Color(0xFF101211)
private val Mint = Color(0xFFA6F3D1)
private val TextMain = Color(0xFFF4F6F5)
private val TextMuted = Color(0xFF9EA5A1)
private val Border = Color(0xFF343936)
private val Danger = Color(0xFFFF6B78)

internal data class DashboardStrings(
    val speed: String,
    val home: String,
    val settings: String,
    val download: String,
    val upload: String,
    val notConnected: String,
    val connected: String,
    val connecting: String,
    val disconnecting: String,
    val connectHint: String,
    val disconnectHint: String,
    val selectServer: String,
    val fastestServer: String,
    val noServers: String,
    val speedTitle: String,
    val speedSubtitle: String,
    val servers: String,
    val baseServers: String,
    val plusServers: String,
    val otherServers: String,
    val manageSubscriptions: String,
    val telegramNews: String,
)

@Composable
internal fun dashboardStrings() = DashboardStrings(
    speed = stringResource(com.envy.dualcorevpn.R.string.nav_speed),
    home = stringResource(com.envy.dualcorevpn.R.string.nav_home),
    settings = stringResource(com.envy.dualcorevpn.R.string.nav_settings),
    download = stringResource(com.envy.dualcorevpn.R.string.metric_download),
    upload = stringResource(com.envy.dualcorevpn.R.string.metric_upload),
    notConnected = stringResource(com.envy.dualcorevpn.R.string.connection_not_connected),
    connected = stringResource(com.envy.dualcorevpn.R.string.connection_connected),
    connecting = stringResource(com.envy.dualcorevpn.R.string.connection_connecting),
    disconnecting = stringResource(com.envy.dualcorevpn.R.string.connection_disconnecting),
    connectHint = stringResource(com.envy.dualcorevpn.R.string.connection_tap_connect),
    disconnectHint = stringResource(com.envy.dualcorevpn.R.string.connection_tap_disconnect),
    selectServer = stringResource(com.envy.dualcorevpn.R.string.server_select),
    fastestServer = stringResource(com.envy.dualcorevpn.R.string.server_optimal),
    noServers = stringResource(com.envy.dualcorevpn.R.string.server_empty),
    speedTitle = stringResource(com.envy.dualcorevpn.R.string.speed_title),
    speedSubtitle = stringResource(com.envy.dualcorevpn.R.string.speed_current_download),
    servers = stringResource(com.envy.dualcorevpn.R.string.servers_title),
    baseServers = stringResource(com.envy.dualcorevpn.R.string.servers_base),
    plusServers = stringResource(com.envy.dualcorevpn.R.string.servers_plus),
    otherServers = stringResource(com.envy.dualcorevpn.R.string.servers_other),
    manageSubscriptions = stringResource(com.envy.dualcorevpn.R.string.subscriptions_manage),
    telegramNews = stringResource(com.envy.dualcorevpn.R.string.telegram_news),
)

internal data class LiveRates(val downloadBytesPerSecond: Long = 0, val uploadBytesPerSecond: Long = 0)

@Composable
internal fun rememberLiveRates(active: Boolean): LiveRates {
    var rates by remember { mutableStateOf(LiveRates()) }
    var previousRx by remember { mutableLongStateOf(TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0L)) }
    var previousTx by remember { mutableLongStateOf(TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0L)) }
    LaunchedEffect(active) {
        if (!active) {
            rates = LiveRates()
            return@LaunchedEffect
        }
        previousRx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0L)
        previousTx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0L)
        while (true) {
            delay(1_000)
            val rx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(previousRx)
            val tx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(previousTx)
            rates = LiveRates(rx - previousRx, tx - previousTx)
            previousRx = rx
            previousTx = tx
        }
    }
    return rates
}

@Composable
internal fun DashboardHeader() {
    val context = LocalContext.current
    val strings = dashboardStrings()
    val telegramUri = remember {
        BuildConfig.MAXSPEED_TELEGRAM_URL.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?.takeIf { it.scheme.equals("https", ignoreCase = true) && it.host.equals("t.me", ignoreCase = true) }
    }
    val telegramIntent = telegramUri?.let { Intent(Intent.ACTION_VIEW, it) }
    val telegramEnabled = telegramIntent?.resolveActivity(context.packageManager) != null
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("MaxSpeedVPN", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        IconButton(
            enabled = telegramEnabled,
            onClick = { telegramIntent?.let(context::startActivity) },
            modifier = Modifier.semantics { contentDescription = strings.telegramNews },
        ) { TelegramMark(Modifier.size(26.dp), if (telegramEnabled) TextMain else TextMuted) }
    }
}

@Composable
internal fun HomeDashboard(
    state: VpnSessionState,
    selected: ServerProfile?,
    servers: List<ServerProfile>,
    subscriptions: List<Subscription>,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (ServerProfile) -> Unit,
    onManageSubscriptions: () -> Unit,
) {
    val strings = dashboardStrings()
    val connected = state is VpnSessionState.Connected
    val busy = state is VpnSessionState.Connecting || state is VpnSessionState.Disconnecting
    val rates = rememberLiveRates(connected)
    Column(Modifier.fillMaxSize().background(Bg)) {
        DashboardHeader()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(strings.download, rates.downloadBytesPerSecond, Modifier.weight(1f))
                MetricCard(strings.upload, rates.uploadBytesPerSecond, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            ConnectionControl(
                state = state,
                enabled = selected != null || connected || busy,
                onClick = { if (connected || busy) onDisconnect() else selected?.let { onConnect(it.config) } },
            )
            Spacer(Modifier.height(26.dp))
            ServerSlider(selected, servers, subscriptions, onSelect, onManageSubscriptions)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetricCard(label: String, bytesPerSecond: Long, modifier: Modifier) {
    val (value, unit) = formatRate(bytesPerSecond)
    var history by remember { mutableStateOf(List(12) { 0L }) }
    LaunchedEffect(bytesPerSecond) { history = (history + bytesPerSecond).takeLast(12) }
    Card(
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Border),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF1B1F1D), PanelHigh)))
                .padding(horizontal = 15.dp, vertical = 14.dp),
        ) {
            Text(label, color = TextMuted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(value, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
            }
            Spacer(Modifier.height(5.dp))
            MiniWave(history, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun MiniWave(history: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path()
        val max = history.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
        history.forEachIndexed { index, sample ->
            val x = size.width * index / history.lastIndex.coerceAtLeast(1)
            val fraction = if (max <= 1f) {
                .58f + sin(index * 1.7).toFloat() * .12f
            } else {
                1f - sample / max * .78f
            }
            val y = size.height * fraction.coerceIn(.12f, .88f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Mint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
@Composable
private fun ConnectionControl(state: VpnSessionState, enabled: Boolean, onClick: () -> Unit) {
    val strings = dashboardStrings()
    val connected = state is VpnSessionState.Connected
    val busy = state is VpnSessionState.Connecting || state is VpnSessionState.Disconnecting
    val title = when (state) {
        VpnSessionState.Disconnected -> strings.notConnected
        is VpnSessionState.Connecting -> strings.connecting
        is VpnSessionState.Connected -> strings.connected
        is VpnSessionState.Disconnecting -> strings.disconnecting
        is VpnSessionState.Error -> state.message
    }
    val buttonScale by animateFloatAsState(if (connected) 1.035f else 1f, tween(220), label = "connectionScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(178.dp)
                .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                .background(Brush.radialGradient(listOf(Color(0xFF1B201E), PanelHigh)), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = if (connected || busy) strings.disconnectHint else strings.connectHint
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF0D0F0E), radius = size.minDimension / 2 - 4.dp.toPx(), style = Stroke(7.dp.toPx()))
                drawCircle(if (enabled) Mint.copy(alpha = .62f) else Border, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(Color(0xFF3B423F), radius = size.minDimension / 2 - 9.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            PowerMark(Modifier.size(65.dp), if (enabled) Mint else TextMuted)
        }
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "connectionStatus",
        ) { value ->
            Text(value, color = if (state is VpnSessionState.Error) Danger else TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(if (connected || busy) strings.disconnectHint else strings.connectHint, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ServerSlider(
    selected: ServerProfile?,
    servers: List<ServerProfile>,
    subscriptions: List<Subscription>,
    onSelect: (ServerProfile) -> Unit,
    onManageSubscriptions: () -> Unit,
) {
    val strings = dashboardStrings()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth().height(154.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (servers.isEmpty()) Mint.copy(alpha = .62f) else Border),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        if (servers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(onClick = onManageSubscriptions),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(46.dp).background(Mint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = Bg, fontSize = 30.sp, fontWeight = FontWeight.Medium) }
                    Spacer(Modifier.height(12.dp))
                    Text(strings.noServers, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val selectedIndex = servers.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
            val dragOffset = remember { Animatable(0f) }
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                val slotWidthPx = constraints.maxWidth / 3f

                fun move(step: Int) {
                    if (step == 0 || servers.size < 2 || dragOffset.isRunning) return
                    scope.launch {
                        val target = -step * slotWidthPx
                        dragOffset.animateTo(target, tween(280, easing = FastOutSlowInEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(servers[(selectedIndex + step).floorMod(servers.size)])
                        dragOffset.snapTo(0f)
                    }
                }

                val dragState = rememberDraggableState { delta ->
                    scope.launch {
                        dragOffset.snapTo((dragOffset.value + delta).coerceIn(-slotWidthPx, slotWidthPx))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = servers.size > 1,
                        onDragStopped = { velocity ->
                            val step = carouselStep(dragOffset.value, velocity, slotWidthPx)
                            if (step == 0) scope.launch {
                                dragOffset.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                            } else move(step)
                        },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(58.dp)) {
                        (if (servers.size == 1) listOf(0) else (-2..2).toList()).forEach { relative ->
                            val server = servers[(selectedIndex + relative).floorMod(servers.size)]
                            Box(
                                modifier = Modifier.align(Alignment.Center).size(54.dp).graphicsLayer {
                                    val x = relative * slotWidthPx + dragOffset.value
                                    translationX = x
                                    val distance = (abs(x) / slotWidthPx).coerceIn(0f, 1f)
                                    val scale = 1f - distance * .26f
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 1f - distance * .58f
                                }.clickable { move(relative.coerceIn(-1, 1)) },
                                contentAlignment = Alignment.Center,
                            ) { Text(serverFlag(server), fontSize = 34.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { move(-1) }, enabled = servers.size > 1, modifier = Modifier.size(28.dp)) {
                            Text("‹", color = TextMuted, fontSize = 26.sp)
                        }
                        IconButton(onClick = { move(1) }, enabled = servers.size > 1, modifier = Modifier.size(28.dp)) {
                            Text("›", color = TextMuted, fontSize = 26.sp)
                        }
                    }
                    val current = servers[selectedIndex]
                    val currentDescription = subscriptions.firstOrNull { it.id == current.subscriptionId }?.name.orEmpty()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            localizedServerName(current.name),
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (currentDescription.isNotBlank()) Text(currentDescription, color = Mint, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

internal fun carouselStep(offsetPx: Float, velocityPxPerSecond: Float, slotWidthPx: Float): Int {
    if (slotWidthPx <= 0f) return 0
    val swipedLeft = offsetPx <= -slotWidthPx * .22f || velocityPxPerSecond <= -650f
    val swipedRight = offsetPx >= slotWidthPx * .22f || velocityPxPerSecond >= 650f
    return when {
        swipedLeft -> 1
        swipedRight -> -1
        else -> 0
    }
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

internal data class ServerSections(
    val base: List<ServerProfile>,
    val plus: List<ServerProfile>,
    val others: List<ServerProfile>,
    val managed: Boolean,
)

internal fun planServerSections(servers: List<ServerProfile>, subscriptions: List<Subscription>, managedHosts: String): ServerSections {
    val hosts = managedHosts.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val managedIds = subscriptions.filter { subscription ->
        runCatching { URI(subscription.url).host?.lowercase() in hosts }.getOrDefault(false)
    }.mapTo(mutableSetOf()) { it.id }
    if (managedIds.isEmpty()) return ServerSections(servers, emptyList(), emptyList(), false)
    val owned = servers.filter { it.subscriptionId in managedIds }
    val plus = owned.filter { it.name.startsWith("[plus]", true) || it.name.startsWith("plus:", true) }
    return ServerSections(owned - plus.toSet(), plus, servers.filterNot { it.subscriptionId in managedIds }, true)
}

@Composable
internal fun SpeedDashboard(
    state: VpnSessionState,
    selected: ServerProfile?,
    servers: List<ServerProfile>,
    subscriptions: List<Subscription>,
    latencyResults: Map<String, ServerLatencyResult>,
    latencyTesting: Boolean,
    latencyTestingIds: Set<String>,
    onTestLatency: () -> Unit,
    onTestServerLatency: (ServerProfile) -> Unit,
    onSelect: (ServerProfile) -> Unit,
    onManageSubscriptions: () -> Unit,
) {
    val strings = dashboardStrings()
    val rates = rememberLiveRates(state is VpnSessionState.Connected)
    val sections = remember(servers, subscriptions) { planServerSections(servers, subscriptions, BuildConfig.MAXSPEED_SUBSCRIPTION_HOSTS) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        DashboardHeader()
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item { SpeedGauge(rates.downloadBytesPerSecond, strings) }
        if (sections.managed) {
            if (sections.base.isNotEmpty()) { item { SectionTitle(strings.baseServers, latencyTesting, onTestLatency) }; item { ServerListCard(sections.base, selected, latencyResults, latencyTestingIds, onSelect, onTestServerLatency, false) } }
            if (sections.plus.isNotEmpty()) { item { SectionTitle(strings.plusServers) }; item { ServerListCard(sections.plus, selected, latencyResults, latencyTestingIds, onSelect, onTestServerLatency, true) } }
            if (sections.others.isNotEmpty()) { item { SectionTitle(strings.otherServers) }; item { ServerListCard(sections.others, selected, latencyResults, latencyTestingIds, onSelect, onTestServerLatency, false) } }
        } else {
            item { SectionTitle(strings.servers, latencyTesting, onTestLatency) }
            item { ServerListCard(sections.base, selected, latencyResults, latencyTestingIds, onSelect, onTestServerLatency, false) }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onManageSubscriptions),
                shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Border),
            ) { Text(strings.manageSubscriptions, color = Mint, modifier = Modifier.padding(17.dp), fontWeight = FontWeight.SemiBold) }
        }
        item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

internal fun speedScaleMbps(megabitsPerSecond: Double): Int = when {
    megabitsPerSecond <= 1.0 -> 1
    megabitsPerSecond <= 10.0 -> 10
    megabitsPerSecond <= 100.0 -> 100
    megabitsPerSecond <= 500.0 -> 500
    else -> 1_000
}

internal fun formatSpeedMbps(megabitsPerSecond: Double, scale: Int): String = when {
    megabitsPerSecond == 0.0 -> "0"
    scale <= 1 -> "%.2f".format(java.util.Locale.ROOT, megabitsPerSecond)
    scale <= 10 -> "%.1f".format(java.util.Locale.ROOT, megabitsPerSecond)
    else -> "%.0f".format(java.util.Locale.ROOT, megabitsPerSecond)
}

internal fun formatSpeedTick(value: Double, scale: Int): String = when {
    scale <= 1 && (value == 0.0 || value == 1.0) -> "%.0f".format(java.util.Locale.ROOT, value)
    scale <= 1 -> "%.1f".format(java.util.Locale.ROOT, value)
    else -> "%.0f".format(java.util.Locale.ROOT, value)
}

@Composable
private fun SpeedGauge(bytesPerSecond: Long, strings: DashboardStrings) {
    var snapshot by remember { mutableStateOf(SpeedTestSnapshot()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val running = snapshot.phase == SpeedTestPhase.DOWNLOAD || snapshot.phase == SpeedTestPhase.UPLOAD
    val liveMbps = bytesPerSecond * 8.0 / 1_000_000.0
    val mbps = if (snapshot.phase == SpeedTestPhase.IDLE) liveMbps else snapshot.megabitsPerSecond
    val scale = speedScaleMbps(mbps)
    val targetProgress = (mbps / scale).coerceIn(0.0, 1.0).toFloat()
    val progress by animateFloatAsState(targetProgress, tween(260), label = "gaugeNeedle")
    val phaseLabel = when (snapshot.phase) {
        SpeedTestPhase.DOWNLOAD -> strings.download
        SpeedTestPhase.UPLOAD -> strings.upload
        SpeedTestPhase.COMPLETE -> "${strings.download} ${snapshot.downloadMbps?.roundToInt()} · ${strings.upload} ${snapshot.uploadMbps?.roundToInt()}"
        SpeedTestPhase.IDLE -> strings.speedSubtitle
    }
    val runTest = {
        confirm = false
        error = null
        scope.launch {
            runCatching { NetworkSpeedTester().run { value -> scope.launch { snapshot = value } } }
                .onFailure { error = it.message ?: it.javaClass.simpleName; snapshot = SpeedTestSnapshot() }
        }
        Unit
    }
    Card(
        modifier = Modifier.fillMaxWidth().height(257.dp).clickable(enabled = !running) { confirm = true },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Border),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(176.dp).align(Alignment.TopCenter)) {
                val stroke = 13.dp.toPx()
                val radius = size.width / 2 - 28.dp.toPx()
                val center = Offset(size.width / 2, size.height - 22.dp.toPx())
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                drawArc(Border, 180f, 180f, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Mint, 180f, 180f * progress, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(154, 161, 157)
                    textSize = 10.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                repeat(11) { index ->
                    val angle = Math.toRadians((180 + index * 18).toDouble())
                    val outer = radius - 14.dp.toPx()
                    val inner = outer - if (index % 2 == 0) 10.dp.toPx() else 6.dp.toPx()
                    drawLine(TextMuted, Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner), Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer), 1.dp.toPx())
                    if (index % 2 == 0) {
                        val value = scale * index / 10.0
                        val labelRadius = radius - 42.dp.toPx()
                        val x = center.x + cos(angle).toFloat() * labelRadius
                        val y = center.y + sin(angle).toFloat() * labelRadius - (paint.ascent() + paint.descent()) / 2
                        drawContext.canvas.nativeCanvas.drawText(formatSpeedTick(value, scale), x, y, paint)
                    }
                }
                val needleAngle = Math.toRadians((180 + 180 * progress).toDouble())
                val needleRadius = radius - 48.dp.toPx()
                drawLine(Mint, center, Offset(center.x + cos(needleAngle).toFloat() * needleRadius, center.y + sin(needleAngle).toFloat() * needleRadius), 4.dp.toPx(), StrokeCap.Round)
                drawCircle(PanelHigh, 10.dp.toPx(), center)
                drawCircle(Mint, 3.dp.toPx(), center)
            }
            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                if (running) CircularProgressIndicator(Modifier.size(20.dp), color = Mint, strokeWidth = 2.dp)
                Text(formatSpeedMbps(mbps, scale), color = TextMain, fontSize = 37.sp, fontWeight = FontWeight.Bold)
                Text(error ?: "Mbps · $phaseLabel", color = if (error == null) TextMuted else Color(0xFFFF7A7A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (confirm) {
        val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.speed_test_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.speed_test_confirm_body,
                        if (metered) stringResource(R.string.speed_test_metered_note) else "",
                    ),
                )
            },
            confirmButton = { TextButton(onClick = runTest) { Text(stringResource(R.string.speed_test_start)) } },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.speed_test_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, loading: Boolean = false, onTestLatency: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(top = 4.dp))
        if (onTestLatency != null) IconButton(onClick = onTestLatency, enabled = !loading) {
            AnimatedContent(
                targetState = loading,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "pingAll",
            ) { active ->
                if (active) CircularProgressIndicator(Modifier.size(22.dp), color = Mint, strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_network_check), contentDescription = stringResource(R.string.server_ping_all), tint = Mint, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerListCard(
    items: List<ServerProfile>,
    selected: ServerProfile?,
    latencyResults: Map<String, ServerLatencyResult>,
    latencyTestingIds: Set<String>,
    onSelect: (ServerProfile) -> Unit,
    onTestLatency: (ServerProfile) -> Unit,
    locked: Boolean,
) {
    var menuServer by remember { mutableStateOf<ServerProfile?>(null) }
    var configServer by remember { mutableStateOf<ServerProfile?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val pingLabel = stringResource(R.string.server_ping)
    val viewConfigLabel = stringResource(R.string.server_view_config)
    val shareLabel = stringResource(R.string.server_share_config)
    val closeLabel = stringResource(R.string.server_close_config)
    val shareChooserLabel = stringResource(R.string.server_share_config_chooser)
    Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Border), colors = CardDefaults.cardColors(containerColor = Panel)) {
        if (items.isEmpty()) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text("—", color = TextMuted) }
        items.forEach { server ->
            Box {
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onSelect(server) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuServer = server
                    },
                ).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = PanelHigh, modifier = Modifier.size(38.dp)) { Box(contentAlignment = Alignment.Center) { Text(serverFlag(server), fontSize = 19.sp) } }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(localizedServerName(cleanServerName(server.name)), color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (server.id == selected?.id) FontWeight.Bold else FontWeight.Medium)
                    Text("${server.protocol.uppercase()} · ${server.address}", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (server.id in latencyTestingIds) CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                else latencyResults[server.id]?.let { result ->
                    Text(result.latencyMillis?.let { "$it ms" } ?: "!", color = if (result.latencyMillis != null) Mint else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                if (locked) Text("♙", color = Mint, fontSize = 18.sp)
                Text("›", color = if (server.id == selected?.id) Mint else TextMuted, fontSize = 24.sp)
            }
            DropdownMenu(expanded = menuServer?.id == server.id, onDismissRequest = { menuServer = null }) {
                DropdownMenuItem(
                    text = { Text(pingLabel) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_network_check), null, Modifier.size(22.dp)) },
                    onClick = { menuServer = null; onTestLatency(server) },
                )
                DropdownMenuItem(
                    text = { Text(viewConfigLabel) },
                    leadingIcon = { Text("ⓘ", fontSize = 20.sp) },
                    onClick = { menuServer = null; configServer = server },
                )
            }
            }
        }
    }
    configServer?.let { server ->
        AlertDialog(
            onDismissRequest = { configServer = null },
            title = { Text(localizedServerName(cleanServerName(server.name))) },
            text = { Text(server.config, maxLines = 18, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, server.config)
                    }, shareChooserLabel))
                }) { Text(shareLabel) }
            },
            dismissButton = { TextButton(onClick = { configServer = null }) { Text(closeLabel) } },
        )
    }
}

private fun cleanServerName(name: String): String = name.replace(Regex("^(\\[plus]|plus:)\\s*", RegexOption.IGNORE_CASE), "")

@Composable
private fun formatRate(bytesPerSecond: Long): Pair<String, String> {
    val bits = bytesPerSecond * 8.0
    return when {
        bits >= 1_000_000 -> "%.1f".format(Locale.US, bits / 1_000_000) to stringResource(com.envy.dualcorevpn.R.string.unit_mbps)
        bits >= 1_000 -> "%.0f".format(Locale.US, bits / 1_000) to stringResource(com.envy.dualcorevpn.R.string.unit_kbps)
        else -> bits.roundToInt().toString() to stringResource(com.envy.dualcorevpn.R.string.unit_bps)
    }
}

@Composable
private fun localizedServerName(name: String): String = when (name.trim().lowercase()) {
    "canada" -> stringResource(com.envy.dualcorevpn.R.string.country_canada)
    "germany" -> stringResource(com.envy.dualcorevpn.R.string.country_germany)
    "ireland" -> stringResource(com.envy.dualcorevpn.R.string.country_ireland)
    "china" -> stringResource(com.envy.dualcorevpn.R.string.country_china)
    "united states", "usa" -> stringResource(com.envy.dualcorevpn.R.string.country_united_states)
    "russia" -> stringResource(com.envy.dualcorevpn.R.string.country_russia)
    else -> name
}

private fun serverFlag(server: ServerProfile): String = serverFlagFromName(server.name)

internal fun serverFlagFromName(name: String): String {
    val value = name.lowercase()
    fun matches(vararg aliases: String): Boolean = aliases.any { alias ->
        if (alias.any(Char::isLetter)) {
            Regex("(^|[^\\p{L}])${Regex.escape(alias)}([^\\p{L}]|$)").containsMatchIn(value)
        } else {
            value.contains(alias)
        }
    }
    return when {
        matches("🇦🇹", "austria", "австрия", "österreich", "vienna", "вена", "at") -> "🇦🇹"
        matches("🇨🇦", "canada", "канада", "toronto", "торонто", "ca") -> "🇨🇦"
        matches("🇩🇪", "germany", "германия", "deutschland", "берлин", "berlin", "de") -> "🇩🇪"
        matches("🇮🇪", "ireland", "ирландия", "dublin", "дублин", "ie") -> "🇮🇪"
        matches("🇨🇳", "china", "китай", "beijing", "пекин", "cn") -> "🇨🇳"
        matches("🇺🇸", "united states", "сша", "usa", "new york", "нью-йорк", "us") -> "🇺🇸"
        matches("🇷🇺", "russia", "россия", "moscow", "москва", "ru") -> "🇷🇺"
        matches("🇬🇧", "united kingdom", "великобритания", "англия", "london", "лондон", "uk", "gb") -> "🇬🇧"
        matches("🇳🇱", "netherlands", "нидерланды", "holland", "голландия", "amsterdam", "амстердам", "nl") -> "🇳🇱"
        matches("🇫🇷", "france", "франция", "paris", "париж", "fr") -> "🇫🇷"
        matches("🇫🇮", "finland", "финляндия", "helsinki", "хельсинки", "fi") -> "🇫🇮"
        matches("🇸🇪", "sweden", "швеция", "stockholm", "стокгольм", "se") -> "🇸🇪"
        matches("🇵🇱", "poland", "польша", "warsaw", "варшава", "pl") -> "🇵🇱"
        matches("🇨🇭", "switzerland", "швейцария", "zurich", "zürich", "цюрих", "ch") -> "🇨🇭"
        matches("🇯🇵", "japan", "япония", "tokyo", "токио", "jp") -> "🇯🇵"
        matches("🇸🇬", "singapore", "сингапур", "sg") -> "🇸🇬"
        else -> "🌐"
    }
}

@Composable
private fun MiniWave(modifier: Modifier) { Canvas(modifier) { val p = Path().apply { moveTo(0f, size.height * .7f); cubicTo(size.width * .2f, size.height * .1f, size.width * .32f, size.height, size.width * .5f, size.height * .45f); cubicTo(size.width * .68f, 0f, size.width * .82f, size.height * .8f, size.width, size.height * .25f) }; drawPath(p, Mint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)) } }

@Composable
private fun PowerMark(modifier: Modifier, color: Color) { Canvas(modifier) { val stroke = 5.dp.toPx(); drawArc(color, -45f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round)); drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * .48f), stroke, StrokeCap.Round) } }

@Composable
private fun TelegramMark(modifier: Modifier, color: Color) { Canvas(modifier) { val p = Path().apply { moveTo(size.width * .08f, size.height * .47f); lineTo(size.width * .9f, size.height * .1f); lineTo(size.width * .7f, size.height * .9f); lineTo(size.width * .43f, size.height * .68f); lineTo(size.width * .27f, size.height * .83f); lineTo(size.width * .3f, size.height * .61f); close() }; drawPath(p, color); drawLine(Bg, Offset(size.width * .31f, size.height * .6f), Offset(size.width * .72f, size.height * .31f), 2.dp.toPx()) } }
