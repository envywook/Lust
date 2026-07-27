package com.envy.dualcorevpn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedScaleTest {
    @Test
    fun `uses requested adaptive Mbps steps`() {
        assertEquals(100, speedScaleMbps(0.0))
        assertEquals(100, speedScaleMbps(100.0))
        assertEquals(500, speedScaleMbps(100.1))
        assertEquals(500, speedScaleMbps(500.0))
        assertEquals(1000, speedScaleMbps(500.1))
        assertEquals(1000, speedScaleMbps(5_000.0))
    }
}
