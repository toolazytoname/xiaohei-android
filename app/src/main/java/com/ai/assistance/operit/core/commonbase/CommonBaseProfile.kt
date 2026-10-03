package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.BuildConfig

/**
 * Compile-time common-base profile. Original debug/release keep [BuildConfig.COMMON_BASE] false.
 * [storeNativeTrim] is true when COMMON_STORE or COMMON_PUBLIC_PREVIEW is set.
 * common(debug) keeps native tools packaged. Both public preview variants trim the
 * same FFmpeg / OCR / terminal JNI set. That trim is not COMMON_STORE: enhanced
 * device tools stay on [enhancedDevice].
 *
 * Both store and enhanced sideload keep [isEnabled] true. Enhanced device tools are a separate
 * flag: [enhancedDevice] is true only for the legal combination COMMON_BASE && COMMON_ENHANCED &&
 * !COMMON_STORE. Illegal combinations fail closed (no enhanced tools).
 */
object CommonBaseProfile {
    val isEnabled: Boolean
        get() = BuildConfig.COMMON_BASE

    val storeNativeTrim: Boolean
        get() =
            resolveStoreNativeTrim(
                commonStore = BuildConfig.COMMON_STORE,
                commonPublicPreview = BuildConfig.COMMON_PUBLIC_PREVIEW
            )

    val enhancedDevice: Boolean
        get() =
            resolveEnhancedDevice(
                commonBase = BuildConfig.COMMON_BASE,
                commonStore = BuildConfig.COMMON_STORE,
                commonEnhanced = BuildConfig.COMMON_ENHANCED
            )

    fun resolveStoreNativeTrim(
        commonStore: Boolean,
        commonPublicPreview: Boolean
    ): Boolean {
        return commonStore || commonPublicPreview
    }

    fun resolveEnhancedDevice(
        commonBase: Boolean,
        commonStore: Boolean,
        commonEnhanced: Boolean
    ): Boolean {
        if (!commonEnhanced) {
            return false
        }
        if (!commonBase) {
            return false
        }
        if (commonStore) {
            return false
        }
        return true
    }
}
