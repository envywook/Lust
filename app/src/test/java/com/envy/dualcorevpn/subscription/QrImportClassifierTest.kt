package com.envy.dualcorevpn.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class QrImportClassifierTest {
    @Test
    fun `accepts https subscription but rejects insecure http`() {
        val payload = QrImportClassifier.classify("https://example.com/sub")
        assertTrue(payload is QrImportPayload.Subscription)
        assertEquals("https://example.com/sub", (payload as QrImportPayload.Subscription).request.url)
        assertFailsWith<IllegalArgumentException> { QrImportClassifier.classify("http://example.com/sub") }
    }

    @Test
    fun `rejects multiline oversized and blank payloads`() {
        assertFailsWith<IllegalArgumentException> { QrImportClassifier.classify("") }
        assertFailsWith<IllegalArgumentException> { QrImportClassifier.classify("https://example.com\nsecret") }
        assertFailsWith<IllegalArgumentException> { QrImportClassifier.classify("x".repeat(4_097)) }
    }

    @Test
    fun `does not expose raw credentials in result string`() {
        val payload = QrImportClassifier.classify("mieru://user:secret@example.com:443?transport=TCP")
        assertTrue(payload is QrImportPayload.MieruProfile)
        assertTrue(!payload.toString().contains("secret"))
    }
}
