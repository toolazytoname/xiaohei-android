package com.ai.assistance.operit.ui.features.chat.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoheiWelcomeContentTest {

    @Test
    fun storeAndEnhancedNotes_arePublicPreviewWithSelfConfiguredModels() {
        val store = XiaoheiWelcomeContent.editionNote(commonStore = true)
        val enhanced = XiaoheiWelcomeContent.editionNote(commonStore = false)
        assertEquals(XiaoheiWelcomeContent.STORE_EDITION_NOTE, store)
        assertEquals(XiaoheiWelcomeContent.ENHANCED_EDITION_NOTE, enhanced)
        assertTrue(XiaoheiWelcomeContent.SUBTITLE.contains("公开预览"))
        assertTrue(XiaoheiWelcomeContent.SUBTITLE.contains("不是稳定版"))
        assertTrue(XiaoheiWelcomeContent.SUBTITLE.contains("你自己的服务地址"))
        assertTrue(store.contains("公开预览"))
        assertTrue(store.contains("自行配置"))
        assertTrue(store.contains("语音未完整验收"))
        assertFalse(store.contains("DSP"))
        assertTrue(enhanced.contains("OnePlus 8T"))
        assertTrue(enhanced.contains("自行配置"))
        assertTrue(enhanced.contains("语音与 DSP 未完整验收"))
        assertTrue(XiaoheiWelcomeContent.AFTER_CONFIG_HINT.contains("语音未完整验收"))
        assertFalse(store.contains("待填"))
        assertFalse(enhanced.contains("待填"))
        assertFalse(store.contains("同意"))
        assertFalse(enhanced.contains("已加密"))
    }
}
