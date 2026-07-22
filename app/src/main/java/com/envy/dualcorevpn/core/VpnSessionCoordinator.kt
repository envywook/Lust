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

        when (engine.startupOrder) {
            EngineStartupOrder.ENGINE_FIRST -> startEngineFirst(config)
            EngineStartupOrder.TRANSPORT_FIRST -> startTransportFirst(config)
        }
        active = true
    }

    private suspend fun startEngineFirst(config: String) {
        engine.start(config, -1)
        try {
            transport.start()
        } catch (failure: Throwable) {
            runCatching { engine.stop() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private suspend fun startTransportFirst(config: String) {
        val tunFileDescriptor = transport.start()
        try {
            engine.start(config, tunFileDescriptor)
        } catch (failure: Throwable) {
            runCatching { transport.stop() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    suspend fun stop() = mutex.withLock {
        if (!active) return@withLock
        val firstStop: suspend () -> Unit
        val secondStop: suspend () -> Unit
        if (engine.startupOrder == EngineStartupOrder.ENGINE_FIRST) {
            firstStop = { transport.stop() }
            secondStop = engine::stop
        } else {
            firstStop = engine::stop
            secondStop = { transport.stop() }
        }
        var failure: Throwable? = null
        try {
            firstStop()
        } catch (firstFailure: Throwable) {
            failure = firstFailure
        }
        try {
            secondStop()
        } catch (secondFailure: Throwable) {
            if (failure == null) failure = secondFailure else failure.addSuppressed(secondFailure)
        }
        active = false
        failure?.let { throw it }
    }
}
