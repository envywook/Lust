package com.v2ray.ang.service

/** JNI bridge matching the class and method names registered by v2rayNG's HEV library. */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fileDescriptor: Int)

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStopService()

    @JvmStatic
    @Suppress("FunctionName", "unused")
    private external fun TProxyGetStats(): LongArray

    fun start(configPath: String, fileDescriptor: Int) {
        TProxyStartService(configPath, fileDescriptor)
    }

    fun stop() {
        TProxyStopService()
    }
}
