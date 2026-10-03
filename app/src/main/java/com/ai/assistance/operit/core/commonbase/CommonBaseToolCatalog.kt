package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt

/**
 * Single allow-list for registration, prompts, and execution. Store: confirmed Settings /
 * system-gallery intents only. Enhanced: those plus get_device_status / set_media_volume.
 * Callers keep using the existing properties; they select via [CommonBaseProfile.enhancedDevice].
 */
object CommonBaseToolCatalog {
    const val START_APP = "start_app"
    const val EXECUTE_INTENT = "execute_intent"
    const val GET_DEVICE_STATUS = "get_device_status"
    const val SET_MEDIA_VOLUME = "set_media_volume"

    const val SETTINGS_PACKAGE = "com.android.settings"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"
    const val ACTION_PICK = "android.intent.action.PICK"
    const val ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES"
    const val GALLERY_IMAGE_URI = "content://media/external/images/media"
    const val TYPE_ACTIVITY = "activity"
    const val PERCENT = "percent"

    val storeToolNames: Set<String> = linkedSetOf(START_APP, EXECUTE_INTENT)

    val enhancedToolNames: Set<String> = linkedSetOf(GET_DEVICE_STATUS, SET_MEDIA_VOLUME)

    val registeredToolNames: Set<String>
        get() = toolNames(enhancedDevice = CommonBaseProfile.enhancedDevice)

    val promptToolNames: Set<String>
        get() = registeredToolNames

    fun toolNames(enhancedDevice: Boolean): Set<String> {
        return if (enhancedDevice) {
            LinkedHashSet<String>(storeToolNames.size + enhancedToolNames.size).apply {
                addAll(storeToolNames)
                addAll(enhancedToolNames)
            }
        } else {
            storeToolNames
        }
    }

    fun isPromisedTool(
        toolName: String,
        enhancedDevice: Boolean = CommonBaseProfile.enhancedDevice
    ): Boolean = toolNames(enhancedDevice).contains(toolName)

    fun requiresUserConfirmation(
        toolName: String,
        enhancedDevice: Boolean = CommonBaseProfile.enhancedDevice
    ): Boolean = isPromisedTool(toolName, enhancedDevice)

    fun forceExplicitConfirmation(
        toolName: String,
        enabled: Boolean = CommonBaseProfile.isEnabled,
        enhancedDevice: Boolean = CommonBaseProfile.enhancedDevice
    ): Boolean = enabled && requiresUserConfirmation(toolName, enhancedDevice)

    val promptCategoriesEn: List<SystemToolPromptCategory>
        get() = promptCategories(useEnglish = true, enhancedDevice = CommonBaseProfile.enhancedDevice)

    val promptCategoriesCn: List<SystemToolPromptCategory>
        get() = promptCategories(useEnglish = false, enhancedDevice = CommonBaseProfile.enhancedDevice)

    fun promptCategories(
        useEnglish: Boolean,
        enhancedDevice: Boolean
    ): List<SystemToolPromptCategory> {
        val tools = mutableListOf<ToolPrompt>()
        if (useEnglish) {
            tools.add(
                startAppPrompt(
                    description =
                        "Open the system Settings app after the user confirms this invocation. package_name must be com.android.settings. Do not pass activity."
                )
            )
            tools.add(
                executeIntentPrompt(
                    description =
                        "Open system Settings or the system gallery after the user confirms this invocation. Only activity intents with the documented action/URI pairs are allowed. service, broadcast, extras, flags, and arbitrary components are rejected."
                )
            )
            if (enhancedDevice) {
                tools.add(getDeviceStatusPrompt(useEnglish = true))
                tools.add(setMediaVolumePrompt(useEnglish = true))
            }
            return listOf(SystemToolPromptCategory(categoryName = "Available tools", tools = tools))
        }
        tools.add(
            startAppPrompt(
                description =
                    "在用户确认本次调用后打开系统设置。package_name 必须是 com.android.settings，不要传 activity。"
            )
        )
        tools.add(
            executeIntentPrompt(
                description =
                    "在用户确认本次调用后打开系统设置或系统相册。仅允许文档中的 activity action/URI 组合。service、broadcast、extras、flags 和任意 component 都会被拒绝。"
            )
        )
        if (enhancedDevice) {
            tools.add(getDeviceStatusPrompt(useEnglish = false))
            tools.add(setMediaVolumePrompt(useEnglish = false))
        }
        return listOf(SystemToolPromptCategory(categoryName = "可用工具", tools = tools))
    }

    val usageGuidelinesEn: String
        get() = usageGuidelines(useEnglish = true, enhancedDevice = CommonBaseProfile.enhancedDevice)

    val usageGuidelinesCn: String
        get() = usageGuidelines(useEnglish = false, enhancedDevice = CommonBaseProfile.enhancedDevice)

    fun usageGuidelines(useEnglish: Boolean, enhancedDevice: Boolean): String {
        return if (useEnglish) {
            val scope =
                if (enhancedDevice) {
                    "This profile can open system Settings or the system gallery, call get_device_status, and call set_media_volume. Do not call packages, files, HTTP, shell, UI, or workflow tools."
                } else {
                    "This profile can only open system Settings or the system gallery. Do not call packages, files, HTTP, shell, UI, or workflow tools."
                }
            """
When calling a tool, the user will see your response, then confirm the action before it runs.

<tool name="tool_name">
<param name="parameter_name">parameter_value</param>
</tool>

$scope
""".trimIndent()
        } else {
            val scope =
                if (enhancedDevice) {
                    "当前配置可以打开系统设置或系统相册，并在确认后调用 get_device_status 与 set_media_volume。不要调用包、文件、HTTP、shell、UI 或工作流工具。"
                } else {
                    "当前配置只能打开系统设置或系统相册。不要调用包、文件、HTTP、shell、UI 或工作流工具。"
                }
            """
调用工具时，用户会看到你的响应，并在执行前确认该操作。

<tool name="tool_name">
<param name="parameter_name">parameter_value</param>
</tool>

$scope
""".trimIndent()
        }
    }

    fun permissionDescription(toolName: String, percent: String? = null): String {
        return when (toolName) {
            START_APP -> "Open system Settings"
            EXECUTE_INTENT -> "Open system Settings or the system gallery"
            GET_DEVICE_STATUS ->
                "Read battery, charging, media volume, and screen interactive/lock state"
            SET_MEDIA_VOLUME ->
                if (percent.isNullOrEmpty()) {
                    "Set media volume"
                } else {
                    "Set media volume to $percent percent"
                }
            else -> toolName
        }
    }

    private fun startAppPrompt(description: String): ToolPrompt {
        return ToolPrompt(
            name = START_APP,
            description = description,
            parametersStructured =
                listOf(
                    ToolParameterSchema(
                        name = "package_name",
                        type = "string",
                        description = "must be com.android.settings",
                        required = true
                    )
                )
        )
    }

    private fun executeIntentPrompt(description: String): ToolPrompt {
        return ToolPrompt(
            name = EXECUTE_INTENT,
            description = description,
            parametersStructured =
                listOf(
                    ToolParameterSchema(
                        name = "action",
                        type = "string",
                        description =
                            "android.settings.SETTINGS, android.intent.action.PICK, or android.provider.action.PICK_IMAGES",
                        required = true
                    ),
                    ToolParameterSchema(
                        name = "uri",
                        type = "string",
                        description =
                            "required for PICK: content://media/external/images/media; empty for SETTINGS and PICK_IMAGES",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "package",
                        type = "string",
                        description = "empty, or com.android.settings only with ACTION_SETTINGS",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "type",
                        type = "string",
                        description = "must be activity when present",
                        required = false,
                        default = TYPE_ACTIVITY
                    )
                )
        )
    }

    private fun getDeviceStatusPrompt(useEnglish: Boolean): ToolPrompt {
        return ToolPrompt(
            name = GET_DEVICE_STATUS,
            description =
                if (useEnglish) {
                    "Read battery percent, charging, media volume, and whether the screen is interactive or keyguard-locked after the user confirms this invocation. No parameters. Does not return serial numbers, app lists, or other identifiers."
                } else {
                    "在用户确认本次调用后读取电量百分比、充电状态、媒体音量和屏幕是否可交互/是否锁定。不要传参数。不返回序列号、应用列表或其他标识。"
                },
            parametersStructured = emptyList()
        )
    }

    private fun setMediaVolumePrompt(useEnglish: Boolean): ToolPrompt {
        return ToolPrompt(
            name = SET_MEDIA_VOLUME,
            description =
                if (useEnglish) {
                    "Set media volume after the user confirms this invocation. percent must be an integer 0..100. The result reports the quantized stream index actually applied; a mismatch is a failure."
                } else {
                    "在用户确认本次调用后设置媒体音量。percent 必须是 0 到 100 的整数。结果报告实际量化后的 stream index；回读不一致视为失败。"
                },
            parametersStructured =
                listOf(
                    ToolParameterSchema(
                        name = PERCENT,
                        type = "integer",
                        description = "integer 0..100, no leading zeros, sign, decimal, or suffix",
                        required = true
                    )
                )
        )
    }
}
