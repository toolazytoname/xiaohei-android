package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.ResolvedCharacterCardToolAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonBasePackageLookupTest {

    @Test
    fun commonEnabled_doesNotInvokePackageLookup_andReturnsEmptyNames() {
        var lookupCount = 0
        val names =
            CommonBasePackageLookup.availableNames(enabled = true) {
                lookupCount += 1
                setOf("plugin.pack", "mcp.server")
            }
        assertEquals(emptySet<String>(), names)
        assertEquals(0, lookupCount)
    }

    @Test
    fun originalProfile_invokesLookupAndKeepsReturnedNames() {
        var lookupCount = 0
        val names =
            CommonBasePackageLookup.availableNames(enabled = false) {
                lookupCount += 1
                linkedSetOf("plugin.pack", "mcp.server")
            }
        assertEquals(linkedSetOf("plugin.pack", "mcp.server"), names)
        assertEquals(1, lookupCount)
    }

    @Test
    fun originalProfile_propagatesLookupFailureInsteadOfSwallowing() {
        val thrown =
            runCatching {
                CommonBasePackageLookup.availableNames(enabled = false) {
                    error("package manager ensureInitialized")
                }
            }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(thrown!!.message?.contains("ensureInitialized") == true)
    }
}

class CommonBaseCharacterCardToolAccessTest {

    @Test
    fun customCard_intersectsCatalogWithAllowedBuiltinTools_andNeverLooksUpPackages() {
        var lookupCount = 0
        val commonAccess =
            CommonBaseCharacterCardToolAccess.resolve(
                customEnabled = true,
                allowedBuiltinTools =
                    listOf(
                        CommonBaseToolCatalog.START_APP,
                        "use_package",
                        "execute_shell"
                    ),
                globalToolVisibility = mapOf("use_package" to true),
                catalogToolNames = CommonBaseToolCatalog.storeToolNames
            )
        val access =
            ResolvedCharacterCardToolAccess(
                customEnabled = commonAccess.customEnabled,
                effectiveBuiltinToolVisibility = commonAccess.effectiveBuiltinToolVisibility,
                allowedPackageNames = emptySet(),
                allowedSkillNames = emptySet(),
                allowedMcpServerNames = emptySet(),
                canUsePackageSystem = false,
                hasAnyAllowedExternalSource = false
            )
        val ignoredPackages =
            CommonBasePackageLookup.availableNames(enabled = true) {
                lookupCount += 1
                setOf("allowed.plugin")
            }

        assertEquals(
            mapOf(
                CommonBaseToolCatalog.START_APP to true,
                CommonBaseToolCatalog.EXECUTE_INTENT to false
            ),
            commonAccess.effectiveBuiltinToolVisibility
        )
        assertTrue(access.customEnabled)
        assertTrue(access.isBuiltinToolAllowed(CommonBaseToolCatalog.START_APP))
        assertFalse(access.isBuiltinToolAllowed(CommonBaseToolCatalog.EXECUTE_INTENT))
        assertFalse(access.isBuiltinToolAllowed("use_package"))
        assertFalse(access.isBuiltinToolAllowed("execute_shell"))
        assertFalse(access.canUsePackageSystem)
        assertFalse(access.hasAnyAllowedExternalSource)
        assertEquals(emptySet<String>(), access.allowedPackageNames)
        assertEquals(emptySet<String>(), access.allowedSkillNames)
        assertEquals(emptySet<String>(), access.allowedMcpServerNames)
        assertFalse(access.isExternalSourceAllowed("allowed.plugin"))
        assertEquals(emptySet<String>(), ignoredPackages)
        assertEquals(0, lookupCount)
    }

    @Test
    fun customDisabled_keepsCatalogToolsVisible_butStillExcludesPackagesAndMcp() {
        val commonAccess =
            CommonBaseCharacterCardToolAccess.resolve(
                customEnabled = false,
                allowedBuiltinTools = listOf("use_package"),
                globalToolVisibility = mapOf(CommonBaseToolCatalog.EXECUTE_INTENT to false),
                catalogToolNames = CommonBaseToolCatalog.storeToolNames
            )
        val access =
            ResolvedCharacterCardToolAccess(
                customEnabled = commonAccess.customEnabled,
                effectiveBuiltinToolVisibility = commonAccess.effectiveBuiltinToolVisibility,
                allowedPackageNames = emptySet(),
                allowedSkillNames = emptySet(),
                allowedMcpServerNames = emptySet(),
                canUsePackageSystem = false,
                hasAnyAllowedExternalSource = false
            )

        assertEquals(
            mapOf(
                CommonBaseToolCatalog.START_APP to true,
                CommonBaseToolCatalog.EXECUTE_INTENT to false
            ),
            commonAccess.effectiveBuiltinToolVisibility
        )
        assertFalse(access.customEnabled)
        assertTrue(access.isBuiltinToolAllowed(CommonBaseToolCatalog.START_APP))
        assertFalse(access.isBuiltinToolAllowed(CommonBaseToolCatalog.EXECUTE_INTENT))
        assertEquals(emptySet<String>(), access.allowedPackageNames)
        assertFalse(access.canUsePackageSystem)
        assertFalse(access.hasAnyAllowedExternalSource)
    }

    @Test
    fun confirmedStartAppOnSameAiTool_stillDeniedWhenDuplicatePackageNamePresent() {
        val commonAccess =
            CommonBaseCharacterCardToolAccess.resolve(
                customEnabled = true,
                allowedBuiltinTools = listOf(CommonBaseToolCatalog.START_APP),
                catalogToolNames = CommonBaseToolCatalog.storeToolNames
            )
        val access =
            ResolvedCharacterCardToolAccess(
                customEnabled = commonAccess.customEnabled,
                effectiveBuiltinToolVisibility = commonAccess.effectiveBuiltinToolVisibility,
                allowedPackageNames = emptySet(),
                allowedSkillNames = emptySet(),
                allowedMcpServerNames = emptySet(),
                canUsePackageSystem = false,
                hasAnyAllowedExternalSource = false
            )
        assertTrue(access.isBuiltinToolAllowed(CommonBaseToolCatalog.START_APP))

        val tool =
            AITool(
                name = CommonBaseToolCatalog.START_APP,
                parameters =
                    listOf(
                        ToolParameter("package_name", "com.example.unapproved"),
                        ToolParameter("package_name", CommonBaseToolCatalog.SETTINGS_PACKAGE)
                    )
            )
        val denied =
            CommonBaseExecutionGuard.resultFor(
                tool = tool,
                confirmation = CommonBaseConfirmation.ExplicitUserGrant,
                enabled = true,
                enhancedDevice = false
            )
        assertNotNull(denied)
        assertFalse(denied!!.success)
        assertEquals(CommonBaseToolCatalog.START_APP, denied.toolName)
        assertTrue(denied.error.orEmpty().contains("package_name"))

        val allowedSameTool =
            AITool(
                name = CommonBaseToolCatalog.START_APP,
                parameters =
                    listOf(ToolParameter("package_name", CommonBaseToolCatalog.SETTINGS_PACKAGE))
            )
        assertNull(
            CommonBaseExecutionGuard.resultFor(
                tool = allowedSameTool,
                confirmation = CommonBaseConfirmation.ExplicitUserGrant,
                enabled = true,
                enhancedDevice = false
            )
        )
        assertNotNull(
            CommonBaseExecutionGuard.resultFor(
                tool = allowedSameTool,
                confirmation = CommonBaseConfirmation.None,
                enabled = true,
                enhancedDevice = false
            )
        )
    }
}
