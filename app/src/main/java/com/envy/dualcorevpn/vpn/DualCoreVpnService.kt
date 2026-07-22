package com.envy.dualcorevpn.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.envy.dualcorevpn.MainActivity
import com.envy.dualcorevpn.R
import com.envy.dualcorevpn.core.EngineKind
import com.envy.dualcorevpn.core.NativeSingBoxGateway
import com.envy.dualcorevpn.core.NativeXrayGateway
import com.envy.dualcorevpn.core.SingBoxEngine
import com.envy.dualcorevpn.core.VpnEvent
import com.envy.dualcorevpn.core.VpnSessionCoordinator
import com.envy.dualcorevpn.core.VpnSessionState
import com.envy.dualcorevpn.core.VpnSessionStore
import com.envy.dualcorevpn.core.VpnSessionStateMachine
import com.envy.dualcorevpn.core.XrayConfigValidator
import com.envy.dualcorevpn.core.XrayEngine
import com.envy.dualcorevpn.core.XrayRuntime
import com.envy.dualcorevpn.logging.AppLog
import com.envy.dualcorevpn.settings.VpnSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class DualCoreVpnService : VpnService() {
    private val stateMachine = VpnSessionStateMachine(onStateChanged = VpnSessionStore::update)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operation: Job? = null
    private var coordinator: VpnSessionCoordinator? = null
    private var initializationFailure: Throwable? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.initialize(File(filesDir, "logs"))
        AppLog.info("VPN", "Service created")
        initializationFailure = runCatching { XrayRuntime.initialize(this) }
            .exceptionOrNull()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect(intent.getStringExtra(EXTRA_XRAY_CONFIG))
            ACTION_DISCONNECT -> disconnect()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun connect(config: String?) {
        if (coordinator != null) return
        val engineKind = VpnSettingsRepository(this).load().engine
        stateMachine.dispatch(VpnEvent.ConnectRequested(engineKind))
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
        operation?.cancel()
        operation = serviceScope.launch {
            try {
                if (engineKind == EngineKind.XRAY) {
                    initializationFailure?.let { throw IllegalStateException("Xray runtime initialization failed", it) }
                }
                require(!config.isNullOrBlank()) { "Proxy configuration is required" }
                AppLog.info("VPN", "Starting $engineKind + HEV session")
                val session = createCoordinator(engineKind)
                coordinator = session
                session.start(config)
                stateMachine.dispatch(VpnEvent.Connected(System.currentTimeMillis()))
                AppLog.info("VPN", "Session connected")
                updateNotification(getString(R.string.status_connected))
            } catch (cancelled: CancellationException) {
                AppLog.info("VPN", "Session start cancelled")
                throw cancelled
            } catch (error: Throwable) {
                AppLog.error("VPN", "Session start failed: ${error.message ?: error.javaClass.simpleName}", error)
                coordinator = null
                stateMachine.dispatch(VpnEvent.Failed(error.message ?: "VPN start failed"))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun disconnect() {
        if (coordinator == null && operation?.isActive != true) return
        stateMachine.dispatch(VpnEvent.DisconnectRequested)
        operation?.cancel()
        operation = serviceScope.launch {
            stopSession()
            stateMachine.dispatch(VpnEvent.Disconnected)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createCoordinator(engineKind: EngineKind): VpnSessionCoordinator {
        val settings = VpnSettingsRepository(this).load()
        val engine = when (engineKind) {
            EngineKind.XRAY -> XrayEngine(
                gateway = NativeXrayGateway(),
                validator = XrayConfigValidator,
            )
            EngineKind.SING_BOX -> SingBoxEngine(
                NativeSingBoxGateway(this) { exitCode ->
                    handleRuntimeFailure(EngineKind.SING_BOX, "sing-box stopped unexpectedly (exit=$exitCode)")
                },
            )
        }
        val tunTransport = AndroidTunSessionTransport(
            establishTun = {
                Builder()
                    .setSession(getString(R.string.app_name))
                    .setMtu(settings.mtu)
                    .addAddress("198.18.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(settings.dnsServer)
                    .addDisallowedApplication(packageName)
                    .apply {
                        if (settings.ipv6Enabled) {
                            addAddress("fc00::1", 126)
                            addRoute("::", 0)
                        }
                    }
                    .establish() ?: error("Android refused to establish the VPN interface")
            },
            writeConfig = { content ->
                File(filesDir, "hev-socks5-tunnel.yaml").apply { writeText(content) }.absolutePath
            },
            hevConfig = HevConfig(mtu = settings.mtu, ipv6Enabled = settings.ipv6Enabled),
            onFailure = { failure ->
                AppLog.error("HEV", "Native tunnel failed", failure)
                handleRuntimeFailure(engineKind, failure.message ?: "HEV tunnel failed")
            },
        )
        return VpnSessionCoordinator(engine, tunTransport)
    }

    private fun handleRuntimeFailure(engine: EngineKind, message: String) {
        serviceScope.launch {
            val active = when (val state = stateMachine.state) {
                is VpnSessionState.Connecting -> state.engine == engine
                is VpnSessionState.Connected -> state.engine == engine
                else -> false
            }
            if (!active) return@launch
            AppLog.error("VPN", message)
            stateMachine.dispatch(VpnEvent.Failed(message))
            runCatching { stopSession() }
                .onFailure { AppLog.error("VPN", "Failed to stop broken session", it) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun stopSession() {
        try {
            coordinator?.stop()
        } finally {
            coordinator = null
        }
    }

    override fun onDestroy() {
        runBlocking(Dispatchers.IO) {
            operation?.cancelAndJoin()
            stopSession()
        }
        if (stateMachine.state !is VpnSessionState.Error) {
            stateMachine.dispatch(VpnEvent.Terminated)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        AppLog.warn("VPN", "VPN permission revoked")
        disconnect()
        super.onRevoke()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun buildNotification(status: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(status)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            0,
            getString(R.string.disconnect),
            PendingIntent.getService(
                this,
                1,
                Intent(this, DualCoreVpnService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(status)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.envy.dualcorevpn.CONNECT"
        const val ACTION_DISCONNECT = "com.envy.dualcorevpn.DISCONNECT"
        const val EXTRA_XRAY_CONFIG = "com.envy.dualcorevpn.XRAY_CONFIG"
        private const val CHANNEL_ID = "vpn_connection"
        private const val NOTIFICATION_ID = 1001
    }
}
