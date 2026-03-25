package io.github.hyyz17200.tunnelgate

import android.content.Context

object StatusRepository {
    private const val PREFS = "tunnel_gate_status"
    private const val KEY_HOOK_ONLINE = "hook_online"
    private const val KEY_MONITORING_STARTED = "monitoring_started"
    private const val KEY_LAST_MESSAGE = "last_message"
    private const val KEY_LAST_UPDATE_AT = "last_update_at"

    data class Snapshot(
        val hookOnline: Boolean,
        val monitoringStarted: Boolean,
        val lastMessage: String,
        val lastUpdateAt: Long,
    ) {
        fun isFresh(now: Long = System.currentTimeMillis(), freshnessMs: Long = 30_000L): Boolean {
            return hookOnline && now - lastUpdateAt <= freshnessMs
        }
    }

    fun updateHookStatus(context: Context, hookOnline: Boolean, monitoringStarted: Boolean, lastMessage: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HOOK_ONLINE, hookOnline)
            .putBoolean(KEY_MONITORING_STARTED, monitoringStarted)
            .putString(KEY_LAST_MESSAGE, lastMessage)
            .putLong(KEY_LAST_UPDATE_AT, System.currentTimeMillis())
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            hookOnline = sp.getBoolean(KEY_HOOK_ONLINE, false),
            monitoringStarted = sp.getBoolean(KEY_MONITORING_STARTED, false),
            lastMessage = sp.getString(KEY_LAST_MESSAGE, "") ?: "",
            lastUpdateAt = sp.getLong(KEY_LAST_UPDATE_AT, 0L),
        )
    }
}
