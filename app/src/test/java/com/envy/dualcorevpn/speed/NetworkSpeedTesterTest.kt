package com.envy.dualcorevpn.speed

import java.io.BufferedInputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NetworkSpeedTesterTest {
    @Test
    fun `runs download before upload and reports final values`() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        var uploadedBytes = 0
        val server = ServerSocket(0)
        val worker = thread(name = "speed-test-server") {
            repeat(2) {
                server.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    val headers = readHeaders(input)
                    if (headers.startsWith("GET /__down")) {
                        requests += "download"
                        val body = ByteArray(32 * 1024) { 1 }
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    } else {
                        requests += "upload"
                        val length = Regex("(?i)Content-Length: (\\d+)").find(headers)?.groupValues?.get(1)?.toInt() ?: 0
                        var remaining = length
                        val buffer = ByteArray(4096)
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count < 0) break
                            uploadedBytes += count
                            remaining -= count
                        }
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            flush()
                        }
                    }
                }
            }
        }
        try {
            val phases = mutableListOf<SpeedTestPhase>()
            val result = NetworkSpeedTester(
                baseUrl = "http://127.0.0.1:${server.localPort}",
                downloadBytes = 32 * 1024,
                uploadBytes = 24 * 1024,
            ).run { phases += it.phase }

            worker.join(2_000)
            assertEquals(listOf("download", "upload"), requests)
            assertEquals(24 * 1024, uploadedBytes)
            assertEquals(SpeedTestPhase.COMPLETE, result.phase)
            assertNotNull(result.downloadMbps)
            assertNotNull(result.uploadMbps)
            assertEquals(SpeedTestPhase.DOWNLOAD, phases.first())
            assertEquals(SpeedTestPhase.COMPLETE, phases.last())
        } finally {
            server.close()
        }
    }

    private fun readHeaders(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size < 16 * 1024) {
            val value = input.read()
            if (value < 0) break
            bytes += value.toByte()
            val size = bytes.size
            if (size >= 4 && bytes[size - 4] == 13.toByte() && bytes[size - 3] == 10.toByte() &&
                bytes[size - 2] == 13.toByte() && bytes[size - 1] == 10.toByte()
            ) break
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }
}
