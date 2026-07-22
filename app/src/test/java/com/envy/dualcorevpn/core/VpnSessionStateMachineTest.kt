package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnSessionStateMachineTest {
    @Test
    fun `valid connection lifecycle reaches connected then disconnected`() {
        val machine = VpnSessionStateMachine()

        assertEquals(VpnSessionState.Connecting(EngineKind.XRAY), machine.dispatch(VpnEvent.ConnectRequested(EngineKind.XRAY)))
        assertEquals(VpnSessionState.Connected(EngineKind.XRAY, 123L), machine.dispatch(VpnEvent.Connected(123L)))
        assertEquals(VpnSessionState.Disconnecting(EngineKind.XRAY), machine.dispatch(VpnEvent.DisconnectRequested))
        assertEquals(VpnSessionState.Disconnected, machine.dispatch(VpnEvent.Disconnected))
    }

    @Test
    fun `cannot report connected directly from disconnected`() {
        val machine = VpnSessionStateMachine()
        assertFailsWith<IllegalStateException> { machine.dispatch(VpnEvent.Connected(123L)) }
        assertEquals(VpnSessionState.Disconnected, machine.state)
    }

    @Test
    fun `startup failure keeps engine and message`() {
        val machine = VpnSessionStateMachine()
        machine.dispatch(VpnEvent.ConnectRequested(EngineKind.SING_BOX))
        assertEquals(
            VpnSessionState.Error(EngineKind.SING_BOX, "core failed"),
            machine.dispatch(VpnEvent.Failed("core failed")),
        )
    }
}
