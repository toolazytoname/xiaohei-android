package com.ai.assistance.operit.core.devicebridge

/**
 * Closed Companion client contract. Android-free so host tests can compile it.
 * Values match the OnePlus 8T DSP Companion; this is not a store-channel promise.
 */
object DspContract {
    const val COMPANION_PACKAGE = "io.github.toolazytoname.xiaohei.dsp"
    const val CONTROL_SERVICE = "io.github.toolazytoname.xiaohei.dsp.DspControlService"
    const val CLIENT_PACKAGE_METADATA = "io.github.toolazytoname.xiaohei.dsp.CLIENT_PACKAGE"

    const val WAKE_ACTION = "io.github.toolazytoname.xiaohei.action.WAKEWORD"
    const val STATUS_ACTION = "io.github.toolazytoname.xiaohei.action.DSP_STATUS"
    const val SIGNATURE_PERMISSION = "io.github.toolazytoname.xiaohei.permission.WAKEWORD_EVENT"

    const val ACTION_ARM = "io.github.toolazytoname.xiaohei.dsp.action.ARM"
    const val STOP_URI = "content://io.github.toolazytoname.xiaohei.dsp.stop"
    const val STATUS_URI = "content://io.github.toolazytoname.xiaohei.dsp.status"
    const val DISARM_METHOD = "disarm"
    const val STATUS_METHOD = "status"

    const val SCHEMA_VERSION = "wakeword-event.v1"
    const val EXPECTED_KEYWORD_ID = "xiaobuxiaobu.0220.0828"

    const val EXTRA_KEYWORD_ID = "keyword_id"
    const val EXTRA_CONFIDENCE = "confidence"
    const val EXTRA_CAPTURE_AVAILABLE = "capture_available"
    const val EXTRA_SCHEMA_VERSION = "schema_version"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_OK = "ok"
    const val EXTRA_STATE = "state"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_SESSION_GENERATION = "xiaohei.dsp.session_generation"

    const val STATE_DETACHED = "DETACHED"
    const val STATE_ACTIVE_PREFIX = "ACTIVE"

    const val MIN_CONFIDENCE = 0
    const val MAX_CONFIDENCE = 100
    const val MIN_EVENT_ID_LENGTH = 8
    const val MAX_EVENT_ID_LENGTH = 128
    const val EVENT_ID_DEDUP_LIMIT = 16

    val WAKE_EXTRA_KEYS: Set<String> =
        linkedSetOf(
            EXTRA_KEYWORD_ID,
            EXTRA_CONFIDENCE,
            EXTRA_CAPTURE_AVAILABLE,
            EXTRA_SCHEMA_VERSION,
            EXTRA_EVENT_ID
        )

    fun isDetachedState(state: String): Boolean = state == STATE_DETACHED

    fun isActiveState(state: String): Boolean = state.startsWith(STATE_ACTIVE_PREFIX)

    fun isVerifiedDetached(result: DspProviderResult): Boolean {
        return result.ok && isDetachedState(result.state)
    }

    fun isAllowedWakeTokenChar(c: Char): Boolean {
        return c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '_' || c == '-'
    }

    fun isAllowedEventId(value: String): Boolean {
        if (value.length < MIN_EVENT_ID_LENGTH || value.length > MAX_EVENT_ID_LENGTH) {
            return false
        }
        return value.all { isAllowedWakeTokenChar(it) }
    }
}

data class DspProviderResult(
    val ok: Boolean,
    val state: String
)

data class WakeFields(
    val schemaVersion: String?,
    val eventId: String?,
    val keywordId: String?,
    val confidence: Int?,
    val captureAvailable: Boolean?,
    val keys: Set<String>
)

enum class DspRejectReason {
    STORE_PROFILE,
    USER_DISABLED,
    VIS_NOT_READY,
    COMPANION_MISSING,
    COMPANION_STALE,
    CLIENT_MISMATCH,
    SIGNATURE_MISMATCH,
    PERMISSION_MISSING,
    NOT_SELECTED_ASSISTANT,
    SCHEMA,
    DUPLICATE,
    BUSY,
    DETACH_FAILED,
    SHOW_FAILED
}

enum class DspReportedState {
    UNKNOWN,
    DISARMED,
    ARM_REQUESTED,
    ARMED
}

enum class DspCleanupDecision {
    Arm,
    NoArm,
    Stale,
    Ignored,
    Failed
}

object DspAudioSettlePolicy {
    fun maySettle(asrCancelSucceeded: Boolean, ttsStopSucceeded: Boolean): Boolean {
        return asrCancelSucceeded && ttsStopSucceeded
    }
}

sealed class HandoffOutcome {
    data object Started : HandoffOutcome()

    data class Rejected(val reason: DspRejectReason) : HandoffOutcome()
}

sealed class CapturePrep {
    data object Skip : CapturePrep()

    data object Ready : CapturePrep()

    data class Fail(val reason: DspRejectReason) : CapturePrep()
}

data class DspEnablementInput(
    val enhancedDevice: Boolean,
    val userEnabled: Boolean,
    val visReady: Boolean = true,
    val companionInstalled: Boolean,
    val companionClientPackage: String?,
    val appPackage: String,
    val signaturesMatch: Boolean,
    val permissionUsable: Boolean,
    val selectedVoiceInteraction: Boolean
)

data class DspEnablementDecision(
    val ok: Boolean,
    val reason: DspRejectReason?
) {
    val errorMessage: String?
        get() = reason?.let { DspEnablement.message(it) }
}
