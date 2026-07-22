package com.envy.dualcorevpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnSessionStore {
    private val mutableState = MutableStateFlow<VpnSessionState>(VpnSessionState.Disconnected)
    val state: StateFlow<VpnSessionState> = mutableState.asStateFlow()

    fun update(state: VpnSessionState) {
        mutableState.value = state
    }
}
