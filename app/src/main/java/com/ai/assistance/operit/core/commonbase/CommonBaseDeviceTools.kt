package com.ai.assistance.operit.core.commonbase

interface CommonBaseMediaVolumePort {
    val streamIndex: Int

    fun maxIndex(): Int

    fun currentIndex(): Int

    fun setIndex(index: Int)
}

interface CommonBaseDeviceStatusPort {
    fun batteryPercent(): Int

    fun charging(): Boolean

    fun screenInteractive(): Boolean

    fun keyguardLocked(): Boolean
}

data class CommonBaseDeviceStatusSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val mediaVolumePercent: Int,
    val mediaVolumeIndex: Int,
    val mediaVolumeMaxIndex: Int,
    val mediaVolumeStreamIndex: Int,
    val screenInteractive: Boolean,
    val keyguardLocked: Boolean
)

sealed class CommonBaseVolumeChange {
    data class Applied(
        val requestedPercent: Int,
        val actualPercent: Int,
        val targetIndex: Int,
        val actualIndex: Int,
        val maxIndex: Int,
        val streamIndex: Int
    ) : CommonBaseVolumeChange()

    data class NotApplied(
        val reason: String,
        val requestedPercent: Int,
        val targetIndex: Int? = null,
        val actualIndex: Int? = null,
        val maxIndex: Int? = null,
        val streamIndex: Int? = null
    ) : CommonBaseVolumeChange()
}

object CommonBaseDeviceVolume {
    fun parsePercent(raw: String): Int? {
        if (raw.isEmpty() || raw != raw.trim()) {
            return null
        }
        if (!raw.all { ch -> ch in '0'..'9' }) {
            return null
        }
        if (raw.length > 1 && raw[0] == '0') {
            return null
        }
        val value = raw.toIntOrNull() ?: return null
        if (value !in 0..100) {
            return null
        }
        if (value.toString() != raw) {
            return null
        }
        return value
    }

    fun percentToIndex(percent: Int, maxIndex: Int): Int {
        require(percent in 0..100) { "percent must be 0..100" }
        require(maxIndex > 0) { "maxIndex must be > 0" }
        return (((percent.toLong() * maxIndex.toLong()) + 50L) / 100L).toInt()
    }

    fun indexToPercent(index: Int, maxIndex: Int): Int {
        require(index in 0..maxIndex) { "index must be 0..maxIndex" }
        require(maxIndex > 0) { "maxIndex must be > 0" }
        return (((index.toLong() * 100L) + (maxIndex.toLong() / 2L)) / maxIndex.toLong()).toInt()
    }

    fun applyPercent(percent: Int, port: CommonBaseMediaVolumePort): CommonBaseVolumeChange {
        if (percent !in 0..100) {
            return CommonBaseVolumeChange.NotApplied(
                reason = "percent must be an integer 0..100",
                requestedPercent = percent
            )
        }
        val streamIndex = port.streamIndex
        val maxIndex =
            try {
                port.maxIndex()
            } catch (error: Exception) {
                return CommonBaseVolumeChange.NotApplied(
                    reason = "media volume max index unavailable: ${error.message ?: error::class.java.simpleName}",
                    requestedPercent = percent,
                    streamIndex = streamIndex
                )
            }
        if (maxIndex <= 0) {
            return CommonBaseVolumeChange.NotApplied(
                reason = "media volume max index is $maxIndex",
                requestedPercent = percent,
                maxIndex = maxIndex,
                streamIndex = streamIndex
            )
        }
        val targetIndex = percentToIndex(percent, maxIndex)
        try {
            port.setIndex(targetIndex)
        } catch (error: Exception) {
            return CommonBaseVolumeChange.NotApplied(
                reason = "setStreamVolume failed: ${error.message ?: error::class.java.simpleName}",
                requestedPercent = percent,
                targetIndex = targetIndex,
                maxIndex = maxIndex,
                streamIndex = streamIndex
            )
        }
        val actualIndex =
            try {
                port.currentIndex()
            } catch (error: Exception) {
                return CommonBaseVolumeChange.NotApplied(
                    reason = "media volume readback failed: ${error.message ?: error::class.java.simpleName}",
                    requestedPercent = percent,
                    targetIndex = targetIndex,
                    maxIndex = maxIndex,
                    streamIndex = streamIndex
                )
            }
        if (actualIndex != targetIndex) {
            return CommonBaseVolumeChange.NotApplied(
                reason =
                    "media volume readback index $actualIndex does not match requested index $targetIndex",
                requestedPercent = percent,
                targetIndex = targetIndex,
                actualIndex = actualIndex,
                maxIndex = maxIndex,
                streamIndex = streamIndex
            )
        }
        return CommonBaseVolumeChange.Applied(
            requestedPercent = percent,
            actualPercent = indexToPercent(actualIndex, maxIndex),
            targetIndex = targetIndex,
            actualIndex = actualIndex,
            maxIndex = maxIndex,
            streamIndex = streamIndex
        )
    }

    fun formatApplied(change: CommonBaseVolumeChange.Applied): String {
        return buildString {
            append("requested_percent=").append(change.requestedPercent).append('\n')
            append("actual_percent=").append(change.actualPercent).append('\n')
            append("actual_index=").append(change.actualIndex).append('\n')
            append("max_index=").append(change.maxIndex).append('\n')
            append("stream_index=").append(change.streamIndex)
        }
    }
}

object CommonBaseDeviceStatus {
    val allowedKeys: Set<String> =
        linkedSetOf(
            "battery_percent",
            "charging",
            "media_volume_percent",
            "media_volume_index",
            "media_volume_max_index",
            "media_volume_stream_index",
            "screen_interactive",
            "keyguard_locked"
        )

    val forbiddenKeys: Set<String> =
        linkedSetOf(
            "serial",
            "android_id",
            "device_id",
            "imei",
            "mac",
            "fingerprint",
            "package",
            "packages",
            "app_list",
            "androidid"
        )

    fun read(
        statusPort: CommonBaseDeviceStatusPort,
        volumePort: CommonBaseMediaVolumePort
    ): CommonBaseDeviceStatusSnapshot {
        val maxIndex = volumePort.maxIndex()
        require(maxIndex > 0) { "media volume max index is $maxIndex" }
        val currentIndex = volumePort.currentIndex()
        require(currentIndex in 0..maxIndex) {
            "media volume index $currentIndex is outside 0..$maxIndex"
        }
        val batteryPercent = statusPort.batteryPercent()
        require(batteryPercent in 0..100) { "battery percent out of range: $batteryPercent" }
        return CommonBaseDeviceStatusSnapshot(
            batteryPercent = batteryPercent,
            charging = statusPort.charging(),
            mediaVolumePercent = CommonBaseDeviceVolume.indexToPercent(currentIndex, maxIndex),
            mediaVolumeIndex = currentIndex,
            mediaVolumeMaxIndex = maxIndex,
            mediaVolumeStreamIndex = volumePort.streamIndex,
            screenInteractive = statusPort.screenInteractive(),
            keyguardLocked = statusPort.keyguardLocked()
        )
    }

    fun format(snapshot: CommonBaseDeviceStatusSnapshot): String {
        return buildString {
            append("battery_percent=").append(snapshot.batteryPercent).append('\n')
            append("charging=").append(snapshot.charging).append('\n')
            append("media_volume_percent=").append(snapshot.mediaVolumePercent).append('\n')
            append("media_volume_index=").append(snapshot.mediaVolumeIndex).append('\n')
            append("media_volume_max_index=").append(snapshot.mediaVolumeMaxIndex).append('\n')
            append("media_volume_stream_index=").append(snapshot.mediaVolumeStreamIndex).append('\n')
            append("screen_interactive=").append(snapshot.screenInteractive).append('\n')
            append("keyguard_locked=").append(snapshot.keyguardLocked)
        }
    }

    fun parseKeys(formatted: String): Set<String> {
        return formatted
            .lineSequence()
            .map { line -> line.substringBefore('=', missingDelimiterValue = "").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
