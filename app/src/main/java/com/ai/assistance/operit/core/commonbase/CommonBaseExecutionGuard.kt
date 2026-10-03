package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

object CommonBaseExecutionGuard {
    fun catalogResultFor(
        tool: AITool,
        enabled: Boolean = CommonBaseProfile.isEnabled,
        enhancedDevice: Boolean = CommonBaseProfile.enhancedDevice
    ): ToolResult? {
        return toResult(
            toolName = tool.name,
            decision =
                CommonBaseCapabilityPolicy.evaluateCatalog(
                    enabled = enabled,
                    toolName = tool.name,
                    parameters = parametersOf(tool),
                    enhancedDevice = enhancedDevice
                )
        )
    }

    fun resultFor(
        tool: AITool,
        confirmation: CommonBaseConfirmation = CommonBaseConfirmationSource.current(),
        enabled: Boolean = CommonBaseProfile.isEnabled,
        enhancedDevice: Boolean = CommonBaseProfile.enhancedDevice
    ): ToolResult? {
        return toResult(
            toolName = tool.name,
            decision =
                CommonBaseCapabilityPolicy.evaluateExecution(
                    enabled = enabled,
                    request =
                        CommonBaseCapabilityRequest(
                            toolName = tool.name,
                            parameters = parametersOf(tool),
                            confirmation = confirmation,
                            enhancedDevice = enhancedDevice
                        )
                )
        )
    }

    private fun parametersOf(tool: AITool): List<CommonBaseParameter> {
        // Keep the original list. Do not associate() here: last-wins would disagree with
        // executors that find() the first value.
        return tool.parameters.map { parameter ->
            CommonBaseParameter(name = parameter.name, value = parameter.value)
        }
    }

    private fun toResult(toolName: String, decision: CommonBaseDecision): ToolResult? {
        return when (decision) {
            CommonBaseDecision.AllowUnrestricted,
            CommonBaseDecision.Allow -> null
            is CommonBaseDecision.Deny ->
                ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                    error = decision.reason
                )
        }
    }
}
