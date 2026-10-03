package com.ai.assistance.operit.core.commonbase

data class CommonBaseParameter(val name: String, val value: String)

data class CommonBaseCapabilityRequest(
    val toolName: String,
    val parameters: List<CommonBaseParameter> = emptyList(),
    val confirmation: CommonBaseConfirmation = CommonBaseConfirmation.None,
    val enhancedDevice: Boolean = false
)

sealed class CommonBaseDecision {
    data object AllowUnrestricted : CommonBaseDecision()

    data object Allow : CommonBaseDecision()

    data class Deny(val reason: String) : CommonBaseDecision()
}

object CommonBaseCapabilityPolicy {
    private val packageTools = setOf("use_package", "package_proxy")
    private val cliTools = setOf("search", "proxy")
    private val shellTools = setOf("execute_shell", "close_all_virtual_displays")
    private val fileTools =
        setOf(
            "list_files",
            "read_file",
            "read_file_part",
            "read_file_full",
            "write_file",
            "write_file_binary",
            "delete_file",
            "move_file",
            "copy_file",
            "file_exists",
            "find_files",
            "file_info",
            "zip_files",
            "unzip_files",
            "open_file",
            "share_file",
            "grep_code",
            "grep_context"
        )
    private val httpTools =
        setOf("http_request", "multipart_request", "manage_cookies", "visit_web", "download_file")
    private val uiTools =
        setOf(
            "get_page_info",
            "click_element",
            "tap",
            "swipe",
            "set_input_text",
            "press_key",
            "capture_screenshot",
            "ui_automator",
            "ui_node_info"
        )
    private val workflowTools = setOf("trigger_workflow", "create_workflow", "list_workflows")
    private val scriptTools = setOf("execute_js", "run_javascript")

    fun evaluateCatalog(
        enabled: Boolean,
        toolName: String,
        parameters: List<CommonBaseParameter> = emptyList(),
        enhancedDevice: Boolean = CommonBaseProfile.enhancedDevice
    ): CommonBaseDecision {
        if (!enabled) {
            return CommonBaseDecision.AllowUnrestricted
        }
        if (toolName.isEmpty() || toolName != toolName.trim()) {
            return CommonBaseDecision.Deny(
                "Tool name is empty or not canonical, so it cannot run in the common base profile."
            )
        }
        if (!CommonBaseToolCatalog.isPromisedTool(toolName, enhancedDevice)) {
            return CommonBaseDecision.Deny(denyReasonForUnpromisedTool(toolName, enhancedDevice))
        }
        firstDuplicateParameterName(parameters)?.let { duplicateName ->
            return duplicateParameterDenied(toolName, duplicateName)
        }
        return CommonBaseDecision.Allow
    }

    fun evaluateExecution(
        enabled: Boolean,
        request: CommonBaseCapabilityRequest
    ): CommonBaseDecision {
        if (!enabled) {
            return CommonBaseDecision.AllowUnrestricted
        }
        val enhancedDevice = request.enhancedDevice
        val catalog =
            evaluateCatalog(
                enabled = true,
                toolName = request.toolName,
                parameters = request.parameters,
                enhancedDevice = enhancedDevice
            )
        if (catalog is CommonBaseDecision.Deny) {
            return catalog
        }
        if (request.confirmation !is CommonBaseConfirmation.ExplicitUserGrant) {
            return CommonBaseDecision.Deny(
                "Tool '${request.toolName}' was refused because this common-base invocation has no explicit user confirmation. Internal JS, broadcasts, workflows, and auto-allow are not treated as confirmation."
            )
        }
        return when (request.toolName) {
            CommonBaseToolCatalog.START_APP -> evaluateStartApp(request.parameters)
            CommonBaseToolCatalog.EXECUTE_INTENT -> evaluateExecuteIntent(request.parameters)
            CommonBaseToolCatalog.GET_DEVICE_STATUS ->
                evaluateGetDeviceStatus(request.parameters, enhancedDevice)
            CommonBaseToolCatalog.SET_MEDIA_VOLUME ->
                evaluateSetMediaVolume(request.parameters, enhancedDevice)
            else ->
                CommonBaseDecision.Deny(
                    "Tool '${request.toolName}' is not an executable common-base capability."
                )
        }
    }

    fun packageActivationDeniedMessage(packageName: String): String {
        val shown = packageName.trim().ifEmpty { "(missing package name)" }
        return "Package activation is not available in the common base profile. Refused package '$shown'."
    }

    fun scriptBroadcastDeniedReason(): String {
        return "Script broadcast execution is not available in the common base profile."
    }

    private fun evaluateStartApp(parameters: List<CommonBaseParameter>): CommonBaseDecision {
        val collected =
            collectDeclaredParameters(
                parameters = parameters,
                allowedKeys = setOf("package_name"),
                unexpectedReason = { unexpected ->
                    "start_app in the common base profile only accepts package_name=${CommonBaseToolCatalog.SETTINGS_PACKAGE}. Unexpected parameters: ${unexpected.joinToString()}."
                }
            )
        collected.deny?.let { return it }
        val values = collected.values
        if (values["package_name"] != CommonBaseToolCatalog.SETTINGS_PACKAGE) {
            return CommonBaseDecision.Deny(
                "start_app in the common base profile can only open system Settings (package_name=${CommonBaseToolCatalog.SETTINGS_PACKAGE}). Arbitrary apps are not allowed."
            )
        }
        return CommonBaseDecision.Allow
    }

    private fun evaluateExecuteIntent(parameters: List<CommonBaseParameter>): CommonBaseDecision {
        val collected =
            collectDeclaredParameters(
                parameters = parameters,
                allowedKeys = setOf("action", "uri", "package", "type"),
                unexpectedReason = { unexpected ->
                    "execute_intent in the common base profile rejects extras, flags, component, and other undeclared parameters. Unexpected parameters: ${unexpected.joinToString()}."
                }
            )
        collected.deny?.let { return it }
        val values = collected.values
        val type = values["type"] ?: CommonBaseToolCatalog.TYPE_ACTIVITY
        if (type != CommonBaseToolCatalog.TYPE_ACTIVITY) {
            return CommonBaseDecision.Deny(
                "execute_intent in the common base profile only allows type=${CommonBaseToolCatalog.TYPE_ACTIVITY}. service and broadcast are not allowed."
            )
        }
        val action = values["action"]
        val uri = values["uri"]
        val packageName = values["package"]
        return when (action) {
            CommonBaseToolCatalog.ACTION_SETTINGS -> {
                if (uri != null) {
                    return CommonBaseDecision.Deny(
                        "Opening Settings with execute_intent cannot include a URI."
                    )
                }
                if (packageName != null && packageName != CommonBaseToolCatalog.SETTINGS_PACKAGE) {
                    return CommonBaseDecision.Deny(
                        "Opening Settings with execute_intent cannot target package '$packageName'."
                    )
                }
                CommonBaseDecision.Allow
            }
            CommonBaseToolCatalog.ACTION_PICK -> {
                if (packageName != null) {
                    return CommonBaseDecision.Deny(
                        "Opening the system gallery with ACTION_PICK cannot set a package."
                    )
                }
                if (uri != CommonBaseToolCatalog.GALLERY_IMAGE_URI) {
                    return CommonBaseDecision.Deny(
                        "Opening the system gallery with ACTION_PICK requires uri=${CommonBaseToolCatalog.GALLERY_IMAGE_URI}."
                    )
                }
                CommonBaseDecision.Allow
            }
            CommonBaseToolCatalog.ACTION_PICK_IMAGES -> {
                if (packageName != null || uri != null) {
                    return CommonBaseDecision.Deny(
                        "Opening the system photo picker cannot include package or uri parameters."
                    )
                }
                CommonBaseDecision.Allow
            }
            else ->
                CommonBaseDecision.Deny(
                    "execute_intent in the common base profile only allows opening system Settings or the system gallery. This action/URI/package combination is not allowed."
                )
        }
    }

    private fun evaluateGetDeviceStatus(
        parameters: List<CommonBaseParameter>,
        enhancedDevice: Boolean
    ): CommonBaseDecision {
        if (!enhancedDevice) {
            return CommonBaseDecision.Deny(
                denyReasonForUnpromisedTool(CommonBaseToolCatalog.GET_DEVICE_STATUS, enhancedDevice = false)
            )
        }
        val collected =
            collectDeclaredParameters(
                parameters = parameters,
                allowedKeys = emptySet(),
                unexpectedReason = { unexpected ->
                    "get_device_status does not accept parameters. Unexpected parameters: ${unexpected.joinToString()}."
                }
            )
        collected.deny?.let { return it }
        return CommonBaseDecision.Allow
    }

    private fun evaluateSetMediaVolume(
        parameters: List<CommonBaseParameter>,
        enhancedDevice: Boolean
    ): CommonBaseDecision {
        if (!enhancedDevice) {
            return CommonBaseDecision.Deny(
                denyReasonForUnpromisedTool(CommonBaseToolCatalog.SET_MEDIA_VOLUME, enhancedDevice = false)
            )
        }
        val collected =
            collectDeclaredParameters(
                parameters = parameters,
                allowedKeys = setOf(CommonBaseToolCatalog.PERCENT),
                unexpectedReason = { unexpected ->
                    "set_media_volume only accepts percent as an integer 0..100. Unexpected parameters: ${unexpected.joinToString()}."
                }
            )
        collected.deny?.let { return it }
        val raw = collected.values[CommonBaseToolCatalog.PERCENT]
        if (raw == null) {
            return CommonBaseDecision.Deny(
                "set_media_volume requires percent as an integer 0..100."
            )
        }
        if (CommonBaseDeviceVolume.parsePercent(raw) == null) {
            return CommonBaseDecision.Deny(
                "set_media_volume percent must be an integer 0..100 with no leading zeros, sign, decimal, or suffix."
            )
        }
        return CommonBaseDecision.Allow
    }

    private fun denyReasonForUnpromisedTool(toolName: String, enhancedDevice: Boolean): String {
        val family =
            when {
                toolName.contains(':') || packageTools.contains(toolName) ->
                    "Package activation and package tools"
                cliTools.contains(toolName) -> "CLI search/proxy tools"
                shellTools.contains(toolName) -> "Shell/terminal tools"
                fileTools.contains(toolName) -> "File tools"
                httpTools.contains(toolName) -> "HTTP/network tools"
                uiTools.contains(toolName) -> "UI automation tools"
                workflowTools.contains(toolName) -> "Workflow tools"
                scriptTools.contains(toolName) -> "Script tools"
                toolName == "send_broadcast" -> "Broadcast tools"
                else -> "Tool '$toolName'"
            }
        return "$family are not available in the common base profile. This profile only allows ${promisedScope(enhancedDevice)} after explicit user confirmation."
    }

    private fun promisedScope(enhancedDevice: Boolean): String {
        return if (enhancedDevice) {
            "system Settings, the system gallery, get_device_status, and set_media_volume"
        } else {
            "system Settings or the system gallery"
        }
    }

    private fun firstDuplicateParameterName(parameters: List<CommonBaseParameter>): String? {
        val seen = LinkedHashSet<String>()
        for (parameter in parameters) {
            if (!seen.add(parameter.name)) {
                return parameter.name
            }
        }
        return null
    }

    private fun duplicateParameterDenied(toolName: String, parameterName: String): CommonBaseDecision.Deny {
        return CommonBaseDecision.Deny(
            "Tool '$toolName' was refused because parameter '$parameterName' was supplied more than once. Common-base validation does not last-win duplicate parameters."
        )
    }

    private fun collectDeclaredParameters(
        parameters: List<CommonBaseParameter>,
        allowedKeys: Set<String>,
        unexpectedReason: (List<String>) -> String
    ): DeclaredParameterCollection {
        val unexpected = LinkedHashSet<String>()
        val values = LinkedHashMap<String, String>()
        for (parameter in parameters) {
            val name = parameter.name
            if (name.isEmpty() || name != name.trim()) {
                return DeclaredParameterCollection(
                    deny =
                        CommonBaseDecision.Deny(
                            "Common-base tool parameters must use exact declared names without surrounding whitespace."
                        )
                )
            }
            if (name !in allowedKeys) {
                unexpected.add(name)
                continue
            }
            val value = parameter.value
            if (value.isEmpty() || value != value.trim()) {
                return DeclaredParameterCollection(
                    deny =
                        CommonBaseDecision.Deny(
                            "Common-base tool parameter '$name' must be a non-empty exact value without surrounding whitespace."
                        )
                )
            }
            values[name] = value
        }
        if (unexpected.isNotEmpty()) {
            return DeclaredParameterCollection(
                deny = CommonBaseDecision.Deny(unexpectedReason(unexpected.sorted()))
            )
        }
        return DeclaredParameterCollection(values = values)
    }

    private data class DeclaredParameterCollection(
        val values: Map<String, String> = emptyMap(),
        val deny: CommonBaseDecision.Deny? = null
    )
}
