package com.envy.dualcorevpn.core

/** Native callback adapter used to keep protected sockets outside the VPN tunnel. */
class NativeXrayHandler(
    private val protectFd: (Int) -> Boolean,
) {
    fun protect(fileDescriptor: Long): Boolean {
        if (fileDescriptor !in 0..Int.MAX_VALUE.toLong()) return false
        return protectFd(fileDescriptor.toInt())
    }
}
