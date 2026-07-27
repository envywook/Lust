package com.envy.dualcorevpn.server

import com.envy.dualcorevpn.subscription.ServerProfile
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

fun interface TcpProbe {
    suspend fun measure(host: String, port: Int): Long
}

data class ServerLatencyResult(
    val latencyMillis: Long?,
    val error: String?,
)

class ServerLatencyTester(
    private val probe: TcpProbe? = null,
) {
    suspend fun testOne(
        server: ServerProfile,
        timeoutMillis: Long = 3_000,
    ): ServerLatencyResult {
        require(timeoutMillis > 0) { "Timeout must be positive" }
        return measure(server, timeoutMillis)
    }

    suspend fun test(
        servers: List<ServerProfile>,
        concurrency: Int = 10,
        timeoutMillis: Long = 3_000,
    ): Map<String, ServerLatencyResult> = coroutineScope {
        require(concurrency in 1..16) { "Concurrency must be between 1 and 16" }
        require(timeoutMillis > 0) { "Timeout must be positive" }
        val semaphore = Semaphore(concurrency)
        servers.map { server ->
            async { server.id to semaphore.withPermit { measure(server, timeoutMillis) } }
        }.awaitAll().toMap()
    }

    private suspend fun measure(server: ServerProfile, timeoutMillis: Long): ServerLatencyResult = try {
        val latency = withTimeout(timeoutMillis) {
            probe?.measure(server.address, server.port)
                ?: measureTcp(server.address, server.port, timeoutMillis)
        }
        ServerLatencyResult(latency, null)
    } catch (_: TimeoutCancellationException) {
        ServerLatencyResult(null, "Timeout")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ServerLatencyResult(null, error.message ?: error.javaClass.simpleName)
    }

    private suspend fun measureTcp(host: String, port: Int, timeoutMillis: Long): Long =
        runInterruptible(Dispatchers.IO) {
            val started = System.nanoTime()
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(host, port),
                    timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            }
            (System.nanoTime() - started) / 1_000_000
        }
}
