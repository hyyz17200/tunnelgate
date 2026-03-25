package io.github.hyyz17200.tunnelgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppConst.ACTION_HOOK_STATUS -> {
                val monitoringStarted = intent.getBooleanExtra(EXTRA_MONITORING_STARTED, false)
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                StatusRepository.updateHookStatus(context, hookOnline = true, monitoringStarted = monitoringStarted, lastMessage = message)
            }

            AppConst.ACTION_LOG_EVENT -> {
                val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { SOURCE_APP }
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                if (message.isNotBlank()) {
                    LogRepository.append(context, source, message)
                }
            }
        }
    }

    companion object {
        const val EXTRA_MONITORING_STARTED = "monitoring_started"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SOURCE = "source"

        const val SOURCE_HOOK = "hook"
        const val SOURCE_APP = "app"
    }
}
