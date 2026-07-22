package com.envy.dualcorevpn.core

import android.content.Context
import com.envy.dualcorevpn.logging.AppLog
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class NativeSingBoxGateway(
    context: Context,
    private val onUnexpectedExit: (Int) -> Unit = {},
) : SingBoxGateway {
    private val appContext = context.applicationContext
    private val executable = File(appContext.applicationInfo.nativeLibraryDir, "libsingbox.so")
    private val runtimeDirectory = File(appContext.noBackupFilesDir, "sing-box")
    private val configFile = File(runtimeDirectory, "config.json")
    @Volatile private var process: Process? = null

    override suspend fun start(config: String) = withContext(Dispatchers.IO) {
        check(process == null) { "sing-box already running" }
        check(executable.canExecute()) { "sing-box executable is unavailable: ${executable.absolutePath}" }
        runtimeDirectory.mkdirs()
        configFile.writeText(config)
        runChecked("check", "-D", runtimeDirectory.absolutePath, "-c", configFile.absolutePath)
        requireSocksPortAvailable()
        val started = ProcessBuilder(
            executable.absolutePath,
            "run",
            "-D", runtimeDirectory.absolutePath,
            "-c", configFile.absolutePath,
        ).redirectErrorStream(true).start()
        synchronized(this@NativeSingBoxGateway) {
            check(process == null) { "sing-box already running" }
            process = started
        }
        Thread({
            runCatching {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { AppLog.info("SING_BOX", it) }
                }
            }.onFailure { error ->
                val active = synchronized(this@NativeSingBoxGateway) { process === started }
                if (active && started.isAlive) AppLog.error("SING_BOX", "Log reader failed: ${error.message}", error)
            }
        }, "sing-box-log").apply { isDaemon = true }.start()
        try {
            waitForSocks(started)
        } catch (failure: Throwable) {
            synchronized(this@NativeSingBoxGateway) {
                if (process === started) process = null
            }
            stopProcess(started)
            throw failure
        }
        AppLog.info("SING_BOX", "READY version=${version()}")
        watchForUnexpectedExit(started)
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        val running = synchronized(this@NativeSingBoxGateway) {
            process.also { process = null }
        }
        running?.let(::stopProcess)
        AppLog.info("SING_BOX", "STOPPED")
    }

    override fun version(): String = runChecked("version").lineSequence()
        .firstOrNull { it.startsWith("sing-box version") }
        ?.removePrefix("sing-box version ")?.trim()
        ?.takeIf(String::isNotBlank)
        ?: error("sing-box version output is missing")

    private fun watchForUnexpectedExit(started: Process) {
        Thread({
            val exitCode = runCatching { started.waitFor() }.getOrElse { return@Thread }
            val unexpected = synchronized(this) {
                if (process !== started) false else {
                    process = null
                    true
                }
            }
            if (unexpected) {
                AppLog.error("SING_BOX", "Runtime exited unexpectedly (exit=$exitCode)")
                onUnexpectedExit(exitCode)
            }
        }, "sing-box-exit").apply { isDaemon = true }.start()
    }

    private fun requireSocksPortAvailable() {
        runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress("127.0.0.1", SingBoxConfigConverter.SOCKS_PORT))
            }
        }.getOrElse {
            error("SOCKS 127.0.0.1:${SingBoxConfigConverter.SOCKS_PORT} is already in use")
        }
    }

    private fun probeSocks(): Boolean = runCatching {
        Socket().use { socket ->
            socket.soTimeout = 200
            socket.connect(InetSocketAddress("127.0.0.1", SingBoxConfigConverter.SOCKS_PORT), 100)
            socket.getOutputStream().write(byteArrayOf(5, 1, 0))
            socket.getOutputStream().flush()
            val response = ByteArray(2)
            var offset = 0
            while (offset < response.size) {
                val read = socket.getInputStream().read(response, offset, response.size - offset)
                if (read < 0) return@runCatching false
                offset += read
            }
            response.contentEquals(byteArrayOf(5, 0))
        }
    }.getOrDefault(false)

    private suspend fun waitForSocks(started: Process) {
        repeat(100) {
            if (!started.isAlive) error("sing-box exited before SOCKS became ready (exit=${started.exitValue()})")
            if (probeSocks() && started.isAlive) return
            delay(50)
        }
        error("sing-box SOCKS 127.0.0.1:${SingBoxConfigConverter.SOCKS_PORT} did not become ready")
    }

    private fun runChecked(vararg arguments: String): String {
        val command = listOf(executable.absolutePath) + arguments
        val commandProcess = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = CompletableFuture.supplyAsync {
            commandProcess.inputStream.bufferedReader().use { it.readText() }
        }
        if (!commandProcess.waitFor(15, TimeUnit.SECONDS)) {
            commandProcess.destroyForcibly()
            commandProcess.waitFor(2, TimeUnit.SECONDS)
            error("sing-box command timed out: ${arguments.firstOrNull()}")
        }
        val text = output.get(2, TimeUnit.SECONDS)
        check(commandProcess.exitValue() == 0) { text.trim().ifBlank { "sing-box command failed" } }
        return text
    }

    private fun stopProcess(target: Process) {
        target.destroy()
        if (!target.waitFor(2, TimeUnit.SECONDS)) {
            target.destroyForcibly()
            target.waitFor(2, TimeUnit.SECONDS)
        }
    }
}
