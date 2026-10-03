package com.ai.assistance.operit.core.commonbase

/**
 * Single navigation allow/deny policy for menus and routes.
 *
 * Denied Screen nested-class simple names are the only exclusion list. Route ids are
 * `native.<snake_case>` of those names; NavItem.route values map onto the same Screen types.
 * Callers must refuse excluded navigation without substituting another capability.
 *
 * Original [enabled]=false keeps every host screen and route reachable.
 */
object CommonBaseNavigationPolicy {
    private val deniedScreenTypeNames: Set<String> =
        linkedSetOf(
            "Packages",
            "Market",
            "MarketCategory",
            "MarketNotifications",
            "MarketEntryDetail",
            "MarketAuthor",
            "MarketManage",
            "ArtifactPublish",
            "ArtifactContinuePublish",
            "ArtifactEdit",
            "RepoPublish",
            "RepoEdit",
            "RepoPublishVersion",
            "Toolbox",
            "ShizukuCommands",
            "GitHubAccount",
            "Workflow",
            "WorkflowDetail",
            "ToolPermission",
            "ExternalHttpChatSettings",
            "ToolPkgComposeDsl",
            "ToolPkgPluginConfig",
            "Terminal",
            "TerminalSetup",
            "TerminalAutoConfig",
            "AppPermissions",
            "UIDebugger",
            "ShellExecutor",
            "ToolTester",
            "ProcessLimitRemover",
            "HtmlPackager",
            "AutoGlmOneClick",
            "AutoGlmTool"
        )

    private val navItemRouteToScreenType: Map<String, String> =
        linkedMapOf(
            "ai_chat" to "AiChat",
            "shizuku_commands" to "ShizukuCommands",
            "assistant_config" to "AssistantConfig",
            "settings" to "Settings",
            "tool_permissions" to "ToolPermission",
            "chat_history_settings" to "ChatHistorySettings",
            "packages" to "Packages",
            "memory_base" to "MemoryBase",
            "terminal" to "Terminal",
            "toolbox" to "Toolbox",
            "about" to "About",
            "agreement" to "Agreement",
            "help" to "Help",
            "token_config" to "TokenConfig",
            "workflow" to "Workflow"
        )

    private val deniedRouteIds: Set<String> =
        deniedScreenTypeNames.map(::routeIdForScreenType).toSet()

    fun routeIdForScreenType(typeName: String): String = "native.${camelToSnakeCase(typeName)}"

    fun evaluateScreenType(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        typeName: String
    ): CommonBaseDecision {
        if (!enabled) {
            return CommonBaseDecision.AllowUnrestricted
        }
        val canonical = typeName.trim()
        if (canonical.isEmpty() || canonical != typeName) {
            return CommonBaseDecision.Deny(
                "Screen type is empty or not canonical, so it cannot open in the common base profile."
            )
        }
        if (canonical in deniedScreenTypeNames) {
            return denyNavigation(canonical)
        }
        return CommonBaseDecision.Allow
    }

    fun evaluateRouteId(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        routeId: String
    ): CommonBaseDecision {
        if (!enabled) {
            return CommonBaseDecision.AllowUnrestricted
        }
        val canonical = routeId.trim()
        if (canonical.isEmpty() || canonical != routeId) {
            return CommonBaseDecision.Deny(
                "Route id is empty or not canonical, so it cannot open in the common base profile."
            )
        }
        if (isPluginRouteId(canonical)) {
            return CommonBaseDecision.Deny(
                "Plugin and tool-package routes are not available in the common base profile."
            )
        }
        if (canonical in deniedRouteIds) {
            return denyNavigation(canonical)
        }
        if (canonical.startsWith("native.")) {
            return CommonBaseDecision.Allow
        }
        return CommonBaseDecision.Deny(
            "Route '$canonical' is not a host route in the common base profile."
        )
    }

    fun evaluateNavItemRoute(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        navItemRoute: String
    ): CommonBaseDecision {
        if (!enabled) {
            return CommonBaseDecision.AllowUnrestricted
        }
        val canonical = navItemRoute.trim()
        if (canonical.isEmpty() || canonical != navItemRoute) {
            return CommonBaseDecision.Deny(
                "Nav item route is empty or not canonical, so it cannot open in the common base profile."
            )
        }
        val typeName = navItemRouteToScreenType[canonical] ?: return CommonBaseDecision.Allow
        return evaluateScreenType(enabled = true, typeName = typeName)
    }

    fun allowsScreenType(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        typeName: String
    ): Boolean = evaluateScreenType(enabled = enabled, typeName = typeName) !is CommonBaseDecision.Deny

    fun allowsRoute(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        routeId: String
    ): Boolean = evaluateRouteId(enabled = enabled, routeId = routeId) !is CommonBaseDecision.Deny

    fun allowsNavItemRoute(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        navItemRoute: String
    ): Boolean = evaluateNavItemRoute(enabled = enabled, navItemRoute = navItemRoute) !is CommonBaseDecision.Deny

    private fun denyNavigation(identifier: String): CommonBaseDecision.Deny {
        return CommonBaseDecision.Deny(
            "Navigation to '$identifier' is not available in the common base profile."
        )
    }

    private fun isPluginRouteId(routeId: String): Boolean {
        val lowered = routeId.lowercase()
        return lowered.startsWith("toolpkg:") ||
            lowered.startsWith("toolpkg.") ||
            lowered.startsWith("plugin:") ||
            lowered.startsWith("mcp:")
    }

    private fun camelToSnakeCase(name: String): String {
        return name
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("([a-z\\d])([A-Z])"), "$1_$2")
            .lowercase()
    }
}
