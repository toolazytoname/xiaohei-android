package com.ai.assistance.operit.core.commonbase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonBaseNavigationPolicyTest {

    @Test
    fun originalProfile_allowsEveryKnownScreenTypeAndNavItem() {
        knownScreenTypes.forEach { typeName ->
            assertTrue(typeName, CommonBaseNavigationPolicy.allowsScreenType(enabled = false, typeName = typeName))
            assertTrue(
                typeName,
                CommonBaseNavigationPolicy.allowsRoute(
                    enabled = false,
                    routeId = CommonBaseNavigationPolicy.routeIdForScreenType(typeName)
                )
            )
        }
        navItemRoutes.forEach { route ->
            assertTrue(route, CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = false, navItemRoute = route))
        }
        assertTrue(
            CommonBaseNavigationPolicy.allowsRoute(enabled = false, routeId = "toolpkg:demo.pack:home")
        )
    }

    @Test
    fun commonEnabled_deniesExcludedFamilies_andKeepsAssistantSurfaces() {
        deniedScreenTypes.forEach { typeName ->
            val decision = CommonBaseNavigationPolicy.evaluateScreenType(enabled = true, typeName = typeName)
            assertTrue(typeName, decision is CommonBaseDecision.Deny)
            assertFalse(typeName, CommonBaseNavigationPolicy.allowsScreenType(enabled = true, typeName = typeName))
            assertFalse(
                typeName,
                CommonBaseNavigationPolicy.allowsRoute(
                    enabled = true,
                    routeId = CommonBaseNavigationPolicy.routeIdForScreenType(typeName)
                )
            )
        }
        keptScreenTypes.forEach { typeName ->
            val decision = CommonBaseNavigationPolicy.evaluateScreenType(enabled = true, typeName = typeName)
            assertEquals(typeName, CommonBaseDecision.Allow, decision)
            assertTrue(typeName, CommonBaseNavigationPolicy.allowsScreenType(enabled = true, typeName = typeName))
        }
    }

    @Test
    fun commonEnabled_denyDoesNotNameASubstituteCapability() {
        val decision =
            CommonBaseNavigationPolicy.evaluateRouteId(enabled = true, routeId = "native.packages")
        val deny = decision as CommonBaseDecision.Deny
        val reason = deny.reason.lowercase()
        assertFalse(reason.contains("aichat"))
        assertFalse(reason.contains("ai_chat"))
        assertFalse(reason.contains("settings"))
        assertFalse(reason.contains("terminal"))
        assertFalse(reason.contains("root"))
    }

    @Test
    fun commonEnabled_pluginAndNonHostRoutesDenied_nativeUnknownKept() {
        assertFalse(CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "toolpkg:demo.pack:home"))
        assertFalse(CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "plugin:market"))
        assertFalse(CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "mcp:server"))
        assertTrue(CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "native.file_manager"))
        assertTrue(CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "native.text_to_speech"))
        assertTrue(CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "native.speech_services_settings"))
    }

    @Test
    fun commonEnabled_navItemsMatchScreenPolicy_andSessionRefuseIsNotARedirect() {
        assertFalse(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "packages"))
        assertFalse(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "toolbox"))
        assertFalse(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "shizuku_commands"))
        assertFalse(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "workflow"))
        assertFalse(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "terminal"))
        assertFalse(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "tool_permissions"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "ai_chat"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "assistant_config"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "memory_base"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "settings"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "help"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "about"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "agreement"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "chat_history_settings"))
        assertTrue(CommonBaseNavigationPolicy.allowsNavItemRoute(enabled = true, navItemRoute = "token_config"))
        val denied = CommonBaseNavigationPolicy.evaluateNavItemRoute(enabled = true, navItemRoute = "packages")
        assertTrue(denied is CommonBaseDecision.Deny)
        assertTrue(denied !is CommonBaseDecision.Allow)
        assertTrue(denied !is CommonBaseDecision.AllowUnrestricted)
    }

    @Test
    fun routeIdsFollowNativeSnakeCaseOfScreenTypes() {
        assertEquals("native.shizuku_commands", CommonBaseNavigationPolicy.routeIdForScreenType("ShizukuCommands"))
        assertEquals("native.ui_debugger", CommonBaseNavigationPolicy.routeIdForScreenType("UIDebugger"))
        assertEquals(
            "native.tool_pkg_compose_dsl",
            CommonBaseNavigationPolicy.routeIdForScreenType("ToolPkgComposeDsl")
        )
        assertEquals("native.text_to_speech", CommonBaseNavigationPolicy.routeIdForScreenType("TextToSpeech"))
        assertFalse(
            CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "native.shizuku_commands")
        )
        assertTrue(
            CommonBaseNavigationPolicy.allowsRoute(enabled = true, routeId = "native.text_to_speech")
        )
    }

    @Test
    fun everyKnownScreenTypeIsClassifiedExactlyOnce() {
        assertTrue(deniedScreenTypes.intersect(keptScreenTypes).isEmpty())
        knownScreenTypes.forEach { typeName ->
            val allowed = CommonBaseNavigationPolicy.allowsScreenType(enabled = true, typeName = typeName)
            if (typeName in deniedScreenTypes) {
                assertFalse(typeName, allowed)
            } else {
                assertTrue(typeName, allowed)
            }
        }
    }

    private val deniedScreenTypes =
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

    private val keptScreenTypes =
        linkedSetOf(
            "AiChat",
            "MemoryBase",
            "Settings",
            "Help",
            "About",
            "Agreement",
            "UpdateHistory",
            "AssistantConfig",
            "TokenConfig",
            "UserPreferencesSettings",
            "ModelConfig",
            "ModelConfigOnboarding",
            "SpeechServicesSettings",
            "MnnModelDownload",
            "PersonaCardGeneration",
            "WaifuModeSettings",
            "CustomEmojiManagement",
            "TagMarket",
            "ModelPromptsSettings",
            "FunctionalConfig",
            "ThemeSettings",
            "GlobalDisplaySettings",
            "LayoutAdjustmentSettings",
            "ChatHistorySettings",
            "ChatBackupSettings",
            "LanguageSettings",
            "TokenUsageStatistics",
            "ContextSummarySettings",
            "FileManager",
            "Logcat",
            "SqlViewer",
            "FFmpegToolbox",
            "TextToSpeech",
            "SpeechToText",
            "DefaultAssistantGuide",
            "MarkdownDemo"
        )

    private val knownScreenTypes = deniedScreenTypes + keptScreenTypes

    private val navItemRoutes =
        listOf(
            "ai_chat",
            "shizuku_commands",
            "assistant_config",
            "settings",
            "tool_permissions",
            "chat_history_settings",
            "packages",
            "memory_base",
            "terminal",
            "toolbox",
            "about",
            "agreement",
            "help",
            "token_config",
            "workflow"
        )
}
