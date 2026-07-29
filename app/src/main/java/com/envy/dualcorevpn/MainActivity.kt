package com.envy.dualcorevpn

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.envy.dualcorevpn.server.SmartConnectPlanner
import com.envy.dualcorevpn.server.SmartConnectState
import com.envy.dualcorevpn.server.PlannedServer
import com.envy.dualcorevpn.server.ServerListPlanner
import com.envy.dualcorevpn.routing.RoutingMode
import com.envy.dualcorevpn.server.ServerSort
import com.envy.dualcorevpn.settings.VpnSettings
import com.envy.dualcorevpn.settings.VpnSettingsRepository
import com.envy.dualcorevpn.subscription.MieruDeepLink
import com.envy.dualcorevpn.subscription.MieruImportRequest
import com.envy.dualcorevpn.subscription.ServerProfile
import com.envy.dualcorevpn.subscription.Subscription
import com.envy.dualcorevpn.subscription.SubscriptionClipboard
import com.envy.dualcorevpn.subscription.SubscriptionDeepLink
import com.envy.dualcorevpn.subscription.SubscriptionImportRequest
import com.envy.dualcorevpn.subscription.SubscriptionRepository
import com.envy.dualcorevpn.subscription.SubscriptionRefreshWorker
import com.envy.dualcorevpn.subscription.QrImportClassifier
import com.envy.dualcorevpn.subscription.QrImportPayload
import com.envy.dualcorevpn.update.UpdateRepository
import com.envy.dualcorevpn.ui.HomeDashboard
import com.envy.dualcorevpn.ui.AdvancedFeaturesScreen
import com.envy.dualcorevpn.ui.DashboardHeader
import com.envy.dualcorevpn.ui.SpeedDashboard
import com.envy.dualcorevpn.ui.dashboardStrings
import com.envy.dualcorevpn.vpn.DualCoreVpnService
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF080A09)
private val SurfaceColor = Color(0xFF151817)
private val SurfaceRaised = Color(0xFF101211)
private val SurfaceStrong = Color(0xFF1C2521)
private val Accent = Color(0xFFA6F3D1)
private val AccentSoft = Color(0xFF1C2521)
private val ContentPrimary = Color(0xFFF4F6F5)
private val Muted = Color(0xFF9EA5A1)
private val Outline = Color(0xFF343936)
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

private const val REQUEST_NOTIFICATION_PERMISSION = 1001

class MainActivity : ComponentActivity() {
    private lateinit var repository: SubscriptionRepository
    private lateinit var settingsRepository: VpnSettingsRepository
    private lateinit var backupRepository: LustBackupRepository
    private lateinit var updateRepository: UpdateRepository
    private var vpnSettings by mutableStateOf(VpnSettings())
    private var permissionResult: ((Boolean) -> Unit)? = null
    private var pendingConfig: String? = null
    private var afterNotificationPermission: (() -> Unit)? = null
    private var notificationPermissionResolved = false

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) return
        notificationPermissionResolved = true
        val continuation = afterNotificationPermission
        afterNotificationPermission = null
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            message = getString(R.string.notification_permission_denied)
        }
        continuation?.invoke()
    }
    private var reloadUi by mutableStateOf(0)
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var latencyResults by mutableStateOf<Map<String, ServerLatencyResult>>(emptyMap())
    private var latencyTesting by mutableStateOf(false)
    private var latencyTestingIds by mutableStateOf<Set<String>>(emptySet())
    private var updateStatus by mutableStateOf("")
    private var pendingSubscriptionImport by mutableStateOf<SubscriptionImportRequest?>(null)
    private var pendingMieruImport by mutableStateOf<MieruImportRequest?>(null)
    private var pendingQrProfile by mutableStateOf<ServerProfile?>(null)
    private var clearViewedIntentDataOnResume = false
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
        updateStatus = getString(R.string.update_version, BuildConfig.VERSION_NAME)
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
                    onSelect = {
                        repository.select(it.id)
                        SmartConnectState(this).pin(it.id)
                        reloadUi++
                    },
                    pendingSubscriptionImport = pendingSubscriptionImport,
                    onDismissSubscriptionImport = { pendingSubscriptionImport = null },
                    onAddSubscription = ::addSubscription,
                    onUpdateSubscription = ::updateSubscription,
                    onRemoveSubscription = { repository.remove(it); reloadUi++ },
                    onExportBackup = { backupExportLauncher.launch("lust-backup.json") },
                    onImportBackup = { backupImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                    updateStatus = updateStatus,
                    onCheckUpdate = ::checkForUpdate,
                    onScanQr = ::scanQrCode,
                    vpnSettings = vpnSettings,
                    onSaveVpnSettings = { settings ->
                        settingsRepository.save(settings)
                        SubscriptionRefreshWorker.schedule(this, settings.subscriptionRefreshHours)
                        vpnSettings = settings
                        message = "VPN-настройки сохранены; применятся при следующем подключении"
                    },
                    latencyResults = latencyResults,
                    latencyTesting = latencyTesting,
                    latencyTestingIds = latencyTestingIds,
                    onTestLatency = ::testServerLatency,
                    onTestServerLatency = ::testSingleServerLatency,
                )
                pendingMieruImport?.let { request ->
                    AlertDialog(
                        onDismissRequest = { pendingMieruImport = null },
                        title = { Text(stringResource(R.string.mieru_import_title)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.mieru_import_summary,
                                    request.profile.name,
                                    request.profile.address,
                                    request.profile.port,
                                    request.username,
                                    request.transport,
                                    request.mtu,
                                    request.multiplexing,
                                ),
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    repository.importProfile(request.profile)
                                    pendingMieruImport = null
                                    reloadUi++
                                    message = getString(R.string.mieru_import_success)
                                },
                            ) { Text(stringResource(R.string.mieru_import_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingMieruImport = null }) {
                                Text(stringResource(R.string.mieru_import_cancel))
                            }
                        },
                    )
                }
                pendingQrProfile?.let { profile ->
                    AlertDialog(
                        onDismissRequest = { pendingQrProfile = null },
                        title = { Text(stringResource(R.string.qr_import_title)) },
                        text = { Text(stringResource(R.string.qr_import_profile_summary, profile.name, profile.protocol, profile.address, profile.port)) },
                        confirmButton = {
                            Button(onClick = {
                                repository.importProfile(profile)
                                pendingQrProfile = null
                                reloadUi++
                                message = getString(R.string.qr_import_success)
                            }) { Text(stringResource(R.string.qr_import_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingQrProfile = null }) {
                                Text(stringResource(R.string.mieru_import_cancel))
                            }
                        },
                    )
                }
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
        updateStatus = getString(R.string.update_checking)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val update = updateRepository.check() ?: return@withContext null
                    update to updateRepository.downloadAndVerify(update)
                }
            }.onSuccess { result ->
                loading = false
                if (result == null) {
                    updateStatus = getString(R.string.update_latest, BuildConfig.VERSION_NAME)
                } else {
                    updateStatus = getString(R.string.update_downloaded, result.first.tag)
                    runCatching { updateRepository.install(result.second) }.onFailure {
                        message = it.message ?: "Не удалось открыть системный установщик"
                    }
                }
            }.onFailure {
                loading = false
                val reason = it.message ?: getString(R.string.update_unknown_error)
                updateStatus = getString(R.string.update_error, reason)
                message = updateStatus
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

    override fun onPostResume() {
        super.onPostResume()
        if (clearViewedIntentDataOnResume) {
            intent?.data = null
            clearViewedIntentDataOnResume = false
        }
    }

    private fun handleSubscriptionIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val source = intent.dataString ?: return
        clearViewedIntentDataOnResume = true
        if (source.startsWith("mieru://", ignoreCase = true)) {
            runCatching { MieruDeepLink.parse(source) }
                .onSuccess { pendingMieruImport = it }
                .onFailure { message = getString(R.string.mieru_import_invalid) }
            return
        }
        pendingSubscriptionImport = SubscriptionDeepLink.parse(source) ?: return
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
            val servers = repository.servers()
            latencyTestingIds = servers.mapTo(mutableSetOf()) { it.id }
            latencyResults = ServerLatencyTester().test(servers, concurrency = 10, timeoutMillis = 3_000)
            latencyTestingIds = emptySet()
            latencyTesting = false
        }
    }

    private fun testSingleServerLatency(server: ServerProfile) {
        if (server.id in latencyTestingIds) return
        lifecycleScope.launch {
            latencyTestingIds = latencyTestingIds + server.id
            val result = ServerLatencyTester().testOne(server, timeoutMillis = 3_000)
            latencyResults = latencyResults + (server.id to result)
            latencyTestingIds = latencyTestingIds - server.id
        }
    }

    private fun scanQrCode() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(::handleQrValue) }
            .addOnFailureListener { message = getString(R.string.qr_import_failed) }
    }

    private fun handleQrValue(raw: String) {
        runCatching { QrImportClassifier.classify(raw) }
            .onSuccess { payload ->
                when (payload) {
                    is QrImportPayload.Subscription -> pendingSubscriptionImport = payload.request.copy(
                        name = payload.request.name.ifBlank { getString(R.string.qr_subscription_name) },
                    )
                    is QrImportPayload.MieruProfile -> pendingMieruImport = payload.request
                    is QrImportPayload.Profile -> pendingQrProfile = payload.profile
                }
            }
            .onFailure { message = getString(R.string.qr_import_invalid) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionResolved &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            afterNotificationPermission = { requestConnect(config) }
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
            return
        }
        val pinned = repository.servers().firstOrNull { it.config == config } ?: return
        if (!vpnSettings.smartConnectEnabled) {
            SmartConnectState(this).pin(pinned.id)
            requestVpnPermission(pinned)
            return
        }
        lifecycleScope.launch {
            loading = true
            val servers = repository.servers()
            val results = ServerLatencyTester().test(servers, timeoutMillis = 3_000)
            latencyResults = results
            val state = SmartConnectState(this@MainActivity)
            val failures = state.record(pinned.id, results[pinned.id]?.latencyMillis != null)
            val chosen = SmartConnectPlanner.choose(pinned, servers, results, failures)
            if (chosen.id != pinned.id) {
                repository.select(chosen.id)
                state.pin(chosen.id)
                reloadUi++
                message = getString(R.string.smart_connect_switched, chosen.name)
            }
            loading = false
            requestVpnPermission(chosen)
        }
    }

    private fun requestVpnPermission(server: ServerProfile) {
        if (VpnService.prepare(this) == null) startVpn(server.config) else {
            pendingConfig = server.config
            permissionResult = { granted ->
                if (granted) pendingConfig?.let(::startVpn)
                pendingConfig = null
            }
            vpnPermissionLauncher.launch(VpnService.prepare(this))
        }
    }

    private fun startVpn(config: String) {
        val serverName = repository.servers().firstOrNull { it.config == config }?.name
        val intent = Intent(this, DualCoreVpnService::class.java)
            .setAction(DualCoreVpnService.ACTION_CONNECT)
            .putExtra(DualCoreVpnService.EXTRA_XRAY_CONFIG, config)
            .putExtra(DualCoreVpnService.EXTRA_SERVER_NAME, serverName)
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

private enum class AppTab { SPEED, HOME, SETTINGS, SUBSCRIPTIONS }

private fun AppTab.navigationOrder(): Int = when (this) {
    AppTab.SPEED, AppTab.SUBSCRIPTIONS -> 0
    AppTab.HOME -> 1
    AppTab.SETTINGS -> 2
}

@Composable
private fun AppTabIcon(tab: AppTab, selected: Boolean) {
    val (regular, filled) = when (tab) {
        AppTab.SPEED -> R.drawable.ic_speed_regular to R.drawable.ic_speed_filled
        AppTab.HOME -> R.drawable.ic_home_regular to R.drawable.ic_home_filled
        AppTab.SETTINGS -> R.drawable.ic_settings_regular to R.drawable.ic_settings_filled
        AppTab.SUBSCRIPTIONS -> return
    }
    val size = when (tab) {
        AppTab.HOME -> 29.dp
        AppTab.SPEED -> 28.dp
        AppTab.SETTINGS -> 27.dp
        AppTab.SUBSCRIPTIONS -> 28.dp
    }
    val yOffset = when (tab) {
        AppTab.HOME -> 0.dp
        AppTab.SPEED -> 1.dp
        AppTab.SETTINGS -> 0.5.dp
        AppTab.SUBSCRIPTIONS -> 0.dp
    }
    Crossfade(targetState = selected, animationSpec = tween(140), label = "tabIcon") { active ->
        Icon(
            painter = painterResource(if (active) filled else regular),
            contentDescription = null,
            tint = if (active) Accent else Muted,
            modifier = Modifier.size(size).offset(y = yOffset),
        )
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
    pendingSubscriptionImport: SubscriptionImportRequest?,
    onDismissSubscriptionImport: () -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onUpdateSubscription: (Subscription) -> Unit,
    onRemoveSubscription: (Subscription) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    updateStatus: String,
    onCheckUpdate: () -> Unit,
    onScanQr: () -> Unit,
    vpnSettings: VpnSettings,
    onSaveVpnSettings: (VpnSettings) -> Unit,
    latencyResults: Map<String, ServerLatencyResult>,
    latencyTesting: Boolean,
    latencyTestingIds: Set<String>,
    onTestLatency: () -> Unit,
    onTestServerLatency: (ServerProfile) -> Unit,
) {
    revision.hashCode()
    val vpnState by VpnSessionStore.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var subscriptionsParent by remember { mutableStateOf(AppTab.SPEED) }
    val subscriptions = repository.subscriptions()
    val servers = repository.servers()
    val selected = servers.firstOrNull { it.id == repository.selectedServerId() } ?: servers.firstOrNull()

    Column(Modifier.fillMaxSize().background(Background)) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val direction = if (targetState.navigationOrder() >= initialState.navigationOrder()) 1 else -1
                    (slideInHorizontally(tween(280)) { width -> direction * width / 5 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { width -> -direction * width / 6 } + fadeOut(tween(160)))
                },
                label = "mainTab",
            ) { activeTab ->
            when (activeTab) {
                AppTab.HOME -> HomeDashboard(
                    state = vpnState,
                    selected = selected,
                    servers = servers,
                    subscriptions = subscriptions,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onSelect = onSelect,
                    onManageSubscriptions = {
                        subscriptionsParent = AppTab.HOME
                        tab = AppTab.SUBSCRIPTIONS
                    },
                )
                AppTab.SPEED -> SpeedDashboard(
                    state = vpnState,
                    selected = selected,
                    servers = servers,
                    subscriptions = subscriptions,
                    latencyResults = latencyResults,
                    latencyTesting = latencyTesting,
                    latencyTestingIds = latencyTestingIds,
                    onTestLatency = onTestLatency,
                    onTestServerLatency = onTestServerLatency,
                    onSelect = onSelect,
                    onManageSubscriptions = {
                        subscriptionsParent = AppTab.SPEED
                        tab = AppTab.SUBSCRIPTIONS
                    },
                )
                AppTab.SUBSCRIPTIONS -> SubscriptionsScreen(
                    subscriptions = subscriptions,
                    loading = loading,
                    onBack = { tab = subscriptionsParent },
                    onAdd = onAddSubscription,
                    onUpdate = onUpdateSubscription,
                    onRemove = onRemoveSubscription,
                )
                AppTab.SETTINGS -> SettingsScreen(
                    vpnSettings,
                    onSaveVpnSettings,
                    onExportBackup,
                    onImportBackup,
                    updateStatus,
                    onCheckUpdate,
                    onScanQr,
                )
            }
            }
            if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
            }
        }
        Box(
            modifier = Modifier.background(Background).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Surface(
                color = Color(0x52171B19),
                shape = RoundedCornerShape(23.dp),
                border = BorderStroke(1.dp, Color(0x6189928E)),
                modifier = Modifier.fillMaxWidth().height(72.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(23.dp),
                        ambientColor = Color.Black.copy(alpha = .70f),
                        spotColor = Accent.copy(alpha = .12f),
                    )
                    .clip(RoundedCornerShape(23.dp)),
            ) {
                val strings = dashboardStrings()
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().blur(14.dp).background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = .07f),
                                    Accent.copy(alpha = .055f),
                                    Color.Black.copy(alpha = .10f),
                                ),
                            ),
                        ),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = .22f), Color.Transparent),
                            ),
                        ),
                    )
                    BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                    val destinations = listOf(AppTab.SPEED, AppTab.HOME, AppTab.SETTINGS)
                    val activeDestination = if (tab == AppTab.SUBSCRIPTIONS) subscriptionsParent else tab
                    val activeIndex = destinations.indexOf(activeDestination).coerceAtLeast(0)
                    val itemWidth = (maxWidth - 8.dp) / 3
                    val indicatorX by animateDpAsState(
                        targetValue = (itemWidth + 4.dp) * activeIndex,
                        animationSpec = tween(320),
                        label = "navIndicator",
                    )
                    Box(
                        Modifier.offset(x = indicatorX).width(itemWidth).fillMaxHeight()
                            .background(Color(0x701C2521), RoundedCornerShape(18.dp))
                            .border(1.dp, Color(0x80A6F3D1), RoundedCornerShape(18.dp)),
                    )
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        destinations.forEach { item ->
                            val label = when (item) {
                                AppTab.SPEED -> strings.speed
                                AppTab.HOME -> strings.home
                                AppTab.SETTINGS -> strings.settings
                                AppTab.SUBSCRIPTIONS -> ""
                            }
                            val active = activeDestination == item
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                                    if (tab != item) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        tab = item
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Transparent,
                            ) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    AppTabIcon(item, active)
                                    Text(label, color = if (active) Color(0xFFA6F3D1) else Color(0xFFA0A5A2), fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    }
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
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit, onUpdate: (Subscription) -> Unit, onRemove: (Subscription) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹", color = Accent, fontSize = 30.sp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.subscriptions_back), color = Accent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(
                stringResource(R.string.subscriptions_title),
                stringResource(R.string.subscriptions_count, subscriptions.size),
                Modifier.weight(1f),
            )
            val addSubscriptionLabel = stringResource(R.string.subscriptions_add)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = addSubscriptionLabel }
                    .clickable(enabled = !loading) { showAdd = true },
            ) {
                Surface(
                    color = Accent,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", color = Color.Black, fontSize = 24.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (subscriptions.isEmpty()) EmptyState(
            stringResource(R.string.subscriptions_empty_title),
            stringResource(R.string.subscriptions_empty_text),
            stringResource(R.string.subscriptions_add),
            { showAdd = true },
        )
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
                            OutlinedButton(onClick = { onUpdate(subscription) }, enabled = !loading) { Text(stringResource(R.string.subscriptions_update)) }
                            TextButton(onClick = { onRemove(subscription) }, enabled = !loading) { Text(stringResource(R.string.subscriptions_remove), color = Danger) }
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

private enum class SettingsPage { ROOT, TRAFFIC, FEATURES }

@Composable
private fun SettingsScreen(
    settings: VpnSettings,
    onSave: (VpnSettings) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    updateStatus: String,
    onCheckUpdate: () -> Unit,
    onScanQr: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    when (page) {
        SettingsPage.ROOT -> Column(Modifier.fillMaxSize()) {
            DashboardHeader()
            SettingsRoot(
                settings = settings,
                onOpenTraffic = { page = SettingsPage.TRAFFIC },
                onOpenFeatures = { page = SettingsPage.FEATURES },
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                updateStatus = updateStatus,
                onCheckUpdate = onCheckUpdate,
            )
        }
        SettingsPage.TRAFFIC -> VpnSettingsDetails(
            settings = settings,
            onSave = onSave,
            onBack = { page = SettingsPage.ROOT },
        )
        SettingsPage.FEATURES -> AdvancedFeaturesScreen(
            initial = settings,
            onBack = { page = SettingsPage.ROOT },
            onSave = onSave,
            onScanQr = onScanQr,
        )
    }
}

@Composable
private fun SettingsRoot(
    settings: VpnSettings,
    onOpenTraffic: () -> Unit,
    onOpenFeatures: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    updateStatus: String,
    onCheckUpdate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsSectionTitle(stringResource(R.string.settings_vpn_traffic)) }
        item {
            SettingsNavigationCard(
                title = stringResource(R.string.settings_traffic_title),
                description = stringResource(R.string.settings_traffic_description),
                value = routingModeLabel(settings.routingMode),
                onClick = onOpenTraffic,
            )
        }
        item {
            SettingsNavigationCard(
                title = stringResource(R.string.features_title),
                description = stringResource(R.string.features_subtitle),
                value = stringResource(R.string.open),
                onClick = onOpenFeatures,
            )
        }
        item { SettingsSectionTitle(stringResource(R.string.settings_core)) }
        item {
            SettingsNavigationCard(
                title = stringResource(R.string.settings_core_title),
                description = stringResource(R.string.settings_core_description),
                value = engineName(settings.engine),
                onClick = onOpenTraffic,
            )
        }
        item { SettingsSectionTitle(stringResource(R.string.settings_service)) }
        item {
            SettingsNavigationCard(
                title = stringResource(R.string.settings_backup_title),
                description = stringResource(R.string.settings_backup_description),
                value = stringResource(R.string.settings_export),
                onClick = onExportBackup,
            )
        }
        item {
            SettingsNavigationCard(
                title = stringResource(R.string.settings_restore_title),
                description = stringResource(R.string.settings_restore_description),
                value = stringResource(R.string.settings_import),
                onClick = onImportBackup,
            )
        }
        item {
            SettingsNavigationCard(
                title = stringResource(R.string.settings_update_title),
                description = updateStatus,
                value = stringResource(R.string.settings_update_check),
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
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = ContentPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Muted, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Text(value, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
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
    Text(text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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
