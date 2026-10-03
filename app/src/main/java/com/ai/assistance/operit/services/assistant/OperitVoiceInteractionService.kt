package com.ai.assistance.operit.services.assistant

import android.service.voice.VoiceInteractionService
import com.ai.assistance.operit.core.devicebridge.DspAndroidBridge
import com.ai.assistance.operit.util.AppLogger

/**
 * Operit 语音交互服务
 * 
 * 这是让 Operit 能够被 Android 系统识别为数字助理应用的核心服务。
 * 当用户长按 Home 键或触发其他助手调用方式时，系统会启动这个服务。
 */
class OperitVoiceInteractionService : VoiceInteractionService() {
    
    companion object {
        private const val TAG = "OperitVoiceInteraction"
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "VoiceInteractionService created")
    }
    
    /**
     * 当服务准备就绪时调用
     * 这里可以进行一些初始化操作
     */
    override fun onReady() {
        super.onReady()
        AppLogger.d(TAG, "VoiceInteractionService ready")
        DspAndroidBridge.attachVoiceInteraction(this)
    }
    
    /**
     * 系统请求启动识别会话时调用
     * 这里应该返回 true 表示我们可以处理这个请求
     */
    override fun onGetSupportedVoiceActions(voiceActions: MutableSet<String>): MutableSet<String> {
        AppLogger.d(TAG, "onGetSupportedVoiceActions: $voiceActions")
        return super.onGetSupportedVoiceActions(voiceActions)
    }
    
    override fun onShutdown() {
        AppLogger.d(TAG, "VoiceInteractionService shutting down")
        DspAndroidBridge.detachVoiceInteraction(this)
        super.onShutdown()
    }
    
    override fun onDestroy() {
        AppLogger.d(TAG, "VoiceInteractionService destroyed")
        DspAndroidBridge.detachVoiceInteraction(this)
        super.onDestroy()
    }
}

