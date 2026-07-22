package com.envy.dualcorevpn.core

import android.content.Context
import android.provider.Settings
import com.envy.dualcorevpn.logging.AppLog
import go.Seq
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import libv2ray.Libv2ray

object XudpBaseKey {
    private const val KEY_BYTES = 32

    fun fromAndroidId(androidId: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(androidId.toByteArray(Charsets.UTF_8).copyOf(KEY_BYTES))
}

object XrayRuntime {
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        try {
            val applicationContext = context.applicationContext
            Seq.setContext(applicationContext)
            val androidId = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID,
            ).orEmpty()
            Libv2ray.initCoreEnv(
                applicationContext.filesDir.absolutePath,
                XudpBaseKey.fromAndroidId(androidId),
            )
            AppLog.info("Xray", "Runtime initialized; version=${Libv2ray.checkVersionX()}")
        } catch (failure: Throwable) {
            initialized.set(false)
            AppLog.error("Xray", "Runtime initialization failed", failure)
            throw failure
        }
    }
}
