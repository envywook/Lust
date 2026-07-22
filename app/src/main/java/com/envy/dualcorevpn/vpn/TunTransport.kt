package com.envy.dualcorevpn.vpn

import android.os.ParcelFileDescriptor
import com.v2ray.ang.service.TProxyService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface TunTransport {
    fun start(tun: ParcelFileDescriptor, config: HevConfig)
    fun stop()
}

class HevTunTransport(private val cacheDir: File) : TunTransport {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun start(tun: ParcelFileDescriptor, config: HevConfig) {
        check(running.compareAndSet(false, true)) { "TUN transport is already running" }
        val configFile = File(cacheDir, "hev-socks5-tunnel.yml")
        configFile.writeText(config.toYaml())
        worker = Thread({
            try {
                TProxyService.start(configFile.absolutePath, tun.fd)
            } finally {
                running.set(false)
            }
        }, "hev-tun-transport").apply { start() }
    }

    override fun stop() {
        if (!running.getAndSet(false)) return
        TProxyService.stop()
        worker?.join(3_000)
        worker = null
    }
}
