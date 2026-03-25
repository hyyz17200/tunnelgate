package io.github.hyyz17200.tunnelgate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var swAutomationEnabled: SwitchCompat
    private lateinit var rgControlMode: RadioGroup
    private lateinit var rgCellular: RadioGroup
    private lateinit var rgWifiBlacklist: RadioGroup
    private lateinit var rgWifiOther: RadioGroup
    private lateinit var rgNoNetwork: RadioGroup
    private lateinit var swRequireValidated: SwitchCompat
    private lateinit var etBlacklist: EditText
    private lateinit var etDebounce: EditText
    private lateinit var etBootDelay: EditText
    private lateinit var etStartCommand: EditText
    private lateinit var etStopCommand: EditText
    private lateinit var layoutShellSection: LinearLayout
    private lateinit var layoutTaskerSection: LinearLayout
    private lateinit var tvTaskerStart: TextView
    private lateinit var tvTaskerStop: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvSuStatus: TextView
    private lateinit var tvLsposedStatus: TextView
    private lateinit var tvLogs: TextView
    private lateinit var tvDeveloper: TextView

    private var taskerStartConfig = TaskerActionConfig()
    private var taskerStopConfig = TaskerActionConfig()
    private var pendingCaptureOp: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val op = pendingCaptureOp
        pendingCaptureOp = null
        if (op == null) return@registerForActivityResult

        if (result.resultCode != Activity.RESULT_OK) {
            setStatus(getString(R.string.status_capture_cancelled, op))
            return@registerForActivityResult
        }

        val data = result.data
        val bundle = data?.getBundleExtra(V2RayTunTaskerContract.EXTRA_BUNDLE)
            ?: data?.extras?.getBundle(V2RayTunTaskerContract.EXTRA_BUNDLE)
        val blurb = data?.getStringExtra(V2RayTunTaskerContract.EXTRA_STRING_BLURB)
            ?: data?.extras?.getString(V2RayTunTaskerContract.EXTRA_STRING_BLURB)
            ?: ""

        if (bundle == null) {
            setStatus(getString(R.string.status_capture_failed_bundle))
            return@registerForActivityResult
        }

        val saved = TaskerActionConfig(
            bundleBase64 = TaskerBundleCodec.encode(bundle),
            blurb = blurb,
        )

        when (op) {
            ControlReceiver.CMD_START -> taskerStartConfig = saved
            ControlReceiver.CMD_STOP -> taskerStopConfig = saved
        }

        refreshTaskerSummary()
        refreshLogs()
        setStatus(getString(R.string.status_captured_action, op))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        tvDeveloper.movementMethod = LinkMovementMethod.getInstance()
        tvDeveloper.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hyyz17200")))
            }
        }

        loadIntoUi(ConfigRepository.loadForUi(this))
        bindActions()
        refreshLogs()
        setStatus(getString(R.string.status_loaded_current_config))
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnostics()
        requestHookStatus()
        mainHandler.postDelayed({ refreshDiagnostics() }, 500L)
        refreshLogs()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }

    private fun bindViews() {
        swAutomationEnabled = findViewById(R.id.swAutomationEnabled)
        rgControlMode = findViewById(R.id.rgControlMode)
        rgCellular = findViewById(R.id.rgCellular)
        rgWifiBlacklist = findViewById(R.id.rgWifiBlacklist)
        rgWifiOther = findViewById(R.id.rgWifiOther)
        rgNoNetwork = findViewById(R.id.rgNoNetwork)
        swRequireValidated = findViewById(R.id.swRequireValidated)
        etBlacklist = findViewById(R.id.etBlacklist)
        etDebounce = findViewById(R.id.etDebounce)
        etBootDelay = findViewById(R.id.etBootDelay)
        etStartCommand = findViewById(R.id.etStartCommand)
        etStopCommand = findViewById(R.id.etStopCommand)
        layoutShellSection = findViewById(R.id.layoutShellSection)
        layoutTaskerSection = findViewById(R.id.layoutTaskerSection)
        tvTaskerStart = findViewById(R.id.tvTaskerStart)
        tvTaskerStop = findViewById(R.id.tvTaskerStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvSuStatus = findViewById(R.id.tvSuStatus)
        tvLsposedStatus = findViewById(R.id.tvLsposedStatus)
        tvLogs = findViewById(R.id.tvLogs)
        tvLogs.movementMethod = ScrollingMovementMethod()
        tvDeveloper = findViewById(R.id.tvDeveloper)
    }

    private fun bindActions() {
        rgControlMode.setOnCheckedChangeListener { _, _ -> updateModeVisibility() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val config = readFromUi()
            ConfigRepository.saveFromUi(this, config)
            requestReevaluate()
            refreshDiagnostics()
            setStatus(getString(R.string.status_saved))
        }

        findViewById<Button>(R.id.btnRestoreDefaults).setOnClickListener {
            val config = PolicyConfig()
            loadIntoUi(config)
            ConfigRepository.saveFromUi(this, config)
            requestReevaluate()
            refreshDiagnostics()
            setStatus(getString(R.string.status_restored_defaults))
        }

        findViewById<Button>(R.id.btnManualStart).setOnClickListener {
            sendManual(ControlReceiver.CMD_START)
        }

        findViewById<Button>(R.id.btnManualStop).setOnClickListener {
            sendManual(ControlReceiver.CMD_STOP)
        }

        findViewById<Button>(R.id.btnCaptureStart).setOnClickListener {
            captureTasker(ControlReceiver.CMD_START)
        }

        findViewById<Button>(R.id.btnCaptureStop).setOnClickListener {
            captureTasker(ControlReceiver.CMD_STOP)
        }

        findViewById<Button>(R.id.btnClearTasker).setOnClickListener {
            taskerStartConfig = TaskerActionConfig()
            taskerStopConfig = TaskerActionConfig()
            refreshTaskerSummary()
            setStatus(getString(R.string.status_tasker_cleared))
        }

        findViewById<Button>(R.id.btnRefreshStatus).setOnClickListener {
            refreshDiagnostics()
            requestHookStatus()
            mainHandler.postDelayed({ refreshDiagnostics() }, 500L)
            refreshLogs()
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            LogRepository.clear(this)
            refreshLogs()
            setStatus(getString(R.string.status_logs_cleared))
        }
    }

    private fun loadIntoUi(config: PolicyConfig) {
        swAutomationEnabled.isChecked = config.automationEnabled
        rgControlMode.check(
            if (config.controlMode == ControlMode.V2RAYTUN_TASKER) R.id.rbModeTasker else R.id.rbModeShell
        )
        rgCellular.check(if (config.cellularAction == PolicyAction.START) R.id.rbCellularStart else R.id.rbCellularStop)
        rgWifiBlacklist.check(if (config.wifiBlacklistAction == PolicyAction.START) R.id.rbWifiBlacklistStart else R.id.rbWifiBlacklistStop)
        rgWifiOther.check(if (config.wifiOtherAction == PolicyAction.START) R.id.rbWifiOtherStart else R.id.rbWifiOtherStop)
        rgNoNetwork.check(if (config.noNetworkAction == PolicyAction.START) R.id.rbNoNetworkStart else R.id.rbNoNetworkStop)
        swRequireValidated.isChecked = config.requireValidated
        etBlacklist.setText(config.blacklistText)
        etDebounce.setText(config.debounceMs.toString())
        etBootDelay.setText(config.bootDelayMs.toString())
        etStartCommand.setText(config.startCommand)
        etStopCommand.setText(config.stopCommand)
        taskerStartConfig = config.taskerStart
        taskerStopConfig = config.taskerStop
        refreshTaskerSummary()
        updateModeVisibility()
    }

    private fun readFromUi(): PolicyConfig {
        val debounce = etDebounce.text.toString().trim().toLongOrNull() ?: 2000L
        val bootDelay = etBootDelay.text.toString().trim().toLongOrNull() ?: 8000L
        return PolicyConfig(
            automationEnabled = swAutomationEnabled.isChecked,
            controlMode = selectedMode(),
            cellularAction = actionFromGroup(rgCellular, R.id.rbCellularStart),
            wifiBlacklistAction = actionFromGroup(rgWifiBlacklist, R.id.rbWifiBlacklistStart),
            wifiOtherAction = actionFromGroup(rgWifiOther, R.id.rbWifiOtherStart),
            noNetworkAction = actionFromGroup(rgNoNetwork, R.id.rbNoNetworkStart),
            requireValidated = swRequireValidated.isChecked,
            blacklistText = etBlacklist.text.toString(),
            debounceMs = debounce.coerceAtLeast(0L),
            bootDelayMs = bootDelay.coerceAtLeast(0L),
            startCommand = etStartCommand.text.toString().trim(),
            stopCommand = etStopCommand.text.toString().trim(),
            taskerStart = taskerStartConfig,
            taskerStop = taskerStopConfig,
        )
    }

    private fun selectedMode(): ControlMode {
        return if (rgControlMode.checkedRadioButtonId == R.id.rbModeTasker) {
            ControlMode.V2RAYTUN_TASKER
        } else {
            ControlMode.SHELL
        }
    }

    private fun actionFromGroup(group: RadioGroup, startId: Int): PolicyAction {
        return if (group.checkedRadioButtonId == startId) PolicyAction.START else PolicyAction.STOP
    }

    private fun captureTasker(op: String) {
        pendingCaptureOp = op
        try {
            captureLauncher.launch(V2RayTunTaskerContract.buildEditIntent())
            setStatus(getString(R.string.status_open_tasker_hint, op))
        } catch (t: Throwable) {
            pendingCaptureOp = null
            setStatus(getString(R.string.status_open_tasker_failed, t.javaClass.simpleName))
        }
    }

    private fun refreshTaskerSummary() {
        tvTaskerStart.text = summarizeTasker(getString(R.string.label_start_action), taskerStartConfig)
        tvTaskerStop.text = summarizeTasker(getString(R.string.label_stop_action), taskerStopConfig)
    }

    private fun summarizeTasker(label: String, config: TaskerActionConfig): String {
        return if (config.isConfigured()) {
            val blurb = if (config.blurb.isBlank()) getString(R.string.label_no_blurb) else config.blurb
            getString(R.string.template_tasker_configured, label, blurb)
        } else {
            getString(R.string.template_tasker_not_configured, label)
        }
    }

    private fun updateModeVisibility() {
        val shell = selectedMode() == ControlMode.SHELL
        layoutShellSection.visibility = if (shell) View.VISIBLE else View.GONE
        layoutTaskerSection.visibility = if (shell) View.GONE else View.VISIBLE
    }

    private fun sendManual(op: String) {
        val intent = Intent(ControlReceiver.ACTION_CONTROL).apply {
            component = android.content.ComponentName(AppConst.APPLICATION_ID, AppConst.RECEIVER_CLASS)
            putExtra(ControlReceiver.EXTRA_COMMAND, op)
            putExtra(ControlReceiver.EXTRA_REASON, "manual")
        }
        sendBroadcast(intent)
        setStatus(getString(R.string.status_manual_sent, op))
        mainHandler.postDelayed({ refreshLogs() }, 250L)
    }

    private fun requestReevaluate() {
        sendBroadcast(Intent(ControlReceiver.ACTION_REEVALUATE))
    }

    private fun requestHookStatus() {
        sendBroadcast(Intent(AppConst.ACTION_QUERY_HOOK_STATUS))
    }

    private fun refreshDiagnostics() {
        backgroundExecutor.execute {
            val suResult = ShellUtils.checkSu()
            val suText = if (suResult.code == 0) {
                getString(R.string.diag_su_ok)
            } else {
                getString(R.string.diag_su_failed, suResult.code, suResult.stderr.ifBlank { suResult.stdout }.ifBlank { "-" })
            }

            val snapshot = StatusRepository.snapshot(this)
            val lsposedText = if (snapshot.isFresh()) {
                val timeText = DateFormat.getDateTimeInstance().format(Date(snapshot.lastUpdateAt))
                getString(
                    if (snapshot.monitoringStarted) R.string.diag_lsposed_ok_monitoring else R.string.diag_lsposed_ok_idle,
                    timeText,
                    snapshot.lastMessage.ifBlank { "-" }
                )
            } else {
                getString(R.string.diag_lsposed_missing)
            }

            mainHandler.post {
                tvSuStatus.text = suText
                tvLsposedStatus.text = lsposedText
                when {
                    suResult.code != 0 -> setStatus(getString(R.string.prompt_check_su))
                    !snapshot.isFresh() -> setStatus(getString(R.string.prompt_check_lsposed))
                }
            }
        }
    }

    private fun refreshLogs() {
        val text = LogRepository.readAll(this)
        tvLogs.text = if (text.isBlank()) getString(R.string.logs_empty) else text
    }

    private fun setStatus(message: String) {
        tvStatus.text = message
    }
}
