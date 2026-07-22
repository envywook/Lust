package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class XrayEngineTest {
    @Test
    fun `start passes config and tun descriptor to gateway`() = runTest {
        val gateway = RecordingGateway()
        val engine = XrayEngine(gateway, XrayConfigValidator)
        val config = """{"inbounds":[],"outbounds":[{"protocol":"freedom"}]}"""

        engine.start(config, 42)
        engine.stop()

        assertEquals(listOf(config to 42), gateway.started)
        assertEquals(1, gateway.stopCount)
    }

    @Test
    fun `invalid config never reaches native gateway`() = runTest {
        val gateway = RecordingGateway()
        val engine = XrayEngine(gateway, XrayConfigValidator)

        assertFailsWith<IllegalArgumentException> { engine.start("not json", 42) }
        assertEquals(emptyList<Pair<String, Int>>(), gateway.started)
    }

    private class RecordingGateway : XrayGateway {
        val started = mutableListOf<Pair<String, Int>>()
        var stopCount = 0
        override fun start(config: String, tunFileDescriptor: Int) { started += config to tunFileDescriptor }
        override fun stop() { stopCount++ }
        override fun measureDelay(): Long = 1
        override fun version(): String = "test"
    }
}
