package com.envy.dualcorevpn.server

import com.envy.dualcorevpn.subscription.ServerProfile
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun interface TcpProbe {
    suspend fun measure(host: String, port: Int): Long
}

data class ServerLatencyResult(
    val latencyMillis: Long?,
    val error: String?,
)

class ServerLatencyTester(
    private val probe: TcpProbe = TcpProbe { host, port ->
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(host, port), DEFAULT_SOCKET_TIMEOUT_MILLIS) }
            (System.nanoTime() - started) / 1_000_000
        }
    },
) {
    suspend fun test(
        servers: List<ServerProfile>,
        concurrency: Int = 4,
        timeoutMillis: Long = 5_000,
    ): Map<String, ServerLatencyResult> = coroutineScope {
        require(concurrency in 1..16) { "Concurrency must be between 1 and 16" }
        require(timeoutMillis > 0) { "Timeout must be positive" }
        val semaphore = Semaphore(concurrency)
        servers.map { server ->
            async {
                server.id to semaphore.withPermit {
                    runCatching { withTimeout(timeoutMillis) { probe.measure(server.address, server.port) } }
                        .fold(
                            onSuccess = { ServerLatencyResult(it, null) },
                            onFailure = { ServerLatencyResult(null, it.message ?: it.javaClass.simpleName) },
                        )
                }
            }
        }.awaitAll().toMap()
    }

    private companion object {
        const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 5_000
    }
}
