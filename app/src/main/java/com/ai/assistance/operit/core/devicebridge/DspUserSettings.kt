package com.ai.assistance.operit.core.devicebridge

import android.content.Context

/** User DSP switch. Default off; persisted locally. Not a store-channel setting. */
class DspUserSettings(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isUserEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setUserEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "xiaohei_enhanced_dsp"
        private const val KEY_ENABLED = "dsp_enabled"
    }
}
