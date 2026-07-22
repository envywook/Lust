package com.envy.dualcorevpn.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigConverterTest {
    @Test
    fun `converts xray vless outbound and preserves tls transport`() {
        val xray = """{
          "outbounds":[{
            "tag":"proxy","protocol":"vless",
            "settings":{"vnext":[{"address":"edge.example","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000001","flow":"xtls-rprx-vision"}]}]},
            "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"edge.example"}}
          }]
        }"""

        val root = JSONObject(SingBoxConfigConverter.convert(xray))
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)

        assertEquals("socks", inbound.getString("type"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(10808, inbound.getInt("listen_port"))
        assertEquals("vless", outbound.getString("type"))
        assertEquals("edge.example", outbound.getString("server"))
        assertEquals("00000000-0000-0000-0000-000000000001", outbound.getString("uuid"))
        assertTrue(outbound.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test
    fun `direct profile produces executable sing-box smoke config`() {
        val xray = """{"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}]}"""
        val outbound = JSONObject(SingBoxConfigConverter.convert(xray)).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("direct", outbound.getString("type"))
        assertFalse(outbound.has("server"))
    }

    @Test
    fun `preserves supported tls verification settings`() {
        val xray = """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"edge.example","port":443,"password":"secret"}]},"streamSettings":{"security":"tls","tlsSettings":{"serverName":"edge.example","allowInsecure":true,"alpn":["h2"]}}}]}"""

        val tls = JSONObject(SingBoxConfigConverter.convert(xray))
            .getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls")

        assertTrue(tls.getBoolean("insecure"))
        assertEquals("h2", tls.getJSONArray("alpn").getString(0))
    }

    @Test
    fun `rejects routing multiple proxy outbounds and unsupported tls fields`() {
        val cases = listOf(
            """{"routing":{"rules":[{"type":"field","domain":["example.com"],"outboundTag":"direct"}]},"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}]}""",
            """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"a","port":443,"password":"a"}]}},{"tag":"backup","protocol":"trojan","settings":{"servers":[{"address":"b","port":443,"password":"b"}]}}]}""",
            """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"a","port":443,"password":"a"}]},"streamSettings":{"security":"tls","tlsSettings":{"serverName":"a","certificates":[{"certificateFile":"client.crt"}]}}}]}""",
            """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"a","port":443,"password":"a"}]},"streamSettings":{"security":"xtls","network":"tcp"}}]}""",
            """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"a","port":443,"password":"a"}]},"streamSettings":{"security":"tls","network":"ws","tlsSettings":{"serverName":"a"}}}]}""",
            """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"a","port":443,"password":"a"}]},"streamSettings":{"security":"tls","network":"grpc","tlsSettings":{"serverName":"a"}}}]}""",
        )

        cases.forEach { config ->
            assertTrue(runCatching { SingBoxConfigConverter.convert(config) }.isFailure)
        }
    }
}
