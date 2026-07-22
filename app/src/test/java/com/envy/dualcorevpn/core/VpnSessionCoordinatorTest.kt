package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnSessionCoordinatorTest {
    @Test fun `starts engine before transport and stops in reverse order`() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = VpnSessionCoordinator(
            engine = FakeEngine(calls),
            transport = FakeTransport(calls)
        )

        coordinator.start("{}")
        coordinator.stop()

        assertEquals(listOf("validate", "engine.start", "transport.start", "transport.stop", "engine.stop"), calls)
    }

    @Test fun `transport first policy preserves xray startup order`() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = VpnSessionCoordinator(
            engine = FakeEngine(calls, startupOrder = EngineStartupOrder.TRANSPORT_FIRST),
            transport = FakeTransport(calls),
        )

        coordinator.start("{}")
        coordinator.stop()

        assertEquals(listOf("validate", "transport.start", "engine.start", "engine.stop", "transport.stop"), calls)
    }

    @Test fun `invalid config starts nothing`() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = VpnSessionCoordinator(
            engine = FakeEngine(calls, ValidationResult.Invalid("bad config")),
            transport = FakeTransport(calls)
        )

        val error = assertFailsWith<IllegalArgumentException> { coordinator.start("bad") }

        assertEquals("bad config", error.message)
        assertEquals(listOf("validate"), calls)
    }

    @Test fun `transport failure rolls engine back`() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = VpnSessionCoordinator(
            engine = FakeEngine(calls),
            transport = FakeTransport(calls, failStart = true)
        )

        assertFailsWith<IllegalStateException> { coordinator.start("{}") }

        assertEquals(listOf("validate", "engine.start", "transport.start", "engine.stop"), calls)
    }

    private class FakeEngine(
        private val calls: MutableList<String>,
        private val result: ValidationResult = ValidationResult.Valid,
        override val startupOrder: EngineStartupOrder = EngineStartupOrder.ENGINE_FIRST,
    ) : CoreEngine {
        override val kind = EngineKind.XRAY
        override suspend fun validate(config: String): ValidationResult { calls += "validate"; return result }
        override suspend fun start(config: String, tunFileDescriptor: Int) { calls += "engine.start" }
        override suspend fun stop() { calls += "engine.stop" }
    }

    private class FakeTransport(
        private val calls: MutableList<String>,
        private val failStart: Boolean = false
    ) : SessionTransport {
        override fun start(): Int {
            calls += "transport.start"
            if (failStart) error("transport failed")
            return 42
        }
        override fun stop() { calls += "transport.stop" }
    }
}
