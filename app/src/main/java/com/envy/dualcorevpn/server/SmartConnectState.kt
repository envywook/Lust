package com.envy.dualcorevpn.server

import android.content.Context

class SmartConnectState(context: Context) {
    private val preferences = context.getSharedPreferences("smart_connect", Context.MODE_PRIVATE)

    fun failures(serverId: String): Int =
        if (preferences.getString(KEY_SERVER, null) == serverId) preferences.getInt(KEY_FAILURES, 0) else 0

    fun record(serverId: String, reachable: Boolean): Int {
        val failures = if (reachable) 0 else (failures(serverId) + 1).coerceAtMost(SmartConnectPlanner.FAILURE_THRESHOLD)
        preferences.edit().putString(KEY_SERVER, serverId).putInt(KEY_FAILURES, failures).apply()
        return failures
    }

    fun pin(serverId: String) {
        preferences.edit().putString(KEY_SERVER, serverId).putInt(KEY_FAILURES, 0).apply()
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_FAILURES = "failures"
    }
}
