package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommonBaseExecutionGuardTest {

    @Test
    fun duplicatePackageName_bothOrdersDeniedThroughGuard() {
        assertDenied(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to OTHER_PACKAGE,
                "package_name" to CommonBaseToolCatalog.SETTINGS_PACKAGE
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to CommonBaseToolCatalog.SETTINGS_PACKAGE,
                "package_name" to OTHER_PACKAGE
            )
        )
    }

    @Test
    fun duplicateAction_bothOrdersDeniedThroughGuard() {
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to OTHER_ACTION,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "action" to OTHER_ACTION
            )
        )
    }

    @Test
    fun duplicateType_bothOrdersDeniedThroughGuard() {
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "type" to "service",
                "type" to CommonBaseToolCatalog.TYPE_ACTIVITY
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "type" to CommonBaseToolCatalog.TYPE_ACTIVITY,
                "type" to "service"
            )
        )
    }

    @Test
    fun duplicateUri_bothOrdersDeniedThroughGuard() {
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_PICK,
                "uri" to OTHER_URI,
                "uri" to CommonBaseToolCatalog.GALLERY_IMAGE_URI
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_PICK,
                "uri" to CommonBaseToolCatalog.GALLERY_IMAGE_URI,
                "uri" to OTHER_URI
            )
        )
    }

    @Test
    fun duplicateComponent_bothOrdersDeniedThroughGuard() {
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "component" to OTHER_COMPONENT,
                "component" to OTHER_COMPONENT_ALT
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "component" to OTHER_COMPONENT_ALT,
                "component" to OTHER_COMPONENT
            )
        )
    }

    @Test
    fun missingConfirmation_refusesPromisedTools() {
        assertDenied(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to CommonBaseToolCatalog.SETTINGS_PACKAGE
            ),
            confirmation = CommonBaseConfirmation.None
        )
        assertDenied(
            settingsIntent(),
            confirmation = CommonBaseConfirmation.None
        )
    }

    @Test
    fun confirmedSettingsAndPhotoPicker_areAllowed() {
        assertAllowed(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to CommonBaseToolCatalog.SETTINGS_PACKAGE
            )
        )
        assertAllowed(settingsIntent())
        assertAllowed(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_PICK,
                "uri" to CommonBaseToolCatalog.GALLERY_IMAGE_URI
            )
        )
        assertAllowed(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_PICK_IMAGES
            )
        )
    }

    @Test
    fun arbitraryPackageActionServiceBroadcastAndExtras_areDenied() {
        assertDenied(
            tool(CommonBaseToolCatalog.START_APP, "package_name" to OTHER_PACKAGE)
        )
        assertDenied(
            tool(CommonBaseToolCatalog.EXECUTE_INTENT, "action" to OTHER_ACTION)
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "type" to "service"
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "type" to "broadcast"
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "extras" to ""
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to CommonBaseToolCatalog.ACTION_SETTINGS,
                "component" to ""
            )
        )
        assertDenied(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to " ${CommonBaseToolCatalog.SETTINGS_PACKAGE} "
            )
        )
    }

    @Test
    fun unknownTools_areDeniedWhenEnabled() {
        assertDenied(tool("execute_shell", "command" to "id"))
        assertDenied(tool("send_broadcast", "action" to OTHER_ACTION))
        assertDenied(tool("use_package", "package_name" to OTHER_PACKAGE))
    }

    @Test
    fun originalProfile_doesNotRestrictThroughGuard() {
        val confirmation = CommonBaseConfirmation.None
        assertAllowed(
            tool(
                CommonBaseToolCatalog.START_APP,
                "package_name" to OTHER_PACKAGE,
                "package_name" to CommonBaseToolCatalog.SETTINGS_PACKAGE
            ),
            confirmation = confirmation,
            enabled = false
        )
        assertAllowed(
            tool(
                CommonBaseToolCatalog.EXECUTE_INTENT,
                "action" to OTHER_ACTION,
                "type" to "service",
                "extras" to "{\"x\":1}",
                "component" to OTHER_COMPONENT
            ),
            confirmation = confirmation,
            enabled = false
        )
        assertAllowed(
            tool("execute_shell", "command" to "id"),
            confirmation = confirmation,
            enabled = false
        )
        assertAllowed(
            tool("send_broadcast", "action" to OTHER_ACTION),
            confirmation = confirmation,
            enabled = false
        )
    }

    private fun settingsIntent(): AITool {
        return tool(
            CommonBaseToolCatalog.EXECUTE_INTENT,
            "action" to CommonBaseToolCatalog.ACTION_SETTINGS
        )
    }

    private fun tool(name: String, vararg parameters: Pair<String, String>): AITool {
        return AITool(
            name = name,
            parameters = parameters.map { parameter -> ToolParameter(parameter.first, parameter.second) }
        )
    }

    private fun assertAllowed(
        tool: AITool,
        confirmation: CommonBaseConfirmation = CommonBaseConfirmation.ExplicitUserGrant,
        enabled: Boolean = true,
        enhancedDevice: Boolean = false
    ) {
        assertNull(decide(tool, confirmation, enabled, enhancedDevice))
    }

    private fun assertDenied(
        tool: AITool,
        confirmation: CommonBaseConfirmation = CommonBaseConfirmation.ExplicitUserGrant,
        enabled: Boolean = true,
        enhancedDevice: Boolean = false
    ) {
        val denied = decide(tool, confirmation, enabled, enhancedDevice)
        assertNotNull(denied)
        assertFalse(denied!!.success)
        assertFalse(denied.error.isNullOrBlank())
    }

    private fun decide(
        tool: AITool,
        confirmation: CommonBaseConfirmation,
        enabled: Boolean,
        enhancedDevice: Boolean = false
    ): ToolResult? {
        return CommonBaseExecutionGuard.resultFor(
            tool = tool,
            confirmation = confirmation,
            enabled = enabled,
            enhancedDevice = enhancedDevice
        )
    }

    companion object {
        private const val OTHER_PACKAGE = "com.example.unapproved"
        private const val OTHER_ACTION = "android.intent.action.VIEW"
        private const val OTHER_URI = "content://com.example/item"
        private const val OTHER_COMPONENT = "com.example/.OtherActivity"
        private const val OTHER_COMPONENT_ALT = "com.example/.AltActivity"
    }
}
