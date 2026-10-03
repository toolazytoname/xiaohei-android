package com.ai.assistance.operit.services.assistant

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.core.commonbase.CommonBaseProfile
import com.ai.assistance.operit.core.devicebridge.DspAndroidBridge
import com.ai.assistance.operit.core.devicebridge.DspContract
import com.ai.assistance.operit.core.devicebridge.DspLockscreenPolicy
import com.ai.assistance.operit.core.devicebridge.DspSessionEnterState
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.util.AppLogger

/**
 * Operit 语音交互会话服务
 * 
 * 当用户触发助手时（如长按 Home 键），系统会创建这个服务的会话实例。
 * 我们在这里启动悬浮窗来提供 AI 助手功能。
 */
class OperitVoiceInteractionSessionService : VoiceInteractionSessionService() {
    
    companion object {
        private const val TAG = "OperitSessionService"
    }
    
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        AppLogger.d(TAG, "Creating new voice interaction session")
        return OperitVoiceInteractionSession(this)
    }
    
    /**
     * Operit 的语音交互会话实现
     */
    private class OperitVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
        
        companion object {
            private const val TAG = "OperitSession"
            private const val UNLOCK_REQUIRED_TEXT =
                "需要解锁后才能继续语音助手。不会绕过系统锁屏，也还不是息屏免解锁问答。"
        }

        private val enterState = DspSessionEnterState()
        private var userPresentReceiver: BroadcastReceiver? = null

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            AppLogger.d(TAG, "Session show requested with flags: $showFlags")
            clearUnlockWatchers()
            val token = args?.getLong(DspContract.EXTRA_SESSION_GENERATION, -1L) ?: -1L
            val live = token <= 0L || DspAndroidBridge.isSessionLive(token)
            if (!enterState.onShow(token, live)) {
                cancelPendingEnter()
                finish()
                return
            }
            if (shouldWaitForUnlock()) {
                showUnlockRequiredUi()
                watchForUnlock()
                return
            }
            enterExistingAssistant()
        }

        override fun onLockscreenShown() {
            if (shouldWaitForUnlock()) {
                AppLogger.d(TAG, "Lockscreen shown; keeping unlock-required session UI")
                return
            }
            super.onLockscreenShown()
        }
        
        override fun onHide() {
            super.onHide()
            AppLogger.d(TAG, "Session hide requested")
            cancelPendingEnter()
            finish()
        }
        
        override fun onDestroy() {
            AppLogger.d(TAG, "Session destroyed")
            cancelPendingEnter()
            super.onDestroy()
        }

        private fun shouldWaitForUnlock(): Boolean {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return DspLockscreenPolicy.mustWaitForUnlock(
                enhancedDevice = CommonBaseProfile.enhancedDevice,
                keyguardLocked = keyguard.isKeyguardLocked
            )
        }

        private fun showUnlockRequiredUi() {
            val text = TextView(context).apply {
                gravity = Gravity.CENTER
                text = UNLOCK_REQUIRED_TEXT
                textSize = 16f
                setPadding(48, 48, 48, 48)
                setTextColor(0xFFFFFFFF.toInt())
            }
            val root = FrameLayout(context).apply {
                setBackgroundColor(0xE602070F.toInt())
                addView(
                    text,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            setContentView(root)
        }

        private fun watchForUnlock() {
            if (!enterState.unlockWatch.startWatch()) {
                return
            }
            val capturedWatchGeneration = enterState.unlockWatch.watchGeneration
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (Intent.ACTION_USER_PRESENT == intent?.action &&
                        !keyguard.isKeyguardLocked &&
                        enterState.unlockWatch.canEnterAfterUnlock(capturedWatchGeneration)
                    ) {
                        enterExistingAssistant()
                    }
                }
            }
            userPresentReceiver = receiver
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        private fun clearUnlockWatchers() {
            userPresentReceiver?.let { receiver ->
                runCatching { context.unregisterReceiver(receiver) }
            }
            userPresentReceiver = null
        }

        private fun cancelPendingEnter() {
            enterState.unlockWatch.invalidate()
            clearUnlockWatchers()
            if (!enterState.entered && enterState.sessionToken > 0L) {
                DspAndroidBridge.abandonSession(enterState.sessionToken)
            }
        }

        private fun enterExistingAssistant() {
            if (enterState.entered) {
                return
            }
            if (!enterState.unlockWatch.canImmediateEnter()) {
                return
            }
            if (enterState.sessionToken > 0L && !DspAndroidBridge.isSessionLive(enterState.sessionToken)) {
                cancelPendingEnter()
                finish()
                return
            }
            if (shouldWaitForUnlock()) {
                return
            }
            enterState.markEntered()
            enterState.unlockWatch.stopWatchingKeepEnter()
            clearUnlockWatchers()
            if (!startFloatingChatService()) {
                enterState.clearEntered()
                if (enterState.sessionToken > 0L) {
                    DspAndroidBridge.abandonSession(enterState.sessionToken)
                }
                enterState.unlockWatch.invalidate()
                finish()
                return
            }
            finish()
        }
        
        /**
         * 启动悬浮窗聊天服务
         */
        private fun startFloatingChatService(): Boolean {
            return try {
                val intent = Intent(context, FloatingChatService::class.java).apply {
                    putExtra("INITIAL_MODE", FloatingMode.FULLSCREEN.name)
                    putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
                }
                val started =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                if (started == null) {
                    AppLogger.e(TAG, "Failed to start floating chat service")
                    false
                } else {
                    AppLogger.d(TAG, "Floating chat service started")
                    true
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start floating chat service", e)
                false
            }
        }
    }
}
