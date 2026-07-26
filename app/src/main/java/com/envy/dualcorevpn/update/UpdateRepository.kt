package com.envy.dualcorevpn.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.envy.dualcorevpn.BuildConfig
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

class UpdateRepository(private val context: Context) {
    fun check(): AppUpdate? {
        val current = ReleaseVersion.parse("v${BuildConfig.VERSION_NAME}") ?: error("Некорректная версия приложения")
        val body = requestBytes(RELEASES_URL, MAX_METADATA).toString(Charsets.UTF_8)
        return UpdateCatalog.select(JSONArray(body), current, Build.SUPPORTED_ABIS.toList())
    }

    fun downloadAndVerify(update: AppUpdate): File {
        val sumsBody = requestBytes(update.checksums.downloadUrl, MAX_METADATA).toString(Charsets.UTF_8)
        val expected = UpdateChecksums.expected(sumsBody, update.apk.name) ?: error("Контрольная сумма APK отсутствует или неоднозначна")
        require(update.apk.size in 1..MAX_APK) { "Недопустимый размер APK" }
        val directory = File(context.cacheDir, "updates/${update.tag}").apply { mkdirs() }
        val temporary = File(directory, "${update.apk.name}.part")
        val destination = File(directory, update.apk.name)
        temporary.delete()
        try {
            val connection = open(update.apk.downloadUrl)
            connection.inputStream.use { input -> temporary.outputStream().buffered().use { output ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_APK) { "APK превышает допустимый размер" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                require(total == update.apk.size) { "Размер загруженного APK не совпадает" }
                require(MessageDigest.isEqual(digest.digest(), expected.hexBytes())) { "SHA-256 APK не совпадает" }
            } }
            connection.disconnect()
            verifyApk(temporary, update.version)
            destination.delete()
            require(temporary.renameTo(destination)) { "Не удалось сохранить APK" }
            return destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            error("Разреши установку из Lust и нажми «Обновить» ещё раз")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun verifyApk(apk: File, expectedVersion: ReleaseVersion) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("Файл не является APK")
        require(info.packageName == BuildConfig.APPLICATION_ID) { "APK принадлежит другому приложению" }
        require(info.versionName == expectedVersion.toString()) { "Версия APK не совпадает с GitHub release" }
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        require(archiveVersion > BuildConfig.VERSION_CODE && expectedVersion > ReleaseVersion.parse("v${BuildConfig.VERSION_NAME}")!!) { "Обновление не новее установленной версии" }
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.toList().orEmpty() else info.signatures?.toList().orEmpty()
        require(signatures.size == 1) { "Неожиданное количество подписей APK" }
        val certificate = MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
        require(MessageDigest.isEqual(certificate, BuildConfig.RELEASE_CERT_SHA256.hexBytes())) { "APK подписан неизвестным сертификатом" }
    }

    private fun requestBytes(url: String, limit: Long): ByteArray {
        val connection = open(url)
        try {
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= limit) { "Ответ сервера слишком большой" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= limit) { "Ответ сервера слишком большой" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun open(value: String): HttpURLConnection {
        UpdateCatalog.requireTrustedUrl(value)
        var url = URL(value)
        repeat(6) { redirects ->
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Lust-Android/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("Перенаправление без адреса")
                connection.disconnect()
                require(redirects < 5) { "Слишком много перенаправлений" }
                UpdateCatalog.requireTrustedUrl(location)
                url = URL(location)
            } else {
                require(connection.responseCode in 200..299) { "GitHub вернул HTTP ${connection.responseCode}" }
                return connection
            }
        }
        error("Слишком много перенаправлений")
    }

    private fun String.hexBytes(): ByteArray {
        require(length == 64 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/envywook/Lust/releases?per_page=20"
        const val MAX_METADATA = 1L * 1024 * 1024
        const val MAX_APK = 250L * 1024 * 1024
    }
}
