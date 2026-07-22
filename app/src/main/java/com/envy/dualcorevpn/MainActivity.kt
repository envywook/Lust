package com.envy.dualcorevpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.envy.dualcorevpn.core.EngineKind
import com.envy.dualcorevpn.core.VpnSessionState
import com.envy.dualcorevpn.core.VpnSessionStore
import com.envy.dualcorevpn.logging.AppLog
import com.envy.dualcorevpn.logging.LogEntry
import com.envy.dualcorevpn.logging.LogFilter
import com.envy.dualcorevpn.logging.LogLevel
import com.envy.dualcorevpn.server.ServerLatencyResult
import com.envy.dualcorevpn.server.ServerLatencyTester
import com.envy.dualcorevpn.settings.VpnSettings
import com.envy.dualcorevpn.settings.VpnSettingsRepository
import com.envy.dualcorevpn.subscription.ServerProfile
import com.envy.dualcorevpn.subscription.Subscription
import com.envy.dualcorevpn.subscription.SubscriptionRepository
import com.envy.dualcorevpn.vpn.DualCoreVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF090D12)
private val SurfaceColor = Color(0xFF111820)
private val SurfaceRaised = Color(0xFF17212B)
private val SurfaceStrong = Color(0xFF1D2A36)
private val Accent = Color(0xFF65D9FF)
private val AccentSoft = Color(0xFF173443)
private val ContentPrimary = Color(0xFFF2F7FA)
private val Muted = Color(0xFF91A1AF)
private val Outline = Color(0xFF273744)
private val Success = Color(0xFF55E39A)
private val Warning = Color(0xFFFFC66D)
private val Danger = Color(0xFFFF7280)

class MainActivity : ComponentActivity() {
    private lateinit var repository: SubscriptionRepository
    private lateinit var settingsRepository: VpnSettingsRepository
    private var vpnSettings by mutableStateOf(VpnSettings())
    private var permissionResult: ((Boolean) -> Unit)? = null
    private var pendingConfig: String? = null
    private var reloadUi by mutableStateOf(0)
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var latencyResults by mutableStateOf<Map<String, ServerLatencyResult>>(emptyMap())
    private var latencyTesting by mutableStateOf(false)

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        permissionResult?.invoke(result.resultCode == RESULT_OK)
        permissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SubscriptionRepository(applicationContext)
        settingsRepository = VpnSettingsRepository(applicationContext)
        vpnSettings = settingsRepository.load()
        AppLog.initialize(java.io.File(filesDir, "logs"))
        AppLog.info("UI", "Application opened")
        setContent {
            LustTheme {
                LustApp(
                    revision = reloadUi,
                    repository = repository,
                    loading = loading,
                    message = message,
                    onDismissMessage = { message = null },
                    onConnect = ::requestConnect,
                    onDisconnect = ::stopVpn,
                    onSelect = { repository.select(it.id); reloadUi++ },
                    onAddSubscription = ::addSubscription,
                    onUpdateSubscription = ::updateSubscription,
                    onRemoveSubscription = { repository.remove(it); reloadUi++ },
                    onExportLogs = ::exportLogs,
                    vpnSettings = vpnSettings,
                    onSaveVpnSettings = { settings ->
                        settingsRepository.save(settings)
                        vpnSettings = settings
                        message = "VPN-настройки сохранены; применятся при следующем подключении"
                    },
                    latencyResults = latencyResults,
                    latencyTesting = latencyTesting,
                    onTestLatency = ::testServerLatency,
                )
            }
        }
    }

    private fun addSubscription(name: String, url: String) = runSubscriptionAction {
        repository.addAndUpdate(name, url)
    }

    private fun updateSubscription(subscription: Subscription) = runSubscriptionAction {
        repository.update(subscription)
    }

    private fun runSubscriptionAction(action: suspend () -> com.envy.dualcorevpn.subscription.SubscriptionUpdateResult) {
        if (loading) return
        lifecycleScope.launch {
            loading = true
            message = null
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { result ->
                    message = "Импортировано: ${result.importedCount} · пропущено: ${result.unsupportedCount} · ошибок: ${result.invalidCount} · дублей: ${result.duplicateCount}"
                    reloadUi++
                }
                .onFailure { message = it.message ?: "Не удалось обновить подписку" }
            loading = false
        }
    }

    private fun testServerLatency() {
        if (latencyTesting) return
        lifecycleScope.launch {
            latencyTesting = true
            latencyResults = ServerLatencyTester().test(repository.servers())
            latencyTesting = false
        }
    }

    private fun exportLogs() {
        val exportDirectory = java.io.File(cacheDir, "exports").apply { mkdirs() }
        val exportFile = java.io.File(exportDirectory, "lust-diagnostics.log").apply {
            writeText(AppLog.exportText())
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", exportFile)
        val intent = ShareCompat.IntentBuilder(this)
            .setType("text/plain")
            .setSubject("Lust diagnostics")
            .setStream(uri)
            .intent
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Экспорт журнала"))
    }

    private fun requestConnect(config: String) {
        if (VpnService.prepare(this) == null) startVpn(config) else {
            pendingConfig = config
            permissionResult = { granted ->
                if (granted) pendingConfig?.let(::startVpn)
                pendingConfig = null
            }
            vpnPermissionLauncher.launch(VpnService.prepare(this))
        }
    }

    private fun startVpn(config: String) {
        val intent = Intent(this, DualCoreVpnService::class.java)
            .setAction(DualCoreVpnService.ACTION_CONNECT)
            .putExtra(DualCoreVpnService.EXTRA_XRAY_CONFIG, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopVpn() {
        startService(Intent(this, DualCoreVpnService::class.java).setAction(DualCoreVpnService.ACTION_DISCONNECT))
    }
}

@Composable
private fun LustTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent,
            onPrimary = Background,
            primaryContainer = AccentSoft,
            onPrimaryContainer = ContentPrimary,
            secondary = Success,
            onSecondary = Background,
            background = Background,
            onBackground = ContentPrimary,
            surface = SurfaceColor,
            onSurface = ContentPrimary,
            surfaceVariant = SurfaceRaised,
            onSurfaceVariant = Muted,
            outline = Outline,
            error = Danger,
            onError = Background,
        ),
        content = content,
    )
}

private enum class AppTab(val title: String) {
    HOME("Главная"),
    SERVERS("Серверы"),
    SUBSCRIPTIONS("Подписки"),
    LOGS("Журнал"),
    SETTINGS("Настройки"),
}

@Composable
private fun AppTabIcon(tab: AppTab, selected: Boolean) {
    val color = if (selected) Accent else Muted
    Canvas(Modifier.size(22.dp)) {
        val stroke = if (selected) 2.2.dp.toPx() else 1.8.dp.toPx()
        val line = Stroke(width = stroke)
        when (tab) {
            AppTab.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * .16f, size.height * .48f)
                    lineTo(size.width * .5f, size.height * .18f)
                    lineTo(size.width * .84f, size.height * .48f)
                }
                drawPath(roof, color, style = line)
                val body = Path().apply {
                    moveTo(size.width * .24f, size.height * .42f)
                    lineTo(size.width * .24f, size.height * .82f)
                    lineTo(size.width * .76f, size.height * .82f)
                    lineTo(size.width * .76f, size.height * .42f)
                }
                drawPath(body, color, style = line)
            }
            AppTab.SERVERS -> {
                drawCircle(color, radius = size.minDimension * .29f, center = center, style = line)
                drawCircle(color, radius = stroke * .75f, center = center)
                drawLine(color, Offset(center.x, size.height * .08f), Offset(center.x, size.height * .21f), stroke)
                drawLine(color, Offset(center.x, size.height * .79f), Offset(center.x, size.height * .92f), stroke)
            }
            AppTab.SUBSCRIPTIONS -> {
                drawCircle(color, radius = size.minDimension * .19f, center = Offset(size.width * .34f, size.height * .42f), style = line)
                drawCircle(color, radius = size.minDimension * .19f, center = Offset(size.width * .66f, size.height * .58f), style = line)
                drawLine(color, Offset(size.width * .43f, size.height * .48f), Offset(size.width * .57f, size.height * .52f), stroke)
            }
            AppTab.LOGS -> {
                drawRect(color, topLeft = Offset(size.width * .2f, size.height * .12f), size = size.copy(width = size.width * .6f, height = size.height * .76f), style = line)
                for (y in listOf(.34f, .5f, .66f)) {
                    drawLine(color, Offset(size.width * .32f, size.height * y), Offset(size.width * .68f, size.height * y), stroke)
                }
            }
            AppTab.SETTINGS -> {
                drawCircle(color, radius = size.minDimension * .24f, center = center, style = line)
                drawCircle(color, radius = size.minDimension * .08f, center = center, style = line)
                val r1 = size.minDimension * .3f
                val r2 = size.minDimension * .43f
                for ((dx, dy) in listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)) {
                    drawLine(color, Offset(center.x + dx * r1, center.y + dy * r1), Offset(center.x + dx * r2, center.y + dy * r2), stroke)
                }
            }
        }
    }
}

@Composable
private fun LustApp(
    revision: Int,
    repository: SubscriptionRepository,
    loading: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (ServerProfile) -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onUpdateSubscription: (Subscription) -> Unit,
    onRemoveSubscription: (Subscription) -> Unit,
    onExportLogs: () -> Unit,
    vpnSettings: VpnSettings,
    onSaveVpnSettings: (VpnSettings) -> Unit,
    latencyResults: Map<String, ServerLatencyResult>,
    latencyTesting: Boolean,
    onTestLatency: () -> Unit,
) {
    revision.hashCode()
    val vpnState by VpnSessionStore.state.collectAsState()
    val logEntries by AppLog.entries.collectAsState()
    var tab by remember { mutableStateOf(AppTab.HOME) }
    val subscriptions = repository.subscriptions()
    val servers = repository.servers()
    val selected = servers.firstOrNull { it.id == repository.selectedServerId() } ?: servers.firstOrNull()

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(vpnState, selected, servers.size, subscriptions.size, onConnect, onDisconnect) { tab = it }
                AppTab.SERVERS -> ServersScreen(servers, selected, onSelect, latencyResults, latencyTesting, onTestLatency) { tab = AppTab.SUBSCRIPTIONS }
                AppTab.SUBSCRIPTIONS -> SubscriptionsScreen(subscriptions, loading, onAddSubscription, onUpdateSubscription, onRemoveSubscription)
                AppTab.LOGS -> LogsScreen(logEntries, onClear = AppLog::clear, onExport = onExportLogs)
                AppTab.SETTINGS -> SettingsScreen(vpnSettings, onSaveVpnSettings)
            }
            if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
            }
        }
        Surface(
            color = SurfaceColor,
            shadowElevation = 12.dp,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            NavigationBar(containerColor = SurfaceColor, tonalElevation = 0.dp) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { AppTabIcon(item, tab == item) },
                        label = { Text(item.title, fontSize = 10.sp, maxLines = 1) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = ContentPrimary,
                            indicatorColor = AccentSoft,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        }
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } },
            title = { Text(if (text.contains("добавлена") || text.contains("обновлена")) "Готово" else "Lust") },
            text = { Text(text) },
        )
    }
}

@Composable
private fun HomeScreen(
    state: VpnSessionState,
    selected: ServerProfile?,
    serverCount: Int,
    subscriptionCount: Int,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    navigate: (AppTab) -> Unit,
) {
    val connected = state is VpnSessionState.Connected
    val busy = state is VpnSessionState.Connecting || state is VpnSessionState.Disconnecting
    val stateColor = when {
        connected -> Success
        busy -> Warning
        state is VpnSessionState.Error -> Danger
        else -> Muted
    }
    val engineLabel = when (state) {
        is VpnSessionState.Connecting -> engineName(state.engine)
        is VpnSessionState.Connected -> engineName(state.engine)
        is VpnSessionState.Disconnecting -> engineName(state.engine)
        is VpnSessionState.Error -> state.engine?.let(::engineName) ?: "—"
        VpnSessionState.Disconnected -> "Готово"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("LUST", color = ContentPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                    Text("PRIVATE NETWORK", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Surface(color = stateColor.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connected, busy, state is VpnSessionState.Error)
                        Spacer(Modifier.width(7.dp))
                        Text(stateLabel(state), color = stateColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                border = BorderStroke(1.dp, if (connected) Success.copy(alpha = .45f) else Outline),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(58.dp).background(if (connected) Success.copy(alpha = .14f) else AccentSoft, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Ω", color = if (connected) Success else Accent, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    connected -> "Соединение защищено"
                                    busy -> stateLabel(state)
                                    state is VpnSessionState.Error -> "Не удалось подключиться"
                                    else -> "Готов к подключению"
                                },
                                color = ContentPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (connected) "Трафик проходит через защищённый туннель" else "Выбери сервер и запусти VPN",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusMetric("ЯДРО", engineLabel, Modifier.weight(1f))
                        StatusMetric("СЕРВЕРОВ", serverCount.toString(), Modifier.weight(1f))
                        StatusMetric("ПОДПИСОК", subscriptionCount.toString(), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SectionLabel("МАРШРУТ")
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navigate(AppTab.SERVERS) },
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = BorderStroke(1.dp, Outline),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(AccentSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Text(selected?.protocol?.take(1)?.uppercase() ?: "+", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected?.name ?: "Сервер не выбран", color = ContentPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            selected?.let { "${it.protocol.uppercase()}  ·  ${it.address}:${it.port}" } ?: "Открой список серверов",
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("›", color = Accent, fontSize = 28.sp)
                }
            }
        }
        item {
            Button(
                onClick = { if (connected || busy) onDisconnect() else selected?.let { onConnect(it.config) } },
                enabled = selected != null || connected || busy,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connected || busy) SurfaceStrong else Accent,
                    contentColor = if (connected || busy) Danger else Background,
                    disabledContainerColor = SurfaceRaised,
                    disabledContentColor = Muted,
                ),
                border = if (connected || busy) BorderStroke(1.dp, Danger.copy(alpha = .4f)) else null,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Warning, strokeWidth = 2.dp)
                if (busy) Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        busy -> "ОТМЕНИТЬ"
                        connected -> "ОТКЛЮЧИТЬ"
                        else -> "ПОДКЛЮЧИТЬ"
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
        if (state is VpnSessionState.Error) item {
            Surface(color = Danger.copy(alpha = .1f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Danger.copy(alpha = .3f))) {
                Text(state.message, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ServersScreen(
    servers: List<ServerProfile>,
    selected: ServerProfile?,
    onSelect: (ServerProfile) -> Unit,
    latencyResults: Map<String, ServerLatencyResult>,
    latencyTesting: Boolean,
    onTestLatency: () -> Unit,
    openSubscriptions: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Серверы", serverCountLabel(servers.size), Modifier.weight(1f))
            TextButton(onClick = onTestLatency, enabled = servers.isNotEmpty() && !latencyTesting) {
                Text(if (latencyTesting) "ПРОВЕРКА…" else "ПРОВЕРИТЬ ВСЕ", color = Accent)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (servers.isEmpty()) EmptyState("Нет серверов", "Добавь ссылку подписки — серверы появятся здесь.", "ДОБАВИТЬ ПОДПИСКУ", openSubscriptions)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(servers, key = { it.id }) { server ->
                val active = server.id == selected?.id
                Card(
                    Modifier.fillMaxWidth().clickable { onSelect(server) },
                    colors = CardDefaults.cardColors(containerColor = if (active) AccentSoft else SurfaceColor),
                    border = BorderStroke(1.dp, if (active) Accent.copy(alpha = .55f) else Outline),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(if (active) Accent else SurfaceRaised, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Text(server.protocol.take(1).uppercase(), color = if (active) Background else Accent, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("${server.address}:${server.port}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val latency = latencyResults[server.id]
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (active) "ВЫБРАН" else server.protocol.uppercase(), color = if (active) Accent else Muted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            if (latency != null) {
                                Text(
                                    latency.latencyMillis?.let { "$it мс" } ?: "НЕДОСТУПЕН",
                                    color = if (latency.latencyMillis != null) Success else Danger,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsScreen(
    subscriptions: List<Subscription>, loading: Boolean,
    onAdd: (String, String) -> Unit, onUpdate: (Subscription) -> Unit, onRemove: (Subscription) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Подписки", "${subscriptions.size} добавлено", Modifier.weight(1f))
            Button(onClick = { showAdd = true }, enabled = !loading, shape = RoundedCornerShape(14.dp)) { Text("+ ДОБАВИТЬ") }
        }
        Spacer(Modifier.height(18.dp))
        if (subscriptions.isEmpty()) EmptyState("Подписок пока нет", "Вставь URL подписки. Lust загрузит и разберёт серверы автоматически.", "+ ДОБАВИТЬ", { showAdd = true })
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(subscriptions, key = { it.id }) { subscription ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, Outline),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(subscription.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(subscription.url, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onUpdate(subscription) }, enabled = !loading) { Text("ОБНОВИТЬ") }
                            TextButton(onClick = { onRemove(subscription) }, enabled = !loading) { Text("УДАЛИТЬ", color = Danger) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddSubscriptionDialog(onDismiss = { showAdd = false }) { name, url -> showAdd = false; onAdd(name, url) }
}

@Composable
private fun AddSubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая подписка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (необязательно)") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it.trim() }, label = { Text("URL подписки") }, placeholder = { Text("https://…") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onAdd(name.trim(), url.trim()) }, enabled = url.startsWith("http")) { Text("ДОБАВИТЬ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА") } },
    )
}

@Composable
private fun LogsScreen(entries: List<LogEntry>, onClear: () -> Unit, onExport: () -> Unit) {
    var minimumLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    val levels = LogLevel.entries
    val visible = LogFilter.apply(entries, minimumLevel, source, query).asReversed()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Журнал", "${visible.size} из ${entries.size} событий", Modifier.weight(1f))
            TextButton(onClick = onExport, enabled = entries.isNotEmpty()) { Text("ЭКСПОРТ", color = Accent) }
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("ОЧИСТИТЬ", color = Danger) }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск по сообщению") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("Источник: VPN, Xray, HEV, UI") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            levels.forEach { level ->
                val selected = minimumLevel == level
                TextButton(
                    onClick = { minimumLevel = level },
                    modifier = Modifier.background(
                        if (selected) SurfaceRaised else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    ),
                ) { Text(level.name, color = if (selected) Accent else Muted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) {
            EmptyState("События не найдены", "Измени уровень, источник или поисковый запрос.", "СБРОСИТЬ ФИЛЬТРЫ") {
                minimumLevel = LogLevel.DEBUG
                query = ""
                source = ""
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { "${it.timestampMillis}:${it.source}:${it.message.hashCode()}" }) { entry ->
                    LogEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> Muted
        LogLevel.INFO -> Accent
        LogLevel.WARN -> Color(0xFFFFC66D)
        LogLevel.ERROR -> Danger
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Outline),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.level.name, color = levelColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("  ${entry.source}", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestampMillis)),
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(entry.message, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SettingsScreen(settings: VpnSettings, onSave: (VpnSettings) -> Unit) {
    var mtu by remember(settings) { mutableStateOf(settings.mtu.toString()) }
    var dnsServer by remember(settings) { mutableStateOf(settings.dnsServer) }
    var ipv6Enabled by remember(settings) { mutableStateOf(settings.ipv6Enabled) }
    var engine by remember(settings) { mutableStateOf(settings.engine) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Настройки", "Реальные параметры Android VPN и HEV") }
        item { SettingsSectionTitle("ЯДРО") }
        item {
            SettingsCard(
                "Xray-core",
                "AndroidLibXrayLite",
                if (engine == EngineKind.XRAY) "АКТИВНО" else "ВЫБРАТЬ",
                selected = engine == EngineKind.XRAY,
                onClick = { engine = EngineKind.XRAY },
            )
        }
        item {
            SettingsCard(
                "sing-box",
                "Изолированное ядро · SOCKS 127.0.0.1:10808",
                if (engine == EngineKind.SING_BOX) "АКТИВНО" else "ВЫБРАТЬ",
                selected = engine == EngineKind.SING_BOX,
                onClick = { engine = EngineKind.SING_BOX },
            )
        }
        item { SettingsSectionTitle("VPN-ИНТЕРФЕЙС") }
        item {
            OutlinedTextField(
                value = mtu,
                onValueChange = { mtu = it.filter(Char::isDigit).take(4) },
                label = { Text("MTU, 576–9000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = dnsServer,
                onValueChange = { dnsServer = it.take(253) },
                label = { Text("DNS-сервер") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("IPv6", fontWeight = FontWeight.Medium)
                        Text("Адрес и маршрут IPv6 в Android TUN и HEV", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = ipv6Enabled, onCheckedChange = { ipv6Enabled = it })
                }
            }
        }
        validationError?.let { error -> item { Text(error, color = Danger, fontSize = 13.sp) } }
        item {
            Button(
                onClick = {
                    runCatching { VpnSettings.validate(mtu, dnsServer, ipv6Enabled, engine) }
                        .onSuccess { validationError = null; onSave(it) }
                        .onFailure { validationError = it.message ?: "Некорректные настройки" }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("СОХРАНИТЬ") }
        }
        item { SettingsSectionTitle("ТРАНСПОРТ И ДИАГНОСТИКА") }
        item { SettingsCard("HEV tun2socks", "Android TUN → HEV → SOCKS 127.0.0.1:10808 → ${if (engine == EngineKind.XRAY) "Xray" else "sing-box"}", "ВКЛЮЧЕНО") }
        item { SettingsCard("Постоянный журнал", "Core/service stack trace, поиск, фильтры, экспорт, ротация 2 МБ", "ВКЛЮЧЕНО") }
        item { SettingsCard("Версия приложения", "Alpha · настройки применяются при следующем подключении", BuildConfig.VERSION_NAME) }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    SectionLabel(text)
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    value: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> SurfaceColor.copy(alpha = .55f)
                selected -> AccentSoft
                else -> SurfaceColor
            },
        ),
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = .6f) else Outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().then(if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) ContentPrimary else Muted)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(value, color = if (selected) Accent else if (enabled) Success else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(title, color = ContentPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
    Text(subtitle, color = Muted, fontSize = 12.sp)
}

@Composable
private fun EmptyState(title: String, text: String, button: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = SurfaceColor, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Outline)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                Box(Modifier.size(54.dp).background(AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Text("Ω", fontSize = 30.sp, color = Accent, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, color = ContentPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(text, color = Muted, modifier = Modifier.padding(vertical = 10.dp), lineHeight = 20.sp)
                Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) { Text(button, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun StatusDot(connected: Boolean, busy: Boolean, failed: Boolean = false) {
    val color = when {
        connected -> Success
        busy -> Warning
        failed -> Danger
        else -> Muted
    }
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun StatusMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = SurfaceColor, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Spacer(Modifier.height(3.dp))
            Text(value, color = ContentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun serverCountLabel(count: Int): String {
    val form = when {
        count % 100 in 11..14 -> "серверов"
        count % 10 == 1 -> "сервер"
        count % 10 in 2..4 -> "сервера"
        else -> "серверов"
    }
    return "$count $form · проверка TCP-соединения"
}

private fun engineName(engine: EngineKind): String = when (engine) {
    EngineKind.XRAY -> "Xray"
    EngineKind.SING_BOX -> "sing-box"
}

private fun stateLabel(state: VpnSessionState): String = when (state) {
    VpnSessionState.Disconnected -> "Отключено"
    is VpnSessionState.Connecting -> "Подключение"
    is VpnSessionState.Connected -> "Подключено"
    is VpnSessionState.Disconnecting -> "Отключение"
    is VpnSessionState.Error -> "Ошибка"
}
