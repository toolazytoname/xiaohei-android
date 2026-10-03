package com.ai.assistance.operit.core.devicebridge

import org.junit.Test

class DspBridgePolicyTest {
    @Test fun disabledRejectsWake() = DspBridgeHostScenarios.disabledRejectsWake()

    @Test fun storeRejectsWake() = DspBridgeHostScenarios.storeRejectsWake()

    @Test fun notReadyRejectsWake() = DspBridgeHostScenarios.notReadyRejectsWake()

    @Test fun badSchemaRejected() = DspBridgeHostScenarios.badSchemaRejected()

    @Test fun duplicateRejected() = DspBridgeHostScenarios.duplicateRejected()

    @Test fun busyRejected() = DspBridgeHostScenarios.busyRejected()

    @Test fun detachFailureDoesNotShow() = DspBridgeHostScenarios.detachFailureDoesNotShow()

    @Test fun wakeCallOrderDisarmVerifiedShow() = DspBridgeHostScenarios.wakeCallOrderDisarmVerifiedShow()

    @Test fun staleGenerationCleanupDoesNotArm() = DspBridgeHostScenarios.staleGenerationCleanupDoesNotArm()

    @Test fun disableThenCleanupDoesNotArm() = DspBridgeHostScenarios.disableThenCleanupDoesNotArm()

    @Test fun armCommandDoesNotMarkArmedUntilActive() =
        DspBridgeHostScenarios.armCommandDoesNotMarkArmedUntilActive()

    @Test fun utteranceEndDoesNotExitSession() = DspBridgeHostScenarios.utteranceEndDoesNotExitSession()

    @Test fun lockscreenPolicy() = DspBridgeHostScenarios.lockscreenPolicy()

    @Test fun enablementErrorsAreSpecific() = DspBridgeHostScenarios.enablementErrorsAreSpecific()

    @Test fun prepareCaptureSkipAndDetachFail() = DspBridgeHostScenarios.prepareCaptureSkipAndDetachFail()

    @Test
    fun disableDoesNotMarkDisarmedWhenProviderFails() =
        DspBridgeHostScenarios.disableDoesNotMarkDisarmedWhenProviderFails()

    @Test
    fun disableDoesNotMarkDisarmedWhenProviderThrows() =
        DspBridgeHostScenarios.disableDoesNotMarkDisarmedWhenProviderThrows()

    @Test
    fun visShutdownDoesNotMarkDisarmedWhenDisarmFails() =
        DspBridgeHostScenarios.visShutdownDoesNotMarkDisarmedWhenDisarmFails()

    @Test
    fun capturedCleanupTokenDoesNotStealNewerSession() =
        DspBridgeHostScenarios.capturedCleanupTokenDoesNotStealNewerSession()

    @Test
    fun delayedShowAfterTimeoutDoesNotStart() = DspBridgeHostScenarios.delayedShowAfterTimeoutDoesNotStart()

    @Test
    fun lockscreenCancelDoesNotEnterOrArm() = DspBridgeHostScenarios.lockscreenCancelDoesNotEnterOrArm()

    @Test
    fun cancelRecognitionFailureDoesNotSettle() =
        DspBridgeHostScenarios.cancelRecognitionFailureDoesNotSettle()

    @Test
    fun rearmReevaluatesEnablementNotJustVisReady() =
        DspBridgeHostScenarios.rearmReevaluatesEnablementNotJustVisReady()

    @Test
    fun mainThreadEntryReturnsBeforeSlowDisarm() =
        DspBridgeHostScenarios.mainThreadEntryReturnsBeforeSlowDisarm()

    @Test
    fun cancelledQueuedWorkDoesNotRun() = DspBridgeHostScenarios.cancelledQueuedWorkDoesNotRun()

    @Test fun abandonDoesNotTouchOtherSession() = DspBridgeHostScenarios.abandonDoesNotTouchOtherSession()
}
