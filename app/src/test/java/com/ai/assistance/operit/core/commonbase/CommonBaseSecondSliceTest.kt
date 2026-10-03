package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardSystemOperationTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonBaseStartupPolicyTest {

    @Test
    fun commonEnabled_skipsWorkflowUiBindAndMcpPrep_butDoesNotSkipWhenOriginal() {
        assertTrue(CommonBaseStartupPolicy.skipWorkflowScheduler(enabled = true))
        assertTrue(CommonBaseStartupPolicy.skipUiHierarchyProviderBind(enabled = true))
        assertTrue(CommonBaseStartupPolicy.skipTerminalMcpRuntimePrep(enabled = true))
        assertTrue(CommonBaseStartupPolicy.skipBuiltinWorkflowAndToolboxPlugins(enabled = true))
        assertTrue(CommonBaseStartupPolicy.skipPermissionGuideForRemovedPermissions(enabled = true))
        assertTrue(CommonBaseStartupPolicy.skipToolPkgNavigationRuntime(enabled = true))
        assertTrue(CommonBaseStartupPolicy.createDefaultChatIfHistoryEmpty(enabled = true))
        assertTrue(CommonBaseStartupPolicy.allowComposerSendWithoutExistingChat(enabled = true))

        assertFalse(CommonBaseStartupPolicy.skipWorkflowScheduler(enabled = false))
        assertFalse(CommonBaseStartupPolicy.skipUiHierarchyProviderBind(enabled = false))
        assertFalse(CommonBaseStartupPolicy.skipTerminalMcpRuntimePrep(enabled = false))
        assertFalse(CommonBaseStartupPolicy.skipBuiltinWorkflowAndToolboxPlugins(enabled = false))
        assertFalse(CommonBaseStartupPolicy.skipPermissionGuideForRemovedPermissions(enabled = false))
        assertFalse(CommonBaseStartupPolicy.skipToolPkgNavigationRuntime(enabled = false))
        assertFalse(CommonBaseStartupPolicy.createDefaultChatIfHistoryEmpty(enabled = false))
        assertFalse(CommonBaseStartupPolicy.allowComposerSendWithoutExistingChat(enabled = false))
    }
}

class CommonBaseToolBackingTest {

    @Test
    fun startAppBackingIsExactlyStandardSystemOperationTools() {
        assertEquals(
            StandardSystemOperationTools::class.java,
            CommonBaseToolBacking.systemOperationToolsType()
        )
        assertEquals("StandardSystemOperationTools", CommonBaseToolBacking.systemOperationToolsType().simpleName)
    }
}

class CommonBaseManageableToolPromptsTest {

    @Test
    fun commonEnabled_returnsCatalogToolsIncludingStartApp() {
        val names =
            SystemToolPrompts.getManageableToolPrompts(useEnglish = true, enabled = true)
                .map { it.name }
                .toSet()
        assertEquals(CommonBaseToolCatalog.registeredToolNames, names)
        assertTrue(names.contains(CommonBaseToolCatalog.START_APP))
        assertTrue(names.contains(CommonBaseToolCatalog.EXECUTE_INTENT))
        assertFalse(names.contains("use_package"))
        assertFalse(names.contains("sleep"))
    }

    @Test
    fun originalProfile_keepsUpstreamCategoriesAndDoesNotInventStartApp() {
        val names =
            SystemToolPrompts.getManageableToolPrompts(useEnglish = true, enabled = false)
                .map { it.name }
                .toSet()
        assertTrue(names.contains("use_package"))
        assertTrue(names.contains("sleep"))
        assertFalse(names.contains(CommonBaseToolCatalog.START_APP))
        assertFalse(names.contains(CommonBaseToolCatalog.EXECUTE_INTENT))
    }

    @Test
    fun commonEnabled_toolOrderIsAppliedToCatalog() {
        val ordered =
            SystemToolPrompts.getManageableToolPrompts(
                useEnglish = false,
                toolOrder = listOf(CommonBaseToolCatalog.EXECUTE_INTENT, CommonBaseToolCatalog.START_APP),
                enabled = true
            ).map { it.name }
        assertEquals(
            listOf(CommonBaseToolCatalog.EXECUTE_INTENT, CommonBaseToolCatalog.START_APP),
            ordered.take(2)
        )
        assertFalse(ordered.contains("use_package"))
        assertFalse(ordered.contains("sleep"))
    }
}
