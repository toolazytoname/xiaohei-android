package com.ai.assistance.operit.core.devicebridge

object DspEnablement {
    fun evaluate(input: DspEnablementInput): DspEnablementDecision {
        if (!input.enhancedDevice) {
            return deny(DspRejectReason.STORE_PROFILE)
        }
        if (!input.userEnabled) {
            return deny(DspRejectReason.USER_DISABLED)
        }
        if (!input.companionInstalled) {
            return deny(DspRejectReason.COMPANION_MISSING)
        }
        val client = input.companionClientPackage
        if (client == null) {
            return deny(DspRejectReason.COMPANION_STALE)
        }
        if (client != input.appPackage) {
            return deny(DspRejectReason.CLIENT_MISMATCH)
        }
        if (!input.signaturesMatch) {
            return deny(DspRejectReason.SIGNATURE_MISMATCH)
        }
        if (!input.permissionUsable) {
            return deny(DspRejectReason.PERMISSION_MISSING)
        }
        if (!input.selectedVoiceInteraction) {
            return deny(DspRejectReason.NOT_SELECTED_ASSISTANT)
        }
        if (!input.visReady) {
            return deny(DspRejectReason.VIS_NOT_READY)
        }
        return DspEnablementDecision(ok = true, reason = null)
    }

    fun message(reason: DspRejectReason): String {
        return when (reason) {
            DspRejectReason.STORE_PROFILE -> "当前构建不是增强版，DSP 入口未开放。"
            DspRejectReason.USER_DISABLED -> "DSP 低功耗唤醒已关闭。"
            DspRejectReason.VIS_NOT_READY -> "系统语音交互服务尚未就绪，不能启用 DSP。"
            DspRejectReason.COMPANION_MISSING -> "未安装 DSP Companion，无法启用。不会自动安装。"
            DspRejectReason.COMPANION_STALE -> "已安装的 Companion 过旧（缺少 CLIENT_PACKAGE），无法启用。"
            DspRejectReason.CLIENT_MISMATCH -> "Companion 声明的客户端包名与本应用不一致，无法启用。"
            DspRejectReason.SIGNATURE_MISMATCH -> "本应用与 Companion 签名不一致，无法启用。"
            DspRejectReason.PERMISSION_MISSING -> "唤醒签名权限不可用，无法启用。"
            DspRejectReason.NOT_SELECTED_ASSISTANT -> "当前应用还不是系统所选语音助手，无法启用。"
            DspRejectReason.SCHEMA -> "唤醒事件校验失败，已忽略。"
            DspRejectReason.DUPLICATE -> "重复唤醒事件，已忽略。"
            DspRejectReason.BUSY -> "已有助手会话进行中，忽略本次唤醒。"
            DspRejectReason.DETACH_FAILED -> "未能确认 DSP 已 DETACHED，已停止本次开始。"
            DspRejectReason.SHOW_FAILED -> "系统助手会话未能显示，已停止本次开始。"
        }
    }

    private fun deny(reason: DspRejectReason): DspEnablementDecision {
        return DspEnablementDecision(ok = false, reason = reason)
    }
}

object DspLockscreenPolicy {
    fun mustWaitForUnlock(enhancedDevice: Boolean, keyguardLocked: Boolean): Boolean {
        return enhancedDevice && keyguardLocked
    }
}

/**
 * Lockscreen pending-enter state. Cancel/hide/destroy invalidates this wake only.
 * A second startWatch() while already watching does not register again.
 */
class DspUnlockWatchState {
    var watchGeneration: Long = 0
        private set
    var sessionToken: Long = 0L
        private set
    var watching: Boolean = false
        private set
    var enterAllowed: Boolean = true
        private set

    fun bindSession(token: Long) {
        sessionToken = token
        enterAllowed = true
        watching = false
        watchGeneration += 1
    }

    /** @return true if the caller should register a new unlock receiver */
    fun startWatch(): Boolean {
        if (!enterAllowed || watching) {
            return false
        }
        watching = true
        watchGeneration += 1
        return true
    }

    fun invalidate() {
        enterAllowed = false
        watching = false
        watchGeneration += 1
    }

    fun stopWatchingKeepEnter() {
        watching = false
        watchGeneration += 1
    }

    fun canEnterAfterUnlock(capturedWatchGeneration: Long): Boolean {
        return enterAllowed &&
            watching &&
            watchGeneration == capturedWatchGeneration &&
            sessionToken != 0L
    }

    fun canImmediateEnter(): Boolean = enterAllowed
}

/**
 * One VIS session's enter/watch state. onShow clears a previous watch and resets entered so a
 * stale unlock cannot start a newer session. Manual assistant uses token = -1.
 */
class DspSessionEnterState {
    var entered: Boolean = false
        private set
    var sessionToken: Long = 0L
        private set
    val unlockWatch = DspUnlockWatchState()

    /**
     * @return false if this show is a stale DSP generation and the caller should cancel/finish.
     * Manual token -1 continues (caller must still require a real unlock).
     */
    fun onShow(token: Long, dspSessionLive: Boolean): Boolean {
        entered = false
        unlockWatch.invalidate()
        sessionToken = token
        unlockWatch.bindSession(token)
        if (token > 0L && !dspSessionLive) {
            return false
        }
        return true
    }

    fun markEntered() {
        entered = true
    }

    fun clearEntered() {
        entered = false
    }
}
