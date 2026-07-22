package com.v2ray.ang.service

object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun runTun2socks(config: String, tunFileDescriptor: Int)

    @JvmStatic
    external fun stopTun2socks()
}
