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
    fun `publishes every state transition`() {
        val published = mutableListOf<VpnSessionState>()
        val machine = VpnSessionStateMachine(onStateChanged = { published.add(it) })

        machine.dispatch(VpnEvent.ConnectRequested(EngineKind.XRAY))
        machine.dispatch(VpnEvent.Connected(123L))
        machine.dispatch(VpnEvent.DisconnectRequested)
        machine.dispatch(VpnEvent.Disconnected)

        assertEquals(
            listOf(
                VpnSessionState.Connecting(EngineKind.XRAY),
                VpnSessionState.Connected(EngineKind.XRAY, 123L),
                VpnSessionState.Disconnecting(EngineKind.XRAY),
                VpnSessionState.Disconnected,
            ),
            published,
        )
    }

    @Test
    fun `disconnect during startup cancels cleanly`() {
        val machine = VpnSessionStateMachine()

        machine.dispatch(VpnEvent.ConnectRequested(EngineKind.XRAY))

        assertEquals(VpnSessionState.Disconnecting(EngineKind.XRAY), machine.dispatch(VpnEvent.DisconnectRequested))
        assertEquals(VpnSessionState.Disconnected, machine.dispatch(VpnEvent.Disconnected))
    }

    @Test
    fun `service termination resets every active state`() {
        val activeStates = listOf(
            VpnSessionState.Connecting(EngineKind.XRAY),
            VpnSessionState.Connected(EngineKind.XRAY, 123L),
            VpnSessionState.Disconnecting(EngineKind.XRAY),
            VpnSessionState.Error(EngineKind.XRAY, "failed"),
        )

        activeStates.forEach { initial ->
            assertEquals(VpnSessionState.Disconnected, VpnSessionStateMachine(initial).dispatch(VpnEvent.Terminated))
        }
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
