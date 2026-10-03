package com.ai.assistance.operit.core.commonbase

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager

internal class AndroidMediaVolumePort(context: Context) : CommonBaseMediaVolumePort {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val streamIndex: Int = AudioManager.STREAM_MUSIC

    override fun maxIndex(): Int = audioManager.getStreamMaxVolume(streamIndex)

    override fun currentIndex(): Int = audioManager.getStreamVolume(streamIndex)

    override fun setIndex(index: Int) {
        audioManager.setStreamVolume(streamIndex, index, /* flags= */ 0)
    }
}

internal class AndroidDeviceStatusPort(context: Context) : CommonBaseDeviceStatusPort {
    private val appContext = context.applicationContext

    override fun batteryPercent(): Int {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        require(percent in 0..100) { "battery percent out of range: $percent" }
        return percent
    }

    override fun charging(): Boolean {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return manager.isCharging
    }

    override fun screenInteractive(): Boolean {
        val manager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return manager.isInteractive
    }

    override fun keyguardLocked(): Boolean {
        val manager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return manager.isKeyguardLocked
    }
}

internal fun commonBaseDeviceToolExecutor(context: Context): CommonBaseDeviceToolExecutor {
    return CommonBaseDeviceToolExecutor(
        statusPort = AndroidDeviceStatusPort(context),
        volumePort = AndroidMediaVolumePort(context)
    )
}
