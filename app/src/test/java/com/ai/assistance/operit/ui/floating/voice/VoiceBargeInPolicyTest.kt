package com.ai.assistance.operit.ui.floating.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBargeInPolicyTest {

    @Test
    fun exactShortStopCommandsMatchAfterNormalize() {
        assertTrue(VoiceBargeInPolicy.matchesStopCommand("停一下"))
        assertTrue(VoiceBargeInPolicy.matchesStopCommand("停下"))
        assertTrue(VoiceBargeInPolicy.matchesStopCommand("停止"))
        assertTrue(VoiceBargeInPolicy.matchesStopCommand("stop"))
        assertTrue(VoiceBargeInPolicy.matchesStopCommand("Stop."))
        assertTrue(VoiceBargeInPolicy.matchesStopCommand(" 停一下。"))
        assertTrue(VoiceBargeInPolicy.matchesStopCommand("stop it"))
    }

    @Test
    fun substringOrPartialStopDoesNotMatch() {
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("停"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("停一"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("请停一下我们继续"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("不要停止"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("停止播放音乐"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("stopping"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand(""))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("你好"))
        assertFalse(VoiceBargeInPolicy.matchesStopCommand("停一下我们再继续"))
    }

    @Test
    fun captureSuppressIgnoredDuringBargeInListen() {
        assertFalse(
            VoiceBargeInPolicy.shouldDropByCaptureSuppress(
                nowMs = 1000,
                suppressUntilMs = 2200,
                bargeInStopListen = true,
            )
        )
        assertTrue(
            VoiceBargeInPolicy.shouldDropByCaptureSuppress(
                nowMs = 1000,
                suppressUntilMs = 2200,
                bargeInStopListen = false,
            )
        )
        assertFalse(
            VoiceBargeInPolicy.shouldDropByCaptureSuppress(
                nowMs = 3000,
                suppressUntilMs = 2200,
                bargeInStopListen = false,
            )
        )
    }
}

class VoiceBargeInGateTest {

    @Test
    fun conversationAllowsAutoSendSilence() {
        val gate = VoiceBargeInGate()
        val decision = gate.onRecognition("今天天气怎么样", isFinal = false, waveAutoSendSilence = true)
        assertTrue(decision.sendToConversation)
        assertTrue(decision.enableAutoSendSilence)
        assertFalse(decision.interruptAiTurn)
    }

    @Test
    fun bargeInDropsNonStopAndDisablesAutoSend() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val echo = gate.onRecognition("请稍等我们继续", isFinal = false, waveAutoSendSilence = true)
        assertFalse(echo.sendToConversation)
        assertFalse(echo.enableAutoSendSilence)
        assertFalse(echo.interruptAiTurn)
        assertTrue(echo.ignoredAsLateOrResidue)
        assertTrue(gate.inFlightUtterance)
    }

    @Test
    fun bargeInExactFinalStopInterruptsAndDoesNotSend() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val decision = gate.onRecognition("停一下", isFinal = true, waveAutoSendSilence = true)
        assertTrue(decision.interruptAiTurn)
        assertFalse(decision.sendToConversation)
        assertFalse(decision.enableAutoSendSilence)
        assertFalse(decision.dropCurrentUtteranceAfterInterrupt)
        assertFalse(gate.inFlightUtterance)
    }

    @Test
    fun bargeInPartialTingDoesNotInterrupt() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val decision = gate.onRecognition("停", isFinal = false, waveAutoSendSilence = true)
        assertFalse(decision.interruptAiTurn)
        assertTrue(decision.ignoredAsLateOrResidue)
    }

    @Test
    fun lateFinalAfterFinalStopIsDroppedThenNextUtteranceCanSend() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val stop = gate.onRecognition("停一下", isFinal = true, waveAutoSendSilence = true)
        assertTrue(stop.interruptAiTurn)
        gate.resumeConversationListen(
            dropCurrentUtterance = stop.dropCurrentUtteranceAfterInterrupt,
            suppressDuplicateStop = true,
        )

        val lateFinal = gate.onRecognition("停一下", isFinal = true, waveAutoSendSilence = true)
        assertTrue(lateFinal.ignoredAsLateOrResidue)
        assertFalse(lateFinal.sendToConversation)
        assertFalse(lateFinal.interruptAiTurn)

        val next = gate.onRecognition("你好", isFinal = true, waveAutoSendSilence = true)
        assertTrue(next.sendToConversation)
        assertTrue(next.enableAutoSendSilence)
        assertFalse(next.interruptAiTurn)
    }

    @Test
    fun noInFlightResumeDoesNotSwallowNihao() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        assertFalse(gate.inFlightUtterance)
        gate.resumeConversationListen(dropCurrentUtterance = false)
        assertFalse(gate.dropUntilUtteranceEnd)

        val next = gate.onRecognition("你好", isFinal = true, waveAutoSendSilence = true)
        assertTrue(next.sendToConversation)
        assertFalse(next.ignoredAsLateOrResidue)
    }

    @Test
    fun finalStopDoesNotDropFollowingNonStopSpeech() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val stop = gate.onRecognition("停止", isFinal = true, waveAutoSendSilence = true)
        assertTrue(stop.interruptAiTurn)
        assertFalse(stop.dropCurrentUtteranceAfterInterrupt)
        gate.resumeConversationListen(
            dropCurrentUtterance = false,
            suppressDuplicateStop = true,
        )

        val duplicate = gate.onRecognition("停止", isFinal = true, waveAutoSendSilence = true)
        assertTrue(duplicate.ignoredAsLateOrResidue)

        val next = gate.onRecognition("继续聊", isFinal = true, waveAutoSendSilence = true)
        assertTrue(next.sendToConversation)
    }

    @Test
    fun busyPartialThenNormalResumeDropsThatUtteranceThenNihaoSends() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val busyPartial = gate.onRecognition("请稍等", isFinal = false, waveAutoSendSilence = true)
        assertFalse(busyPartial.sendToConversation)
        assertFalse(busyPartial.interruptAiTurn)
        assertTrue(gate.inFlightUtterance)

        gate.resumeConversationListen(dropCurrentUtterance = false)
        assertTrue(gate.dropUntilUtteranceEnd)

        val laterPartial = gate.onRecognition("请稍等我们继续", isFinal = false, waveAutoSendSilence = true)
        assertFalse(laterPartial.sendToConversation)
        assertTrue(laterPartial.ignoredAsLateOrResidue)
        assertTrue(gate.dropUntilUtteranceEnd)

        val leftoverFinal = gate.onRecognition("请稍等我们继续", isFinal = true, waveAutoSendSilence = true)
        assertFalse(leftoverFinal.sendToConversation)
        assertFalse(leftoverFinal.interruptAiTurn)
        assertFalse(gate.dropUntilUtteranceEnd)
        assertFalse(gate.inFlightUtterance)

        val nihao = gate.onRecognition("你好", isFinal = true, waveAutoSendSilence = true)
        assertTrue(nihao.sendToConversation)
        assertTrue(nihao.enableAutoSendSilence)
    }

    @Test
    fun stopPartialThenResidueAfterForcedResumeIsNotSent() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val stopPartial = gate.onRecognition("停一下", isFinal = false, waveAutoSendSilence = true)
        assertFalse(stopPartial.interruptAiTurn)
        assertFalse(stopPartial.sendToConversation)
        assertTrue(gate.inFlightUtterance)

        gate.resumeConversationListen(dropCurrentUtterance = true, suppressDuplicateStop = true)
        assertTrue(gate.dropUntilUtteranceEnd)

        val residuePartial = gate.onRecognition("停一下我们再继续", isFinal = false, waveAutoSendSilence = true)
        assertFalse(residuePartial.sendToConversation)
        assertFalse(residuePartial.interruptAiTurn)

        val residueFinal = gate.onRecognition("停一下我们再继续", isFinal = true, waveAutoSendSilence = true)
        assertFalse(residueFinal.sendToConversation)
        assertFalse(residueFinal.interruptAiTurn)
        assertFalse(gate.dropUntilUtteranceEnd)

        val nihao = gate.onRecognition("你好", isFinal = true, waveAutoSendSilence = true)
        assertTrue(nihao.sendToConversation)
    }

    @Test
    fun longSentencePartialStopPrefixDoesNotInterruptWhenFinalIsNotCommand() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val prefix = gate.onRecognition("停一下", isFinal = false, waveAutoSendSilence = true)
        assertFalse(prefix.interruptAiTurn)
        assertFalse(prefix.sendToConversation)

        val grown = gate.onRecognition("停一下我们再继续", isFinal = false, waveAutoSendSilence = true)
        assertFalse(grown.interruptAiTurn)
        assertFalse(grown.sendToConversation)

        val final = gate.onRecognition("停一下我们再继续", isFinal = true, waveAutoSendSilence = true)
        assertFalse(final.interruptAiTurn)
        assertFalse(final.sendToConversation)
        assertFalse(gate.inFlightUtterance)
    }

    @Test
    fun emptyResetAfterBusyFragmentAllowsNihao() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        gate.onRecognition("请稍等", isFinal = false, waveAutoSendSilence = true)
        gate.resumeConversationListen(dropCurrentUtterance = false)
        assertTrue(gate.dropUntilUtteranceEnd)

        val reset = gate.onRecognition("", isFinal = false, waveAutoSendSilence = true)
        assertTrue(reset.ignoredAsLateOrResidue)
        assertFalse(reset.sendToConversation)
        assertFalse(gate.dropUntilUtteranceEnd)
        assertFalse(gate.inFlightUtterance)

        val nihao = gate.onRecognition("你好", isFinal = true, waveAutoSendSilence = true)
        assertTrue(nihao.sendToConversation)
    }

    @Test
    fun staleEpochResultDoesNotSendOrInterrupt() {
        val gate = VoiceBargeInGate()
        gate.enterBargeInStopListen()
        val busyEpoch = gate.epoch
        gate.onRecognition("请稍等", isFinal = false, waveAutoSendSilence = true)
        gate.resumeConversationListen(dropCurrentUtterance = false)
        assertTrue(gate.epoch != busyEpoch)

        val stalePartial =
            gate.onRecognition(
                "请稍等我们继续",
                isFinal = false,
                waveAutoSendSilence = true,
                resultEpoch = busyEpoch,
            )
        assertFalse(stalePartial.sendToConversation)
        assertFalse(stalePartial.interruptAiTurn)
        assertTrue(stalePartial.ignoredAsLateOrResidue)

        val staleStop =
            gate.onRecognition(
                "停一下",
                isFinal = true,
                waveAutoSendSilence = true,
                resultEpoch = busyEpoch,
            )
        assertFalse(staleStop.interruptAiTurn)
        assertFalse(staleStop.sendToConversation)

        val nihao = gate.onRecognition("你好", isFinal = true, waveAutoSendSilence = true)
        assertTrue(nihao.sendToConversation)
    }
}

class VoiceWaveCapturePlannerTest {

    @Test
    fun prepareKeepsExistingCaptureAndNeverStopsMic() {
        val plan =
            VoiceWaveCapturePlanner.prepareForAiTurn(
                waveActive = true,
                isRecording = true,
                isProcessingSpeech = false,
            )
        assertTrue(plan.enterBargeIn)
        assertTrue(plan.keepExistingCapture)
        assertFalse(plan.stopCapture)
        assertFalse(plan.startCaptureWithoutCancelingAi)
        assertTrue(plan.cancelResumeJob)
        assertTrue(plan.pausedForAi)
    }

    @Test
    fun prepareStartsSamePipelineWithoutCancelingAiWhenIdle() {
        val plan =
            VoiceWaveCapturePlanner.prepareForAiTurn(
                waveActive = true,
                isRecording = false,
                isProcessingSpeech = false,
            )
        assertTrue(plan.startCaptureWithoutCancelingAi)
        assertFalse(plan.stopCapture)
        assertTrue(plan.enterBargeIn)
    }

    @Test
    fun prepareIsNoOpOutsideWave() {
        val plan =
            VoiceWaveCapturePlanner.prepareForAiTurn(
                waveActive = false,
                isRecording = true,
                isProcessingSpeech = false,
            )
        assertFalse(plan.enterBargeIn)
        assertFalse(plan.shouldResumeAfterAiTurn)
        assertFalse(plan.stopCapture)
    }

    @Test
    fun avatarAndVoiceStopShareInterruptCancelResumeAndTts() {
        val fromAvatar =
            VoiceWaveCapturePlanner.interrupt(
                shouldResumeAfterAiTurn = true,
                isAiBusy = true,
                isRecording = true,
                isProcessingSpeech = false,
                dropCurrentUtterance = false,
            )
        val fromVoice =
            VoiceWaveCapturePlanner.interrupt(
                shouldResumeAfterAiTurn = true,
                isAiBusy = true,
                isRecording = true,
                isProcessingSpeech = false,
                dropCurrentUtterance = true,
            )
        assertEquals(fromAvatar.cancelAiTurn, fromVoice.cancelAiTurn)
        assertEquals(fromAvatar.cancelTtsJob, fromVoice.cancelTtsJob)
        assertEquals(fromAvatar.cancelAiStreamJob, fromVoice.cancelAiStreamJob)
        assertEquals(fromAvatar.cancelResumeJob, fromVoice.cancelResumeJob)
        assertTrue(fromAvatar.cancelAiTurn)
        assertTrue(fromAvatar.cancelTtsJob)
        assertTrue(fromAvatar.cancelResumeJob)
        assertFalse(fromAvatar.startCaptureIfIdle)
        assertTrue(fromVoice.dropCurrentUtterance)
        assertFalse(fromAvatar.dropCurrentUtterance)
    }

    @Test
    fun interruptCancelsResumeSoFinishedAiTurnDoesNotRestartCapture() {
        var shouldResume = true
        val interrupt =
            VoiceWaveCapturePlanner.interrupt(
                shouldResumeAfterAiTurn = shouldResume,
                isAiBusy = true,
                isRecording = true,
                isProcessingSpeech = false,
                dropCurrentUtterance = true,
            )
        assertTrue(interrupt.cancelResumeJob)
        shouldResume = false

        val resume =
            VoiceWaveCapturePlanner.resumeAfterAiTurn(
                waveActive = true,
                shouldResumeAfterAiTurn = shouldResume,
                isRecording = true,
                isProcessingSpeech = false,
            )
        assertTrue(resume.skipBecauseInterruptedOrInactive)
        assertFalse(resume.startCaptureIfIdle)
        assertFalse(resume.resumeConversation)
    }

    @Test
    fun normalAiFinishKeepsCaptureIfAlreadyRecording() {
        val resume =
            VoiceWaveCapturePlanner.resumeAfterAiTurn(
                waveActive = true,
                shouldResumeAfterAiTurn = true,
                isRecording = true,
                isProcessingSpeech = false,
            )
        assertFalse(resume.skipBecauseInterruptedOrInactive)
        assertTrue(resume.resumeConversation)
        assertFalse(resume.startCaptureIfIdle)
    }
}
