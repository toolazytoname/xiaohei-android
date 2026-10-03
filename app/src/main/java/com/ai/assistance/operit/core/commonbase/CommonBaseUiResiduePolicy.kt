package com.ai.assistance.operit.core.commonbase

/**
 * Overlay and context-attachment extras that remain in host chat/floating UI after
 * navigation trim. COMMON_BASE refuses these without substituting another capability.
 *
 * Original [enabled]=false keeps every extra reachable, including restored overlay
 * state and direct ViewModel/delegate callbacks.
 */
object CommonBaseUiResiduePolicy {
    fun allowsAiComputer(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    fun allowsWorkspace(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    fun allowsScreenContentAttach(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        !enabled

    fun allowsNotificationAttach(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    fun allowsLocationAttach(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    fun allowsPackageAttach(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    fun allowsScreenOcr(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    fun showTtsAccessibilityNote(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = !enabled

    /**
     * COMMON_BASE merged manifest has no CAMERA. Requesting it is denied without a
     * dialog, so capture uses an external TakePicture / IMAGE_CAPTURE activity.
     * Original still requests this app's CAMERA permission first.
     */
    fun requestsOwnCameraPermission(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        !enabled

    /**
     * Floating-window panel only lists residue extras (no photo/file/camera).
     * When every extra is refused, hide + and the panel even if the panel
     * visibility flag is restored true. Does not add options.
     */
    fun hasFloatingWindowAttachmentOptions(
        enabled: Boolean = CommonBaseProfile.isEnabled
    ): Boolean =
        allowsScreenContentAttach(enabled) ||
            allowsNotificationAttach(enabled) ||
            allowsLocationAttach(enabled) ||
            allowsScreenOcr(enabled) ||
            allowsPackageAttach(enabled)

    fun allowsAttachmentToken(
        token: String,
        enabled: Boolean = CommonBaseProfile.isEnabled
    ): Boolean {
        if (!enabled) {
            return true
        }
        if (token.startsWith("package_attach:")) return false
        return when (token) {
            "screen_capture",
            "notifications_capture",
            "location_capture" -> false
            else -> true
        }
    }
}
