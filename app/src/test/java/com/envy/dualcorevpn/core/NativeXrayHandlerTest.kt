package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeXrayHandlerTest {
    @Test
    fun `protect delegates valid file descriptor`() {
        val seen = mutableListOf<Int>()
        val handler = NativeXrayGateway.NativeHandler { fd ->
            seen += fd
            fd == 42
        }

        assertTrue(handler.protect(42L))
        assertTrue(seen == listOf(42))
    }

    @Test
    fun `protect rejects descriptor outside int range`() {
        var called = false
        val handler = NativeXrayGateway.NativeHandler {
            called = true
            true
        }

        assertFalse(handler.protect(Int.MAX_VALUE.toLong() + 1L))
        assertFalse(called)
    }
}
