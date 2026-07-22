package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineContractTest {
    @Test
    fun `engine validation rejects blank config before start`() = runTest {
        val engine = FakeEngine()
        val result = engine.validate("   ")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(0, engine.startCount)
    }

    private class FakeEngine : CoreEngine {
        var startCount = 0
        override val kind = EngineKind.SING_BOX
        override suspend fun validate(config: String): ValidationResult =
            if (config.isBlank()) ValidationResult.Invalid("Configuration is blank")
            else ValidationResult.Valid
        override suspend fun start(config: String, tunFileDescriptor: Int) { startCount++ }
        override suspend fun stop() = Unit
    }
}
