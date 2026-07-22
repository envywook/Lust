package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayEngineTest {
    @Test fun `start passes valid config to gateway and stop is idempotent`() = runTest {
        val gateway = RecordingGateway()
        val engine = XrayEngine(gateway, XrayConfigValidator())
        val config = """{"inbounds":[],"outbounds":[{"protocol":"freedom"}]}"""

        engine.start(config)
        engine.stop()
        engine.stop()

        assertEquals(listOf(config), gateway.started)
        assertEquals(1, gateway.stopCount)
    }

    @Test fun `invalid config never reaches native gateway`() = runTest {
        val gateway = RecordingGateway()
        val engine = XrayEngine(gateway, XrayConfigValidator())
        assertFailsWith<IllegalArgumentException> { engine.start("not json") }
        assertEquals(emptyList<String>(), gateway.started)
    }

    @Test fun `second start is rejected until stop`() = runTest {
        val engine = XrayEngine(RecordingGateway(), XrayConfigValidator())
        val config = """{"inbounds":[],"outbounds":[{"protocol":"freedom"}]}"""
        engine.start(config)
        assertFailsWith<IllegalStateException> { engine.start(config) }
    }

    private class RecordingGateway : XrayGateway {
        val started = mutableListOf<String>()
        var stopCount = 0
        override fun start(config: String, tunFileDescriptor: Int) { started += "$config:$tunFileDescriptor" }
        override fun stop() { stopCount++ }
        override fun measureDelay(): Long = 1
        override fun version(): String = "test"
    }
}
