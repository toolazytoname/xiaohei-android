package com.ai.assistance.operit.core.commonbase

/**
 * Common-base chat/tool paths must not call package/MCP lookups. The [lookup] lambda is not invoked
 * when [enabled] is true; original [enabled]=false still runs [lookup] and must not swallow errors.
 */
object CommonBasePackageLookup {
    fun <T> skipWhenCommon(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        commonValue: T,
        lookup: () -> T
    ): T {
        if (enabled) {
            return commonValue
        }
        return lookup()
    }

    fun availableNames(
        enabled: Boolean = CommonBaseProfile.isEnabled,
        lookup: () -> Set<String>
    ): Set<String> {
        return skipWhenCommon(
            enabled = enabled,
            commonValue = emptySet(),
            lookup = lookup
        )
    }
}
