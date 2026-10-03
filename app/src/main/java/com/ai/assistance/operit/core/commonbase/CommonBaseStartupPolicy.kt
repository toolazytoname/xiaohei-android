package com.ai.assistance.operit.core.commonbase

/**
 * Startup skips for the common-base profile. Original [enabled]=false keeps every original
 * initializer. Callers must still run chat / model / voice setup; do not early-return the whole
 * Application or Activity onCreate.
 */
object CommonBaseStartupPolicy {
    fun skipWorkflowScheduler(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = enabled

    fun skipUiHierarchyProviderBind(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        enabled

    fun skipTerminalMcpRuntimePrep(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean = enabled

    fun skipBuiltinWorkflowAndToolboxPlugins(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        enabled

    fun skipPermissionGuideForRemovedPermissions(
        enabled: Boolean = CommonBaseProfile.isEnabled
    ): Boolean = enabled

    fun skipToolPkgNavigationRuntime(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        enabled

    /**
     * Common skips plugin/permission overlays, so a fresh install can show the
     * composer with an empty Room store. Official keeps requiring the user to
     * create a chat (or enable start-with-new-chat).
     */
    fun createDefaultChatIfHistoryEmpty(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        enabled

    /**
     * Official composer toasts and returns when [currentChatId] is null.
     * Common lets [MessageCoordinationDelegate.sendUserMessage] auto-create.
     */
    fun allowComposerSendWithoutExistingChat(enabled: Boolean = CommonBaseProfile.isEnabled): Boolean =
        enabled
}
