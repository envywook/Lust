package com.envy.dualcorevpn.core

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

internal class NativeXrayGateway(
    private val controller: CoreController = Libv2ray.newCoreController(NoOpCoreCallbackHandler),
) : XrayGateway {
    override fun start(config: String, tunFileDescriptor: Int) {
        controller.startLoop(config, tunFileDescriptor)
    }

    override fun stop() {
        if (controller.isRunning) controller.stopLoop()
    }

    override fun measureDelay(): Long = controller.measureDelay("")

    override fun version(): String = Libv2ray.checkVersionX()

    /** Adapter retained for native integrations that expose socket protection callbacks. */
    class NativeHandler(
        private val protectFd: (Int) -> Boolean,
    ) {
        fun protect(fileDescriptor: Long): Boolean {
            if (fileDescriptor !in 0..Int.MAX_VALUE.toLong()) return false
            return protectFd(fileDescriptor.toInt())
        }
    }

    private object NoOpCoreCallbackHandler : CoreCallbackHandler {
        override fun onEmitStatus(type: Long, message: String?): Long = 0L
        override fun shutdown(): Long = 0L
        override fun startup(): Long = 0L
    }
}
