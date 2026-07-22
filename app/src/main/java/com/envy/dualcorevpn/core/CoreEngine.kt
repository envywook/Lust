package com.envy.dualcorevpn.core

enum class EngineKind { XRAY, SING_BOX }

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

interface CoreEngine {
    val kind: EngineKind
    suspend fun validate(config: String): ValidationResult
    suspend fun start(config: String, tunFileDescriptor: Int)
    suspend fun stop()
}
