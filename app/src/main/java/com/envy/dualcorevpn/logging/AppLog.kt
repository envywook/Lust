package com.envy.dualcorevpn.logging

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
)

/** Process-wide, persistent ring log used by UI, VPN service, and native callbacks. */
object AppLog {
    private const val TAG = "Lust"
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    private const val MAX_MEMORY_ENTRIES = 1_000
    private val lock = Any()
    private val logFile = AtomicReference<File?>()
    private val mutableEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = mutableEntries.asStateFlow()

    fun initialize(directory: File) {
        synchronized(lock) {
            if (logFile.get() != null) return
            directory.mkdirs()
            val file = File(directory, "lust.log")
            logFile.set(file)
            mutableEntries.value = readFrom(file).takeLast(MAX_MEMORY_ENTRIES)
        }
    }

    fun debug(source: String, message: String) = write(LogLevel.DEBUG, source, message)
    fun info(source: String, message: String) = write(LogLevel.INFO, source, message)
    fun warn(source: String, message: String) = write(LogLevel.WARN, source, message)
    fun error(source: String, message: String, failure: Throwable? = null) {
        val details = failure?.stackTraceToString()?.let { "$message\n$it" } ?: message
        write(LogLevel.ERROR, source, details)
    }

    fun clear() {
        synchronized(lock) {
            logFile.get()?.writeText("")
            mutableEntries.value = emptyList()
        }
    }

    fun exportText(): String = synchronized(lock) { logFile.get()?.takeIf(File::exists)?.readText().orEmpty() }

    private fun write(level: LogLevel, source: String, message: String) {
        val cleanSource = source.replace('|', '/')
        val cleanMessage = message.replace("\r", "").trimEnd()
        val entry = LogEntry(System.currentTimeMillis(), level, cleanSource, cleanMessage)
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, "[$cleanSource] $cleanMessage")
            LogLevel.INFO -> Log.i(TAG, "[$cleanSource] $cleanMessage")
            LogLevel.WARN -> Log.w(TAG, "[$cleanSource] $cleanMessage")
            LogLevel.ERROR -> Log.e(TAG, "[$cleanSource] $cleanMessage")
        }
        synchronized(lock) {
            mutableEntries.value = (mutableEntries.value + entry).takeLast(MAX_MEMORY_ENTRIES)
            val file = logFile.get() ?: return
            if (file.exists() && file.length() >= MAX_FILE_BYTES) rotate(file)
            file.appendText(serialize(entry))
        }
    }

    private fun rotate(file: File) {
        val previous = File(file.parentFile, "lust.previous.log")
        previous.delete()
        file.renameTo(previous)
    }

    private fun serialize(entry: LogEntry): String = buildString {
        append(TIMESTAMP.format(Date(entry.timestampMillis)))
        append('|').append(entry.level)
        append('|').append(entry.source)
        append('|').append(entry.message.replace("\n", "\\n"))
        append('\n')
    }

    private fun readFrom(file: File): List<LogEntry> {
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('|', limit = 4)
                if (parts.size != 4) return@mapNotNull null
                val time = runCatching { TIMESTAMP.parse(parts[0])?.time }.getOrNull() ?: return@mapNotNull null
                val level = runCatching { LogLevel.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                LogEntry(time, level, parts[2], parts[3].replace("\\n", "\n"))
            }.toList()
        }
    }

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
}
