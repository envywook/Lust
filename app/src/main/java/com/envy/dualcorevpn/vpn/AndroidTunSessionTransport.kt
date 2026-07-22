package com.envy.dualcorevpn.vpn

import android.os.ParcelFileDescriptor
import com.envy.dualcorevpn.core.SessionTransport
import com.v2ray.ang.service.TProxyService

class AndroidTunSessionTransport(
    private val establishTun: () -> ParcelFileDescriptor,
    private val hevConfig: HevConfig = HevConfig(),
) : SessionTransport {
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null

    override fun start(): Int {
        check(tunDescriptor == null) { "TUN transport is already running" }
        val descriptor = establishTun()
        try {
            val thread = Thread(
                { TProxyService.runTun2socks(hevConfig.toYaml(), descriptor.fd) },
                "hev-socks5-tunnel",
            ).apply { start() }
            tunDescriptor = descriptor
            tunnelThread = thread
            return descriptor.fd
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }

    override fun stop() {
        if (tunDescriptor == null) return
        TProxyService.stopTun2socks()
        tunnelThread?.join(STOP_TIMEOUT_MILLIS)
        tunnelThread = null
        tunDescriptor?.close()
        tunDescriptor = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 2_000L
    }
}
