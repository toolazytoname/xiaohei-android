package com.ai.assistance.operit.core.devicebridge

interface DspCompanionPorts {
    fun disarm(): DspProviderResult

    fun confirmDetached(result: DspProviderResult): Boolean

    fun showSession(): Boolean

    fun arm()

    fun readStatus(): DspProviderResult
}

open class DefaultDspPorts : DspCompanionPorts {
    override fun disarm(): DspProviderResult = DspProviderResult(ok = false, state = "")

    override fun confirmDetached(result: DspProviderResult): Boolean {
        return DspContract.isVerifiedDetached(result)
    }

    override fun showSession(): Boolean = false

    override fun arm() {}

    override fun readStatus(): DspProviderResult = DspProviderResult(ok = false, state = "")
}

class DspHandoffCoordinator(
    private val machine: DspSessionMachine,
    private val wakePolicy: DspWakePolicy = DspWakePolicy()
) {
    private val lock = Any()

    fun handleWake(
        fields: WakeFields,
        enablement: DspEnablementInput,
        ports: DspCompanionPorts,
        isLive: () -> Boolean = { true }
    ): HandoffOutcome {
        val token: Long
        synchronized(lock) {
            if (!isLive()) {
                return HandoffOutcome.Rejected(DspRejectReason.BUSY)
            }
            val gates = DspEnablement.evaluate(enablement.copy(visReady = machine.visReady))
            if (!gates.ok) {
                return HandoffOutcome.Rejected(gates.reason ?: DspRejectReason.STORE_PROFILE)
            }
            val invalid = wakePolicy.validate(fields)
            if (invalid != null) {
                return invalid
            }
            token = machine.markHandoffStart()
            if (token == 0L) {
                return HandoffOutcome.Rejected(DspRejectReason.BUSY)
            }
        }
        var detached = false
        return try {
            val disarmed = ports.disarm()
            synchronized(lock) {
                if (!isLive() || !machine.handoffMatches(token)) {
                    machine.abortOwnHandoff(token)
                    return HandoffOutcome.Rejected(DspRejectReason.BUSY)
                }
                if (!ports.confirmDetached(disarmed)) {
                    machine.abortOwnHandoff(token)
                    return HandoffOutcome.Rejected(DspRejectReason.DETACH_FAILED)
                }
                detached = true
            }
            val shown = ports.showSession()
            synchronized(lock) {
                if (!isLive() || !machine.handoffMatches(token)) {
                    machine.abortOwnHandoff(token)
                    return HandoffOutcome.Rejected(DspRejectReason.SHOW_FAILED)
                }
                if (!shown) {
                    machine.abortHandoffAfterVerifiedDetach(token)
                    return HandoffOutcome.Rejected(DspRejectReason.SHOW_FAILED)
                }
                if (!machine.completeHandoffEnterSession(token)) {
                    return HandoffOutcome.Rejected(DspRejectReason.SHOW_FAILED)
                }
                HandoffOutcome.Started
            }
        } catch (_: Exception) {
            synchronized(lock) {
                if (machine.handoffMatches(token)) {
                    if (detached) {
                        machine.abortHandoffAfterVerifiedDetach(token)
                    } else {
                        machine.abortOwnHandoff(token)
                    }
                }
            }
            HandoffOutcome.Rejected(
                if (detached) DspRejectReason.SHOW_FAILED else DspRejectReason.DETACH_FAILED
            )
        }
    }

    fun prepareCapture(
        enablement: DspEnablementInput,
        ports: DspCompanionPorts,
        isLive: () -> Boolean = { true }
    ): CapturePrep {
        val token: Long
        synchronized(lock) {
            if (!isLive()) {
                return CapturePrep.Fail(DspRejectReason.BUSY)
            }
            if (!enablement.enhancedDevice || !enablement.userEnabled) {
                return CapturePrep.Skip
            }
            val gates = DspEnablement.evaluate(enablement.copy(visReady = machine.visReady))
            if (!gates.ok) {
                return CapturePrep.Fail(gates.reason ?: DspRejectReason.STORE_PROFILE)
            }
            if (machine.inSession) {
                return CapturePrep.Ready
            }
            token = machine.markHandoffStart()
            if (token == 0L) {
                return CapturePrep.Fail(DspRejectReason.BUSY)
            }
        }
        return try {
            val disarmed = ports.disarm()
            synchronized(lock) {
                if (!isLive() || !machine.handoffMatches(token)) {
                    machine.abortOwnHandoff(token)
                    return CapturePrep.Fail(DspRejectReason.BUSY)
                }
                if (!ports.confirmDetached(disarmed)) {
                    machine.abortOwnHandoff(token)
                    return CapturePrep.Fail(DspRejectReason.DETACH_FAILED)
                }
                if (!machine.completeHandoffEnterSession(token)) {
                    return CapturePrep.Fail(DspRejectReason.BUSY)
                }
                CapturePrep.Ready
            }
        } catch (_: Exception) {
            synchronized(lock) {
                machine.abortOwnHandoff(token)
            }
            CapturePrep.Fail(DspRejectReason.DETACH_FAILED)
        }
    }

    fun markUserEnabledImmediate(enabled: Boolean) {
        synchronized(lock) { machine.setUserEnabled(enabled) }
    }

    fun markVisReadyImmediate() {
        synchronized(lock) { machine.onVisReady() }
    }

    fun markVisNotReadyImmediate() {
        synchronized(lock) { machine.markVisNotReady() }
    }

    fun applyUserEnabled(
        enabled: Boolean,
        enablement: DspEnablementInput,
        ports: DspCompanionPorts
    ): DspEnablementDecision {
        synchronized(lock) {
            machine.setUserEnabled(enabled)
            if (enabled) {
                val gates =
                    DspEnablement.evaluate(
                        enablement.copy(userEnabled = true, visReady = machine.visReady)
                    )
                if (!gates.ok) {
                    return gates
                }
            }
        }
        if (!enabled) {
            val result =
                try {
                    ports.disarm()
                } catch (_: Exception) {
                    DspProviderResult(ok = false, state = "")
                }
            synchronized(lock) {
                if (ports.confirmDetached(result)) {
                    machine.onStatus(ok = true, state = result.state)
                } else {
                    machine.markUnverified()
                }
                return DspEnablementDecision(ok = false, reason = DspRejectReason.USER_DISABLED)
            }
        }
        maybeArm(enablement, ports)
        return DspEnablementDecision(ok = true, reason = null)
    }

    fun onVisReady(enablement: DspEnablementInput, ports: DspCompanionPorts) {
        synchronized(lock) {
            machine.setUserEnabled(enablement.userEnabled)
            machine.onVisReady()
        }
        val gates =
            DspEnablement.evaluate(enablement.copy(visReady = true, userEnabled = machine.userEnabled))
        if (gates.ok) {
            maybeArm(enablement, ports)
        }
    }

    fun onVisShutdown(ports: DspCompanionPorts) {
        synchronized(lock) {
            machine.onVisShutdown()
        }
        val result =
            try {
                ports.disarm()
            } catch (_: Exception) {
                DspProviderResult(ok = false, state = "")
            }
        synchronized(lock) {
            if (ports.confirmDetached(result)) {
                machine.onStatus(ok = true, state = result.state)
            } else {
                machine.markUnverified()
            }
        }
    }

    fun onStatus(ok: Boolean, state: String) {
        synchronized(lock) { machine.onStatus(ok, state) }
    }

    fun requestSessionExit(): Long {
        synchronized(lock) { return machine.requestSessionExit() }
    }

    fun onAudioSettled(
        cleanupGeneration: Long,
        enablement: DspEnablementInput,
        ports: DspCompanionPorts
    ): DspCleanupDecision {
        synchronized(lock) {
            val decision = machine.onAudioSettled(cleanupGeneration)
            if (decision != DspCleanupDecision.Arm) {
                return decision
            }
            val gates =
                DspEnablement.evaluate(
                    enablement.copy(userEnabled = machine.userEnabled, visReady = machine.visReady)
                )
            if (!gates.ok) {
                return DspCleanupDecision.NoArm
            }
        }
        maybeArm(enablement, ports)
        return DspCleanupDecision.Arm
    }

    fun onAudioSettleFailed(cleanupGeneration: Long): DspCleanupDecision {
        synchronized(lock) {
            return machine.onAudioSettleFailed(cleanupGeneration)
        }
    }

    fun abandonSession(token: Long): Boolean {
        synchronized(lock) {
            return machine.abandonSession(token)
        }
    }

    fun peekNextGeneration(): Long {
        synchronized(lock) {
            return machine.peekNextGeneration()
        }
    }

    fun canShowPostedSession(expectedGeneration: Long): Boolean {
        synchronized(lock) {
            return machine.handoffInFlight &&
                machine.userEnabled &&
                machine.visReady &&
                machine.handoffMatches(expectedGeneration)
        }
    }

    fun isSessionLive(token: Long): Boolean {
        synchronized(lock) {
            return machine.isSessionLive(token)
        }
    }

    fun noteUtteranceEnd() {
        synchronized(lock) { machine.noteUtteranceEnd() }
    }

    fun snapshotReported(): DspReportedState {
        synchronized(lock) {
            return machine.reported
        }
    }

    fun snapshotGeneration(): Long {
        synchronized(lock) {
            return machine.generation
        }
    }

    fun snapshotUserEnabled(): Boolean {
        synchronized(lock) {
            return machine.userEnabled
        }
    }

    fun snapshotVisReady(): Boolean {
        synchronized(lock) {
            return machine.visReady
        }
    }

    fun snapshotBusy(): Boolean {
        synchronized(lock) {
            return machine.isBusy
        }
    }

    private fun maybeArm(enablement: DspEnablementInput, ports: DspCompanionPorts) {
        synchronized(lock) {
            if (machine.isBusy) {
                return
            }
            val gates =
                DspEnablement.evaluate(
                    enablement.copy(userEnabled = machine.userEnabled, visReady = machine.visReady)
                )
            if (!gates.ok) {
                return
            }
        }
        ports.arm()
        val status =
            try {
                ports.readStatus()
            } catch (_: Exception) {
                DspProviderResult(ok = false, state = "")
            }
        synchronized(lock) {
            if (machine.isBusy || !machine.userEnabled || !machine.visReady) {
                return
            }
            val gates =
                DspEnablement.evaluate(
                    enablement.copy(userEnabled = machine.userEnabled, visReady = machine.visReady)
                )
            if (!gates.ok) {
                return
            }
            machine.onArmCommandSent()
            machine.onStatus(status.ok, status.state)
        }
    }
}
