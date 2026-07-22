package com.envy.dualcorevpn.vpn

import android.os.ParcelFileDescriptor
import com.envy.dualcorevpn.core.SessionTransport
import com.v2ray.ang.service.TProxyService

internal interface Tun2SocksGateway {
    fun start(configPath: String, fileDescriptor: Int)
    fun stop()
}

internal object NativeTun2SocksGateway : Tun2SocksGateway {
    override fun start(configPath: String, fileDescriptor: Int) =
        TProxyService.start(configPath, fileDescriptor)

    override fun stop() = TProxyService.stop()
}

internal class Tun2SocksStarter(
    private val gateway: Tun2SocksGateway,
    private val onFailure: (Throwable) -> Unit,
) {
    fun start(configPath: String, fileDescriptor: Int) {
        try {
            gateway.start(configPath, fileDescriptor)
        } catch (failure: Throwable) {
            onFailure(failure)
            throw failure
        }
    }
}

internal class AndroidTunSessionTransport(
    private val establishTun: () -> ParcelFileDescriptor,
    private val writeConfig: (String) -> String,
    private val gateway: Tun2SocksGateway = NativeTun2SocksGateway,
    private val hevConfig: HevConfig = HevConfig(),
    private val onFailure: (Throwable) -> Unit = {},
) : SessionTransport {
    private var tunDescriptor: ParcelFileDescriptor? = null

    override fun start(): Int {
        check(tunDescriptor == null) { "TUN transport is already running" }
        val descriptor = establishTun()
        try {
            val configPath = writeConfig(hevConfig.toYaml())
            Tun2SocksStarter(gateway, onFailure).start(configPath, descriptor.fd)

            tunDescriptor = descriptor
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
        tunDescriptor?.close()
        tunDescriptor = null
        failure?.let { throw it }
    }
}
