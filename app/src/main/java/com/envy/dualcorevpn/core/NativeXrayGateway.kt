package com.envy.dualcorevpn.core

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import com.envy.dualcorevpn.logging.AppLog

internal class NativeXrayGateway(
    private val controller: CoreController = Libv2ray.newCoreController(LoggingCoreCallbackHandler),
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

    private object LoggingCoreCallbackHandler : CoreCallbackHandler {
        override fun onEmitStatus(type: Long, message: String?): Long {
            message?.takeIf(String::isNotBlank)?.let { AppLog.info("Xray", "[$type] $it") }
            return 0L
        }

        override fun shutdown(): Long {
            AppLog.info("Xray", "Core shutdown callback")
            return 0L
        }

        override fun startup(): Long {
            AppLog.info("Xray", "Core startup callback")
            return 0L
        }
    }
}
