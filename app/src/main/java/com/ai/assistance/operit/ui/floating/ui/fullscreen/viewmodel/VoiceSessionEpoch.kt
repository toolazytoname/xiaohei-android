package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

/**
 * Fullscreen wave session vs speech generations.
 *
 * Stop answering keeps [session] so a delayed listen resume from the same wave may
 * still start capture. It increments [speech] so already-queued TTS segments and a
 * cancelled stream collector cannot speak after interrupt.
 *
 * End / leave wave increments both generations, closes [waveOpen], and records the
 * discarded stream identity. Stale capture and TTS jobs that captured the old
 * numbers must no-op; Compose cannot re-attach that stream to start a new collector.
 */
data class VoiceSessionEpoch(
    val session: Long = 0L,
    val speech: Long = 0L,
    val waveOpen: Boolean = false,
    val discardedStreamIdentity: Int? = null,
) {
    fun enterWave(): VoiceSessionEpoch =
        copy(
            session = session + 1,
            speech = speech + 1,
            waveOpen = true,
            discardedStreamIdentity = null,
        )

    fun stopAnswering(streamIdentity: Int?): VoiceSessionEpoch =
        copy(
            speech = speech + 1,
            discardedStreamIdentity = streamIdentity ?: discardedStreamIdentity,
        )

    fun endSession(streamIdentity: Int?): VoiceSessionEpoch =
        copy(
            session = session + 1,
            speech = speech + 1,
            waveOpen = false,
            discardedStreamIdentity = streamIdentity ?: discardedStreamIdentity,
        )

    fun leaveWave(streamIdentity: Int?): VoiceSessionEpoch {
        if (!waveOpen) {
            return this
        }
        return endSession(streamIdentity)
    }

    fun reset(): VoiceSessionEpoch =
        copy(
            session = session + 1,
            speech = speech + 1,
            waveOpen = false,
            discardedStreamIdentity = null,
        )

    fun invalidateSpeech(): VoiceSessionEpoch = copy(speech = speech + 1)

    fun isCurrentSession(jobSession: Long): Boolean = jobSession == session

    fun allowsCaptureStart(jobSession: Long): Boolean = waveOpen && jobSession == session

    fun allowsQueuedSpeak(jobSpeech: Long): Boolean = jobSpeech == speech

    fun allowsStreamCollect(streamIdentity: Int): Boolean =
        discardedStreamIdentity != streamIdentity
}
