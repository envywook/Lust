package com.envy.dualcorevpn.core

sealed interface VpnEvent {
    data class ConnectRequested(val engine: EngineKind) : VpnEvent
    data class Connected(val startedAtEpochMillis: Long) : VpnEvent
    data object DisconnectRequested : VpnEvent
    data object Disconnected : VpnEvent
    data class Failed(val message: String) : VpnEvent
}

class VpnSessionStateMachine(
    initial: VpnSessionState = VpnSessionState.Disconnected,
) {
    var state: VpnSessionState = initial
        private set

    fun dispatch(event: VpnEvent): VpnSessionState {
        state = when (val current = state) {
            VpnSessionState.Disconnected -> when (event) {
                is VpnEvent.ConnectRequested -> VpnSessionState.Connecting(event.engine)
                else -> invalid(event)
            }

            is VpnSessionState.Connecting -> when (event) {
                is VpnEvent.Connected -> VpnSessionState.Connected(current.engine, event.startedAtEpochMillis)
                is VpnEvent.Failed -> VpnSessionState.Error(current.engine, event.message)
                else -> invalid(event)
            }

            is VpnSessionState.Connected -> when (event) {
                VpnEvent.DisconnectRequested -> VpnSessionState.Disconnecting(current.engine)
                is VpnEvent.Failed -> VpnSessionState.Error(current.engine, event.message)
                else -> invalid(event)
            }

            is VpnSessionState.Disconnecting -> when (event) {
                VpnEvent.Disconnected -> VpnSessionState.Disconnected
                is VpnEvent.Failed -> VpnSessionState.Error(current.engine, event.message)
                else -> invalid(event)
            }

            is VpnSessionState.Error -> when (event) {
                VpnEvent.Disconnected -> VpnSessionState.Disconnected
                is VpnEvent.ConnectRequested -> VpnSessionState.Connecting(event.engine)
                else -> invalid(event)
            }
        }
        return state
    }

    private fun invalid(event: VpnEvent): Nothing =
        error("Invalid transition: $state + $event")
}
