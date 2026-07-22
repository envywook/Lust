package com.envy.dualcorevpn.core

sealed interface VpnSessionState {
    data object Disconnected : VpnSessionState

    data class Connecting(
        val engine: EngineKind,
    ) : VpnSessionState

    data class Connected(
        val engine: EngineKind,
        val startedAtEpochMillis: Long,
    ) : VpnSessionState

    data class Disconnecting(
        val engine: EngineKind,
    ) : VpnSessionState

    data class Error(
        val engine: EngineKind?,
        val message: String,
    ) : VpnSessionState
}
