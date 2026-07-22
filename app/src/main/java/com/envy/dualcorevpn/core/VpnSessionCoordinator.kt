package com.envy.dualcorevpn.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SessionTransport {
    fun start(): Int
    fun stop()
}

class VpnSessionCoordinator(
    private val engine: CoreEngine,
    private val transport: SessionTransport,
) {
    private val mutex = Mutex()
    private var active = false

    suspend fun start(config: String) = mutex.withLock {
        check(!active) { "VPN session is already active" }
        val validation = engine.validate(config)
        require(validation is ValidationResult.Valid) {
            (validation as ValidationResult.Invalid).reason
        }

        val tunFileDescriptor = transport.start()
        try {
            engine.start(config, tunFileDescriptor)
        } catch (failure: Throwable) {
            runCatching { transport.stop() }.onFailure(failure::addSuppressed)
            throw failure
        }
        active = true
    }

    suspend fun stop() = mutex.withLock {
        if (!active) return@withLock
        var failure: Throwable? = null
        try {
            engine.stop()
        } catch (engineFailure: Throwable) {
            failure = engineFailure
        }
        try {
            transport.stop()
        } catch (transportFailure: Throwable) {
            if (failure == null) failure = transportFailure
            else failure.addSuppressed(transportFailure)
        }
        active = false
        failure?.let { throw it }
    }
}
