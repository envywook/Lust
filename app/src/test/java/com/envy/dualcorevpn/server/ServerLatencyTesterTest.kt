package com.envy.dualcorevpn.server

import com.envy.dualcorevpn.subscription.ServerProfile
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerLatencyTesterTest {
    private fun server(id: String) = ServerProfile(id, "sub", id, "vless", "$id.example", 443, "{}")

    @Test
    fun `reports latency and endpoint failures independently`() = runBlocking {
        val tester = ServerLatencyTester { host, _ ->
            if (host.startsWith("bad")) error("unreachable")
            42L
        }

        val results = tester.test(listOf(server("good"), server("bad")), concurrency = 2, timeoutMillis = 500)

        assertEquals(42L, results.getValue("good").latencyMillis)
        assertEquals(null, results.getValue("good").error)
        assertEquals(null, results.getValue("bad").latencyMillis)
        assertTrue(results.getValue("bad").error!!.isNotBlank())
    }

    @Test
    fun `never exceeds requested concurrency`() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val tester = ServerLatencyTester { _, _ ->
            val now = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            delay(40)
            active.decrementAndGet()
            40L
        }

        tester.test((1..8).map { server("s$it") }, concurrency = 3, timeoutMillis = 500)

        assertTrue(peak.get() <= 3)
        assertEquals(3, peak.get())
    }
}
