package com.envy.dualcorevpn.ui

import com.envy.dualcorevpn.core.TrafficCounterState
import com.envy.dualcorevpn.core.TunnelByteCounters
import com.envy.dualcorevpn.core.advanceTrafficCounters
import com.envy.dualcorevpn.core.bytesPerSecond
import com.envy.dualcorevpn.core.hevTunnelByteCounters
import com.envy.dualcorevpn.core.trafficDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatsTest {
    @Test
    fun `traffic delta ignores unsupported and reset counters`() {
        assertEquals(50L, trafficDelta(previous = 100L, current = 150L))
        assertEquals(0L, trafficDelta(previous = 150L, current = 20L))
        assertEquals(0L, trafficDelta(previous = -1L, current = 20L))
        assertEquals(0L, trafficDelta(previous = 20L, current = -1L))
    }

    @Test
    fun `traffic counters accumulate deltas without counting resets`() {
        val first = advanceTrafficCounters(TrafficCounterState(100L, 200L), currentRx = 150L, currentTx = 240L)
        assertEquals(TrafficCounterState(150L, 240L, totalRx = 50L, totalTx = 40L), first)

        val reset = advanceTrafficCounters(first, currentRx = 10L, currentTx = 20L)
        assertEquals(TrafficCounterState(10L, 20L, totalRx = 50L, totalTx = 40L), reset)
    }

    @Test
    fun `rate uses actual monotonic sampling interval`() {
        assertEquals(500L, bytesPerSecond(previous = 100L, current = 1_100L, elapsedMillis = 2_000L))
        assertEquals(2_000L, bytesPerSecond(previous = 100L, current = 1_100L, elapsedMillis = 500L))
        assertEquals(0L, bytesPerSecond(previous = 100L, current = 1_100L, elapsedMillis = 0L))
    }

    @Test
    fun `HEV stats map tunnel upload and download byte counters`() {
        assertEquals(
            TunnelByteCounters(txBytes = 200L, rxBytes = 400L),
            hevTunnelByteCounters(longArrayOf(10L, 200L, 30L, 400L)),
        )
        assertEquals(null, hevTunnelByteCounters(longArrayOf(10L, 200L)))
        assertEquals(null, hevTunnelByteCounters(longArrayOf(10L, -1L, 30L, 400L)))
    }

    @Test
    fun `session duration uses elapsed monotonic time`() {
        assertEquals("00:00:00", formatSessionDuration(elapsedMillis = -1_000L))
        assertEquals("00:00:01", formatSessionDuration(elapsedMillis = 1_999L))
        assertEquals("25:01:02", formatSessionDuration(elapsedMillis = 90_062_000L))
    }

    @Test
    fun `endpoint label accepts validated host only`() {
        assertEquals("edge.example:443", safeEndpointLabel("edge.example", 443))
        assertEquals("[2001:db8::1]:8443", safeEndpointLabel("2001:db8::1", 8443))
        assertEquals("—", safeEndpointLabel("secret-user@edge.example", 443))
        assertEquals("—", safeEndpointLabel("https://token:secret@edge.example/private?q=secret", 443))
        assertEquals("—", safeEndpointLabel("ss://YWVzLTI1Ni1nY206c2VjcmV0", 443))
        assertEquals("—", safeEndpointLabel("vmess://123e4567-e89b-12d3-a456-426614174000", 443))
        assertEquals("—", safeEndpointLabel("YWVzLTI1Ni1nY206c2VjcmV0", 443))
        assertEquals("—", safeEndpointLabel("YWVzLTI1Ni1nY206.c2VjcmV0", 443))
        assertEquals("—", safeEndpointLabel("YWVzLTI1Ni1nY206c2VjcmV0.com", 443))
        assertEquals("—", safeEndpointLabel("YWVzLTI1Ni1nY206.c2VjcmV0.example", 443))
        assertEquals("—", safeEndpointLabel("123e4567-e89b-12d3-a456-426614174000", 443))
        assertEquals("—", safeEndpointLabel("123e4567.e89b.12d3.a456.426614174000", 443))
        assertEquals("—", safeEndpointLabel("123e4567.e89b.12d3.a456.426614174000.com", 443))
        assertEquals("—", safeEndpointLabel("edge.example", 0))
        assertEquals("—", safeEndpointLabel("\n\t", 443))
    }

    @Test
    fun `session details layout reserves visible value row`() {
        val layout = sessionDetailsLayout()

        assertEquals(132, layout.cardHeightDp)
        assertEquals(6, layout.items.size)
        assertEquals(0, layout.items.count { it.value.isBlank() })
    }
}
