package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

class CommonBaseDeviceToolExecutor(
    private val statusPort: CommonBaseDeviceStatusPort,
    private val volumePort: CommonBaseMediaVolumePort
) {
    fun getDeviceStatus(tool: AITool): ToolResult {
        return try {
            val snapshot = CommonBaseDeviceStatus.read(statusPort, volumePort)
            val formatted = CommonBaseDeviceStatus.format(snapshot)
            val keys = CommonBaseDeviceStatus.parseKeys(formatted)
            require(keys == CommonBaseDeviceStatus.allowedKeys) {
                "device status keys $keys do not match the allow-list"
            }
            require(keys.none { key -> CommonBaseDeviceStatus.forbiddenKeys.contains(key) }) {
                "device status included a forbidden identifier key"
            }
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(formatted)
            )
        } catch (error: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error =
                    "get_device_status failed: ${error.message ?: error::class.java.simpleName}"
            )
        }
    }

    fun setMediaVolume(tool: AITool): ToolResult {
        val percentValue =
            tool.parameters
                .firstOrNull { parameter -> parameter.name == CommonBaseToolCatalog.PERCENT }
                ?.value
        val percent = percentValue?.let { CommonBaseDeviceVolume.parsePercent(it) }
        if (percent == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "set_media_volume requires percent as an integer 0..100."
            )
        }
        return when (val change = CommonBaseDeviceVolume.applyPercent(percent, volumePort)) {
            is CommonBaseVolumeChange.Applied ->
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(CommonBaseDeviceVolume.formatApplied(change))
                )
            is CommonBaseVolumeChange.NotApplied ->
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(formatNotApplied(change)),
                    error = change.reason
                )
        }
    }

    private fun formatNotApplied(change: CommonBaseVolumeChange.NotApplied): String {
        return buildString {
            append("requested_percent=").append(change.requestedPercent)
            change.targetIndex?.let { append("\ntarget_index=").append(it) }
            change.actualIndex?.let { append("\nactual_index=").append(it) }
            change.maxIndex?.let { append("\nmax_index=").append(it) }
            change.streamIndex?.let { append("\nstream_index=").append(it) }
        }
    }
}
