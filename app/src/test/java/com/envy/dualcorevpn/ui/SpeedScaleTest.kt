package com.envy.dualcorevpn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedScaleTest {
    @Test
    fun `uses requested adaptive Mbps steps`() {
        assertEquals(1, speedScaleMbps(0.0))
        assertEquals(1, speedScaleMbps(1.0))
        assertEquals(10, speedScaleMbps(1.01))
        assertEquals(10, speedScaleMbps(10.0))
        assertEquals(100, speedScaleMbps(10.01))
        assertEquals(100, speedScaleMbps(100.0))
        assertEquals(500, speedScaleMbps(100.1))
        assertEquals(500, speedScaleMbps(500.0))
        assertEquals(1000, speedScaleMbps(500.1))
        assertEquals(1000, speedScaleMbps(5_000.0))
    }

    @Test
    fun `keeps low speeds visible instead of rounding them to zero`() {
        assertEquals("0", formatSpeedMbps(0.0, 1))
        assertEquals("0.30", formatSpeedMbps(0.3, 1))
        assertEquals("4.5", formatSpeedMbps(4.5, 10))
        assertEquals("42", formatSpeedMbps(42.4, 100))
        assertEquals("0", formatSpeedTick(0.0, 1))
        assertEquals("0.2", formatSpeedTick(0.2, 1))
        assertEquals("1", formatSpeedTick(1.0, 1))
        assertEquals("2", formatSpeedTick(2.0, 10))
    }
}
