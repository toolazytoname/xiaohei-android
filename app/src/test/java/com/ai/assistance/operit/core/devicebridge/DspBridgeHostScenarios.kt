package com.ai.assistance.operit.core.devicebridge

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RecordingDspPorts(
    var disarmResult: DspProviderResult = DspProviderResult(ok = true, state = DspContract.STATE_DETACHED),
    var showResult: Boolean = true,
    var statusResult: DspProviderResult = DspProviderResult(ok = true, state = "MODEL_LOADED"),
    var disarmThrows: Boolean = false,
    var disarmDelayMs: Long = 0L,
    var disarmStarted: CountDownLatch? = null,
    var disarmBlock: CountDownLatch? = null
) : DspCompanionPorts {
    val calls = mutableListOf<String>()

    override fun disarm(): DspProviderResult {
        disarmStarted?.countDown()
        val block = disarmBlock
        if (block != null) {
            require(block.await(5, TimeUnit.SECONDS)) { "disarm block timeout" }
        }
        if (disarmDelayMs > 0L) {
            Thread.sleep(disarmDelayMs)
        }
        calls.add("disarm")
        if (disarmThrows) {
            throw RuntimeException("disarm failed")
        }
        return disarmResult
    }

    override fun confirmDetached(result: DspProviderResult): Boolean {
        val ok = DspContract.isVerifiedDetached(result)
        if (ok) {
            calls.add("verified")
        }
        return ok
    }

    override fun showSession(): Boolean {
        calls.add("show")
        return showResult
    }

    override fun arm() {
        calls.add("arm")
    }

    override fun readStatus(): DspProviderResult {
        calls.add("readStatus")
        return statusResult
    }
}

object DspBridgeHostScenarios {
    const val APP = "studio.weichao.xiaohei.common"

    fun validWake(eventId: String = "550e8400-e29b-41d4-a716-446655440000"): WakeFields {
        return WakeFields(
            schemaVersion = DspContract.SCHEMA_VERSION,
            eventId = eventId,
            keywordId = DspContract.EXPECTED_KEYWORD_ID,
            confidence = 99,
            captureAvailable = false,
            keys = DspContract.WAKE_EXTRA_KEYS
        )
    }

    fun enabledInput(
        enhancedDevice: Boolean = true,
        userEnabled: Boolean = true,
        visReady: Boolean = true,
        companionInstalled: Boolean = true,
        companionClientPackage: String? = APP,
        appPackage: String = APP,
        signaturesMatch: Boolean = true,
        permissionUsable: Boolean = true,
        selectedVoiceInteraction: Boolean = true
    ): DspEnablementInput {
        return DspEnablementInput(
            enhancedDevice = enhancedDevice,
            userEnabled = userEnabled,
            visReady = visReady,
            companionInstalled = companionInstalled,
            companionClientPackage = companionClientPackage,
            appPackage = appPackage,
            signaturesMatch = signaturesMatch,
            permissionUsable = permissionUsable,
            selectedVoiceInteraction = selectedVoiceInteraction
        )
    }

    fun readyCoordinator(): Pair<DspHandoffCoordinator, DspSessionMachine> {
        val machine = DspSessionMachine()
        machine.onVisReady()
        machine.setUserEnabled(true)
        return DspHandoffCoordinator(machine) to machine
    }

    fun runAll(): String {
        disabledRejectsWake()
        storeRejectsWake()
        notReadyRejectsWake()
        badSchemaRejected()
        duplicateRejected()
        busyRejected()
        detachFailureDoesNotShow()
        wakeCallOrderDisarmVerifiedShow()
        staleGenerationCleanupDoesNotArm()
        disableThenCleanupDoesNotArm()
        armCommandDoesNotMarkArmedUntilActive()
        utteranceEndDoesNotExitSession()
        lockscreenPolicy()
        enablementErrorsAreSpecific()
        prepareCaptureSkipAndDetachFail()
        disableDoesNotMarkDisarmedWhenProviderFails()
        disableDoesNotMarkDisarmedWhenProviderThrows()
        visShutdownDoesNotMarkDisarmedWhenDisarmFails()
        capturedCleanupTokenDoesNotStealNewerSession()
        delayedShowAfterTimeoutDoesNotStart()
        lockscreenCancelDoesNotEnterOrArm()
        cancelRecognitionFailureDoesNotSettle()
        rearmReevaluatesEnablementNotJustVisReady()
        mainThreadEntryReturnsBeforeSlowDisarm()
        cancelledQueuedWorkDoesNotRun()
        abandonDoesNotTouchOtherSession()
        serialQueueBoundedAndCompletedNotLive()
        serialQueueExecuteThrowReleases()
        cancelledPrepareAfterBlockedDisarmDoesNotEnter()
        immediateDisableFailsPostedShowWithoutIo()
        oldVisDetachDoesNotClearNewReceiverOrShow()
        failedCleanupHoldsBusyUntilSameTokenSettles()
        abandonOldTokenDoesNotInvalidateNewShow()
        listenStartCancelledAfterPrepareDoesNotOpenMic()
        bindSessionResetsWatchAndAllowsManualUnlock()
        sessionOnShowResetsEnteredAndRejectsStaleGeneration()
        return "PASS dsp-bridge policy/session/handoff"
    }

    fun disabledRejectsWake() {
        val (coordinator, _) = readyCoordinator()
        val ports = RecordingDspPorts()
        val outcome =
            coordinator.handleWake(validWake(), enabledInput(userEnabled = false), ports)
        assertRejected(outcome, DspRejectReason.USER_DISABLED)
        require(ports.calls.isEmpty()) { "disabled wake must not call ports ${ports.calls}" }
    }

    fun storeRejectsWake() {
        val (coordinator, _) = readyCoordinator()
        val ports = RecordingDspPorts()
        val outcome =
            coordinator.handleWake(validWake(), enabledInput(enhancedDevice = false), ports)
        assertRejected(outcome, DspRejectReason.STORE_PROFILE)
        require(ports.calls.isEmpty()) { "store wake must not call ports ${ports.calls}" }
    }

    fun notReadyRejectsWake() {
        val machine = DspSessionMachine()
        machine.setUserEnabled(true)
        val coordinator = DspHandoffCoordinator(machine)
        val ports = RecordingDspPorts()
        val outcome = coordinator.handleWake(validWake(), enabledInput(visReady = true), ports)
        assertRejected(outcome, DspRejectReason.VIS_NOT_READY)
        require(ports.calls.isEmpty()) { "not-ready wake must not call ports ${ports.calls}" }
    }

    fun badSchemaRejected() {
        val (coordinator, _) = readyCoordinator()
        val ports = RecordingDspPorts()
        assertRejected(
            coordinator.handleWake(validWake().copy(schemaVersion = "v0"), enabledInput(), ports),
            DspRejectReason.SCHEMA
        )
        assertRejected(
            coordinator.handleWake(
                validWake().copy(keys = DspContract.WAKE_EXTRA_KEYS + "pcm"),
                enabledInput(),
                ports
            ),
            DspRejectReason.SCHEMA
        )
        assertRejected(
            coordinator.handleWake(validWake().copy(captureAvailable = true), enabledInput(), ports),
            DspRejectReason.SCHEMA
        )
        assertRejected(
            coordinator.handleWake(validWake().copy(keywordId = "nihaoxiaobu"), enabledInput(), ports),
            DspRejectReason.SCHEMA
        )
        assertRejected(
            coordinator.handleWake(validWake().copy(confidence = 101), enabledInput(), ports),
            DspRejectReason.SCHEMA
        )
        require(ports.calls.isEmpty()) { "invalid schema must not call ports ${ports.calls}" }
    }

    fun duplicateRejected() {
        val (coordinator, _) = readyCoordinator()
        val ports = RecordingDspPorts()
        val first = coordinator.handleWake(validWake("event-id-01"), enabledInput(), ports)
        require(first is HandoffOutcome.Started) { "first wake should start" }
        val secondPorts = RecordingDspPorts()
        val second = coordinator.handleWake(validWake("event-id-01"), enabledInput(), secondPorts)
        assertRejected(second, DspRejectReason.DUPLICATE)
        require(secondPorts.calls.isEmpty()) { "duplicate must not call ports ${secondPorts.calls}" }
    }

    fun busyRejected() {
        val (coordinator, _) = readyCoordinator()
        val ports = RecordingDspPorts()
        require(
            coordinator.handleWake(validWake("event-id-aa"), enabledInput(), ports) is HandoffOutcome.Started
        )
        val busyPorts = RecordingDspPorts()
        val busy = coordinator.handleWake(validWake("event-id-bb"), enabledInput(), busyPorts)
        assertRejected(busy, DspRejectReason.BUSY)
        require(busyPorts.calls.isEmpty()) { "busy must not call ports ${busyPorts.calls}" }
    }

    fun detachFailureDoesNotShow() {
        val (coordinator, _) = readyCoordinator()
        val ports =
            RecordingDspPorts(disarmResult = DspProviderResult(ok = true, state = "ACTIVE(handle=1)"))
        val outcome = coordinator.handleWake(validWake(), enabledInput(), ports)
        assertRejected(outcome, DspRejectReason.DETACH_FAILED)
        requireEquals(listOf("disarm"), ports.calls)
        require(!ports.calls.contains("show")) { "DETACHED failure must not show" }
        require(!ports.calls.contains("arm")) { "DETACHED failure must not arm" }
    }

    fun wakeCallOrderDisarmVerifiedShow() {
        val (coordinator, machine) = readyCoordinator()
        val ports = RecordingDspPorts()
        val outcome = coordinator.handleWake(validWake(), enabledInput(), ports)
        require(outcome is HandoffOutcome.Started) { "valid wake should start" }
        requireEquals(listOf("disarm", "verified", "show"), ports.calls)
        require(machine.inSession) { "session should be active" }
        requireEquals(DspReportedState.DISARMED, machine.reported)
    }

    fun staleGenerationCleanupDoesNotArm() {
        val (coordinator, machine) = readyCoordinator()
        val ports = RecordingDspPorts()
        coordinator.handleWake(validWake("event-stale-1"), enabledInput(), ports)
        val firstGeneration = machine.generation
        coordinator.requestSessionExit()
        coordinator.onAudioSettled(firstGeneration, enabledInput(), RecordingDspPorts())
        coordinator.handleWake(validWake("event-stale-2"), enabledInput(), RecordingDspPorts())
        val settlePorts = RecordingDspPorts()
        val decision = coordinator.onAudioSettled(firstGeneration, enabledInput(), settlePorts)
        requireEquals(DspCleanupDecision.Stale, decision)
        require(!settlePorts.calls.contains("arm")) { "stale cleanup must not arm" }
        require(machine.inSession) { "current session must remain" }
    }

    fun disableThenCleanupDoesNotArm() {
        val (coordinator, machine) = readyCoordinator()
        val ports = RecordingDspPorts()
        coordinator.handleWake(validWake("event-disable-1"), enabledInput(), ports)
        val generation = machine.generation
        coordinator.requestSessionExit()
        coordinator.applyUserEnabled(false, enabledInput(userEnabled = false), RecordingDspPorts())
        val settlePorts = RecordingDspPorts()
        val decision = coordinator.onAudioSettled(generation, enabledInput(userEnabled = false), settlePorts)
        requireEquals(DspCleanupDecision.NoArm, decision)
        require(!settlePorts.calls.contains("arm")) { "disable then cleanup must not arm" }
    }

    fun armCommandDoesNotMarkArmedUntilActive() {
        val (coordinator, machine) = readyCoordinator()
        val ports = RecordingDspPorts()
        coordinator.applyUserEnabled(true, enabledInput(), ports)
        require(ports.calls.contains("arm")) { "enable should send arm command" }
        requireEquals(DspReportedState.ARM_REQUESTED, machine.reported)
        coordinator.onStatus(true, "ACTIVE(handle=1)")
        requireEquals(DspReportedState.ARMED, machine.reported)
    }

    fun utteranceEndDoesNotExitSession() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-turn-1"), enabledInput(), RecordingDspPorts())
        coordinator.noteUtteranceEnd()
        require(machine.inSession) { "utterance end must keep session" }
        val busy = coordinator.handleWake(validWake("event-turn-2"), enabledInput(), RecordingDspPorts())
        assertRejected(busy, DspRejectReason.BUSY)
    }

    fun lockscreenPolicy() {
        require(DspLockscreenPolicy.mustWaitForUnlock(enhancedDevice = true, keyguardLocked = true))
        require(!DspLockscreenPolicy.mustWaitForUnlock(enhancedDevice = true, keyguardLocked = false))
        require(!DspLockscreenPolicy.mustWaitForUnlock(enhancedDevice = false, keyguardLocked = true))
    }

    fun enablementErrorsAreSpecific() {
        requireEquals(
            DspRejectReason.COMPANION_MISSING,
            DspEnablement.evaluate(enabledInput(companionInstalled = false)).reason
        )
        requireEquals(
            DspRejectReason.COMPANION_STALE,
            DspEnablement.evaluate(enabledInput(companionClientPackage = null)).reason
        )
        requireEquals(
            DspRejectReason.CLIENT_MISMATCH,
            DspEnablement.evaluate(enabledInput(companionClientPackage = "io.github.toolazytoname.xiaohei")).reason
        )
        requireEquals(
            DspRejectReason.SIGNATURE_MISMATCH,
            DspEnablement.evaluate(enabledInput(signaturesMatch = false)).reason
        )
        requireEquals(
            DspRejectReason.PERMISSION_MISSING,
            DspEnablement.evaluate(enabledInput(permissionUsable = false)).reason
        )
        requireEquals(
            DspRejectReason.NOT_SELECTED_ASSISTANT,
            DspEnablement.evaluate(enabledInput(selectedVoiceInteraction = false)).reason
        )
    }

    fun prepareCaptureSkipAndDetachFail() {
        val (coordinator, _) = readyCoordinator()
        val skipPorts = RecordingDspPorts()
        val skipped = coordinator.prepareCapture(enabledInput(userEnabled = false), skipPorts)
        require(skipped is CapturePrep.Skip) { "disabled capture prep should skip" }
        require(skipPorts.calls.isEmpty()) { "skip must not touch DSP ${skipPorts.calls}" }

        val failPorts =
            RecordingDspPorts(disarmResult = DspProviderResult(ok = false, state = DspContract.STATE_DETACHED))
        val failed = coordinator.prepareCapture(enabledInput(), failPorts)
        require(failed is CapturePrep.Fail) { "unverified DETACHED must fail capture prep" }
        requireEquals(listOf("disarm"), failPorts.calls)
        require(!failPorts.calls.contains("show")) { "manual listen must not startActivity/show on fail" }
    }

    fun disableDoesNotMarkDisarmedWhenProviderFails() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.onStatus(true, "ACTIVE(handle=1)")
        requireEquals(DspReportedState.ARMED, machine.reported)
        val ports =
            RecordingDspPorts(disarmResult = DspProviderResult(ok = false, state = DspContract.STATE_DETACHED))
        coordinator.applyUserEnabled(false, enabledInput(userEnabled = false), ports)
        require(!machine.userEnabled) { "user preference must still turn off" }
        requireEquals(DspReportedState.UNKNOWN, machine.reported)
        require(machine.reported != DspReportedState.DISARMED) { "failed disarm must not claim DISARMED" }
        val armPorts = RecordingDspPorts()
        coordinator.onVisReady(enabledInput(userEnabled = false), armPorts)
        require(!armPorts.calls.contains("arm")) { "disabled after failed disarm must not rearm" }
    }

    fun disableDoesNotMarkDisarmedWhenProviderThrows() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.onStatus(true, "ACTIVE(handle=1)")
        val ports = RecordingDspPorts(disarmThrows = true)
        coordinator.applyUserEnabled(false, enabledInput(userEnabled = false), ports)
        require(!machine.userEnabled)
        requireEquals(DspReportedState.UNKNOWN, machine.reported)
        require(machine.reported != DspReportedState.DISARMED)
    }

    fun visShutdownDoesNotMarkDisarmedWhenDisarmFails() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.onStatus(true, "ACTIVE(handle=1)")
        val ports =
            RecordingDspPorts(disarmResult = DspProviderResult(ok = false, state = "ACTIVE(handle=1)"))
        coordinator.onVisShutdown(ports)
        require(!machine.visReady)
        requireEquals(DspReportedState.UNKNOWN, machine.reported)
        require(machine.reported != DspReportedState.DISARMED)
        val armPorts = RecordingDspPorts()
        coordinator.onAudioSettled(machine.generation, enabledInput(), armPorts)
        require(!armPorts.calls.contains("arm")) { "shutdown must not rearm" }
    }

    fun capturedCleanupTokenDoesNotStealNewerSession() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-token-1"), enabledInput(), RecordingDspPorts())
        val token1 = coordinator.requestSessionExit()
        require(token1 > 0L)
        coordinator.onAudioSettled(token1, enabledInput(), RecordingDspPorts())
        coordinator.handleWake(validWake("event-token-2"), enabledInput(), RecordingDspPorts())
        val token2 = coordinator.requestSessionExit()
        require(token2 != token1) { "new session must have its own token" }
        val oldPorts = RecordingDspPorts()
        requireEquals(
            DspCleanupDecision.Stale,
            coordinator.onAudioSettled(token1, enabledInput(), oldPorts)
        )
        require(!oldPorts.calls.contains("arm")) { "old cleanup token must not arm" }
        val newPorts = RecordingDspPorts()
        requireEquals(
            DspCleanupDecision.Arm,
            coordinator.onAudioSettled(token2, enabledInput(), newPorts)
        )
        require(newPorts.calls.contains("arm")) { "current cleanup token may arm" }
        requireEquals(token2, machine.generation)
    }

    fun delayedShowAfterTimeoutDoesNotStart() {
        val gate = DspGenerationGate()
        val requestId = gate.issue()
        var started = false
        val late = Runnable {
            if (gate.isLive(requestId)) {
                started = true
            }
        }
        gate.invalidate(requestId)
        late.run()
        require(!started) { "late show after timeout must not start" }

        val (coordinator, machine) = readyCoordinator()
        val ports = RecordingDspPorts(showResult = false)
        val outcome = coordinator.handleWake(validWake("event-show-timeout"), enabledInput(), ports)
        assertRejected(outcome, DspRejectReason.SHOW_FAILED)
        require(!machine.inSession)
        require(!machine.handoffInFlight)
        require(!coordinator.canShowPostedSession(coordinator.peekNextGeneration())) {
            "aborted handoff must reject a late posted show"
        }
        require(!coordinator.isSessionLive(1L))
    }

    fun lockscreenCancelDoesNotEnterOrArm() {
        val watch = DspUnlockWatchState()
        watch.bindSession(3L)
        require(watch.startWatch()) { "first watch should register" }
        require(!watch.startWatch()) { "duplicate watch must not register again" }
        val gen = watch.watchGeneration
        require(watch.canEnterAfterUnlock(gen))
        watch.invalidate()
        require(!watch.canEnterAfterUnlock(gen)) { "cancel must invalidate unlock enter" }
        require(!watch.canImmediateEnter()) { "cancel must block immediate enter" }
        require(!watch.startWatch()) { "cancelled wait must not register again" }

        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-lock-cancel"), enabledInput(), RecordingDspPorts())
        val token = machine.generation
        require(coordinator.abandonSession(token))
        require(!machine.inSession)
        require(!machine.handoffInFlight)
        val armPorts = RecordingDspPorts()
        val decision = coordinator.onAudioSettled(token, enabledInput(), armPorts)
        require(decision == DspCleanupDecision.Ignored || decision == DspCleanupDecision.Stale)
        require(!armPorts.calls.contains("arm")) { "lockscreen cancel must not arm" }
        require(!coordinator.isSessionLive(token))
    }

    fun cancelRecognitionFailureDoesNotSettle() {
        require(!DspAudioSettlePolicy.maySettle(asrCancelSucceeded = false, ttsStopSucceeded = true))
        require(!DspAudioSettlePolicy.maySettle(asrCancelSucceeded = true, ttsStopSucceeded = false))
        require(DspAudioSettlePolicy.maySettle(asrCancelSucceeded = true, ttsStopSucceeded = true))
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-asr-fail"), enabledInput(), RecordingDspPorts())
        val token = coordinator.requestSessionExit()
        require(machine.awaitingCleanup)
        val decision = coordinator.onAudioSettleFailed(token)
        requireEquals(DspCleanupDecision.Failed, decision)
        require(machine.awaitingCleanup) { "failed cleanup must keep ownership busy" }
        require(machine.audioOwnershipUnknown)
        requireEquals(DspReportedState.UNKNOWN, machine.reported)
        val armPorts = RecordingDspPorts()
        coordinator.applyUserEnabled(true, enabledInput(), armPorts)
        require(!armPorts.calls.contains("arm")) { "failed ASR/TTS stop must not arm via toggle" }
        val readyPorts = RecordingDspPorts()
        coordinator.onVisReady(enabledInput(), readyPorts)
        require(!readyPorts.calls.contains("arm")) { "failed ASR/TTS stop must not arm via ready" }
    }

    fun rearmReevaluatesEnablementNotJustVisReady() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-rearm-gate"), enabledInput(), RecordingDspPorts())
        val token = coordinator.requestSessionExit()
        val staleAssistant = enabledInput(selectedVoiceInteraction = false)
        val ports = RecordingDspPorts()
        requireEquals(DspCleanupDecision.NoArm, coordinator.onAudioSettled(token, staleAssistant, ports))
        require(!ports.calls.contains("arm")) { "rearm must re-check selected assistant" }
        require(machine.visReady) { "visReady alone is not enough to arm" }

        val (coordinator2, _) = readyCoordinator()
        coordinator2.handleWake(validWake("event-rearm-sig"), enabledInput(), RecordingDspPorts())
        val token2 = coordinator2.requestSessionExit()
        val sigPorts = RecordingDspPorts()
        requireEquals(
            DspCleanupDecision.NoArm,
            coordinator2.onAudioSettled(token2, enabledInput(signaturesMatch = false), sigPorts)
        )
        require(!sigPorts.calls.contains("arm"))
    }

    fun mainThreadEntryReturnsBeforeSlowDisarm() {
        val tasks = ArrayDeque<Runnable>()
        val queue = DspSerialWorkQueue { runnable -> tasks.addLast(runnable) }
        val (coordinator, _) = readyCoordinator()
        val slow = RecordingDspPorts(disarmDelayMs = 200L)
        var done = false
        val started = System.nanoTime()
        queue.submit { live ->
            if (live()) {
                coordinator.applyUserEnabled(false, enabledInput(userEnabled = false), slow)
            }
            done = true
        }
        val callerMs = (System.nanoTime() - started) / 1_000_000L
        require(callerMs < 80L) { "main-thread entry blocked ${callerMs}ms on slow disarm" }
        require(!done) { "slow disarm must not run inline on caller thread" }
        require(slow.calls.isEmpty()) { "queued disarm must not start before worker runs" }
        tasks.removeFirst().run()
        require(done)
        require(slow.calls.contains("disarm"))
    }

    fun cancelledQueuedWorkDoesNotRun() {
        val tasks = ArrayDeque<Runnable>()
        val queue = DspSerialWorkQueue { runnable -> tasks.addLast(runnable) }
        val (coordinator, machine) = readyCoordinator()
        coordinator.onStatus(true, "ACTIVE(handle=1)")
        val ports = RecordingDspPorts()
        var ran = false
        val id =
            queue.submit { live ->
                if (live()) {
                    ran = true
                    coordinator.applyUserEnabled(false, enabledInput(userEnabled = false), ports)
                }
            }
        queue.cancel(id)
        tasks.removeFirst().run()
        require(!ran) { "cancelled queued task must not execute" }
        require(ports.calls.isEmpty())
        requireEquals(DspReportedState.ARMED, machine.reported)
    }

    fun abandonDoesNotTouchOtherSession() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-keep"), enabledInput(), RecordingDspPorts())
        val live = machine.generation
        require(!coordinator.abandonSession(live + 9))
        require(machine.inSession) { "foreign token must not exit the live session" }
        require(coordinator.isSessionLive(live))
    }

    fun serialQueueBoundedAndCompletedNotLive() {
        val queue = DspSerialWorkQueue { it.run() }
        repeat(80) {
            val id = queue.submit { live -> require(live()) }
            require(!queue.isLive(id)) { "completed task must not be live id=$id" }
        }
        requireEquals(0, queue.trackedCount())
        requireEquals(0, queue.cancelledCount())

        val entered = CountDownLatch(1)
        val hold = CountDownLatch(1)
        val sawCancelWhileRunning = AtomicBoolean(false)
        val async = DspSerialWorkQueue { runnable -> Thread(runnable, "dsp-q-test").start() }
        val runningId =
            async.submit { live ->
                entered.countDown()
                require(hold.await(5, TimeUnit.SECONDS))
                sawCancelWhileRunning.set(!live())
            }
        require(entered.await(5, TimeUnit.SECONDS))
        require(async.isLive(runningId))
        async.cancel(runningId)
        require(!async.isLive(runningId)) { "cancel of running task must be visible" }
        require(async.trackedCount() == 1) { "running cancelled task stays tracked until finally" }
        require(async.cancelledCount() == 1)
        hold.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (async.trackedCount() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        requireEquals(0, async.trackedCount())
        requireEquals(0, async.cancelledCount())
        require(sawCancelWhileRunning.get()) { "live predicate must be false after cancel" }
        require(!async.isLive(runningId))
    }

    fun serialQueueExecuteThrowReleases() {
        val queue = DspSerialWorkQueue { throw IllegalStateException("executor down") }
        var thrown = false
        try {
            queue.submit { }
        } catch (_: IllegalStateException) {
            thrown = true
        }
        require(thrown)
        requireEquals(0, queue.trackedCount())
        requireEquals(0, queue.cancelledCount())
        require(!queue.isLive(1L))
    }

    fun cancelledPrepareAfterBlockedDisarmDoesNotEnter() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ports = RecordingDspPorts(disarmStarted = started, disarmBlock = release)
        val (coordinator, machine) = readyCoordinator()
        val live = AtomicBoolean(true)
        val result = AtomicReference<CapturePrep?>(null)
        val worker =
            Thread {
                result.set(coordinator.prepareCapture(enabledInput(), ports) { live.get() })
            }
        worker.start()
        require(started.await(5, TimeUnit.SECONDS)) { "disarm must start before cancel" }
        live.set(false)
        require(ports.calls.isEmpty()) { "cancel must not imply IO already finished ${ports.calls}" }
        release.countDown()
        worker.join(5_000)
        require(!worker.isAlive)
        val prep = result.get()
        require(prep is CapturePrep.Fail) { "cancelled prepare must fail, got $prep" }
        requireEquals(DspRejectReason.BUSY, (prep as CapturePrep.Fail).reason)
        require(ports.calls.contains("disarm")) { "underlying IO still ran after cancel ${ports.calls}" }
        require(!machine.inSession) { "stale prepare must not enter session" }
        require(!machine.handoffInFlight)
        val nextPorts = RecordingDspPorts()
        require(coordinator.handleWake(validWake("event-after-cancel"), enabledInput(), nextPorts) is HandoffOutcome.Started)
        require(nextPorts.calls.contains("show"))
    }

    fun immediateDisableFailsPostedShowWithoutIo() {
        val (coordinator, machine) = readyCoordinator()
        val token = machine.markHandoffStart()
        require(token > 0L)
        require(coordinator.canShowPostedSession(token))
        coordinator.markUserEnabledImmediate(false)
        require(!coordinator.canShowPostedSession(token)) { "disable must fail posted show before IO" }
        require(!machine.userEnabled)
        require(machine.handoffInFlight) { "immediate disable does not wait for disarm to drop handoff" }
        require(
            !DspPostedActionPolicy.canShow(
                requestLive = true,
                prefsEnabled = false,
                visIdentityMatches = true,
                sessionShowAllowed = coordinator.canShowPostedSession(token)
            )
        )
        coordinator.markVisNotReadyImmediate()
        require(!coordinator.snapshotVisReady())
        require(!coordinator.canShowPostedSession(token))
    }

    fun oldVisDetachDoesNotClearNewReceiverOrShow() {
        val owner = DspVisReceiverOwner()
        val visA = "vis-a"
        val visB = "vis-b"
        val attachA = owner.attach(visA)
        require(attachA.shouldRegister)
        require(attachA.unregisterPreviousOwner == null)
        val attachB = owner.attach(visB)
        require(attachB.unregisterPreviousOwner == visA) { "new VIS must unregister old owner" }
        require(attachB.shouldRegister)
        requireEquals(visB, owner.currentVis)
        requireEquals(visB, owner.receiverOwner)
        val detachA = owner.detach(visA)
        require(!detachA.unregister) { "old VIS destroy must not unregister new receiver" }
        require(!detachA.clearedCurrentVis) { "old VIS destroy must not clear new vis" }
        requireEquals(visB, owner.currentVis)
        requireEquals(visB, owner.receiverOwner)
        val registry = DspPostedShowRegistry()
        val newShow = registry.issue(9L)
        if (detachA.clearedCurrentVis) {
            registry.invalidateAll()
        }
        require(registry.isLive(newShow)) { "old VIS detach must not invalidate new show" }
        val detachB = owner.detach(visB)
        require(detachB.unregister)
        require(detachB.clearedCurrentVis)
        if (detachB.clearedCurrentVis) {
            registry.invalidateAll()
        }
        require(!registry.isLive(newShow))
    }

    fun failedCleanupHoldsBusyUntilSameTokenSettles() {
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-hold-1"), enabledInput(), RecordingDspPorts())
        val token = coordinator.requestSessionExit()
        requireEquals(DspCleanupDecision.Failed, coordinator.onAudioSettleFailed(token))
        require(machine.isBusy)
        val stalePorts = RecordingDspPorts()
        requireEquals(
            DspCleanupDecision.Stale,
            coordinator.onAudioSettled(token + 7, enabledInput(), stalePorts)
        )
        require(!stalePorts.calls.contains("arm"))
        require(machine.awaitingCleanup)
        val recoverPorts = RecordingDspPorts()
        requireEquals(
            DspCleanupDecision.Arm,
            coordinator.onAudioSettled(token, enabledInput(), recoverPorts)
        )
        require(recoverPorts.calls.contains("arm")) { "same token trusted settle may recover" }
        require(!machine.awaitingCleanup)
        require(!machine.audioOwnershipUnknown)
    }

    fun abandonOldTokenDoesNotInvalidateNewShow() {
        val registry = DspPostedShowRegistry()
        val (coordinator, machine) = readyCoordinator()
        coordinator.handleWake(validWake("event-old-show"), enabledInput(), RecordingDspPorts())
        val oldToken = machine.generation
        val oldShow = registry.issue(oldToken)
        coordinator.requestSessionExit()
        coordinator.onAudioSettled(oldToken, enabledInput(), RecordingDspPorts())
        coordinator.handleWake(validWake("event-new-show"), enabledInput(), RecordingDspPorts())
        val newToken = machine.generation
        val newShow = registry.issue(newToken)
        val abandoned = coordinator.abandonSession(oldToken)
        require(!abandoned) { "old token must not abandon the new session" }
        registry.invalidateGeneration(oldToken)
        require(!registry.isLive(oldShow))
        require(registry.isLive(newShow)) { "abandon old token must not invalidate new show" }
        require(machine.inSession)
        require(coordinator.isSessionLive(newToken))
    }

    fun listenStartCancelledAfterPrepareDoesNotOpenMic() {
        val gate = DspListenStartController()
        val token = gate.beginStart()
        require(gate.stillPending(token))
        var wouldStartMic = false
        gate.cancelPending()
        if (gate.stillPending(token)) {
            wouldStartMic = true
        }
        require(!wouldStartMic) { "stop after prepareCapture must not start mic" }
        val newer = gate.beginStart()
        var recording = true
        if (gate.stillPending(token)) {
            recording = false
        }
        require(recording) { "CancellationException of old start must not clear newer recording" }
        require(gate.stillPending(newer))
        require(!gate.stillPending(token))
    }

    fun bindSessionResetsWatchAndAllowsManualUnlock() {
        val watch = DspUnlockWatchState()
        watch.bindSession(3L)
        require(watch.startWatch())
        val oldGen = watch.watchGeneration
        require(watch.canEnterAfterUnlock(oldGen))
        watch.bindSession(-1L)
        require(!watch.canEnterAfterUnlock(oldGen)) { "old generation must not enter a new session" }
        require(watch.startWatch()) { "manual token -1 must be allowed to watch" }
        val manualGen = watch.watchGeneration
        require(watch.canEnterAfterUnlock(manualGen)) { "manual system assistant token=-1 may continue after unlock" }
        require(!watch.canEnterAfterUnlock(oldGen))
        watch.invalidate()
        require(!watch.canEnterAfterUnlock(manualGen))
        require(!watch.canImmediateEnter())
    }

    fun sessionOnShowResetsEnteredAndRejectsStaleGeneration() {
        val state = DspSessionEnterState()
        require(state.onShow(4L, dspSessionLive = true))
        require(state.unlockWatch.startWatch())
        val oldGen = state.unlockWatch.watchGeneration
        state.markEntered()
        require(state.entered)
        require(state.onShow(-1L, dspSessionLive = false)) { "manual token=-1 continues even if dsp live is false" }
        require(!state.entered) { "onShow must reset entered" }
        require(!state.unlockWatch.canEnterAfterUnlock(oldGen)) { "onShow must clear old watch generation" }
        require(state.unlockWatch.startWatch())
        val manualGen = state.unlockWatch.watchGeneration
        require(state.unlockWatch.canEnterAfterUnlock(manualGen))
        require(!state.onShow(8L, dspSessionLive = false)) { "stale DSP generation cannot start" }
    }

    private fun assertRejected(outcome: HandoffOutcome, reason: DspRejectReason) {
        require(outcome is HandoffOutcome.Rejected) { "expected rejected, got $outcome" }
        requireEquals(reason, outcome.reason)
    }

    private fun requireEquals(expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("expected=$expected actual=$actual")
        }
    }
}
