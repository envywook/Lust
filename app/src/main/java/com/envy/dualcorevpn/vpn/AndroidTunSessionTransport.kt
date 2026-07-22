package com.envy.dualcorevpn.vpn

import android.os.ParcelFileDescriptor
import com.envy.dualcorevpn.core.SessionTransport
import com.v2ray.ang.service.TProxyService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal interface Tun2SocksGateway {
    fun start(configPath: String, fileDescriptor: Int)
    fun stop()
}

internal object NativeTun2SocksGateway : Tun2SocksGateway {
    override fun start(configPath: String, fileDescriptor: Int) =
        TProxyService.start(configPath, fileDescriptor)

    override fun stop() = TProxyService.stop()
}

internal class AndroidTunSessionTransport(
    private val establishTun: () -> ParcelFileDescriptor,
    private val writeConfig: (String) -> String,
    private val gateway: Tun2SocksGateway = NativeTun2SocksGateway,
    private val hevConfig: HevConfig = HevConfig(),
    private val onFailure: (Throwable) -> Unit = {},
) : SessionTransport {
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null

    override fun start(): Int {
        check(tunDescriptor == null) { "TUN transport is already running" }
        val descriptor = establishTun()
        try {
            val configPath = writeConfig(hevConfig.toYaml())
            val finished = CountDownLatch(1)
            val startupFailure = AtomicReference<Throwable?>()
            val thread = Thread(
                {
                    try {
                        gateway.start(configPath, descriptor.fd)
                        val failure = IllegalStateException("HEV tun2socks stopped unexpectedly")
                        startupFailure.set(failure)
                        onFailure(failure)
                    } catch (failure: Throwable) {
                        startupFailure.set(failure)
                        onFailure(failure)
                    } finally {
                        finished.countDown()
                    }
                },
                "hev-socks5-tunnel",
            ).apply { start() }

            if (finished.await(STARTUP_PROBE_MILLIS, TimeUnit.MILLISECONDS)) {
                throw startupFailure.get() ?: IllegalStateException("HEV tun2socks stopped during startup")
            }

            tunDescriptor = descriptor
            tunnelThread = thread
            // HEV owns Android's TUN FD; Xray must expose a local SOCKS inbound instead.
            return 0
        } catch (failure: Throwable) {
            runCatching { gateway.stop() }
            descriptor.close()
            throw failure
        }
    }

    override fun stop() {
        if (tunDescriptor == null) return
        var failure: Throwable? = null
        try {
            gateway.stop()
        } catch (stopFailure: Throwable) {
            failure = stopFailure
        }
        tunnelThread?.join(STOP_TIMEOUT_MILLIS)
        tunnelThread = null
        tunDescriptor?.close()
        tunDescriptor = null
        failure?.let { throw it }
    }

    private companion object {
        const val STARTUP_PROBE_MILLIS = 150L
        const val STOP_TIMEOUT_MILLIS = 2_000L
    }
}
