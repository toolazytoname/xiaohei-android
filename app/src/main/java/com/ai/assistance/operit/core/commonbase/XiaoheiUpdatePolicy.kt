package com.ai.assistance.operit.core.commonbase

/** Branded fork APKs and patch payloads must never come from the upstream channel. */
object XiaoheiUpdatePolicy {
    suspend fun <T> check(
        commonBaseEnabled: Boolean,
        unavailable: () -> T,
        fetchUpstream: suspend () -> T,
    ): T {
        if (commonBaseEnabled) return unavailable()
        return fetchUpstream()
    }
}
