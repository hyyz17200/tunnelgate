package io.github.hyyz17200.tunnelgate

import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences

object ConfigRepository {
    const val PREFS_NAME = "tunnel_gate_prefs"

    private fun getEditablePrefs(context: Context): SharedPreferences {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun loadForUi(context: Context): PolicyConfig {
        val sp = getEditablePrefs(context)
        return loadFromGetter(
            getString = { key, def -> sp.getString(key, def) },
            getBoolean = { key, def -> sp.getBoolean(key, def) },
            getLong = { key, def -> sp.getLong(key, def) },
        )
    }

    fun saveFromUi(context: Context, config: PolicyConfig) {
        getEditablePrefs(context)
            .edit()
            .putBoolean(ConfigContract.KEY_AUTOMATION_ENABLED, config.automationEnabled)
            .putString(ConfigContract.KEY_CONTROL_MODE, config.controlMode.name)
            .putString(ConfigContract.KEY_CELLULAR_ACTION, config.cellularAction.name)
            .putString(ConfigContract.KEY_WIFI_BLACKLIST_ACTION, config.wifiBlacklistAction.name)
            .putString(ConfigContract.KEY_WIFI_OTHER_ACTION, config.wifiOtherAction.name)
            .putString(ConfigContract.KEY_NO_NETWORK_ACTION, config.noNetworkAction.name)
            .putBoolean(ConfigContract.KEY_REQUIRE_VALIDATED, config.requireValidated)
            .putString(ConfigContract.KEY_BLACKLIST_TEXT, config.blacklistText)
            .putLong(ConfigContract.KEY_DEBOUNCE_MS, config.debounceMs)
            .putLong(ConfigContract.KEY_BOOT_DELAY_MS, config.bootDelayMs)
            .putString(ConfigContract.KEY_START_COMMAND, config.startCommand)
            .putString(ConfigContract.KEY_STOP_COMMAND, config.stopCommand)
            .putString(ConfigContract.KEY_TASKER_START_BUNDLE, config.taskerStart.bundleBase64)
            .putString(ConfigContract.KEY_TASKER_START_BLURB, config.taskerStart.blurb)
            .putString(ConfigContract.KEY_TASKER_STOP_BUNDLE, config.taskerStop.bundleBase64)
            .putString(ConfigContract.KEY_TASKER_STOP_BLURB, config.taskerStop.blurb)
            .apply()
    }

    fun loadForHook(): PolicyConfig {
        return try {
            val pref = XSharedPreferences(AppConst.APPLICATION_ID, PREFS_NAME)
            if (!pref.file.canRead()) return PolicyConfig()
            pref.reload()
            loadFromGetter(
                getString = { key, def -> pref.getString(key, def) },
                getBoolean = { key, def -> pref.getBoolean(key, def) },
                getLong = { key, def -> pref.getLong(key, def) },
            )
        } catch (_: Throwable) {
            PolicyConfig()
        }
    }

    private fun loadFromGetter(
        getString: (String, String?) -> String?,
        getBoolean: (String, Boolean) -> Boolean,
        getLong: (String, Long) -> Long,
    ): PolicyConfig {
        return PolicyConfig(
            automationEnabled = getBoolean(ConfigContract.KEY_AUTOMATION_ENABLED, true),
            controlMode = parseMode(getString(ConfigContract.KEY_CONTROL_MODE, ControlMode.SHELL.name)),
            cellularAction = parseAction(getString(ConfigContract.KEY_CELLULAR_ACTION, PolicyAction.START.name)),
            wifiBlacklistAction = parseAction(getString(ConfigContract.KEY_WIFI_BLACKLIST_ACTION, PolicyAction.STOP.name)),
            wifiOtherAction = parseAction(getString(ConfigContract.KEY_WIFI_OTHER_ACTION, PolicyAction.START.name)),
            noNetworkAction = parseAction(getString(ConfigContract.KEY_NO_NETWORK_ACTION, PolicyAction.STOP.name)),
            requireValidated = getBoolean(ConfigContract.KEY_REQUIRE_VALIDATED, true),
            blacklistText = getString(ConfigContract.KEY_BLACKLIST_TEXT, "") ?: "",
            debounceMs = getLong(ConfigContract.KEY_DEBOUNCE_MS, 2000L),
            bootDelayMs = getLong(ConfigContract.KEY_BOOT_DELAY_MS, 8000L),
            startCommand = getString(ConfigContract.KEY_START_COMMAND, PolicyConfig.DEFAULT_START_COMMAND)
                ?: PolicyConfig.DEFAULT_START_COMMAND,
            stopCommand = getString(ConfigContract.KEY_STOP_COMMAND, PolicyConfig.DEFAULT_STOP_COMMAND)
                ?: PolicyConfig.DEFAULT_STOP_COMMAND,
            taskerStart = TaskerActionConfig(
                bundleBase64 = getString(ConfigContract.KEY_TASKER_START_BUNDLE, "") ?: "",
                blurb = getString(ConfigContract.KEY_TASKER_START_BLURB, "") ?: "",
            ),
            taskerStop = TaskerActionConfig(
                bundleBase64 = getString(ConfigContract.KEY_TASKER_STOP_BUNDLE, "") ?: "",
                blurb = getString(ConfigContract.KEY_TASKER_STOP_BLURB, "") ?: "",
            ),
        )
    }

    private fun parseMode(value: String?): ControlMode {
        return when (value) {
            ControlMode.V2RAYTUN_TASKER.name -> ControlMode.V2RAYTUN_TASKER
            else -> ControlMode.SHELL
        }
    }

    private fun parseAction(value: String?): PolicyAction {
        return when (value) {
            PolicyAction.STOP.name -> PolicyAction.STOP
            else -> PolicyAction.START
        }
    }
}
