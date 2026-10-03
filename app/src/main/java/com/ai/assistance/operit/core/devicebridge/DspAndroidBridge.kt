package com.ai.assistance.operit.core.devicebridge

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.core.commonbase.CommonBaseProfile
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android adapter for the enhanced-device DSP bridge. Store builds never register or arm.
 * Does not log wake extras, event ids, or Companion detail strings.
 */
object DspAndroidBridge {
    private const val TAG = "DspBridge"
    private const val VIS_CLASS =
        "com.ai.assistance.operit.services.assistant.OperitVoiceInteractionService"
    private const val IO_TIMEOUT_MS = 8_000L
    private const val SHOW_WAIT_MS = 2_000L
    private val lock = Any()
    private val machine = DspSessionMachine()
    private val coordinator = DspHandoffCoordinator(machine)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dsp-bridge-io").apply { isDaemon = true }
        }
    private val serialQueue = DspSerialWorkQueue { runnable -> ioExecutor.execute(runnable) }
    private val showRegistry = DspPostedShowRegistry()
    private val visOwner = DspVisReceiverOwner()

    @Volatile private var vis: VoiceInteractionService? = null
    @Volatile private var receiver: BroadcastReceiver? = null

    fun isEnhancedDevice(): Boolean = CommonBaseProfile.enhancedDevice

    fun isUserEnabled(context: Context): Boolean = DspUserSettings(context).isUserEnabled()

    fun describeStatus(context: Context): String {
        if (!isEnhancedDevice()) {
            return DspEnablement.message(DspRejectReason.STORE_PROFILE)
        }
        val decision = DspEnablement.evaluate(probe(context))
        if (!decision.ok) {
            return decision.errorMessage ?: DspEnablement.message(DspRejectReason.USER_DISABLED)
        }
        return when (coordinator.snapshotReported()) {
            DspReportedState.ARMED -> "DSP 已确认 ACTIVE。"
            DspReportedState.ARM_REQUESTED -> "已请求开启 DSP，等待 ACTIVE 状态回报。"
            DspReportedState.DISARMED -> "DSP 已关闭（DETACHED）。"
            DspReportedState.UNKNOWN -> "DSP 开关已打开，尚未收到 ACTIVE 状态。"
        }
    }

    suspend fun setUserEnabled(context: Context, enabled: Boolean): DspEnablementDecision {
        if (!isEnhancedDevice()) {
            DspUserSettings(context).setUserEnabled(false)
            return DspEnablementDecision(ok = false, reason = DspRejectReason.STORE_PROFILE)
        }
        DspUserSettings(context).setUserEnabled(enabled)
        coordinator.markUserEnabledImmediate(enabled)
        if (!enabled) {
            showRegistry.invalidateAll()
        }
        val app = context.applicationContext
        return try {
            onSerialIo(IO_TIMEOUT_MS) { live ->
                if (!live()) {
                    return@onSerialIo DspEnablementDecision(
                        ok = false,
                        reason = if (enabled) DspRejectReason.DETACH_FAILED else DspRejectReason.USER_DISABLED
                    )
                }
                val ports = AndroidDspPorts(app) { vis }
                val enablement = probe(app)
                if (!live()) {
                    return@onSerialIo DspEnablementDecision(
                        ok = false,
                        reason = if (enabled) DspRejectReason.DETACH_FAILED else DspRejectReason.USER_DISABLED
                    )
                }
                coordinator.applyUserEnabled(enabled, enablement, ports)
            }
        } catch (_: TimeoutCancellationException) {
            DspEnablementDecision(
                ok = false,
                reason = if (enabled) DspRejectReason.DETACH_FAILED else DspRejectReason.USER_DISABLED
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DspEnablementDecision(ok = false, reason = DspRejectReason.DETACH_FAILED)
        }
    }

    fun attachVoiceInteraction(service: VoiceInteractionService) {
        if (!isEnhancedDevice()) {
            return
        }
        synchronized(lock) {
            val decision = visOwner.attach(service)
            val previous = decision.unregisterPreviousOwner
            if (previous is VoiceInteractionService) {
                runCatching { previous.unregisterReceiver(receiver) }
                receiver = null
            }
            vis = service
            if (decision.shouldRegister) {
                val dspReceiver = DspBridgeReceiver()
                receiver = dspReceiver
                val filter = IntentFilter().apply {
                    addAction(DspContract.WAKE_ACTION)
                    addAction(DspContract.STATUS_ACTION)
                }
                ContextCompat.registerReceiver(
                    service,
                    dspReceiver,
                    filter,
                    DspContract.SIGNATURE_PERMISSION,
                    null,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        }
        coordinator.markVisReadyImmediate()
        val app = service.applicationContext
        serialQueue.submit { live ->
            if (!live()) {
                return@submit
            }
            coordinator.onVisReady(probe(app), AndroidDspPorts(app) { vis })
        }
        AppLogger.d(TAG, "dsp vis ready; receiver registered")
    }

    fun detachVoiceInteraction(service: VoiceInteractionService) {
        if (!isEnhancedDevice()) {
            return
        }
        val app = service.applicationContext
        val shutdownCurrent: Boolean
        synchronized(lock) {
            val decision = visOwner.detach(service)
            shutdownCurrent = decision.clearedCurrentVis
            if (decision.unregister) {
                runCatching { service.unregisterReceiver(receiver) }
                receiver = null
            }
            if (shutdownCurrent) {
                vis = null
                showRegistry.invalidateAll()
                coordinator.markVisNotReadyImmediate()
            }
        }
        if (shutdownCurrent) {
            serialQueue.submit { live ->
                if (!live()) {
                    return@submit
                }
                coordinator.onVisShutdown(AndroidDspPorts(app) { vis })
            }
        }
        AppLogger.d(TAG, "dsp vis shutdown; disarmed")
    }

    suspend fun prepareCapture(context: Context): Boolean {
        if (!isEnhancedDevice()) {
            return true
        }
        val app = context.applicationContext
        return try {
            onSerialIo(IO_TIMEOUT_MS) { live ->
                if (!live()) {
                    return@onSerialIo false
                }
                when (val prep = coordinator.prepareCapture(probe(app), AndroidDspPorts(app) { vis }, live)) {
                    CapturePrep.Skip, CapturePrep.Ready -> true
                    is CapturePrep.Fail -> {
                        AppLogger.d(TAG, "dsp capture prep failed reason=${prep.reason}")
                        false
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun onSessionExitRequested(): Long {
        if (!isEnhancedDevice()) {
            return -1L
        }
        val token = coordinator.requestSessionExit()
        showRegistry.invalidateGeneration(token)
        return token
    }

    fun onSessionAudioSettled(context: Context, generation: Long) {
        if (!isEnhancedDevice() || generation < 0L) {
            return
        }
        val app = context.applicationContext
        serialQueue.submit { live ->
            if (!live()) {
                return@submit
            }
            val decision = coordinator.onAudioSettled(generation, probe(app), AndroidDspPorts(app) { vis })
            AppLogger.d(TAG, "dsp session audio settled decision=$decision")
        }
    }

    fun onSessionAudioSettleFailed(context: Context, generation: Long) {
        if (!isEnhancedDevice() || generation < 0L) {
            return
        }
        val decision = coordinator.onAudioSettleFailed(generation)
        AppLogger.d(TAG, "dsp session audio settle failed decision=$decision")
    }

    fun abandonSession(generation: Long): Boolean {
        if (!isEnhancedDevice() || generation <= 0L) {
            return false
        }
        val abandoned = coordinator.abandonSession(generation)
        showRegistry.invalidateGeneration(generation)
        AppLogger.d(TAG, "dsp session abandon generation-match=$abandoned")
        return abandoned
    }

    fun isSessionLive(generation: Long): Boolean {
        if (!isEnhancedDevice()) {
            return false
        }
        if (generation <= 0L) {
            return true
        }
        return coordinator.isSessionLive(generation)
    }

    fun noteUtteranceEnd() {
        if (!isEnhancedDevice()) {
            return
        }
        coordinator.noteUtteranceEnd()
    }

    internal fun handleBroadcast(context: Context, intent: Intent) {
        if (!isEnhancedDevice()) {
            return
        }
        when (intent.action) {
            DspContract.STATUS_ACTION -> {
                val ok = intent.getBooleanExtra(DspContract.EXTRA_OK, false)
                val state = intent.getStringExtra(DspContract.EXTRA_STATE).orEmpty()
                coordinator.onStatus(ok, state)
                AppLogger.d(TAG, "dsp status ok=$ok active=${DspContract.isActiveState(state)}")
            }
            DspContract.WAKE_ACTION -> {
                val fields = wakeFields(intent)
                val app = context.applicationContext
                serialQueue.submit { live ->
                    if (!live()) {
                        return@submit
                    }
                    val ports = AndroidDspPorts(app) { vis }
                    val outcome = coordinator.handleWake(fields, probe(app), ports, live)
                    AppLogger.d(
                        TAG,
                        "dsp wake outcome=${outcome::class.java.simpleName} reason=${(outcome as? HandoffOutcome.Rejected)?.reason}"
                    )
                }
            }
        }
    }

    fun probe(context: Context): DspEnablementInput {
        val app = context.applicationContext
        val settings = DspUserSettings(app)
        val pm = app.packageManager
        var installed = false
        var clientPackage: String? = null
        try {
            val info = pm.getApplicationInfo(DspContract.COMPANION_PACKAGE, PackageManager.GET_META_DATA)
            installed = info.enabled
            val raw = info.metaData?.getString(DspContract.CLIENT_PACKAGE_METADATA)
            clientPackage = raw?.takeIf { it.isNotEmpty() }
        } catch (_: PackageManager.NameNotFoundException) {
            installed = false
        }
        val signaturesMatch =
            installed &&
                pm.checkSignatures(app.packageName, DspContract.COMPANION_PACKAGE) ==
                    PackageManager.SIGNATURE_MATCH
        val selfPermission =
            app.checkSelfPermission(DspContract.SIGNATURE_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        val companionPermission =
            installed &&
                pm.checkPermission(DspContract.SIGNATURE_PERMISSION, DspContract.COMPANION_PACKAGE) ==
                    PackageManager.PERMISSION_GRANTED
        val selected =
            VoiceInteractionService.isActiveService(
                app,
                ComponentName(app.packageName, VIS_CLASS)
            )
        return DspEnablementInput(
            enhancedDevice = CommonBaseProfile.enhancedDevice,
            userEnabled = settings.isUserEnabled(),
            visReady = vis != null && machine.visReady,
            companionInstalled = installed,
            companionClientPackage = clientPackage,
            appPackage = app.packageName,
            signaturesMatch = signaturesMatch,
            permissionUsable = selfPermission && companionPermission,
            selectedVoiceInteraction = selected
        )
    }

    fun voiceInputSettingsIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private suspend fun <T> onSerialIo(
        timeoutMs: Long,
        block: (isLive: () -> Boolean) -> T
    ): T {
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val id =
                    serialQueue.submit { live ->
                        if (!live()) {
                            return@submit
                        }
                        val result = runCatching { block(live) }
                        if (live() && cont.isActive) {
                            cont.resumeWith(result)
                        }
                    }
                cont.invokeOnCancellation { serialQueue.cancel(id) }
            }
        }
    }

    private fun wakeFields(intent: Intent): WakeFields {
        val extras = intent.extras
        val keys = extras?.keySet()?.toSet() ?: emptySet()
        val confidence =
            if (intent.hasExtra(DspContract.EXTRA_CONFIDENCE)) {
                intent.getIntExtra(DspContract.EXTRA_CONFIDENCE, Int.MIN_VALUE)
            } else {
                null
            }
        val capture =
            if (intent.hasExtra(DspContract.EXTRA_CAPTURE_AVAILABLE)) {
                intent.getBooleanExtra(DspContract.EXTRA_CAPTURE_AVAILABLE, true)
            } else {
                null
            }
        return WakeFields(
            schemaVersion = intent.getStringExtra(DspContract.EXTRA_SCHEMA_VERSION),
            eventId = intent.getStringExtra(DspContract.EXTRA_EVENT_ID),
            keywordId = intent.getStringExtra(DspContract.EXTRA_KEYWORD_ID),
            confidence = confidence,
            captureAvailable = capture,
            keys = keys
        )
    }

    private class DspBridgeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) {
                return
            }
            handleBroadcast(context, intent)
        }
    }

    private class AndroidDspPorts(
        private val context: Context,
        private val visProvider: () -> VoiceInteractionService?
    ) : DefaultDspPorts() {
        override fun disarm(): DspProviderResult {
            return try {
                val bundle =
                    context.contentResolver.call(
                        Uri.parse(DspContract.STOP_URI),
                        DspContract.DISARM_METHOD,
                        null,
                        null
                    )
                fromBundle(bundle)
            } catch (_: Exception) {
                DspProviderResult(ok = false, state = "")
            }
        }

        override fun showSession(): Boolean {
            val expectedGeneration = coordinator.peekNextGeneration()
            val requestId = showRegistry.issue(expectedGeneration)
            val visAtIssue = visProvider()
            val start: () -> Boolean = {
                val current = visProvider()
                val prefsEnabled = DspUserSettings(context).isUserEnabled()
                val allowed =
                    DspPostedActionPolicy.canShow(
                        requestLive = showRegistry.isLive(requestId),
                        prefsEnabled = prefsEnabled,
                        visIdentityMatches = current != null && current === visAtIssue,
                        sessionShowAllowed = coordinator.canShowPostedSession(expectedGeneration)
                    )
                if (!allowed || current == null) {
                    false
                } else {
                    val args = Bundle()
                    args.putLong(DspContract.EXTRA_SESSION_GENERATION, expectedGeneration)
                    current.showSession(args, 0)
                    true
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return try {
                    start()
                } catch (t: Throwable) {
                    AppLogger.d(TAG, "dsp showSession failed")
                    false
                }
            }
            val result = ArrayBlockingQueue<Boolean>(1)
            mainHandler.post {
                val shown =
                    try {
                        start()
                    } catch (t: Throwable) {
                        AppLogger.d(TAG, "dsp showSession failed")
                        false
                    }
                result.offer(shown)
            }
            val shown = result.poll(SHOW_WAIT_MS, TimeUnit.MILLISECONDS)
            if (shown == null) {
                showRegistry.invalidate(requestId)
                return false
            }
            return shown
        }

        override fun arm() {
            val prefsEnabled = DspUserSettings(context).isUserEnabled()
            val current = visProvider()
            if (!DspPostedActionPolicy.canArm(
                    prefsEnabled = prefsEnabled,
                    visPresent = current != null,
                    visReady = coordinator.snapshotVisReady(),
                    machineAllowsArm = !coordinator.snapshotBusy() && coordinator.snapshotUserEnabled()
                )
            ) {
                return
            }
            val intent =
                Intent(DspContract.ACTION_ARM)
                    .setClassName(DspContract.COMPANION_PACKAGE, DspContract.CONTROL_SERVICE)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: RuntimeException) {
                AppLogger.d(TAG, "dsp arm service start failed")
            }
        }

        override fun readStatus(): DspProviderResult {
            return try {
                val bundle =
                    context.contentResolver.call(
                        Uri.parse(DspContract.STATUS_URI),
                        DspContract.STATUS_METHOD,
                        null,
                        null
                    )
                fromBundle(bundle)
            } catch (_: Exception) {
                DspProviderResult(ok = false, state = "")
            }
        }

        private fun fromBundle(bundle: Bundle?): DspProviderResult {
            if (bundle == null) {
                return DspProviderResult(ok = false, state = "")
            }
            return DspProviderResult(
                ok = bundle.getBoolean(DspContract.EXTRA_OK, false),
                state = bundle.getString(DspContract.EXTRA_STATE).orEmpty()
            )
        }
    }
}
