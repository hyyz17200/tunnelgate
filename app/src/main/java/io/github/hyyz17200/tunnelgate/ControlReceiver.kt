package io.github.hyyz17200.tunnelgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

class ControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_CONTROL) return

        val pending = goAsync()
        EXECUTOR.execute {
            try {
                val config = ConfigRepository.loadForUi(context)
                val op = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
                val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()

                when (config.controlMode) {
                    ControlMode.SHELL -> executeShell(context, config, op, reason)
                    ControlMode.V2RAYTUN_TASKER -> executeTasker(context, config, op, reason)
                }
            } catch (t: Throwable) {
                val message = "Control receiver failed: ${t.javaClass.simpleName}: ${t.message.orEmpty()}"
                Log.e(TAG, message, t)
                LogRepository.append(context, SOURCE_APP, message)
            } finally {
                pending.finish()
            }
        }
    }

    private fun executeShell(context: Context, config: PolicyConfig, op: String, reason: String) {
        val command = when (op) {
            CMD_START -> config.startCommand
            CMD_STOP -> config.stopCommand
            else -> ""
        }.trim()

        if (command.isBlank()) {
            val message = "Skip empty shell command, op=$op reason=$reason"
            Log.w(TAG, message)
            LogRepository.append(context, SOURCE_APP, message)
            return
        }

        val result = ShellUtils.execRoot(command)
        val summary = "Execute shell op=$op reason=$reason code=${result.code}"
        Log.i(TAG, summary)
        LogRepository.append(context, SOURCE_APP, summary)
        if (result.stdout.isNotBlank()) {
            Log.i(TAG, "stdout: ${result.stdout}")
            LogRepository.append(context, SOURCE_APP, "stdout: ${result.stdout}")
        }
        if (result.stderr.isNotBlank()) {
            Log.e(TAG, "stderr: ${result.stderr}")
            LogRepository.append(context, SOURCE_APP, "stderr: ${result.stderr}")
        }
    }

    private fun executeTasker(context: Context, config: PolicyConfig, op: String, reason: String) {
        val actionConfig = when (op) {
            CMD_START -> config.taskerStart
            CMD_STOP -> config.taskerStop
            else -> TaskerActionConfig()
        }

        if (!actionConfig.isConfigured()) {
            val message = "Skip empty tasker action, op=$op reason=$reason"
            Log.w(TAG, message)
            LogRepository.append(context, SOURCE_APP, message)
            return
        }

        val bundle = TaskerBundleCodec.decode(actionConfig.bundleBase64)
        if (bundle == null) {
            val message = "Tasker bundle decode failed, op=$op reason=$reason"
            Log.e(TAG, message)
            LogRepository.append(context, SOURCE_APP, message)
            return
        }

        val fireIntent = V2RayTunTaskerContract.buildFireIntent(bundle, actionConfig.blurb)
        context.sendBroadcast(fireIntent)
        val summary = "Execute tasker op=$op reason=$reason blurb=${actionConfig.blurb}"
        Log.i(TAG, summary)
        LogRepository.append(context, SOURCE_APP, summary)
        LogRepository.append(context, SOURCE_APP, TaskerBundleCodec.summarize(bundle))
    }

    companion object {
        private const val TAG = "TunnelGate"
        private const val SOURCE_APP = "app"

        const val ACTION_CONTROL = AppConst.ACTION_CONTROL
        const val ACTION_REEVALUATE = AppConst.ACTION_REEVALUATE

        const val EXTRA_COMMAND = "command"
        const val EXTRA_REASON = "reason"
        const val EXTRA_SSID = "ssid"

        const val CMD_START = "start"
        const val CMD_STOP = "stop"

        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
