package com.envy.dualcorevpn.core

enum class EngineKind { XRAY, SING_BOX }

enum class EngineStartupOrder { TRANSPORT_FIRST, ENGINE_FIRST }

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

interface CoreEngine {
    val kind: EngineKind
    val startupOrder: EngineStartupOrder
        get() = EngineStartupOrder.TRANSPORT_FIRST
    suspend fun validate(config: String): ValidationResult
    suspend fun start(config: String, tunFileDescriptor: Int)
    suspend fun stop()
}
