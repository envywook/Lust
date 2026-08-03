package com.envy.dualcorevpn.subscription

import com.envy.dualcorevpn.core.SingBoxConfigConverter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionParserTest {
    @Test
    fun `parse report counts imported skipped invalid and duplicate lines`() {
        val valid = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#test"
        val report = SubscriptionParser.parseReport(
            "subscription",
            listOf(valid, valid, "hysteria2://unsupported", "vless://broken").joinToString("\n"),
        )

        assertEquals(1, report.profiles.size)
        assertEquals(1, report.duplicateCount)
        assertEquals(0, report.unsupportedCount)
        assertEquals(2, report.invalidCount)
    }

    @Test
    fun `imports official mierus links and splits mixed transports`() {
        val link = "mierus://fixture-user:fixture-password@mieru.example.invalid?profile=Fixture&port=443&protocol=TCP&port=8000-8010&protocol=UDP&multiplexing=MULTIPLEXING_LOW"

        val profiles = SubscriptionParser.parse("subscription", link)

        assertEquals(listOf("Fixture [TCP]", "Fixture [UDP]"), profiles.map { it.name })
        assertEquals(listOf("mieru", "mieru"), profiles.map { it.protocol })
        val tcp = JSONObject(profiles[0].config).getJSONObject("outbound")
        assertEquals("mieru", tcp.getString("type"))
        assertEquals("TCP", tcp.getString("transport"))
        assertEquals(443, tcp.getInt("server_port"))
        assertEquals("fixture-user", tcp.getString("username"))
        assertEquals("fixture-password", tcp.getString("password"))
        assertEquals("MULTIPLEXING_LOW", tcp.getString("multiplexing"))
        val udp = JSONObject(profiles[1].config).getJSONObject("outbound")
        assertEquals("UDP", udp.getString("transport"))
        assertEquals("8000-8010", udp.getJSONArray("server_ports").getString(0))
        val runtime = JSONObject(SingBoxConfigConverter.convert(profiles[0].config))
        assertEquals("proxy", runtime.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
    }

    @Test
    fun `rejects incompatible and malformed mieru links without misparsing binary uri`() {
        val report = SubscriptionParser.parseReport(
            "subscription",
            listOf(
                "mierus://user:pass@host.example?profile=Bad&port=443&protocol=TCP&mtu=1400",
                "mierus://user:pass@host.example?profile=Bad&port=9000-8000&protocol=UDP",
                "mierus://user:pass@host.example?profile=Bad&port=443&protocol=TCP&port=8443",
                "mierus://user:pass@host.example?profile=Bad&port=443&protocol=TCP&traffic-pattern=GgQIARAK",
                "mieru://AAECAw==",
            ).joinToString("\n"),
        )

        assertEquals(0, report.profiles.size)
        assertEquals(5, report.invalidCount)
        assertEquals(0, report.unsupportedCount)
    }

    @Test
    fun `preserves plus signs and percent encoding in mieru credentials`() {
        val profile = SubscriptionParser.parse(
            "subscription",
            "mierus://user+name:pass%2Bword@mieru.example?profile=Plus&port=443&protocol=TCP",
        ).single()

        val outbound = JSONObject(profile.config).getJSONObject("outbound")
        assertEquals("user+name", outbound.getString("username"))
        assertEquals("pass+word", outbound.getString("password"))
    }

    @Test
    fun `imports hysteria2 tuic and naive as native sing-box profiles`() {
        val body = listOf(
            "hysteria2://secret@hy2.example:8443?sni=edge.example&insecure=1&obfs=salamander&obfs-password=mask#HY2",
            "tuic://11111111-1111-1111-1111-111111111111:pass@tuic.example:443?congestion_control=bbr&udp_over_stream=1&reduce_rtt=true&heartbeat_interval=10s&sni=tuic-sni.example&alpn=h3#TUIC",
            "naive+https://user:pass@naive.example:443?sni=front.example#Naive",
        ).joinToString("\n")

        val profiles = SubscriptionParser.parse("subscription", body)

        assertEquals(listOf("hysteria2", "tuic", "naive"), profiles.map { it.protocol })
        profiles.forEach { profile ->
            val root = JSONObject(profile.config)
            assertEquals("sing-box", root.getString("maxspeedvpn_format"))
            assertEquals(profile.protocol, root.getJSONObject("outbound").getString("type"))
        }
        val hy2 = JSONObject(profiles[0].config).getJSONObject("outbound")
        assertEquals("secret", hy2.getString("password"))
        assertEquals("edge.example", hy2.getJSONObject("tls").getString("server_name"))
        assertEquals("salamander", hy2.getJSONObject("obfs").getString("type"))
        assertTrue(hy2.getJSONObject("tls").getBoolean("insecure"))
        val tuic = JSONObject(profiles[1].config).getJSONObject("outbound")
        assertEquals("11111111-1111-1111-1111-111111111111", tuic.getString("uuid"))
        assertTrue(tuic.getBoolean("udp_over_stream"))
        assertTrue(tuic.getBoolean("zero_rtt_handshake"))
        assertEquals("10s", tuic.getString("heartbeat"))
        val naive = JSONObject(profiles[2].config).getJSONObject("outbound")
        assertEquals("user", naive.getString("username"))
        assertEquals("pass", naive.getString("password"))
    }

    @Test
    fun `rejects naive options unsupported by cronet outbound`() {
        val report = SubscriptionParser.parseReport(
            "subscription",
            "naive+https://user:pass@naive.example:443?insecure=1&alpn=h2#invalid",
        )

        assertTrue(report.profiles.isEmpty())
        assertEquals(1, report.invalidCount)
    }

    @Test
    fun `rejects unsupported xhttp extra instead of silently dropping it`() {
        val extra = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"xPaddingBytes":"100-1000","xmux":{"maxConcurrency":"4"}}""".toByteArray())
        val report = SubscriptionParser.parseReport(
            "subscription",
            "vless://11111111-1111-1111-1111-111111111111@xhttp.example:443?security=tls&type=xhttp&extra=$extra",
        )

        assertTrue(report.profiles.isEmpty())
        assertEquals(1, report.invalidCount)
    }

    @Test
    fun `hy2 alias and xhttp are supported`() {
        val report = SubscriptionParser.parseReport(
            "subscription",
            listOf(
                "hy2://secret@hy2.example:443#alias",
                "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=xhttp#xhttp",
            ).joinToString("\n"),
        )

        assertEquals(2, report.profiles.size)
        assertEquals(listOf("hysteria2", "vless"), report.profiles.map { it.protocol })
        assertEquals(0, report.unsupportedCount)
    }

    @Test
    fun `imports vless xhttp for extended sing-box core`() {
        val extra = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"xPaddingBytes":"200-800"}""".toByteArray())
        val uri = "vless://11111111-1111-1111-1111-111111111111@xhttp.example:443" +
            "?security=reality&sni=edge.example&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "&sid=0123456789abcdef&type=xhttp&host=front.example&path=%2Fapi&mode=stream-one&extra=$extra#XHTTP"

        val profile = SubscriptionParser.parse("sub-xhttp", uri).single()
        val root = JSONObject(SingBoxConfigConverter.convert(profile.config))
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val transport = outbound.getJSONObject("transport")
        val tls = outbound.getJSONObject("tls")

        assertEquals("vless", outbound.getString("type"))
        assertEquals("xhttp", transport.getString("type"))
        assertEquals("front.example", transport.getString("host"))
        assertEquals("/api", transport.getString("path"))
        assertEquals("stream-one", transport.getString("mode"))
        assertEquals("200-800", transport.getString("x_padding_bytes"))
        assertTrue(tls.getJSONObject("reality").getBoolean("enabled"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
    }

    @Test
    fun `generated profile exposes local socks inbound for HEV`() {
        val link = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#test"

        val profile = SubscriptionParser.parse("subscription", link).single()
        val inbounds = JSONObject(profile.config).getJSONArray("inbounds")
        val socks = (0 until inbounds.length())
            .map(inbounds::getJSONObject)
            .single { it.getString("protocol") == "socks" }

        assertEquals("127.0.0.1", socks.getString("listen"))
        assertEquals(10808, socks.getInt("port"))
        assertEquals("socks-in", socks.getString("tag"))
        assertTrue(socks.getJSONObject("settings").getBoolean("udp"))
    }
}
