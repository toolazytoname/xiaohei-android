package com.ai.assistance.operit.core.devicebridge

class DspSessionMachine {
    var generation: Long = 0
        private set
    var visReady: Boolean = false
        private set
    var userEnabled: Boolean = false
        private set
    var inSession: Boolean = false
        private set
    var awaitingCleanup: Boolean = false
        private set
    var handoffInFlight: Boolean = false
        private set
    var reported: DspReportedState = DspReportedState.UNKNOWN
        private set
    var activeHandoffToken: Long = 0L
        private set
    var audioOwnershipUnknown: Boolean = false
        private set

    val isBusy: Boolean
        get() = inSession || awaitingCleanup || handoffInFlight || audioOwnershipUnknown

    fun peekNextGeneration(): Long = if (handoffInFlight) activeHandoffToken else generation + 1

    fun onVisReady() {
        visReady = true
    }

    fun markVisNotReady() {
        visReady = false
    }

    fun onVisShutdown() {
        visReady = false
        val keepUnknown = inSession || awaitingCleanup || audioOwnershipUnknown
        inSession = false
        handoffInFlight = false
        activeHandoffToken = 0L
        if (keepUnknown) {
            // Dropping VIS does not prove ASR/TTS audio was released.
            awaitingCleanup = true
            audioOwnershipUnknown = true
            reported = DspReportedState.UNKNOWN
        } else {
            awaitingCleanup = false
        }
    }

    fun setUserEnabled(enabled: Boolean) {
        userEnabled = enabled
    }

    /** @return handoff token, or 0 if busy */
    fun markHandoffStart(): Long {
        if (isBusy) {
            return 0L
        }
        handoffInFlight = true
        activeHandoffToken = generation + 1
        return activeHandoffToken
    }

    fun handoffMatches(token: Long): Boolean {
        return token > 0L && handoffInFlight && activeHandoffToken == token
    }

    fun abortHandoff() {
        abortOwnHandoff(activeHandoffToken)
    }

    fun abortOwnHandoff(token: Long) {
        if (!handoffMatches(token)) {
            return
        }
        handoffInFlight = false
        generation = token
        activeHandoffToken = 0L
    }

    fun abortHandoffAfterVerifiedDetach() {
        abortHandoffAfterVerifiedDetach(activeHandoffToken)
    }

    fun abortHandoffAfterVerifiedDetach(token: Long) {
        if (!handoffMatches(token)) {
            return
        }
        handoffInFlight = false
        reported = DspReportedState.DISARMED
        generation = token
        activeHandoffToken = 0L
    }

    fun markUnverified() {
        reported = DspReportedState.UNKNOWN
    }

    fun completeHandoffEnterSession(): Boolean {
        return completeHandoffEnterSession(activeHandoffToken)
    }

    fun completeHandoffEnterSession(token: Long): Boolean {
        if (!handoffMatches(token)) {
            return false
        }
        handoffInFlight = false
        generation = token
        activeHandoffToken = 0L
        inSession = true
        awaitingCleanup = false
        audioOwnershipUnknown = false
        reported = DspReportedState.DISARMED
        return true
    }

    fun noteUtteranceEnd() {
        // Multi-turn ASR/TTS inside one session is not a DSP session exit.
    }

    fun requestSessionExit(): Long {
        if (inSession || awaitingCleanup || audioOwnershipUnknown) {
            inSession = false
            awaitingCleanup = true
        }
        return generation
    }

    fun onAudioSettled(cleanupGeneration: Long): DspCleanupDecision {
        if (cleanupGeneration != generation) {
            return DspCleanupDecision.Stale
        }
        if (!awaitingCleanup && !audioOwnershipUnknown) {
            return DspCleanupDecision.Ignored
        }
        awaitingCleanup = false
        audioOwnershipUnknown = false
        if (!userEnabled || !visReady) {
            return DspCleanupDecision.NoArm
        }
        return DspCleanupDecision.Arm
    }

    fun onAudioSettleFailed(cleanupGeneration: Long): DspCleanupDecision {
        if (cleanupGeneration != generation) {
            return DspCleanupDecision.Stale
        }
        if (!awaitingCleanup && !audioOwnershipUnknown) {
            return DspCleanupDecision.Ignored
        }
        awaitingCleanup = true
        audioOwnershipUnknown = true
        reported = DspReportedState.UNKNOWN
        return DspCleanupDecision.Failed
    }

    fun abandonSession(token: Long): Boolean {
        if (token <= 0L) {
            return false
        }
        val matchesSession =
            (inSession || awaitingCleanup || audioOwnershipUnknown) && token == generation
        val matchesHandoff = handoffMatches(token)
        if (!matchesSession && !matchesHandoff) {
            return false
        }
        val keepUnknown = audioOwnershipUnknown && token == generation
        inSession = false
        if (keepUnknown) {
            awaitingCleanup = true
            reported = DspReportedState.UNKNOWN
        } else {
            awaitingCleanup = false
        }
        if (matchesHandoff) {
            abortOwnHandoff(token)
        } else {
            handoffInFlight = false
            activeHandoffToken = 0L
        }
        return true
    }

    fun isSessionLive(token: Long): Boolean {
        if (token <= 0L) {
            return false
        }
        return (inSession && generation == token) || handoffMatches(token)
    }

    fun onArmCommandSent() {
        if (audioOwnershipUnknown || awaitingCleanup || handoffInFlight || inSession) {
            return
        }
        if (reported != DspReportedState.ARMED) {
            reported = DspReportedState.ARM_REQUESTED
        }
    }

    fun onStatus(ok: Boolean, state: String) {
        if (audioOwnershipUnknown || awaitingCleanup) {
            return
        }
        reported =
            when {
                ok && DspContract.isActiveState(state) -> DspReportedState.ARMED
                ok && DspContract.isDetachedState(state) -> DspReportedState.DISARMED
                else -> reported
            }
    }
}
