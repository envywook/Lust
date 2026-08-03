package com.envy.dualcorevpn.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPayloadClassifierTest {
    @Test
    fun `classifies HTTPS subscription without fetching it`() {
        val payload = ImportPayloadClassifier.classify("https://provider.example/subscription")
        assertEquals("https://provider.example/subscription", (payload as ImportPayload.Subscription).request.url)
    }

    @Test
    fun `classifies VLESS as local profile without treating it as subscription URL`() {
        val payload = ImportPayloadClassifier.classify(
            "vless://00000000-0000-4000-8000-000000000001@server.example:443?security=tls&type=tcp#Example",
        ) as ImportPayload.Profiles
        assertEquals(1, payload.profiles.size)
        assertEquals("vless", payload.profiles.single().protocol)
        assertEquals("server.example", payload.profiles.single().address)
    }

    @Test
    fun `classifies Naive as sing-box local profile`() {
        val payload = ImportPayloadClassifier.classify(
            "naive+https://fixture-user:fixture-pass@naive.example:443?insecure=false#Naive",
        ) as ImportPayload.Profiles
        assertEquals("naive", payload.profiles.single().protocol)
        assertTrue(payload.profiles.single().config.contains("\"type\":\"naive\""))
    }

    @Test
    fun `classifies canonical Mieru deep link`() {
        val payload = ImportPayloadClassifier.classify(
            "mieru://fixture-user:fixture-pass@mieru.example:443?transport=TCP#Mieru",
        )
        assertTrue(payload is ImportPayload.MieruProfile)
    }

    @Test
    fun `accepts common meiru spelling as Mieru alias`() {
        val payload = ImportPayloadClassifier.classify(
            "meiru://fixture-user:fixture-pass@mieru.example:443?transport=TCP#Mieru",
        )
        assertTrue(payload is ImportPayload.MieruProfile)
    }

    @Test
    fun `accepts new branded deep link and preserves legacy link`() {
        val encoded = "https%3A%2F%2Fprovider.example%2Fsub"
        assertTrue(ImportPayloadClassifier.classify("maxspeedvpn://add?url=$encoded") is ImportPayload.Subscription)
        assertTrue(ImportPayloadClassifier.classify("lust://add?url=$encoded") is ImportPayload.Subscription)
    }

    @Test
    fun `QR policy rejects insecure subscription but accepts direct server URI`() {
        runCatching { ImportPayloadClassifier.classify("http://provider.example/sub", requireHttpsSubscription = true) }
            .onSuccess { error("HTTP subscription must be rejected") }
        assertTrue(
            ImportPayloadClassifier.classify(
                "vless://00000000-0000-4000-8000-000000000001@server.example:443?security=tls#Example",
                requireHttpsSubscription = true,
            ) is ImportPayload.Profiles,
        )
    }

    @Test
    fun `classifies newline-delimited direct URIs as local profiles`() {
        val payload = ImportPayloadClassifier.classify(
            "vless://00000000-0000-4000-8000-000000000001@first.example:443?security=tls#First\n" +
                "vless://00000000-0000-4000-8000-000000000002@second.example:8443?security=tls#Second",
        ) as ImportPayload.Profiles

        assertEquals(2, payload.profiles.size)
        assertEquals(listOf("First", "Second"), payload.profiles.map(ServerProfile::name))
    }

    @Test
    fun `rejects multiline subscription oversized and unknown payloads without exposing input`() {
        listOf("https://provider.example/sub\nhttps://provider.example/second", "x".repeat(4_097), "unknown://fixture-secret")
            .forEach { value ->
                val failure = runCatching { ImportPayloadClassifier.classify(value) }.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertTrue(failure?.message?.contains("fixture-secret") != true)
            }
    }

    @Test
    fun `QR rejects multiple direct URIs`() {
        val failure = runCatching {
            QrImportClassifier.classify(
                "vless://00000000-0000-4000-8000-000000000001@first.example:443?security=tls#First\n" +
                    "vless://00000000-0000-4000-8000-000000000002@second.example:8443?security=tls#Second",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
