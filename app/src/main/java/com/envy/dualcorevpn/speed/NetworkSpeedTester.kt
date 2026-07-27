package com.envy.dualcorevpn.speed

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

enum class SpeedTestPhase { IDLE, DOWNLOAD, UPLOAD, COMPLETE }

data class SpeedTestSnapshot(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val megabitsPerSecond: Double = 0.0,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
)

class NetworkSpeedTester(
    private val baseUrl: String = "https://speed.cloudflare.com",
    private val downloadBytes: Int = DEFAULT_DOWNLOAD_BYTES,
    private val uploadBytes: Int = DEFAULT_UPLOAD_BYTES,
) {
    suspend fun run(onProgress: (SpeedTestSnapshot) -> Unit): SpeedTestSnapshot = withContext(Dispatchers.IO) {
        withTimeout(OVERALL_TIMEOUT_MILLIS) {
        require(downloadBytes > 0 && uploadBytes > 0) { "Speed test payload sizes must be positive" }
        var snapshot = SpeedTestSnapshot(SpeedTestPhase.DOWNLOAD)
        onProgress(snapshot)
        val download = measureDownload(downloadBytes) { mbps ->
            snapshot = snapshot.copy(phase = SpeedTestPhase.DOWNLOAD, megabitsPerSecond = mbps)
            onProgress(snapshot)
        }
        snapshot = snapshot.copy(phase = SpeedTestPhase.UPLOAD, megabitsPerSecond = 0.0, downloadMbps = download)
        onProgress(snapshot)
        val upload = measureUpload(uploadBytes) { mbps ->
            snapshot = snapshot.copy(phase = SpeedTestPhase.UPLOAD, megabitsPerSecond = mbps)
            onProgress(snapshot)
        }
        snapshot.copy(phase = SpeedTestPhase.COMPLETE, megabitsPerSecond = upload, uploadMbps = upload).also(onProgress)
        }
    }

    private suspend fun measureDownload(bytes: Int, onProgress: (Double) -> Unit): Double {
        val connection = open("$baseUrl/__down?bytes=$bytes")
        check(connection.responseCode in 200..299) { "Speed test download failed: HTTP ${connection.responseCode}" }
        val started = System.nanoTime()
        var lastReport = started
        var transferred = 0L
        try {
            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (transferred < bytes) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), bytes - transferred).toInt())
                    if (count < 0) break
                    transferred += count
                    lastReport = report(started, lastReport, transferred, onProgress)
                }
            }
        } finally {
            connection.disconnect()
        }
        return mbps(started, transferred)
    }

    private suspend fun measureUpload(bytes: Int, onProgress: (Double) -> Unit): Double {
        val connection = open("$baseUrl/__up").apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(bytes)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        connection.connect()
        val started = System.nanoTime()
        var lastReport = started
        var transferred = 0L
        try {
            connection.outputStream.use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (transferred < bytes) {
                    coroutineContext.ensureActive()
                    val count = minOf(buffer.size.toLong(), bytes - transferred).toInt()
                    output.write(buffer, 0, count)
                    transferred += count
                    lastReport = report(started, lastReport, transferred, onProgress)
                }
            }
            check(connection.responseCode in 200..299) { "Speed test upload failed: HTTP ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
        return mbps(started, transferred)
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MILLIS
        readTimeout = TIMEOUT_MILLIS
        useCaches = false
        setRequestProperty("Cache-Control", "no-store")
    }

    private fun report(started: Long, lastReport: Long, bytes: Long, callback: (Double) -> Unit): Long {
        val now = System.nanoTime()
        if (now - lastReport < REPORT_AFTER_NANOS) return lastReport
        callback(mbps(started, bytes))
        return now
    }

    private fun mbps(started: Long, bytes: Long): Double {
        val seconds = (System.nanoTime() - started).coerceAtLeast(1L) / 1_000_000_000.0
        return bytes * 8.0 / seconds / 1_000_000.0
    }

    private companion object {
        const val DEFAULT_DOWNLOAD_BYTES = 20_000_000
        const val DEFAULT_UPLOAD_BYTES = 8_000_000
        const val BUFFER_BYTES = 64 * 1024
        const val TIMEOUT_MILLIS = 20_000
        const val OVERALL_TIMEOUT_MILLIS = 45_000L
        const val REPORT_AFTER_NANOS = 100_000_000L
    }
}
