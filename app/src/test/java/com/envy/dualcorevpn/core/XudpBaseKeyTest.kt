package com.envy.dualcorevpn.core

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class XudpBaseKeyTest {
    @Test
    fun `encodes android id as exactly 32 bytes`() {
        val encoded = XudpBaseKey.fromAndroidId("54226d42ed023f83")
        val decoded = Base64.getUrlDecoder().decode(encoded)

        assertEquals(32, decoded.size)
        assertArrayEquals("54226d42ed023f83".toByteArray(), decoded.copyOf(16))
    }

    @Test
    fun `is deterministic and has no base64 padding`() {
        val first = XudpBaseKey.fromAndroidId("device-id")

        assertEquals(first, XudpBaseKey.fromAndroidId("device-id"))
        assertEquals(false, first.contains('='))
    }
}
