package com.envy.dualcorevpn.core

sealed interface VpnSessionState {
    data object Disconnected : VpnSessionState

    data class Connecting(
        val engine: EngineKind,
        val server: VpnSessionServer? = null,
    ) : VpnSessionState

    data class Connected(
        val engine: EngineKind,
        val startedAtElapsedRealtimeMillis: Long,
        val server: VpnSessionServer? = null,
    ) : VpnSessionState

    data class Disconnecting(
        val engine: EngineKind,
    ) : VpnSessionState

    data class Error(
        val engine: EngineKind?,
        val message: String,
    ) : VpnSessionState
}

data class VpnSessionServer(
    val profileId: String,
    val protocol: String,
    val address: String,
    val port: Int,
)
