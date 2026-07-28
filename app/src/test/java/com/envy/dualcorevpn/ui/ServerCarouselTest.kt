package com.envy.dualcorevpn.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerCarouselTest {
    @Test
    fun `drag distance selects expected adjacent server`() {
        assertEquals(1, carouselStep(offsetPx = -30f, velocityPxPerSecond = 0f, slotWidthPx = 100f))
        assertEquals(-1, carouselStep(offsetPx = 30f, velocityPxPerSecond = 0f, slotWidthPx = 100f))
        assertEquals(0, carouselStep(offsetPx = 20f, velocityPxPerSecond = 0f, slotWidthPx = 100f))
    }

    @Test
    fun `fast fling selects even below distance threshold`() {
        assertEquals(1, carouselStep(offsetPx = -5f, velocityPxPerSecond = -700f, slotWidthPx = 100f))
        assertEquals(-1, carouselStep(offsetPx = 5f, velocityPxPerSecond = 700f, slotWidthPx = 100f))
    }

    @Test
    fun `invalid width never changes selection`() {
        assertEquals(0, carouselStep(offsetPx = -100f, velocityPxPerSecond = -1_000f, slotWidthPx = 0f))
    }
}
