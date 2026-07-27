package com.envy.dualcorevpn.subscription

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import com.envy.dualcorevpn.core.SingBoxConfigConverter

class MieruDeepLinkTest {
    @Test
    fun `parses v1 link and builds native sing-box outbound`() {
        val request = MieruDeepLink.parse(
            "mieru://user%40mail:p%3Aa%2Fss@ru.maxspeedvpn.site:2023?v=1&transport=tcp&mtu=1400&mux=middle#MaxSpeed%20RU%20Mieru",
        )

        assertEquals("MaxSpeed RU Mieru", request.profile.name)
        assertEquals("ru.maxspeedvpn.site", request.profile.address)
        assertEquals(2023, request.profile.port)
        assertEquals("user@mail", request.username)
        assertEquals("TCP", request.transport)
        assertEquals(1400, request.mtu)
        assertEquals("MULTIPLEXING_MIDDLE", request.multiplexing)

        val outbound = JSONObject(request.profile.config).getJSONObject("outbound")
        assertEquals("mieru", outbound.getString("type"))
        assertEquals("user@mail", outbound.getString("username"))
        assertEquals("p:a/ss", outbound.getString("password"))
        assertEquals("MULTIPLEXING_MIDDLE", outbound.getString("multiplexing"))
        assertEquals(1400, outbound.getInt("mtu"))
        assertFalse(request.toString().contains("p:a/ss"))

        val singBox = JSONObject(SingBoxConfigConverter.convert(request.profile.config))
        val converted = singBox.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("mieru", converted.getString("type"))
        assertEquals("proxy", converted.getString("tag"))
        assertEquals("MULTIPLEXING_MIDDLE", converted.getString("multiplexing"))
    }

    @Test
    fun `supports validated transports and multiplexing levels`() {
        val request = MieruDeepLink.parse(
            "mieru://u:p@example.com:443?v=1&transport=udp&mtu=1280&mux=off#UDP",
        )
        assertEquals("UDP", request.transport)
        assertEquals("MULTIPLEXING_OFF", request.multiplexing)
    }

    @Test
    fun `uses v1 defaults when optional query parameters are absent`() {
        val request = MieruDeepLink.parse("mieru://user:password@example.com:443#Default")
        assertEquals("TCP", request.transport)
        assertEquals(1400, request.mtu)
        assertEquals("MULTIPLEXING_DEFAULT", request.multiplexing)
    }

    @Test
    fun `rejects missing credentials host or port`() {
        listOf(
            "mieru://example.com:443?v=1&transport=tcp&mtu=1400&mux=middle",
            "mieru://u:p@:443?v=1&transport=tcp&mtu=1400&mux=middle",
            "mieru://u:p@example.com?v=1&transport=tcp&mtu=1400&mux=middle",
            "mieru://:p@example.com:443?v=1&transport=tcp&mtu=1400&mux=middle",
            "mieru://u:@example.com:443?v=1&transport=tcp&mtu=1400&mux=middle",
        ).forEach { source -> assertThrows(IllegalArgumentException::class.java) { MieruDeepLink.parse(source) } }
    }

    @Test
    fun `rejects unknown version parameters duplicates and invalid values`() {
        listOf(
            "mieru://u:p@example.com:443?v=2&transport=tcp&mtu=1400&mux=middle",
            "mieru://u:p@example.com:443?v=1&transport=quic&mtu=1400&mux=middle",
            "mieru://u:p@example.com:443?v=1&transport=tcp&mtu=1200&mux=middle",
            "mieru://u:p@example.com:443?v=1&transport=tcp&mtu=1400&mux=extreme",
            "mieru://u:p@example.com:443?v=1&transport=tcp&mtu=1400&mux=middle&debug=true",
            "mieru://u:p@example.com:443?v=1&v=1&transport=tcp&mtu=1400&mux=middle",
        ).forEach { source -> assertThrows(IllegalArgumentException::class.java) { MieruDeepLink.parse(source) } }
    }
}
