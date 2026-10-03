package com.ai.assistance.operit.services.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.ai.assistance.operit.core.commonbase.CommonBaseProfile
import com.ai.assistance.operit.core.devicebridge.DspLockscreenPolicy
import com.ai.assistance.operit.core.devicebridge.DspUnlockWatchState
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.util.AppLogger

/**
 * Receives system ASSIST intents and forwards them to Operit's voice assistant entry.
 */
class OperitAssistActivity : Activity() {

    companion object {
        private const val TAG = "OperitAssistActivity"
        private const val UNLOCK_REQUIRED_TEXT =
            "需要解锁后才能继续语音助手。不会绕过系统锁屏，也还不是息屏免解锁问答。"
    }

    private var waitingForUnlock = false
    private var launched = false
    private val unlockWatch = DspUnlockWatchState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlockWatch.bindSession(-1L)
        if (shouldWaitForUnlock()) {
            waitingForUnlock = true
            showUnlockRequiredUi()
            requestSystemUnlock()
            return
        }
        launchAssistEntry()
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!unlockWatch.canImmediateEnter()) {
            return
        }
        if (shouldWaitForUnlock()) {
            waitingForUnlock = true
            showUnlockRequiredUi()
            requestSystemUnlock()
            return
        }
        launchAssistEntry()
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForUnlock &&
            unlockWatch.canImmediateEnter() &&
            !isKeyguardLocked()
        ) {
            launchAssistEntry()
            finish()
        }
    }

    override fun onDestroy() {
        waitingForUnlock = false
        unlockWatch.invalidate()
        super.onDestroy()
    }

    private fun shouldWaitForUnlock(): Boolean {
        return DspLockscreenPolicy.mustWaitForUnlock(
            enhancedDevice = CommonBaseProfile.enhancedDevice,
            keyguardLocked = isKeyguardLocked()
        )
    }

    private fun isKeyguardLocked(): Boolean {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isKeyguardLocked
    }

    private fun showUnlockRequiredUi() {
        val text = TextView(this).apply {
            gravity = Gravity.CENTER
            this.text = UNLOCK_REQUIRED_TEXT
            textSize = 16f
            setPadding(48, 48, 48, 48)
            setTextColor(Color.WHITE)
        }
        val root = FrameLayout(this).apply {
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

    private fun requestSystemUnlock() {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        if (isFinishing || isDestroyed || !unlockWatch.canImmediateEnter()) {
                            return
                        }
                        launchAssistEntry()
                        finish()
                    }

                    override fun onDismissCancelled() {
                        waitingForUnlock = false
                        unlockWatch.invalidate()
                    }

                    override fun onDismissError() {
                        waitingForUnlock = false
                        unlockWatch.invalidate()
                    }
                }
            )
        }
    }

    private fun launchAssistEntry() {
        if (launched) {
            return
        }
        if (!unlockWatch.canImmediateEnter()) {
            return
        }
        if (shouldWaitForUnlock()) {
            return
        }
        launched = true
        waitingForUnlock = false
        AppLogger.d(TAG, "Assist intent received, action=${intent?.action}")
        try {
            val floatingIntent = Intent(this, FloatingChatService::class.java).apply {
                putExtra("INITIAL_MODE", FloatingMode.FULLSCREEN.name)
                putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
            }
            val started =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(floatingIntent)
                } else {
                    startService(floatingIntent)
                }
            if (started == null) {
                launched = false
                unlockWatch.invalidate()
                AppLogger.e(TAG, "Failed to launch assist entry")
            }
        } catch (e: Exception) {
            launched = false
            unlockWatch.invalidate()
            AppLogger.e(TAG, "Failed to launch assist entry", e)
        }
    }
}
