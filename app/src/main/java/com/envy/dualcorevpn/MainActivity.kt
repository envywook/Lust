package com.envy.dualcorevpn

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.envy.dualcorevpn.backup.LustBackupCodec
import com.envy.dualcorevpn.backup.LustBackupRepository
import com.envy.dualcorevpn.core.EngineKind
import com.envy.dualcorevpn.core.VpnSessionState
import com.envy.dualcorevpn.core.VpnSessionStore
import com.envy.dualcorevpn.logging.AppLog
import com.envy.dualcorevpn.logging.LogEntry
import com.envy.dualcorevpn.logging.LogLevel
import com.envy.dualcorevpn.server.ServerLatencyResult
import com.envy.dualcorevpn.server.ServerLatencyTester
import com.envy.dualcorevpn.server.PlannedServer
import com.envy.dualcorevpn.server.ServerListPlanner
import com.envy.dualcorevpn.routing.RoutingMode
import com.envy.dualcorevpn.server.ServerSort
import com.envy.dualcorevpn.settings.VpnSettings
import com.envy.dualcorevpn.settings.VpnSettingsRepository
import com.envy.dualcorevpn.subscription.ServerProfile
import com.envy.dualcorevpn.subscription.Subscription
import com.envy.dualcorevpn.subscription.SubscriptionClipboard
import com.envy.dualcorevpn.subscription.SubscriptionDeepLink
import com.envy.dualcorevpn.subscription.SubscriptionImportRequest
import com.envy.dualcorevpn.subscription.SubscriptionRepository
import com.envy.dualcorevpn.update.UpdateRepository
import com.envy.dualcorevpn.vpn.DualCoreVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF000000)
private val SurfaceColor = Color(0xFF0A0A0A)
private val SurfaceRaised = Color(0xFF121212)
private val SurfaceStrong = Color(0xFF1A1A1A)
private val Accent = Color(0xFFF5F5F5)
private val AccentSoft = Color(0xFF1C1C1C)
private val ContentPrimary = Color(0xFFF5F5F5)
private val Muted = Color(0xFFA3A3A3)
private val Outline = Color(0xFF2B2B2B)
private val Success = Color(0xFF55E39A)
private val Warning = Color(0xFFFFC66D)
private val Danger = Color(0xFFFF7280)
private const val MAX_BACKUP_CHARS = 5_000_000

private fun readBackupBounded(reader: java.io.Reader): String {
    val result = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        require(result.length + count <= MAX_BACKUP_CHARS) { "Файл резервной копии слишком большой" }
        result.append(buffer, 0, count)
    }
    return result.toString()
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: SubscriptionRepository
    private lateinit var settingsRepository: VpnSettingsRepository
    private lateinit var backupRepository: LustBackupRepository
    private lateinit var updateRepository: UpdateRepository
    private var vpnSettings by mutableStateOf(VpnSettings())
    private var permissionResult: ((Boolean) -> Unit)? = null
    private var pendingConfig: String? = null
    private var reloadUi by mutableStateOf(0)
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var latencyResults by mutableStateOf<Map<String, ServerLatencyResult>>(emptyMap())
    private var latencyTesting by mutableStateOf(false)
    private var updateStatus by mutableStateOf("Версия ${BuildConfig.VERSION_NAME}")
    private var pendingSubscriptionImport by mutableStateOf<SubscriptionImportRequest?>(null)
    private var pendingBackupRestore by mutableStateOf<String?>(null)

    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(backupRepository.export()) }
                        ?: error("Не удалось открыть файл")
                }
            }.onSuccess {
                message = "Резервная копия сохранена"
                AppLog.info("BACKUP", "Backup exported")
            }.onFailure {
                message = "Ошибка экспорта: ${it.message}"
                AppLog.error("BACKUP", "Backup export failed", it)
            }
        }
    }

    private val backupImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val value = contentResolver.openInputStream(uri)?.bufferedReader()?.use(::readBackupBounded)
                        ?: error("Не удалось открыть файл")
                    LustBackupCodec.decode(value)
                    value
                }
            }.onSuccess { value ->
                pendingBackupRestore = value
            }.onFailure {
                message = "Не удалось прочитать резервную копию"
                AppLog.error("BACKUP", "Backup file validation failed")
            }
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        permissionResult?.invoke(result.resultCode == RESULT_OK)
        permissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SubscriptionRepository(applicationContext)
        settingsRepository = VpnSettingsRepository(applicationContext)
        backupRepository = LustBackupRepository(applicationContext)
        updateRepository = UpdateRepository(applicationContext)
        vpnSettings = settingsRepository.load()
        AppLog.initialize(java.io.File(filesDir, "logs"))
        AppLog.info("UI", "Application opened")
        handleSubscriptionIntent(intent)
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
                    onToggleFavorite = { repository.toggleFavorite(it.id); reloadUi++ },
                    pendingSubscriptionImport = pendingSubscriptionImport,
                    onDismissSubscriptionImport = { pendingSubscriptionImport = null },
                    onAddSubscription = ::addSubscription,
                    onUpdateSubscription = ::updateSubscription,
                    onRemoveSubscription = { repository.remove(it); reloadUi++ },
                    onExportLogs = ::exportLogs,
                    onExportBackup = { backupExportLauncher.launch("lust-backup.json") },
                    onImportBackup = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                    updateStatus = updateStatus,
                    onCheckUpdate = ::checkForUpdate,
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
                pendingBackupRestore?.let {
                    AlertDialog(
                        onDismissRequest = { pendingBackupRestore = null },
                        title = { Text("Восстановить резервную копию?") },
                        text = { Text("Текущие подписки, серверы, избранное и VPN-настройки будут заменены. Это действие нельзя отменить.") },
                        confirmButton = {
                            Button(onClick = ::confirmBackupRestore) { Text("ВОССТАНОВИТЬ") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingBackupRestore = null }) { Text("ОТМЕНА") }
                        },
                    )
                }
            }
        }
    }

    private fun checkForUpdate() {
        if (loading) return
        loading = true
        updateStatus = "Проверка обновлений…"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val update = updateRepository.check() ?: return@withContext null
                    update to updateRepository.downloadAndVerify(update)
                }
            }.onSuccess { result ->
                loading = false
                if (result == null) {
                    updateStatus = "Установлена актуальная версия ${BuildConfig.VERSION_NAME}"
                } else {
                    updateStatus = "Загружено ${result.first.tag}"
                    runCatching { updateRepository.install(result.second) }.onFailure {
                        message = it.message ?: "Не удалось открыть системный установщик"
                    }
                }
            }.onFailure {
                loading = false
                updateStatus = "Ошибка проверки обновлений"
                message = it.message ?: "Не удалось проверить обновление"
            }
        }
    }

    private fun confirmBackupRestore() {
        val value = pendingBackupRestore ?: return
        pendingBackupRestore = null
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { backupRepository.restore(value) } }
                .onSuccess {
                    vpnSettings = settingsRepository.load()
                    reloadUi++
                    message = "Резервная копия восстановлена"
                    AppLog.info("BACKUP", "Backup restored")
                }
                .onFailure {
                    message = "Ошибка импорта: ${it.message}"
                    AppLog.error("BACKUP", "Backup restore failed", it)
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSubscriptionIntent(intent)
    }

    private fun handleSubscriptionIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val request = SubscriptionDeepLink.parse(intent.dataString) ?: return
        pendingSubscriptionImport = request
        intent.data = null
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
    HOME("Подключение"),
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
    onToggleFavorite: (ServerProfile) -> Unit,
    pendingSubscriptionImport: SubscriptionImportRequest?,
    onDismissSubscriptionImport: () -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onUpdateSubscription: (Subscription) -> Unit,
    onRemoveSubscription: (Subscription) -> Unit,
    onExportLogs: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    updateStatus: String,
    onCheckUpdate: () -> Unit,
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
                AppTab.HOME -> HomeScreen(
                    state = vpnState,
                    selected = selected,
                    servers = servers,
                    subscriptions = subscriptions,
                    favoriteIds = repository.favoriteServerIds(),
                    latencyResults = latencyResults,
                    latencyTesting = latencyTesting,
                    loading = loading,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onSelect = onSelect,
                    onToggleFavorite = onToggleFavorite,
                    onTestLatency = onTestLatency,
                    onAddSubscription = onAddSubscription,
                    onUpdateSubscription = onUpdateSubscription,
                    onRemoveSubscription = onRemoveSubscription,
                    logEntries = logEntries,
                    onClearLogs = AppLog::clear,
                    onExportLogs = onExportLogs,
                )
                AppTab.SETTINGS -> SettingsScreen(
                    vpnSettings,
                    onSaveVpnSettings,
                    onExportBackup,
                    onImportBackup,
                    updateStatus,
                    onCheckUpdate,
                )
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
                            selectedTextColor = Accent,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        }
    }
    pendingSubscriptionImport?.let { request ->
        AddSubscriptionDialog(
            onDismiss = onDismissSubscriptionImport,
            initialName = request.name,
            initialUrl = request.url,
        ) { name, url ->
            onDismissSubscriptionImport()
            onAddSubscription(name, url)
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
    servers: List<ServerProfile>,
    subscriptions: List<Subscription>,
    favoriteIds: Set<String>,
    latencyResults: Map<String, ServerLatencyResult>,
    latencyTesting: Boolean,
    loading: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (ServerProfile) -> Unit,
    onToggleFavorite: (ServerProfile) -> Unit,
    onTestLatency: () -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onUpdateSubscription: (Subscription) -> Unit,
    onRemoveSubscription: (Subscription) -> Unit,
    logEntries: List<LogEntry>,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit,
) {
    var serverQuery by remember { mutableStateOf("") }
    var serverSort by remember { mutableStateOf(ServerSort.NAME) }
    var showAddSubscription by remember { mutableStateOf(false) }
    val subscriptionNames = subscriptions.associate { it.id to it.name }
    val groups = ServerListPlanner.plan(
        servers = servers,
        subscriptionNames = subscriptionNames,
        favoriteIds = favoriteIds,
        query = serverQuery,
        sort = serverSort,
        latencyMillis = latencyResults.mapValues { it.value.latencyMillis },
    )
    val serverCount = servers.size

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
        VpnSessionState.Disconnected -> "—"
    }
    val activeSubscription = selected?.subscriptionId?.let { id -> subscriptions.firstOrNull { it.id == id } }
    var clockMillis by remember(state) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state is VpnSessionState.Connected) {
            clockMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val connectionTime = if (state is VpnSessionState.Connected) {
        formatDuration((clockMillis - state.startedAtEpochMillis).coerceAtLeast(0L))
    } else {
        "00:00:00"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showAddSubscription = true }, enabled = !loading) {
                    Text("+", color = Accent, fontSize = 25.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Время подключения", color = Muted, fontSize = 11.sp)
                Text(connectionTime, color = ContentPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                Surface(color = SurfaceRaised, shape = RoundedCornerShape(18.dp)) {
                    Button(
                        onClick = { if (connected || busy) onDisconnect() else selected?.let { onConnect(it.config) } },
                        enabled = selected != null || connected || busy,
                        modifier = Modifier.padding(11.dp).size(66.dp).semantics {
                            contentDescription = if (connected || busy) "Отключить VPN" else "Подключить VPN"
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connected) Success else Accent,
                            contentColor = Color.White,
                            disabledContainerColor = Accent.copy(alpha = .45f),
                            disabledContentColor = Color.White.copy(alpha = .75f),
                        ),
                    ) { PowerIcon(Modifier.size(32.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        connected -> "Подключено"
                        busy -> stateLabel(state)
                        state is VpnSessionState.Error -> "Ошибка подключения"
                        else -> "Не подключено"
                    },
                    color = stateColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    selected?.name ?: "Выберите сервер",
                    color = ContentPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selected?.let { "${it.protocol.uppercase()} · $engineLabel" } ?: "Добавьте подписку, чтобы начать",
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceColor,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Outline),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(AccentSoft, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Text("↻", color = Accent, fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(activeSubscription?.name ?: "Подписка не выбрана", color = ContentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("$serverCount серверов · ${subscriptions.size} подписок", color = Muted, fontSize = 10.sp)
                    }
                    if (activeSubscription != null) {
                        TextButton(onClick = { onUpdateSubscription(activeSubscription) }, enabled = !loading) {
                            Text("ОБНОВИТЬ", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { showAddSubscription = true }, enabled = !loading) {
                            Text("ДОБАВИТЬ", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { EmbeddedLogConsole(entries = logEntries, onClear = onClearLogs, onExport = onExportLogs) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("УЗЛЫ", color = ContentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onTestLatency, enabled = servers.isNotEmpty() && !latencyTesting) {
                    Text(if (latencyTesting) "ПРОВЕРКА…" else "ПРОВЕРИТЬ", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showAddSubscription = true }, enabled = !loading) {
                    Text("+", color = Accent, fontSize = 20.sp)
                }
            }
        }
        item {
            OutlinedTextField(
                value = serverQuery,
                onValueChange = { serverQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск узла") },
                shape = RoundedCornerShape(12.dp),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SortButton("ПО ИМЕНИ", serverSort == ServerSort.NAME) { serverSort = ServerSort.NAME }
                SortButton("ПО ЗАДЕРЖКЕ", serverSort == ServerSort.LATENCY) { serverSort = ServerSort.LATENCY }
            }
        }
        if (groups.isEmpty()) item {
            InlineEmptyState(
                if (servers.isEmpty()) "Серверов пока нет" else "Ничего не найдено",
                if (servers.isEmpty()) "Добавьте подписку — узлы появятся здесь." else "Измените поисковый запрос.",
            )
        }
        groups.forEach { group ->
            item(key = "group-${group.id}") {
                Text(group.name, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            items(group.servers, key = { "server-${it.server.id}" }) { planned ->
                ReferenceServerRow(
                    planned = planned,
                    selected = planned.server.id == selected?.id,
                    onSelect = { onSelect(planned.server) },
                    onToggleFavorite = { onToggleFavorite(planned.server) },
                )
            }
        }
        if (subscriptions.isNotEmpty()) item {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ПОДПИСКИ", color = ContentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${subscriptions.size}", color = Muted, fontSize = 11.sp)
            }
        }
        if (subscriptions.isNotEmpty()) {
            items(subscriptions, key = { "subscription-${it.id}" }) { subscription ->
                HomeSubscriptionCard(subscription, loading, onUpdateSubscription, onRemoveSubscription)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (showAddSubscription) {
        AddSubscriptionDialog(onDismiss = { showAddSubscription = false }) { name, url ->
            showAddSubscription = false
            onAddSubscription(name, url)
        }
    }
}

@Composable
private fun PowerIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val strokeWidth = 2.8.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(center.x, size.height * .08f),
            end = Offset(center.x, size.height * .48f),
            strokeWidth = strokeWidth,
        )
        drawArc(
            color = Color.White,
            startAngle = -42f,
            sweepAngle = 264f,
            useCenter = false,
            topLeft = Offset(size.width * .14f, size.height * .18f),
            size = androidx.compose.ui.geometry.Size(size.width * .72f, size.height * .72f),
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun FavoriteIcon(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = if (filled) "Убрать из избранного" else "Добавить в избранное" }) {
        val outer = size.minDimension * .46f
        val inner = outer * .45f
        val path = Path()
        repeat(10) { index ->
            val radius = if (index % 2 == 0) outer else inner
            val angle = Math.toRadians((-90 + index * 36).toDouble())
            val point = Offset(center.x + radius * kotlin.math.cos(angle).toFloat(), center.y + radius * kotlin.math.sin(angle).toFloat())
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, if (filled) Warning else Muted, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun CloseIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Удалить подписку" }) {
        val inset = size.minDimension * .28f
        drawLine(Danger, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 2.dp.toPx())
        drawLine(Danger, Offset(size.width - inset, inset), Offset(inset, size.height - inset), 2.dp.toPx())
    }
}

@Composable
private fun ChevronIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Выбранный маршрут" }) {
        val path = Path().apply {
            moveTo(size.width * .35f, size.height * .2f)
            lineTo(size.width * .65f, size.height * .5f)
            lineTo(size.width * .35f, size.height * .8f)
        }
        drawPath(path, Accent, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun SortButton(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.semantics {
            this.selected = selected
            stateDescription = if (selected) "Выбрано" else "Не выбрано"
        },
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Accent else Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) Accent else Muted),
    ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ReferenceServerRow(
    planned: PlannedServer,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val server = planned.server
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected
            stateDescription = if (selected) "Выбранный сервер" else "Сервер не выбран"
        }.clickable(onClick = onSelect),
        color = if (selected) AccentSoft else SurfaceColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = .65f) else Outline),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).background(if (selected) Accent else SurfaceRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(server.protocol.take(1).uppercase(), color = if (selected) Background else Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(server.name, color = ContentPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${server.protocol.uppercase()} · ${server.address}:${server.port}", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            planned.latencyMillis?.let { Text("$it мс", color = Success, fontSize = 10.sp) }
            Box(Modifier.size(34.dp).clickable(onClick = onToggleFavorite), contentAlignment = Alignment.Center) {
                FavoriteIcon(planned.favorite, Modifier.size(17.dp))
            }
            if (selected) ChevronIcon(Modifier.size(16.dp))
        }
    }
}

private fun subscriptionUsageLabel(subscription: Subscription): String {
    val usage = subscription.usage ?: return if (subscription.updatedAt > 0L) "Обновлено" else "Ожидает обновления"
    val parts = mutableListOf<String>()
    usage.usedBytes?.let { used ->
        val total = usage.totalBytes
        parts += if (total != null && total > 0L) "${formatBytes(used)} из ${formatBytes(total)}" else "Использовано ${formatBytes(used)}"
    }
    usage.expiresAtEpochSeconds?.let { seconds ->
        parts += "до ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds * 1000L))}"
    }
    return parts.joinToString(" · ").ifBlank { "Метаданные недоступны" }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) { value /= 1024.0; unit++ }
    return if (unit == 0) "${bytes} ${units[unit]}" else "%.1f %s".format(value, units[unit])
}

@Composable
private fun HomeSubscriptionCard(
    subscription: Subscription,
    loading: Boolean,
    onUpdate: (Subscription) -> Unit,
    onRemove: (Subscription) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Outline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(subscription.name, color = ContentPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subscriptionUsageLabel(subscription), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { onUpdate(subscription) }, enabled = !loading) { Text("ОБНОВИТЬ", color = Accent, fontSize = 10.sp) }
            TextButton(onClick = { onRemove(subscription) }, enabled = !loading, modifier = Modifier.size(48.dp)) {
                CloseIcon(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun InlineEmptyState(title: String, text: String) {
    Surface(color = SurfaceColor, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, color = ContentPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
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
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    initialName: String = "",
    initialUrl: String = "",
    onAdd: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var clipboardError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая подписка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название (необязательно)") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it.trim(); clipboardError = false }, label = { Text("URL подписки") }, placeholder = { Text("https://…") }, singleLine = true)
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                    val request = SubscriptionClipboard.parse(text)
                    if (request == null) clipboardError = true else {
                        url = request.url
                        if (name.isBlank()) name = request.name
                        clipboardError = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("ВСТАВИТЬ ИЗ БУФЕРА")
                }
                if (clipboardError) Text("В буфере нет ссылки подписки", color = Danger, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { onAdd(name.trim(), url.trim()) }, enabled = url.startsWith("https://") || url.startsWith("http://")) { Text("ДОБАВИТЬ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА") } },
    )
}

@Composable
private fun EmbeddedLogConsole(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val recent = entries.takeLast(3).asReversed()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Background),
        border = BorderStroke(1.dp, Outline),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).background(AccentSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(">_", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                    Text("ЛОГИ", color = ContentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("${entries.size} событий · ${if (expanded) "нажми, чтобы свернуть" else "нажми, чтобы развернуть"}", color = Muted, fontSize = 10.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = Accent, fontSize = 22.sp)
            }
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Outline))
                if (recent.isEmpty()) {
                    Text(
                        "Журнал пуст. События подключения появятся здесь.",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { entry -> CompactLogLine(entry) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onExport, enabled = entries.isNotEmpty()) { Text("ЭКСПОРТ", color = if (entries.isNotEmpty()) Accent else Muted, fontSize = 10.sp) }
                    TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("ОЧИСТИТЬ", color = if (entries.isNotEmpty()) Danger else Muted, fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun CompactLogLine(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.DEBUG -> Muted
        LogLevel.INFO -> Accent
        LogLevel.WARN -> Warning
        LogLevel.ERROR -> Danger
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(entry.timestampMillis)),
            color = Muted,
            fontSize = 10.sp,
        )
        Text("  ${entry.level.name.take(1)}", color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(
            "  ${entry.source}: ${entry.message}",
            color = if (entry.level == LogLevel.ERROR) Danger else ContentPrimary.copy(alpha = .86f),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private enum class SettingsPage { ROOT, TRAFFIC }

@Composable
private fun SettingsScreen(
    settings: VpnSettings,
    onSave: (VpnSettings) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    updateStatus: String,
    onCheckUpdate: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    when (page) {
        SettingsPage.ROOT -> SettingsRoot(
            settings = settings,
            onOpenTraffic = { page = SettingsPage.TRAFFIC },
            onExportBackup = onExportBackup,
            onImportBackup = onImportBackup,
            updateStatus = updateStatus,
            onCheckUpdate = onCheckUpdate,
        )
        SettingsPage.TRAFFIC -> VpnSettingsDetails(
            settings = settings,
            onSave = onSave,
            onBack = { page = SettingsPage.ROOT },
        )
    }
}

@Composable
private fun SettingsRoot(
    settings: VpnSettings,
    onOpenTraffic: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    updateStatus: String,
    onCheckUpdate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(18.dp)); ScreenTitle("Настройки", "Управление приложением и VPN") }
        item { SettingsSectionTitle("VPN И ТРАФИК") }
        item {
            SettingsNavigationCard(
                title = "Настройки трафика",
                description = "DNS, MTU, IPv6 и правила маршрутизации",
                value = routingModeLabel(settings.routingMode),
                onClick = onOpenTraffic,
            )
        }
        item { SettingsSectionTitle("НАСТРОЙКИ ЯДРА") }
        item {
            SettingsNavigationCard(
                title = "VPN-ядро",
                description = "Активное ядро для следующего подключения",
                value = engineName(settings.engine),
                onClick = onOpenTraffic,
            )
        }
        item { SettingsSectionTitle("СЛУЖЕБНОЕ") }
        item {
            SettingsNavigationCard(
                title = "Резервирование настроек",
                description = "Экспорт подписок, серверов и VPN-настроек",
                value = "ЭКСПОРТ",
                onClick = onExportBackup,
            )
        }
        item {
            SettingsNavigationCard(
                title = "Восстановление настроек",
                description = "Импорт ранее созданной резервной копии",
                value = "ИМПОРТ",
                onClick = onImportBackup,
            )
        }
        item {
            SettingsNavigationCard(
                title = "Обновление Lust",
                description = updateStatus,
                value = "ПРОВЕРИТЬ",
                onClick = onCheckUpdate,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, Outline),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = ContentPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text(value, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            Text("  ›", color = Muted, fontSize = 22.sp)
        }
    }
}

private fun routingModeLabel(mode: RoutingMode): String = when (mode) {
    RoutingMode.ALL -> "ВСЁ ЧЕРЕЗ VPN"
    RoutingMode.BYPASS_LAN -> "ОБХОД LAN"
    RoutingMode.CUSTOM -> "СВОИ ПРАВИЛА"
}

@Composable
private fun VpnSettingsDetails(
    settings: VpnSettings,
    onSave: (VpnSettings) -> Unit,
    onBack: () -> Unit,
) {
    var mtu by remember(settings) { mutableStateOf(settings.mtu.toString()) }
    var dnsServer by remember(settings) { mutableStateOf(settings.dnsServer) }
    var ipv6Enabled by remember(settings) { mutableStateOf(settings.ipv6Enabled) }
    var engine by remember(settings) { mutableStateOf(settings.engine) }
    var routingMode by remember(settings) { mutableStateOf(settings.routingMode) }
    var routingRules by remember(settings) { mutableStateOf(settings.routingRules) }
    var showAdvanced by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("‹", color = Accent, fontSize = 32.sp, lineHeight = 32.sp, modifier = Modifier.offset(y = (-6).dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Настройки трафика", color = ContentPrimary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("DNS, маршрутизация и VPN-интерфейс", color = Muted, fontSize = 11.sp)
                }
            }
        }
        item { SettingsSectionTitle("КАК НАПРАВЛЯТЬ ТРАФИК") }
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard("Всё через VPN", "Все сайты и приложения используют выбранный сервер", if (routingMode == RoutingMode.ALL) "ВЫБРАНО" else "ВЫБРАТЬ", selected = routingMode == RoutingMode.ALL, selectionControl = true, onClick = { routingMode = RoutingMode.ALL })
                SettingsCard("Обход локальной сети", "Роутер и домашние устройства открываются напрямую", if (routingMode == RoutingMode.BYPASS_LAN) "ВЫБРАНО" else "ВЫБРАТЬ", selected = routingMode == RoutingMode.BYPASS_LAN, selectionControl = true, onClick = { routingMode = RoutingMode.BYPASS_LAN })
                SettingsCard("Свои исключения", "Указанные домены и IP идут напрямую", if (routingMode == RoutingMode.CUSTOM) "ВЫБРАНО" else "ВЫБРАТЬ", selected = routingMode == RoutingMode.CUSTOM, selectionControl = true, onClick = { routingMode = RoutingMode.CUSTOM })
            }
        }
        if (routingMode == RoutingMode.CUSTOM) {
            item {
                OutlinedTextField(
                    value = routingRules,
                    onValueChange = { routingRules = it.take(4096) },
                    label = { Text("Домены и IP — по одному на строке") },
                    placeholder = { Text("example.com\n192.168.50.0/24") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (showAdvanced) "СКРЫТЬ РАСШИРЕННЫЕ" else "РАСШИРЕННЫЕ НАСТРОЙКИ")
            }
        }
        if (showAdvanced) {
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
        }
        validationError?.let { error -> item { Text(error, color = Danger, fontSize = 13.sp) } }
        item {
            Button(
                onClick = {
                    runCatching { VpnSettings.validate(mtu, dnsServer, ipv6Enabled, engine, routingMode, routingRules) }
                        .onSuccess { validationError = null; onSave(it) }
                        .onFailure { validationError = it.message ?: "Некорректные настройки" }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("СОХРАНИТЬ") }
        }
        if (showAdvanced) {
        item { SettingsSectionTitle("ТРАНСПОРТ И ДИАГНОСТИКА") }
        item { SettingsCard("HEV tun2socks", "Android TUN → HEV → SOCKS 127.0.0.1:10808 → ${if (engine == EngineKind.XRAY) "Xray" else "sing-box"}", "ВКЛЮЧЕНО") }
        item { SettingsCard("Постоянный журнал", "Core/service stack trace, поиск, фильтры, экспорт, ротация 2 МБ", "ВКЛЮЧЕНО") }
        item { SettingsCard("Версия приложения", "Alpha · настройки применяются при следующем подключении", BuildConfig.VERSION_NAME) }
        }
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
    selectionControl: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier = when {
        !enabled || onClick == null -> Modifier
        selectionControl -> Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        else -> Modifier.clickable(onClick = onClick)
    }
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
        modifier = Modifier.fillMaxWidth().then(interactionModifier),
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

private fun serverCountLabel(count: Int): String {
    val form = when {
        count % 100 in 11..14 -> "серверов"
        count % 10 == 1 -> "сервер"
        count % 10 in 2..4 -> "сервера"
        else -> "серверов"
    }
    return "$count $form · проверка TCP-соединения"
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
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
