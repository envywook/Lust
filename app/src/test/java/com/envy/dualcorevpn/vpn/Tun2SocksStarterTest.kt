package com.envy.dualcorevpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Tun2SocksStarterTest {
    @Test
    fun `normal JNI return means native tunnel started`() {
        var failures = 0
        val gateway = RecordingGateway()
        val starter = Tun2SocksStarter(gateway) { failures++ }

        starter.start("/data/user/0/app/files/hev.yaml", 42)

        assertEquals(listOf("/data/user/0/app/files/hev.yaml" to 42), gateway.starts)
        assertEquals(0, failures)
    }

    @Test
    fun `JNI exception is reported and rethrown`() {
        val expected = IllegalStateException("native start failed")
        var reported: Throwable? = null
        val gateway = RecordingGateway(expected)
        val starter = Tun2SocksStarter(gateway) { reported = it }

        val actual = runCatching { starter.start("hev.yaml", 42) }.exceptionOrNull()

        assertSame(expected, actual)
        assertSame(expected, reported)
    }

    private class RecordingGateway(
        private val failure: Throwable? = null,
    ) : Tun2SocksGateway {
        val starts = mutableListOf<Pair<String, Int>>()

        override fun start(configPath: String, fileDescriptor: Int) {
            starts += configPath to fileDescriptor
            failure?.let { throw it }
        }

        override fun stop() = Unit
    }
}
