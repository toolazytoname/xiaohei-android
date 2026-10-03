package com.ai.assistance.operit.core.commonbase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class XiaoheiUpdatePolicyTest {
    @Test fun brandedProfilesNeverRequestOrOfferUpstreamPayloads() = runBlocking {
        var calls = 0
        val result = XiaoheiUpdatePolicy.check(
            commonBaseEnabled = true,
            unavailable = { "channel_not_open" },
            fetchUpstream = { calls++; "upstream_apk_or_patch" },
        )
        assertEquals(0, calls)
        assertEquals("channel_not_open", result)
    }

    @Test fun upstreamProfilesRetainTheirExistingUpdateChannel() = runBlocking {
        var calls = 0
        val result = XiaoheiUpdatePolicy.check(
            commonBaseEnabled = false,
            unavailable = { error("Not a Xiaohei profile") },
            fetchUpstream = { calls++; "upstream_apk_or_patch" },
        )
        assertEquals(1, calls)
        assertEquals("upstream_apk_or_patch", result)
    }
}
