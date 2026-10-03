package com.ai.assistance.operit.ui.floating.voice

/**
 * Strict stop-command matching and barge-in consume gating for fullscreen wave voice.
 *
 * Echo / TTS self-trigger is a device acoustic problem. This policy only accepts the whole
 * normalized utterance as a short command; it does not substring-match 停. Interrupt requires a
 * Sherpa *final* exact match so a non-final prefix of a longer sentence (停一下…) is not a command.
 * System AEC is optional and not claimed to work on every device. Do not treat unit tests as
 * barge-in passing.
 *
 * Sherpa events used here (see SherpaSpeechProvider): non-blank partial (isFinal=false), non-blank
 * endpoint final (isFinal=true, then recognizer.reset with no extra empty emit), and empty
 * isFinal=false from startRecognition / cancelRecognition. Do not invent other events.
 */
object VoiceBargeInPolicy {
    val STOP_COMMANDS: Set<String> =
        setOf(
            "停一下",
            "停下",
            "停止",
            "stop",
            "stopit",
        )

    private val STRIP =
        Regex("[\\s\\p{Punct}，。！？；：、“”‘’【】（）()\\[\\]{}<>《》·…—～~、]+")

    fun normalize(text: String): String = STRIP.replace(text.lowercase(), "")

    fun matchesStopCommand(text: String): Boolean {
        val normalized = normalize(text)
        return normalized.isNotEmpty() && normalized in STOP_COMMANDS
    }

    fun shouldDropByCaptureSuppress(
        nowMs: Long,
        suppressUntilMs: Long,
        bargeInStopListen: Boolean,
    ): Boolean {
        if (bargeInStopListen) return false
        return nowMs < suppressUntilMs
    }
}

enum class VoiceBargeInConsumeMode {
    CONVERSATION,
    BARGE_IN_STOP,
}

data class VoiceBargeInDecision(
    val sendToConversation: Boolean,
    val enableAutoSendSilence: Boolean,
    val interruptAiTurn: Boolean,
    val dropCurrentUtteranceAfterInterrupt: Boolean,
    val ignoredAsLateOrResidue: Boolean,
)

class VoiceBargeInGate {
    var consumeMode: VoiceBargeInConsumeMode = VoiceBargeInConsumeMode.CONVERSATION
        private set
    var dropUntilUtteranceEnd: Boolean = false
        private set
    var suppressDuplicateStop: Boolean = false
        private set
    var epoch: Int = 0
        private set
    /** Non-blank Sherpa text seen since the last final or empty reset. */
    var inFlightUtterance: Boolean = false
        private set

    private var inFlightText: String = ""

    val isBargeInStopListen: Boolean
        get() = consumeMode == VoiceBargeInConsumeMode.BARGE_IN_STOP

    fun enterBargeInStopListen() {
        consumeMode = VoiceBargeInConsumeMode.BARGE_IN_STOP
        dropUntilUtteranceEnd = false
        suppressDuplicateStop = false
        epoch += 1
    }

    fun resumeConversationListen(
        dropCurrentUtterance: Boolean,
        suppressDuplicateStop: Boolean = false,
    ) {
        consumeMode = VoiceBargeInConsumeMode.CONVERSATION
        dropUntilUtteranceEnd = inFlightUtterance || dropCurrentUtterance
        this.suppressDuplicateStop = suppressDuplicateStop
        epoch += 1
    }

    fun reset() {
        consumeMode = VoiceBargeInConsumeMode.CONVERSATION
        dropUntilUtteranceEnd = false
        suppressDuplicateStop = false
        closeUtterance()
        epoch += 1
    }

    fun onRecognition(
        text: String,
        isFinal: Boolean,
        waveAutoSendSilence: Boolean,
        resultEpoch: Int = epoch,
    ): VoiceBargeInDecision {
        val blank = text.isBlank()

        if (resultEpoch != epoch) {
            applyStaleResultBoundary(text, isFinal, blank)
            return ignored()
        }

        if (dropUntilUtteranceEnd) {
            val isolated = isolateInFlightResult(text, isFinal, blank)
            if (isolated != null) {
                return isolated
            }
        }

        if (blank) {
            closeUtterance()
            suppressDuplicateStop = false
            return ignored()
        }

        if (consumeMode == VoiceBargeInConsumeMode.CONVERSATION && suppressDuplicateStop) {
            if (VoiceBargeInPolicy.matchesStopCommand(text)) {
                if (isFinal) {
                    suppressDuplicateStop = false
                    closeUtterance()
                } else {
                    markOpenUtterance(text)
                }
                return ignored()
            }
            suppressDuplicateStop = false
        }

        if (consumeMode == VoiceBargeInConsumeMode.BARGE_IN_STOP) {
            if (isFinal) {
                closeUtterance()
                if (VoiceBargeInPolicy.matchesStopCommand(text)) {
                    return VoiceBargeInDecision(
                        sendToConversation = false,
                        enableAutoSendSilence = false,
                        interruptAiTurn = true,
                        dropCurrentUtteranceAfterInterrupt = false,
                        ignoredAsLateOrResidue = false,
                    )
                }
                return ignored()
            }
            markOpenUtterance(text)
            return ignored()
        }

        if (isFinal) {
            closeUtterance()
        } else {
            markOpenUtterance(text)
        }
        return VoiceBargeInDecision(
            sendToConversation = true,
            enableAutoSendSilence = waveAutoSendSilence,
            interruptAiTurn = false,
            dropCurrentUtteranceAfterInterrupt = false,
            ignoredAsLateOrResidue = false,
        )
    }

    private fun applyStaleResultBoundary(text: String, isFinal: Boolean, blank: Boolean) {
        if (blank || isFinal) {
            closeUtterance()
            dropUntilUtteranceEnd = false
            return
        }
        markOpenUtterance(text)
        dropUntilUtteranceEnd = true
    }

    /**
     * Keep isolating the busy-started hypothesis until a real Sherpa boundary.
     * Returns a drop decision, or null to process [text] as a new utterance.
     */
    private fun isolateInFlightResult(
        text: String,
        isFinal: Boolean,
        blank: Boolean,
    ): VoiceBargeInDecision? {
        if (blank) {
            closeUtterance()
            dropUntilUtteranceEnd = false
            suppressDuplicateStop = false
            return ignored()
        }
        // ASR can rewrite every word of a partial hypothesis without ending the utterance.
        // Only a final/reset event is a boundary; never infer it from text similarity.
        if (isFinal) {
            closeUtterance()
            dropUntilUtteranceEnd = false
        } else {
            markOpenUtterance(text)
        }
        return ignored()
    }

    private fun markOpenUtterance(text: String) {
        inFlightUtterance = true
        inFlightText = text
    }

    private fun closeUtterance() {
        inFlightUtterance = false
        inFlightText = ""
    }

    private fun ignored(): VoiceBargeInDecision =
        VoiceBargeInDecision(
            sendToConversation = false,
            enableAutoSendSilence = false,
            interruptAiTurn = false,
            dropCurrentUtteranceAfterInterrupt = false,
            ignoredAsLateOrResidue = true,
        )
}

data class VoiceWavePreparePlan(
    val pausedForAi: Boolean,
    val shouldResumeAfterAiTurn: Boolean,
    val cancelResumeJob: Boolean,
    val enterBargeIn: Boolean,
    val keepExistingCapture: Boolean,
    val startCaptureWithoutCancelingAi: Boolean,
    val stopCapture: Boolean = false,
)

data class VoiceWaveInterruptPlan(
    val cancelAiTurn: Boolean,
    val cancelTtsJob: Boolean,
    val cancelAiStreamJob: Boolean,
    val cancelResumeJob: Boolean,
    val resumeConversation: Boolean,
    val dropCurrentUtterance: Boolean,
    val startCaptureIfIdle: Boolean,
)

data class VoiceWaveResumeAfterAiPlan(
    val skipBecauseInterruptedOrInactive: Boolean,
    val clearPaused: Boolean,
    val clearShouldResume: Boolean,
    val resumeConversation: Boolean,
    val startCaptureIfIdle: Boolean,
)

object VoiceWaveCapturePlanner {
    fun prepareForAiTurn(
        waveActive: Boolean,
        isRecording: Boolean,
        isProcessingSpeech: Boolean,
    ): VoiceWavePreparePlan {
        if (!waveActive) {
            return VoiceWavePreparePlan(
                pausedForAi = false,
                shouldResumeAfterAiTurn = false,
                cancelResumeJob = false,
                enterBargeIn = false,
                keepExistingCapture = true,
                startCaptureWithoutCancelingAi = false,
                stopCapture = false,
            )
        }
        return VoiceWavePreparePlan(
            pausedForAi = true,
            shouldResumeAfterAiTurn = true,
            cancelResumeJob = true,
            enterBargeIn = true,
            keepExistingCapture = isRecording,
            startCaptureWithoutCancelingAi = !isRecording && !isProcessingSpeech,
            stopCapture = false,
        )
    }

    fun interrupt(
        shouldResumeAfterAiTurn: Boolean,
        isAiBusy: Boolean,
        isRecording: Boolean,
        isProcessingSpeech: Boolean,
        dropCurrentUtterance: Boolean,
    ): VoiceWaveInterruptPlan {
        return VoiceWaveInterruptPlan(
            cancelAiTurn = shouldResumeAfterAiTurn || isAiBusy,
            cancelTtsJob = true,
            cancelAiStreamJob = true,
            cancelResumeJob = true,
            resumeConversation = true,
            dropCurrentUtterance = dropCurrentUtterance,
            startCaptureIfIdle = !isRecording && !isProcessingSpeech,
        )
    }

    fun resumeAfterAiTurn(
        waveActive: Boolean,
        shouldResumeAfterAiTurn: Boolean,
        isRecording: Boolean,
        isProcessingSpeech: Boolean,
    ): VoiceWaveResumeAfterAiPlan {
        if (!waveActive || !shouldResumeAfterAiTurn) {
            return VoiceWaveResumeAfterAiPlan(
                skipBecauseInterruptedOrInactive = true,
                clearPaused = false,
                clearShouldResume = false,
                resumeConversation = false,
                startCaptureIfIdle = false,
            )
        }
        return VoiceWaveResumeAfterAiPlan(
            skipBecauseInterruptedOrInactive = false,
            clearPaused = true,
            clearShouldResume = true,
            resumeConversation = true,
            startCaptureIfIdle = !isRecording && !isProcessingSpeech,
        )
    }
}
