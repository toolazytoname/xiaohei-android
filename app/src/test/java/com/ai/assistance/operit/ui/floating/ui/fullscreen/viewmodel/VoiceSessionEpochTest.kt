package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionEpochTest {

    @Test
    fun stopAnsweringKeepsSessionSoListenResumeStaysCurrent() {
        val open = VoiceSessionEpoch().enterWave()
        val sessionAtListen = open.session
        val speechAtAnswer = open.speech
        val stopped = open.stopAnswering(streamIdentity = 41)

        assertEquals(sessionAtListen, stopped.session)
        assertTrue(stopped.waveOpen)
        assertTrue(stopped.allowsCaptureStart(sessionAtListen))
        assertTrue(stopped.isCurrentSession(sessionAtListen))
        assertFalse(stopped.allowsQueuedSpeak(speechAtAnswer))
        assertTrue(stopped.allowsQueuedSpeak(stopped.speech))
        assertFalse(stopped.allowsStreamCollect(41))
        assertTrue(stopped.allowsStreamCollect(42))
    }

    @Test
    fun endSessionRejectsStaleCaptureAndQueuedSpeak() {
        val open = VoiceSessionEpoch().enterWave()
        val jobSession = open.session
        val jobSpeech = open.speech
        val ended = open.endSession(streamIdentity = 7)

        assertTrue(ended.session > jobSession)
        assertTrue(ended.speech > jobSpeech)
        assertFalse(ended.waveOpen)
        assertFalse(ended.allowsCaptureStart(jobSession))
        assertFalse(ended.allowsCaptureStart(ended.session))
        assertFalse(ended.allowsQueuedSpeak(jobSpeech))
        assertFalse(ended.allowsStreamCollect(7))
    }

    @Test
    fun leaveWaveIsIdempotentAfterEndSoSecondExitDoesNotOpenCapture() {
        val open = VoiceSessionEpoch().enterWave()
        val streamId = 19
        val ended = open.endSession(streamIdentity = streamId)
        val leftAgain = ended.leaveWave(streamIdentity = null)

        assertEquals(ended, leftAgain)
        assertFalse(leftAgain.waveOpen)
        assertFalse(leftAgain.allowsCaptureStart(open.session))
        assertFalse(leftAgain.allowsStreamCollect(streamId))
    }

    @Test
    fun avatarLeaveWaveInvalidatesSameSessionCaptureWithoutKeepingWaveOpen() {
        val open = VoiceSessionEpoch().enterWave()
        val jobSession = open.session
        val left = open.leaveWave(streamIdentity = 3)

        assertFalse(left.waveOpen)
        assertFalse(left.allowsCaptureStart(jobSession))
        assertNotEquals(jobSession, left.session)
        assertFalse(left.allowsStreamCollect(3))
    }

    @Test
    fun newWaveAfterEndAllowsFreshCaptureAndDifferentStream() {
        val ended = VoiceSessionEpoch().enterWave().endSession(streamIdentity = 11)
        val next = ended.enterWave()

        assertTrue(next.waveOpen)
        assertTrue(next.allowsCaptureStart(next.session))
        assertFalse(next.allowsCaptureStart(ended.session))
        assertTrue(next.allowsStreamCollect(11))
        assertTrue(next.allowsQueuedSpeak(next.speech))
        assertFalse(next.allowsQueuedSpeak(ended.speech))
    }

    @Test
    fun resetDropsDiscardedStreamAndClosesWaveForStaleJobs() {
        val open = VoiceSessionEpoch().enterWave().stopAnswering(streamIdentity = 5)
        val jobSession = open.session
        val reset = open.reset()

        assertFalse(reset.waveOpen)
        assertFalse(reset.allowsCaptureStart(jobSession))
        assertTrue(reset.allowsStreamCollect(5))
        assertFalse(reset.allowsQueuedSpeak(open.speech))
    }

    @Test
    fun stopThenEndRejectsTheListenSessionThatStopKept() {
        val open = VoiceSessionEpoch().enterWave()
        val sessionKeptByStop = open.session
        val stopped = open.stopAnswering(streamIdentity = 2)
        assertTrue(stopped.allowsCaptureStart(sessionKeptByStop))
        assertFalse(stopped.allowsQueuedSpeak(open.speech))

        val ended = stopped.endSession(streamIdentity = 2)
        assertFalse(ended.allowsCaptureStart(sessionKeptByStop))
        assertFalse(ended.allowsQueuedSpeak(stopped.speech))
        assertFalse(ended.allowsStreamCollect(2))
    }

    @Test
    fun invalidateSpeechDoesNotBlockSameSessionCapture() {
        val open = VoiceSessionEpoch().enterWave()
        val muted = open.invalidateSpeech()

        assertTrue(muted.allowsCaptureStart(open.session))
        assertEquals(open.session, muted.session)
        assertFalse(muted.allowsQueuedSpeak(open.speech))
        // Collector duplicate/discard is checked before this bump; speech++ must not change stream policy.
        assertTrue(muted.allowsStreamCollect(41))
        assertEquals(open.discardedStreamIdentity, muted.discardedStreamIdentity)
    }

    @Test
    fun invalidateSpeechDoesNotReviveOrDropDiscardedStream() {
        val stopped = VoiceSessionEpoch().enterWave().stopAnswering(streamIdentity = 41)
        val muted = stopped.invalidateSpeech()

        assertFalse(muted.allowsStreamCollect(41))
        assertTrue(muted.allowsStreamCollect(42))
        assertEquals(stopped.discardedStreamIdentity, muted.discardedStreamIdentity)
        assertTrue(muted.waveOpen)
        assertEquals(stopped.session, muted.session)
    }
}
