package com.envy.dualcorevpn.subscription

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/** Stable, app-scoped identity used by Remnawave-compatible HWID subscription limits. */
class SubscriptionDeviceIdentity(private val context: Context) {
    data class Headers(
        val hwid: String,
        val deviceOs: String,
        val osVersion: String,
        val deviceModel: String,
    )

    fun headers(scope: String): Headers = Headers(
        hwid = getOrCreateHwid(scope.lowercase()),
        deviceOs = "Android",
        osVersion = Build.VERSION.RELEASE.orEmpty().take(64),
        deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { "Android device" }
            .take(64),
    )

    private fun getOrCreateHwid(scope: String): String {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "$KEY_HWID.${digest(scope).take(16)}"
        preferences.getString(key, null)?.takeIf(HWID_REGEX::matches)?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
        val generated = androidId?.let { digest("${context.packageName}:$scope:$it") } ?: Base64.encodeToString(
            ByteArray(24).also(SecureRandom()::nextBytes), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        check(preferences.edit().putString(key, generated).commit()) { "Device identity could not be persisted" }
        return generated
    }

    private fun digest(value: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private companion object {
        const val PREFERENCES = "subscription_device_identity"
        const val KEY_HWID = "hwid"
        val HWID_REGEX = Regex("^[a-zA-Z0-9=-]{10,64}$")
    }
}
