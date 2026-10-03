package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import android.content.Context
import androidx.compose.runtime.*
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.commonbase.CommonBaseUiResiduePolicy
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.ui.fullscreen.XmlTextProcessor
import com.ai.assistance.operit.ui.floating.ui.pet.AvatarEmotionManager
import com.ai.assistance.operit.ui.floating.voice.SpeechInteractionManager
import com.ai.assistance.operit.ui.floating.voice.VoiceBargeInPolicy
import com.ai.assistance.operit.ui.floating.voice.VoiceWaveCapturePlanner
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.TtsSegmenter
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "FloatingFullscreenViewModel"
private const val FULLSCREEN_TTS_CAPTURE_SUPPRESS_MS = 1200L

data class VoiceAvatarMotionRequest(
    val emotion: AvatarEmotion = AvatarEmotion.IDLE,
    val triggerName: String? = null,
    val playOnce: Boolean = false,
    val sequence: Long = 0L
)

class FloatingFullscreenModeViewModel(
    private val context: Context,
    private val floatContext: FloatContext,
    private val coroutineScope: CoroutineScope,
    initialWaveActive: Boolean
) {
    // ===== 状态定义 =====
    var aiMessage by mutableStateOf(context.getString(R.string.floating_hold_microphone_to_speak))
    
    // UI状态
    var isWaveActive by mutableStateOf(initialWaveActive)
    var showBottomControls by mutableStateOf(true)
    var isEditMode by mutableStateOf(false)
    var editableText by mutableStateOf("")
    var inputText by mutableStateOf("")
    var showDragHints by mutableStateOf(false)

    private var attachScreenContentState by mutableStateOf(false)
    private var attachNotificationsState by mutableStateOf(false)
    private var attachLocationState by mutableStateOf(false)
    private var hasOcrSelectionState by mutableStateOf(false)

    var attachScreenContent: Boolean
        get() = attachScreenContentState
        set(value) {
            attachScreenContentState =
                value && CommonBaseUiResiduePolicy.allowsScreenContentAttach()
        }
    var attachNotifications: Boolean
        get() = attachNotificationsState
        set(value) {
            attachNotificationsState =
                value && CommonBaseUiResiduePolicy.allowsNotificationAttach()
        }
    var attachLocation: Boolean
        get() = attachLocationState
        set(value) {
            attachLocationState = value && CommonBaseUiResiduePolicy.allowsLocationAttach()
        }
    var hasOcrSelection: Boolean
        get() = hasOcrSelectionState
        set(value) {
            hasOcrSelectionState = value && CommonBaseUiResiduePolicy.allowsScreenOcr()
        }
    var isStreamingTtsMuted by mutableStateOf(false)
    var voiceAvatarMotionRequest by mutableStateOf(VoiceAvatarMotionRequest())
        private set
    
    val isInitialLoad = mutableStateOf(true)

     private var aiStreamJob: Job? = null
     private var activeAiStreamIdentity: Int? = null
     private var activeAiMessageTimestamp: Long? = null
     private var ttsSpeakJob: Job? = null
     private var interruptResumeJob: Job? = null
     private var voiceSession = VoiceSessionEpoch()
     private var captureSession: Long? = null

    private val wakePrefs by lazy { WakeWordPreferences(context.applicationContext) }
    private var inactivityTimeoutSeconds: Int = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
    private var prefsJob: Job? = null
    private var inactivityJob: Job? = null
    private var lastVoiceActivityAtMs: Long = 0L

    private var wakeEnterJob: Job? = null
    private var resumeVoiceCaptureJob: Job? = null
    private var shouldResumeVoiceCaptureAfterAiTurn: Boolean = false
    private var suppressRecognitionUntilMs: Long = 0L
    private var waveModeAutoTimeoutEnabled: Boolean = false
    var isVoiceCapturePausedForAi by mutableStateOf(false)
    private var voiceAvatarSequence: Long = 0L
    private var lastHandledVoiceAvatarMessageKey: String? = null
    private var hasInitializedVoiceAvatarFromSnapshot: Boolean = false
    
    // ===== 语音交互管理器 =====
    val speechManager = SpeechInteractionManager(
        context = context,
        coroutineScope = coroutineScope,
        onSpeechResult = { text, _ -> 
            // 收到最终语音结果后直接发送，不再写入底部输入框
            val finalText = text.trim()
            val sessionAtResult = captureSession
            if (finalText.isNotEmpty() && sessionAtResult != null) {
                aiMessage = context.getString(R.string.floating_thinking)
                coroutineScope.launch {
                    // 结束语音后仍投递会重新开一轮回答，并让 await 把麦拉起来。
                    if (!voiceSession.isCurrentSession(sessionAtResult) || captureSession != sessionAtResult) {
                        return@launch
                    }
                    startVoiceAvatarThinking()
                    prepareVoiceCaptureForAiTurn()
                    try {
                        maybeAutoAttachByKeyword(finalText)
                    } catch (_: Exception) {
                    }
                    if (!voiceSession.isCurrentSession(sessionAtResult) || captureSession != sessionAtResult) {
                        return@launch
                    }
                    floatContext.onSendMessage?.invoke(finalText, PromptFunctionType.VOICE)
                    awaitAiTurnAndResumeVoiceCapture()
                }
            }
        },
        onStateChange = { msg -> aiMessage = msg }
    ).also { manager ->
        manager.onBargeInStopCommand = { dropCurrentUtterance ->
            interruptAiTurnAndResumeCapture(
                dropCurrentUtterance = dropCurrentUtterance,
                suppressDuplicateStop = true,
            )
        }
    }
    
    // 代理属性，方便 UI 访问
    val isRecording: Boolean get() = speechManager.isRecording
    val isProcessingSpeech: Boolean get() = speechManager.isProcessingSpeech
    val userMessage: String get() = speechManager.userMessage
    val hasFocus: Boolean get() = speechManager.hasFocus
    // UI 用到 volumeFlow 和 recognitionResultFlow
    val speechService get() = speechManager.speechService
    val volumeLevelFlow get() = speechManager.volumeLevelFlow
    val recognitionResultFlow get() = speechManager.recognitionResultFlow

    // ===== 业务逻辑 =====

    fun toggleStreamingTtsMuted() {
        isStreamingTtsMuted = !isStreamingTtsMuted
        if (isStreamingTtsMuted) {
            stopCurrentTtsPlayback()
        }
    }

    private fun stopCurrentTtsPlayback() {
        ttsSpeakJob?.cancel()
        ttsSpeakJob = null
        coroutineScope.launch { speechManager.voiceService.stop() }
    }

    private fun isAiBusyOrSpeaking(): Boolean {
        return isAiBusy() || speechManager.voiceService.isSpeaking
    }

    private fun shouldInterceptCenterAvatarClick(): Boolean {
        return isVoiceCapturePausedForAi || isAiBusyOrSpeaking()
    }

    private fun prepareVoiceCaptureForAiTurn() {
        val plan =
            VoiceWaveCapturePlanner.prepareForAiTurn(
                waveActive = isWaveActive,
                isRecording = speechManager.isRecording,
                isProcessingSpeech = speechManager.isProcessingSpeech,
            )
        if (!plan.enterBargeIn && !plan.shouldResumeAfterAiTurn) {
            return
        }
        shouldResumeVoiceCaptureAfterAiTurn = plan.shouldResumeAfterAiTurn
        isVoiceCapturePausedForAi = plan.pausedForAi
        if (plan.cancelResumeJob) {
            resumeVoiceCaptureJob?.cancel()
        }
        if (plan.enterBargeIn) {
            speechManager.enterBargeInStopListen()
        }
        // Keep the same AudioRecord for stop-command listen. Dual record would contend for the mic.
        if (plan.stopCapture) {
            stopVoiceCapture(true)
        }
        if (plan.startCaptureWithoutCancelingAi) {
            startWaveCaptureKeepingAiTurn(voiceSession.session)
        }
    }

    private fun startWaveCaptureKeepingAiTurn(jobSession: Long) {
        if (!voiceSession.allowsCaptureStart(jobSession)) {
            return
        }
        captureSession = voiceSession.session
        speechManager.startListening { errorMsg ->
            aiMessage = errorMsg
        }
    }

    private fun interruptAiTurnAndResumeCapture(
        dropCurrentUtterance: Boolean = false,
        suppressDuplicateStop: Boolean = false,
    ) {
        val sessionAtInterrupt = voiceSession.session
        val streamId = activeAiStreamIdentity
        // 停止回答：session 不变，listen 可恢复；抬 speech 代，避免已排队 TTS 在 stop 之后再 speak。
        voiceSession = voiceSession.stopAnswering(streamId)
        val plan =
            VoiceWaveCapturePlanner.interrupt(
                shouldResumeAfterAiTurn = shouldResumeVoiceCaptureAfterAiTurn,
                isAiBusy = isAiBusy(),
                isRecording = speechManager.isRecording,
                isProcessingSpeech = speechManager.isProcessingSpeech,
                dropCurrentUtterance = dropCurrentUtterance,
            )
        if (plan.cancelTtsJob) {
            ttsSpeakJob?.cancel()
            ttsSpeakJob = null
        }
        if (plan.cancelAiStreamJob) {
            aiStreamJob?.cancel()
            aiStreamJob = null
            activeAiStreamIdentity = null
        }
        if (plan.cancelResumeJob) {
            cancelPendingVoiceCaptureResume()
        }
        if (plan.resumeConversation) {
            speechManager.resumeConversationListen(
                dropCurrentUtterance = plan.dropCurrentUtterance,
                suppressDuplicateStop = suppressDuplicateStop,
            )
        }
        if (plan.cancelAiTurn) {
            floatContext.onCancelMessage?.invoke()
        }
        interruptResumeJob?.cancel()
        interruptResumeJob = coroutineScope.launch {
            try {
                speechManager.voiceService.stop()
            } catch (_: Exception) {
            }
            // 未跟踪的 launch 若在 end 之后才跑到 startVoiceCapture，会把已结束的麦重新打开。
            if (plan.startCaptureIfIdle &&
                voiceSession.allowsCaptureStart(sessionAtInterrupt) &&
                !speechManager.isRecording &&
                !speechManager.isProcessingSpeech
            ) {
                startWaveCaptureIfCurrent(sessionAtInterrupt)
            }
        }
    }

    private fun awaitAiTurnAndResumeVoiceCapture() {
        if (!isWaveActive || !shouldResumeVoiceCaptureAfterAiTurn) return
        val sessionAtWait = voiceSession.session
        resumeVoiceCaptureJob?.cancel()
        resumeVoiceCaptureJob = coroutineScope.launch {
            delay(120)
            var observedAiBusy = false
            while (
                isActive &&
                isWaveActive &&
                shouldResumeVoiceCaptureAfterAiTurn &&
                voiceSession.isCurrentSession(sessionAtWait)
            ) {
                val busy = isAiBusyOrSpeaking()
                if (busy) {
                    observedAiBusy = true
                }
                if (observedAiBusy && !busy) {
                    val plan =
                        VoiceWaveCapturePlanner.resumeAfterAiTurn(
                            waveActive = isWaveActive,
                            shouldResumeAfterAiTurn = shouldResumeVoiceCaptureAfterAiTurn,
                            isRecording = speechManager.isRecording,
                            isProcessingSpeech = speechManager.isProcessingSpeech,
                        )
                    if (plan.skipBecauseInterruptedOrInactive) {
                        return@launch
                    }
                    if (plan.clearShouldResume) {
                        shouldResumeVoiceCaptureAfterAiTurn = false
                    }
                    if (plan.clearPaused) {
                        isVoiceCapturePausedForAi = false
                    }
                    if (plan.resumeConversation) {
                        speechManager.resumeConversationListen(dropCurrentUtterance = false)
                    }
                    // AI 这一轮结束后，总是从此刻重新开始计算空闲超时。
                    lastVoiceActivityAtMs = System.currentTimeMillis()
                    if (plan.startCaptureIfIdle &&
                        voiceSession.allowsCaptureStart(sessionAtWait) &&
                        !speechManager.isRecording &&
                        !speechManager.isProcessingSpeech
                    ) {
                        startWaveCaptureIfCurrent(sessionAtWait)
                    }
                    return@launch
                }
                delay(120)
            }
        }
    }

    private fun cancelPendingVoiceCaptureResume() {
        shouldResumeVoiceCaptureAfterAiTurn = false
        isVoiceCapturePausedForAi = false
        resumeVoiceCaptureJob?.cancel()
        resumeVoiceCaptureJob = null
        interruptResumeJob?.cancel()
        interruptResumeJob = null
    }

    fun processAndSpeakAiMessage(lastMessage: ChatMessage?, ttsCleanerRegexs: List<String>) {
        val message = lastMessage ?: return

         // If we are switching to a new message, stop any previous stream collector.
         // This avoids duplicate collectors (and duplicated replay) when the upstream SharedStream replays history.
         if (activeAiMessageTimestamp != null && activeAiMessageTimestamp != message.timestamp) {
             aiStreamJob?.cancel()
             aiStreamJob = null
             activeAiStreamIdentity = null
         }
         activeAiMessageTimestamp = message.timestamp
        
        if (isInitialLoad.value) {
            isInitialLoad.value = false
            if (message.sender == "ai") aiMessage = stripVoiceAvatarTags(message.content)
            return
        }
        
        // A replay/recomposition of the same stream must not invalidate its live TTS
        // generation, nor restart a completed collector from its replay buffer.
        val incomingStream = message.contentStream
        if (message.sender == "ai" && incomingStream != null) {
            val identity = System.identityHashCode(incomingStream)
            if (!voiceSession.allowsStreamCollect(identity) || activeAiStreamIdentity == identity) return
        }
        stopCurrentTtsPlayback()
        // 新消息抬 speech 代：旧 join 链在 stop 之后仍会 speak，不能只 cancel 最新 job。
        voiceSession = voiceSession.invalidateSpeech()
        
        when (message.sender) {
            "think" -> {
                aiStreamJob?.cancel()
                aiStreamJob = null
                activeAiStreamIdentity = null
                aiMessage = context.getString(R.string.floating_thinking)
            }
            "ai" -> {
                val stream = message.contentStream
                if (stream != null) {
                    val streamIdentity = System.identityHashCode(stream)
                    // 结束/停止已丢弃的同一条流若再被 compose 拉起来，会重新排队 TTS。
                    if (!voiceSession.allowsStreamCollect(streamIdentity)) {
                        return
                    }
                    if (aiStreamJob?.isActive == true && activeAiStreamIdentity == streamIdentity) {
                        return
                    }
                    aiStreamJob?.cancel()
                    aiStreamJob = null
                    activeAiStreamIdentity = streamIdentity

                    // 不要立即清空，等待流内容到达
                    val speechAtStart = voiceSession.speech
                    aiStreamJob = coroutineScope.launch {
                        handleStreamResponse(stream, cleaners = ttsCleanerRegexs, speechGeneration = speechAtStart)
                    }
                } else {
                    aiStreamJob?.cancel()
                    aiStreamJob = null
                    activeAiStreamIdentity = null
                    handleStaticResponse(message.content)
                }
            }
        }
    }

    private suspend fun handleStreamResponse(
        stream: Stream<String>,
        cleaners: List<String>,
        speechGeneration: Long,
    ) {
        val sb = StringBuilder()
        var isFirstSentence = true
        var isFirstChar = true
        XmlTextProcessor.processStreamToText(stream).collect { char ->
            if (isFirstChar) {
                aiMessage = "" // 收到第一个字符时才清空等待提示
                isFirstChar = false
            }
            aiMessage += char
            sb.append(char)
            
            val cutIdx = TtsSegmenter.nextSegmentEnd(sb)
            if (cutIdx >= 0) {
                val segment = sb.substring(0, cutIdx)
                if (trySpeak(segment, isFirstSentence, cleaners, armMicSuppression = isFirstSentence, speechGeneration = speechGeneration)) {
                    isFirstSentence = false
                    sb.delete(0, cutIdx)
                }
            }
        }
        trySpeak(sb.toString(), isFirstSentence, cleaners, armMicSuppression = isFirstSentence, speechGeneration = speechGeneration)
    }

    private fun handleStaticResponse(content: String) {
        val plainContent = stripVoiceAvatarTags(content)
        aiMessage = plainContent
    }

    private fun trySpeak(
        text: String,
        interrupt: Boolean,
        cleaners: List<String>,
        armMicSuppression: Boolean = false,
        speechGeneration: Long,
    ): Boolean {
        val cleanText = speechManager.cleanTextForTts(text.trim(), cleaners)
        if (cleanText.isNotEmpty()) {
            // 仅在非直接对话模式（底部输入模式）下应用静音
            if (isStreamingTtsMuted && !isWaveActive) {
                return true
            }
            // 消费分段，避免 collector 卡住；过期 speech 代不得再 enqueue。
            if (!voiceSession.allowsQueuedSpeak(speechGeneration)) {
                return true
            }
            if (armMicSuppression && isWaveActive) {
                suppressRecognitionUntilMs = System.currentTimeMillis() + FULLSCREEN_TTS_CAPTURE_SUPPRESS_MS
            }
            enqueueSpeak(cleanText, interrupt, speechGeneration)
            return true
        }
        return false
    }

    private fun enqueueSpeak(text: String, interrupt: Boolean, speechGeneration: Long) {
        val previousJob = if (interrupt) {
            ttsSpeakJob?.cancel()
            null
        } else {
            ttsSpeakJob
        }

        ttsSpeakJob =
            coroutineScope.launch {
                try {
                    previousJob?.join()
                    // join 之后仍可能已经 stop/end；不核对就会在 voiceService.stop 后再 speak。
                    if (!voiceSession.allowsQueuedSpeak(speechGeneration)) {
                        return@launch
                    }
                    speechManager.voiceService.speak(text, interrupt)
                } catch (_: kotlinx.coroutines.CancellationException) {
                } catch (e: Exception) {
                    AppLogger.e(TAG, "TTS playback failed", e)
                }
            }
    }

    // ===== 语音交互 =====

    fun startVoiceCapture() {
        startVoiceCaptureInternal()
    }

    private fun startWaveCaptureIfCurrent(jobSession: Long) {
        if (!voiceSession.allowsCaptureStart(jobSession)) {
            return
        }
        startVoiceCaptureInternal()
    }

    private fun startVoiceCaptureInternal() {
        // 如果AI正在生成，尝试取消
        val lastMessage = floatContext.messages.lastOrNull()
        val isAiWorking = lastMessage?.sender == "think" || 
                          (lastMessage?.sender == "ai" && lastMessage.contentStream != null)
        
        if (isAiWorking) {
            floatContext.onCancelMessage?.invoke()
        }
        
        captureSession = voiceSession.session
        speechManager.startListening { errorMsg ->
            aiMessage = errorMsg
        }
    }

    fun stopVoiceCapture(isCancel: Boolean) {
        if (isCancel) captureSession = null
        speechManager.stopListening(isCancel)
    }

    fun enterWaveMode(
        wakeLaunched: Boolean = false,
        enableAutoTimeout: Boolean = false
    ) {
        wakeEnterJob?.cancel()
        voiceSession = voiceSession.enterWave()
        val sessionAtEnter = voiceSession.session
        wakeEnterJob = coroutineScope.launch {
            // 语音态 UI 先切换出来（唤醒场景更符合预期）
            isWaveActive = true
            waveModeAutoTimeoutEnabled = enableAutoTimeout
            inactivityJob?.cancel()
            inactivityJob = null

            playWakeGreetingIfNeeded(wakeLaunched)

            if (!voiceSession.allowsCaptureStart(sessionAtEnter)) {
                return@launch
            }
            startWaveCaptureIfCurrent(sessionAtEnter)
            if (speechManager.isRecording && waveModeAutoTimeoutEnabled) {
                lastVoiceActivityAtMs = System.currentTimeMillis()
                startInactivityMonitor()
            } else {
                if (!speechManager.isRecording) {
                    voiceSession = voiceSession.leaveWave(activeAiStreamIdentity)
                    isWaveActive = false
                    showBottomControls = true
                }
            }
        }
    }
    
    fun exitWaveMode() {
        captureSession = null
        // 点头像/超时离开也必须关掉 waveOpen，否则 interrupt 的延迟 start 会把麦重新打开。
        voiceSession = voiceSession.leaveWave(activeAiStreamIdentity)
        wakeEnterJob?.cancel()
        wakeEnterJob = null
        cancelPendingVoiceCaptureResume()
        suppressRecognitionUntilMs = 0L
        waveModeAutoTimeoutEnabled = false
        speechManager.resumeConversationListen(dropCurrentUtterance = false)
        stopVoiceCapture(true)
        coroutineScope.launch { speechManager.voiceService.stop() }
        isWaveActive = false
        showBottomControls = true
        inactivityJob?.cancel()
        inactivityJob = null
        resetVoiceAvatarToIdle()
    }

    private suspend fun playWakeGreetingIfNeeded(wakeLaunched: Boolean) {
        if (!wakeLaunched) return

        val enabled = wakePrefs.wakeGreetingEnabledFlow.first()
        if (!enabled) return

        val text =
            wakePrefs.wakeGreetingTextFlow.first().trim().ifBlank {
                WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT
            }
        if (text.isBlank()) return

        // 唤醒问候语与录音并行执行（全双工）
        if (isWaveActive) {
            suppressRecognitionUntilMs = System.currentTimeMillis() + FULLSCREEN_TTS_CAPTURE_SUPPRESS_MS
        }
        speechManager.speak(text, interrupt = true)
    }

    /** A labeled stop must never toggle voice off if the answer just finished. */
    fun stopAnswering() {
        if (!isWaveActive || !shouldInterceptCenterAvatarClick()) return
        val dropCurrent = speechManager.userMessage.isNotBlank()
        interruptAiTurnAndResumeCapture(
            dropCurrentUtterance = dropCurrent,
            suppressDuplicateStop = dropCurrent,
        )
    }

    /** Stop producers too, so a queued stream cannot resume speech after ending voice. */
    fun endVoiceSession() {
        val streamId = activeAiStreamIdentity
        // 先抬代并记下丢弃的流，再 cancel：否则 identity 被清空后 compose 会按同一 timestamp 再挂 collector。
        voiceSession = voiceSession.endSession(streamId)
        floatContext.onCancelMessage?.invoke()
        aiStreamJob?.cancel()
        aiStreamJob = null
        activeAiStreamIdentity = null
        ttsSpeakJob?.cancel()
        ttsSpeakJob = null
        exitWaveMode()
    }

    fun onCenterAvatarClick() {
        if (isWaveActive && shouldInterceptCenterAvatarClick()) {
            val dropCurrent = speechManager.userMessage.isNotBlank()
            interruptAiTurnAndResumeCapture(
                dropCurrentUtterance = dropCurrent,
                suppressDuplicateStop = dropCurrent,
            )
            return
        }

        if (isWaveActive) {
            exitWaveMode()
        } else {
            enterWaveMode(enableAutoTimeout = false)
        }
    }

    fun handleRecognitionResult(resultText: String, isFinal: Boolean) {
        val bargeInStopListen =
            isWaveActive && (speechManager.isBargeInStopListen || isVoiceCapturePausedForAi)
        if (
            VoiceBargeInPolicy.shouldDropByCaptureSuppress(
                nowMs = System.currentTimeMillis(),
                suppressUntilMs = suppressRecognitionUntilMs,
                bargeInStopListen = bargeInStopListen,
            )
        ) {
            return
        }
        if (isWaveActive && resultText.isNotBlank() && !bargeInStopListen) {
            lastVoiceActivityAtMs = System.currentTimeMillis()
        }
        // 波浪连续听写才启用 2s 静默发送；AI/TTS 插话监听必须关闭，否则回声会变成新一轮 onSendMessage
        speechManager.handleRecognitionResult(
            resultText,
            isFinal,
            autoSendSilence = isWaveActive && !bargeInStopListen,
        )
    }

    // ===== 初始化与清理 =====

     suspend fun initialize(autoEnterVoiceChat: Boolean = false, wakeLaunched: Boolean = false) {
         speechManager.initialize()
         voiceSession = voiceSession.reset()
         cancelPendingVoiceCaptureResume()
         prefsJob?.cancel()
         prefsJob = coroutineScope.launch {
             wakePrefs.voiceCallInactivityTimeoutSecondsFlow.collectLatest { seconds ->
                 inactivityTimeoutSeconds = seconds.coerceIn(1, 600)
             }
         }
         isInitialLoad.value = true
         isWaveActive = autoEnterVoiceChat
         showBottomControls = true
         hasInitializedVoiceAvatarFromSnapshot = false
         lastHandledVoiceAvatarMessageKey = null
         resetVoiceAvatarToIdle()
         exitEditMode()

        // 获取焦点
        val view = floatContext.chatService?.getComposeView()
         if (!speechManager.requestFocus(view)) {
             aiMessage = context.getString(R.string.floating_cannot_get_input_service)
         } else {
             aiMessage = context.getString(R.string.floating_hold_microphone_to_speak)
         }

         if (autoEnterVoiceChat) {
             enterWaveMode(wakeLaunched = wakeLaunched, enableAutoTimeout = true)
         }
     }

     fun cleanup() {
        captureSession = null
        val view = floatContext.chatService?.getComposeView()
        speechManager.releaseFocus(view)
        speechManager.cleanup()
        voiceSession = voiceSession.reset()
        ttsSpeakJob?.cancel()
        ttsSpeakJob = null
        interruptResumeJob?.cancel()
        interruptResumeJob = null
        cancelPendingVoiceCaptureResume()

        prefsJob?.cancel()
        prefsJob = null
        inactivityJob?.cancel()
        inactivityJob = null

         aiStreamJob?.cancel()
         aiStreamJob = null
         activeAiStreamIdentity = null

        wakeEnterJob?.cancel()
        wakeEnterJob = null
        hasInitializedVoiceAvatarFromSnapshot = false
        lastHandledVoiceAvatarMessageKey = null
        resetVoiceAvatarToIdle()
    }

    private fun startInactivityMonitor() {
        inactivityJob?.cancel()
        inactivityJob = coroutineScope.launch {
            while (isActive && isWaveActive) {
                val timeoutMs = inactivityTimeoutSeconds.toLong() * 1000L
                val elapsed = System.currentTimeMillis() - lastVoiceActivityAtMs
                val remaining = timeoutMs - elapsed
                if (remaining <= 0L) {
                    // 如果 AI 正在朗读，不要在朗读过程中退出/关闭。
                    // 等朗读结束后再重新评估 remaining。
                    val voiceService = speechManager.voiceService
                    if (voiceService.isSpeaking) {
                        withTimeoutOrNull(20_000L) {
                            voiceService.speakingStateFlow.filter { speaking -> !speaking }.first()
                        }
                        // 朗读结束后重置计时，给用户一个完整的超时窗口
                        lastVoiceActivityAtMs = System.currentTimeMillis()
                        continue
                    }

                    // AI 在工具调用/处理/生成过程中，也不要自动关闭。
                    // 否则会出现“工具调用耗时较长 -> 超时 -> 窗口自动关闭”。
                    if (isAiBusy()) {
                        while (isActive && isWaveActive && isAiBusy()) {
                            delay(250L)
                        }
                        // AI 忙完后重置计时，避免立刻触发关闭
                        lastVoiceActivityAtMs = System.currentTimeMillis()
                        continue
                    }

                    exitWaveMode()
                    if (floatContext.chatService?.isWakeLaunched() == true) {
                        floatContext.onClose()
                    }
                    return@launch
                }
                delay(minOf(remaining, 500L))
            }
        }
    }

    private fun isAiBusy(): Boolean {
        val state = floatContext.inputProcessingState.value
        val stateBusy =
            state !is InputProcessingState.Idle &&
                state !is InputProcessingState.Completed &&
                state !is InputProcessingState.Error

        val lastMessage = floatContext.messages.lastOrNull()
        val streamBusy =
            lastMessage?.sender == "think" ||
                (lastMessage?.sender == "ai" && lastMessage.contentStream != null)

        return stateBusy || streamBusy
    }

    // ===== 编辑模式 =====

    fun enterEditMode(text: String) {
        coroutineScope.launch { speechManager.stopListening(isCancel = true) }
        editableText = text
        isEditMode = true
        aiMessage = context.getString(R.string.floating_edit_your_message)
    }
    
    fun exitEditMode() {
        isEditMode = false
        editableText = ""
        aiMessage = context.getString(R.string.floating_hold_microphone_to_speak)
    }
    
    fun sendEditedMessage() {
        if (editableText.isNotBlank()) {
            startVoiceAvatarThinking()
            prepareVoiceCaptureForAiTurn()
            floatContext.onSendMessage?.invoke(editableText, PromptFunctionType.VOICE)
            awaitAiTurnAndResumeVoiceCapture()
            isEditMode = false
            editableText = ""
            aiMessage = context.getString(R.string.floating_thinking)
        }
    }
    
    fun sendInputMessage() {
        val text = inputText.trim()
        val captureScreen = attachScreenContent && CommonBaseUiResiduePolicy.allowsScreenContentAttach()
        val captureNotifications =
            attachNotifications && CommonBaseUiResiduePolicy.allowsNotificationAttach()
        val captureLocation = attachLocation && CommonBaseUiResiduePolicy.allowsLocationAttach()
        val captureOcr = hasOcrSelection && CommonBaseUiResiduePolicy.allowsScreenOcr()
        if (text.isEmpty() && !captureScreen && !captureNotifications && !captureLocation && !captureOcr) return

        // 立即清理UI状态，不等待协程
        val shouldCaptureScreen = captureScreen
        val shouldCaptureNotifications = captureNotifications
        val shouldCaptureLocation = captureLocation
        
        inputText = ""
        attachScreenContent = false
        attachNotifications = false
        attachLocation = false
        hasOcrSelection = false
        aiMessage = context.getString(R.string.floating_thinking)

        startVoiceAvatarThinking()
        prepareVoiceCaptureForAiTurn()

        coroutineScope.launch {
            try {
                maybeAutoAttachByKeyword(text)
            } catch (_: Exception) {
            }
            try {
                val attachmentDelegate = floatContext.chatService?.getChatCore()?.getAttachmentDelegate()
                if (shouldCaptureScreen) {
                    attachmentDelegate?.captureScreenContent()
                }
                if (shouldCaptureNotifications) {
                    attachmentDelegate?.captureNotifications()
                }
                if (shouldCaptureLocation) {
                    attachmentDelegate?.captureLocation()
                }
                // hasOcrSelection 的附件已经在 FloatingScreenOcrScreen 中添加了
            } catch (_: Exception) {
            }

            floatContext.onSendMessage?.invoke(text, PromptFunctionType.VOICE)
            awaitAiTurnAndResumeVoiceCapture()
        }
    }

    private suspend fun maybeAutoAttachByKeyword(text: String) {
        if (text.isBlank()) return

        wakePrefs.migrateVoiceAutoAttachItemsIfNeeded()

        val enabled = wakePrefs.voiceAutoAttachEnabledFlow.first()
        if (!enabled) return

        val attachmentDelegate = floatContext.chatService?.getChatCore()?.getAttachmentDelegate() ?: return

        val items = wakePrefs.voiceAutoAttachItemsFlow.first()
        items
            .asSequence()
            .filter { it.enabled }
            .forEach { item ->
                val keywordConfig = item.keywords.trim()
                if (keywordConfig.isBlank()) return@forEach
                if (!matchesAnyKeyword(text, keywordConfig)) return@forEach

                when (item.type) {
                    WakeWordPreferences.VoiceAutoAttachType.SCREEN_OCR -> {
                        if (CommonBaseUiResiduePolicy.allowsScreenContentAttach()) {
                            attachmentDelegate.captureScreenContent()
                        }
                    }
                    WakeWordPreferences.VoiceAutoAttachType.NOTIFICATIONS -> {
                        if (CommonBaseUiResiduePolicy.allowsNotificationAttach()) {
                            attachmentDelegate.captureNotifications()
                        }
                    }
                    WakeWordPreferences.VoiceAutoAttachType.LOCATION -> {
                        if (CommonBaseUiResiduePolicy.allowsLocationAttach()) {
                            attachmentDelegate.captureLocation()
                        }
                    }
                    WakeWordPreferences.VoiceAutoAttachType.TIME -> {
                        attachmentDelegate.captureCurrentTime()
                    }
                }
            }
    }

    private fun matchesAnyKeyword(text: String, keywordConfig: String): Boolean {
        val keywords =
            keywordConfig
                .split('|', ',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return false
        return keywords.any { k -> text.contains(k, ignoreCase = true) }
    }

    fun handleVoiceAvatarMessage(message: ChatMessage?) {
        if (!hasInitializedVoiceAvatarFromSnapshot) {
            hasInitializedVoiceAvatarFromSnapshot = true
            if (message?.sender == "ai" && message.contentStream == null) {
                lastHandledVoiceAvatarMessageKey = buildVoiceAvatarMessageKey(message)
                return
            }
        }

        when (message?.sender) {
            "think" -> startVoiceAvatarThinking()
            "ai" -> {
                if (message.contentStream != null) {
                    startVoiceAvatarThinking()
                    return
                }

                val messageKey = buildVoiceAvatarMessageKey(message)
                if (lastHandledVoiceAvatarMessageKey == messageKey) {
                    return
                }
                lastHandledVoiceAvatarMessageKey = messageKey

                val triggerName = AvatarEmotionManager.extractMoodTagValue(message.content)
                if (!triggerName.isNullOrBlank()) {
                    pushVoiceAvatarMotion(
                        emotion = AvatarEmotionManager.analyzeEmotion(message.content),
                        triggerName = triggerName,
                        playOnce = true
                    )
                    return
                }

                val emotion = AvatarEmotionManager.analyzeEmotion(message.content)
                if (emotion == AvatarEmotion.IDLE) {
                    resetVoiceAvatarToIdle()
                } else {
                    pushVoiceAvatarMotion(emotion = emotion, playOnce = true)
                }
            }
        }
    }

    fun syncVoiceAvatarWithProcessingState(
        state: InputProcessingState,
        latestMessage: ChatMessage?
    ) {
        val shouldResetThinking =
            (state is InputProcessingState.Idle || state is InputProcessingState.Error) &&
                voiceAvatarMotionRequest.triggerName.isNullOrBlank() &&
                voiceAvatarMotionRequest.emotion == AvatarEmotion.THINKING
        if (!shouldResetThinking) {
            return
        }

        val hasCompletedAiMessage =
            latestMessage?.sender == "ai" && latestMessage.contentStream == null
        if (!hasCompletedAiMessage) {
            resetVoiceAvatarToIdle()
        }
    }

    private fun buildVoiceAvatarMessageKey(message: ChatMessage): String {
        return "${message.sender}:${message.timestamp}:${message.content.hashCode()}:${message.contentStream == null}"
    }

    private fun pushVoiceAvatarMotion(
        emotion: AvatarEmotion,
        triggerName: String? = null,
        playOnce: Boolean
    ) {
        voiceAvatarSequence += 1
        voiceAvatarMotionRequest = VoiceAvatarMotionRequest(
            emotion = emotion,
            triggerName = triggerName,
            playOnce = playOnce,
            sequence = voiceAvatarSequence
        )
    }

    private fun startVoiceAvatarThinking() {
        pushVoiceAvatarMotion(emotion = AvatarEmotion.THINKING, playOnce = false)
    }

    private fun resetVoiceAvatarToIdle() {
        pushVoiceAvatarMotion(emotion = AvatarEmotion.IDLE, playOnce = false)
    }

    private fun stripVoiceAvatarTags(content: String): String {
        return AvatarEmotionManager.stripXmlLikeTags(content)
    }
}

@Composable
 fun rememberFloatingFullscreenModeViewModel(
     context: Context,
     floatContext: FloatContext,
     coroutineScope: CoroutineScope,
     initialWaveActive: Boolean
 ) = remember(context, initialWaveActive) {
     FloatingFullscreenModeViewModel(context, floatContext, coroutineScope, initialWaveActive)
 }
