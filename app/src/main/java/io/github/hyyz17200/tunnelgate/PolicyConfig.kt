package io.github.hyyz17200.tunnelgate

data class PolicyConfig(
    val automationEnabled: Boolean = true,
    val controlMode: ControlMode = ControlMode.SHELL,
    val cellularAction: PolicyAction = PolicyAction.START,
    val wifiBlacklistAction: PolicyAction = PolicyAction.STOP,
    val wifiOtherAction: PolicyAction = PolicyAction.START,
    val noNetworkAction: PolicyAction = PolicyAction.STOP,
    val requireValidated: Boolean = true,
    val blacklistText: String = "",
    val debounceMs: Long = 2000L,
    val bootDelayMs: Long = 8000L,
    val startCommand: String = DEFAULT_START_COMMAND,
    val stopCommand: String = DEFAULT_STOP_COMMAND,
    val taskerStart: TaskerActionConfig = TaskerActionConfig(),
    val taskerStop: TaskerActionConfig = TaskerActionConfig(),
) {
    fun blacklistSet(): Set<String> {
        return blacklistText
            .lineSequence()
            .flatMap { line -> line.split(",", "，", ";", "；").asSequence() }
            .map { sanitizeSsid(it) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    companion object {
        const val DEFAULT_START_COMMAND =
            "am start -W -a android.intent.action.VIEW -d v2raytun://control/start -p com.v2raytun.android"
        const val DEFAULT_STOP_COMMAND =
            "am start -W -a android.intent.action.VIEW -d v2raytun://control/stop -p com.v2raytun.android"

        fun sanitizeSsid(raw: String?): String {
            if (raw == null) return ""
            val trimmed = raw.trim().removePrefix("\"").removeSuffix("\"").trim()
            if (trimmed.equals("<unknown ssid>", ignoreCase = true)) return ""
            if (trimmed.equals("unknown ssid", ignoreCase = true)) return ""
            if (trimmed == "<unknown>") return ""
            if (trimmed == "0x") return ""
            return trimmed
        }
    }
}
