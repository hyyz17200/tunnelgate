package io.github.hyyz17200.tunnelgate.hook

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.hyyz17200.tunnelgate.AppConst
import io.github.hyyz17200.tunnelgate.AppEventReceiver
import io.github.hyyz17200.tunnelgate.ConfigRepository
import io.github.hyyz17200.tunnelgate.ControlReceiver
import io.github.hyyz17200.tunnelgate.PolicyConfig
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.atomic.AtomicBoolean

object NetPolicyEngine {
    private const val TAG = "TunnelGate"

    private val inited = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var appContext: Context
    private lateinit var cm: ConnectivityManager
    private lateinit var wifiManager: WifiManager

    private var monitoringStarted = false
    private var pendingEvaluate: Runnable? = null
    private var pendingBootStart: Runnable? = null
    private var lastAppliedEnabled: Boolean? = null
    @Volatile private var currentDefaultNetwork: Network? = null
    @Volatile private var currentDefaultCaps: NetworkCapabilities? = null

    fun init(context: Context) {
        if (!inited.compareAndSet(false, true)) return

        try {
            appContext = context
            cm = context.getSystemService(ConnectivityManager::class.java)
            wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            registerReevaluateReceiver()
            registerStatusQueryReceiver()
            registerBootReceiver()

            logAndBroadcast("init ok")
            pushStatus("init ok")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: init hard-failed $t")
            inited.set(false)
        }
    }

    private fun registerBootReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                logAndBroadcast("boot gate action=$action")
                scheduleStartMonitoring(action)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun registerStatusQueryReceiver() {
        val filter = IntentFilter(AppConst.ACTION_QUERY_HOOK_STATUS)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                pushStatus("status queried")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun registerNetworkCallback() {
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) {
                    currentDefaultNetwork = network
                    scheduleEvaluate("onAvailable")
                }

                override fun onLost(network: Network) {
                    if (currentDefaultNetwork == network) {
                        currentDefaultNetwork = null
                        currentDefaultCaps = null
                    }
                    scheduleEvaluate("onLost")
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    currentDefaultNetwork = network
                    currentDefaultCaps = caps
                    scheduleEvaluate("onCapabilitiesChanged")
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    currentDefaultNetwork = network
                    scheduleEvaluate("onAvailable")
                }

                override fun onLost(network: Network) {
                    if (currentDefaultNetwork == network) {
                        currentDefaultNetwork = null
                        currentDefaultCaps = null
                    }
                    scheduleEvaluate("onLost")
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    currentDefaultNetwork = network
                    currentDefaultCaps = caps
                    scheduleEvaluate("onCapabilitiesChanged")
                }
            }
        }

        cm.registerDefaultNetworkCallback(callback)
    }

    private fun registerReevaluateReceiver() {
        val filter = IntentFilter(ControlReceiver.ACTION_REEVALUATE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (monitoringStarted) {
                    scheduleEvaluate("manualReevaluate")
                } else {
                    scheduleStartMonitoring("manualReevaluate")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun scheduleStartMonitoring(trigger: String) {
        if (monitoringStarted) return
        val cfg = safeLoadConfig()
        pendingBootStart?.let(handler::removeCallbacks)
        pendingBootStart = Runnable {
            if (monitoringStarted) return@Runnable
            try {
                registerNetworkCallback()
                monitoringStarted = true
                logAndBroadcast("monitoring started trigger=$trigger")
                pushStatus("monitoring started")
                scheduleEvaluate("bootReady")
            } catch (t: Throwable) {
                logAndBroadcast("start monitoring failed ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            }
        }
        val delayMs = cfg.bootDelayMs.coerceAtLeast(0L)
        logAndBroadcast("schedule monitoring in ${delayMs}ms trigger=$trigger")
        handler.postDelayed(pendingBootStart!!, delayMs)
    }

    private fun scheduleEvaluate(trigger: String) {
        if (!monitoringStarted) return
        val cfg = safeLoadConfig()
        pendingEvaluate?.let(handler::removeCallbacks)
        pendingEvaluate = Runnable {
            try {
                evaluateAndApply(trigger)
            } catch (t: Throwable) {
                logAndBroadcast("evaluate crashed ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            }
        }
        handler.postDelayed(pendingEvaluate!!, cfg.debounceMs.coerceAtLeast(0L))
    }

    private fun evaluateAndApply(trigger: String) {
        val cfg = safeLoadConfig()
        if (!cfg.automationEnabled) {
            lastAppliedEnabled = null
            logAndBroadcast("automation disabled trigger=$trigger")
            pushStatus("automation disabled")
            return
        }

        val caps = currentDefaultCaps ?: cm.activeNetwork?.let(cm::getNetworkCapabilities)
        val decision = decide(cfg, caps)

        if (lastAppliedEnabled == decision.enabled) {
            logAndBroadcast("unchanged enabled=${decision.enabled}, reason=${decision.reason}, trigger=$trigger")
            pushStatus("unchanged: ${decision.reason}")
            return
        }

        lastAppliedEnabled = decision.enabled
        val command = if (decision.enabled) ControlReceiver.CMD_START else ControlReceiver.CMD_STOP
        logAndBroadcast("apply=$command reason=${decision.reason} ssid=${decision.ssid} trigger=$trigger")
        pushStatus("apply $command: ${decision.reason}")

        val intent = Intent(ControlReceiver.ACTION_CONTROL).apply {
            component = ComponentName(AppConst.APPLICATION_ID, AppConst.RECEIVER_CLASS)
            putExtra(ControlReceiver.EXTRA_COMMAND, command)
            putExtra(ControlReceiver.EXTRA_REASON, decision.reason)
            putExtra(ControlReceiver.EXTRA_SSID, decision.ssid ?: "")
        }

        appContext.sendBroadcast(intent)
    }

    private fun safeLoadConfig(): PolicyConfig {
        return try {
            ConfigRepository.loadForHook()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: load config failed, using defaults: $t")
            PolicyConfig()
        }
    }

    private fun decide(cfg: PolicyConfig, caps: NetworkCapabilities?): Decision {
        if (caps == null) {
            return Decision(cfg.noNetworkAction.isEnabled(), "no_network", null)
        }

        if (cfg.requireValidated && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return Decision(cfg.noNetworkAction.isEnabled(), "not_validated", null)
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val ssid = currentSsid(caps)
            if (ssid == null) {
                return Decision(lastAppliedEnabled ?: false, "wifi_unknown_keep", null)
            }
            val blacklist = cfg.blacklistSet()
            return if (blacklist.contains(PolicyConfig.sanitizeSsid(ssid))) {
                Decision(cfg.wifiBlacklistAction.isEnabled(), "wifi_blacklist", ssid)
            } else {
                Decision(cfg.wifiOtherAction.isEnabled(), "wifi_other", ssid)
            }
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return Decision(cfg.cellularAction.isEnabled(), "cellular", null)
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            // Before, it was `Decision(lastAppliedEnabled ?: false, "vpn_only_keep", null)`
            // which kept the previous state instead of applying the no-network setting.
            // When there is no physical network (just VPN active), it should use noNetworkAction
            return Decision(cfg.noNetworkAction.isEnabled(), "vpn_only_no_network", null)
        }

        return Decision(cfg.noNetworkAction.isEnabled(), "other_transport", null)
    }

    private fun currentSsid(caps: NetworkCapabilities): String? {
        val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.transportInfo as? WifiInfo else null
        val raw = fromCaps?.ssid ?: runCatching { wifiManager.connectionInfo?.ssid }.getOrNull()
        val value = PolicyConfig.sanitizeSsid(raw)
        return value.ifEmpty { null }
    }

    private fun logAndBroadcast(message: String) {
        XposedBridge.log("$TAG: $message")
        val intent = Intent(AppConst.ACTION_LOG_EVENT).apply {
            component = ComponentName(AppConst.APPLICATION_ID, AppConst.EVENT_RECEIVER_CLASS)
            putExtra(AppEventReceiver.EXTRA_SOURCE, AppEventReceiver.SOURCE_HOOK)
            putExtra(AppEventReceiver.EXTRA_MESSAGE, message)
        }
        appContext.sendBroadcast(intent)
    }

    private fun pushStatus(message: String) {
        val intent = Intent(AppConst.ACTION_HOOK_STATUS).apply {
            component = ComponentName(AppConst.APPLICATION_ID, AppConst.EVENT_RECEIVER_CLASS)
            putExtra(AppEventReceiver.EXTRA_MONITORING_STARTED, monitoringStarted)
            putExtra(AppEventReceiver.EXTRA_MESSAGE, message)
        }
        appContext.sendBroadcast(intent)
    }

    private data class Decision(
        val enabled: Boolean,
        val reason: String,
        val ssid: String?,
    )
}
