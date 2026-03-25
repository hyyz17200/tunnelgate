package io.github.hyyz17200.tunnelgate

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    private const val PREFS = "tunnel_gate_logs"
    private const val KEY_TEXT = "log_text"
    private const val MAX_LINES = 200

    fun append(context: Context, source: String, message: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldLines = sp.getString(KEY_TEXT, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()

        oldLines += "${timestamp()} [$source] $message"
        val trimmed = oldLines.takeLast(MAX_LINES).joinToString("\n")
        sp.edit().putString(KEY_TEXT, trimmed).apply()
    }

    fun readAll(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TEXT, "")
            .orEmpty()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TEXT).apply()
    }

    private fun timestamp(): String {
        return SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
