package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSessionStateMachineTest {
    @Test
    fun `valid connection lifecycle reaches connected then idle`() {
        val machine = VpnSessionStateMachine()
        assertTrue(machine.dispatch(VpnSessionState.Preparing))
        assertTrue(machine.dispatch(VpnSessionState.Connecting))
        assertTrue(machine.dispatch(VpnSessionState.Connected))
        assertTrue(machine.dispatch(VpnSessionState.Disconnecting))
        assertTrue(machine.dispatch(VpnSessionState.Idle))
        assertEquals(VpnSessionState.Idle, machine.state.value)
    }

    @Test
    fun `cannot report connected directly from idle`() {
        val machine = VpnSessionStateMachine()
        assertFalse(machine.dispatch(VpnSessionState.Connected))
        assertEquals(VpnSessionState.Idle, machine.state.value)
    }

    @Test
    fun `error can be entered during startup and recovered`() {
        val machine = VpnSessionStateMachine()
        machine.dispatch(VpnSessionState.Preparing)
        machine.dispatch(VpnSessionState.Connecting)
        assertTrue(machine.dispatch(VpnSessionState.Error("core failed")))
        assertTrue(machine.dispatch(VpnSessionState.Idle))
    }
}
