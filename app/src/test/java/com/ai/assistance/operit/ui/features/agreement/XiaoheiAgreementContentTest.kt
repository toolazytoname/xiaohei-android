package com.ai.assistance.operit.ui.features.agreement

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoheiAgreementContentTest {

    @Test
    fun onlyCommonBaseUsesXiaoheiContent() {
        assertTrue(XiaoheiAgreementContent.usesXiaoheiContent(true))
        assertFalse(XiaoheiAgreementContent.usesXiaoheiContent(false))
    }

    @Test
    fun profileAndVersion_areExclusivePerBuildFlags() {
        assertEquals(
            XiaoheiAgreementContent.Profile.UPSTREAM,
            XiaoheiAgreementContent.resolveProfile(commonBase = false, commonStore = false)
        )
        assertEquals(
            XiaoheiAgreementContent.Profile.UPSTREAM,
            XiaoheiAgreementContent.resolveProfile(commonBase = false, commonStore = true)
        )
        assertEquals(
            XiaoheiAgreementContent.Profile.STORE,
            XiaoheiAgreementContent.resolveProfile(commonBase = true, commonStore = true)
        )
        assertEquals(
            XiaoheiAgreementContent.Profile.ENHANCED,
            XiaoheiAgreementContent.resolveProfile(commonBase = true, commonStore = false)
        )

        assertEquals(
            XiaoheiAgreementContent.UPSTREAM_AGREEMENT_VERSION,
            XiaoheiAgreementContent.resolveAgreementVersion(false, false)
        )
        assertEquals(
            XiaoheiAgreementContent.UPSTREAM_AGREEMENT_VERSION,
            XiaoheiAgreementContent.resolveAgreementVersion(false, true)
        )
        assertEquals(
            XiaoheiAgreementContent.STORE_NOTICE_VERSION,
            XiaoheiAgreementContent.resolveAgreementVersion(true, true)
        )
        assertEquals(
            XiaoheiAgreementContent.ENHANCED_NOTICE_VERSION,
            XiaoheiAgreementContent.resolveAgreementVersion(true, false)
        )
        assertEquals("2026-07-15", XiaoheiAgreementContent.UPSTREAM_AGREEMENT_VERSION)
        assertNotEquals(
            XiaoheiAgreementContent.UPSTREAM_AGREEMENT_VERSION,
            XiaoheiAgreementContent.STORE_NOTICE_VERSION
        )
        assertNotEquals(
            XiaoheiAgreementContent.UPSTREAM_AGREEMENT_VERSION,
            XiaoheiAgreementContent.ENHANCED_NOTICE_VERSION
        )
        assertNotEquals(
            XiaoheiAgreementContent.STORE_NOTICE_VERSION,
            XiaoheiAgreementContent.ENHANCED_NOTICE_VERSION
        )
    }

    @Test
    fun oldOperitVersion_doesNotConfirmXiaoheiNotice() {
        val old = XiaoheiAgreementContent.UPSTREAM_AGREEMENT_VERSION
        val store = XiaoheiAgreementContent.STORE_NOTICE_VERSION
        val enhanced = XiaoheiAgreementContent.ENHANCED_NOTICE_VERSION

        assertTrue(XiaoheiAgreementContent.isRecordedVersionCurrent(old, old))
        assertFalse(XiaoheiAgreementContent.isRecordedVersionCurrent(old, store))
        assertFalse(XiaoheiAgreementContent.isRecordedVersionCurrent(old, enhanced))
        assertFalse(XiaoheiAgreementContent.isRecordedVersionCurrent(store, enhanced))
        assertFalse(XiaoheiAgreementContent.isRecordedVersionCurrent(enhanced, store))
        assertFalse(XiaoheiAgreementContent.isRecordedVersionCurrent(null, store))
        assertFalse(XiaoheiAgreementContent.isRecordedVersionCurrent("", store))
        assertTrue(XiaoheiAgreementContent.isRecordedVersionCurrent(store, store))
        assertTrue(XiaoheiAgreementContent.isRecordedVersionCurrent(enhanced, enhanced))
    }

    @Test
    fun copyForUpstream_isRejected() {
        try {
            XiaoheiAgreementContent.copyFor(XiaoheiAgreementContent.Profile.UPSTREAM)
            throw AssertionError("upstream copy must not be produced")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("COMMON_BASE"))
        }
    }

    @Test
    fun storeCopy_isUnpublishedPersonalDraft_notEffectivePolicy() {
        val copy = XiaoheiAgreementContent.copyFor(commonBase = true, commonStore = true)
        val body = copy.notice + "\n" + copy.title + "\n" + copy.subtitle + "\n" + copy.versionLabel
        assertEquals(XiaoheiAgreementContent.Profile.STORE, copy.profile)
        assertEquals(XiaoheiAgreementContent.STORE_NOTICE_VERSION, copy.version)
        assertEquals(XiaoheiAgreementContent.CONTINUE_LABEL, copy.continueLabel)
        assertEquals("已了解，继续测试", copy.continueLabel)
        assertTrue(copy.title.contains("商店版"))
        assertTrue(copy.title.contains("未发布草稿"))
        assertTrue(copy.subtitle.contains("不是已生效的法律政策"))
        assertTrue(body.contains("个人运营"))
        assertTrue(body.contains("未公开发布"))
        assertTrue(body.contains("待填"))
        assertTrue(body.contains("不会填写或编造"))
        assertTrue(body.contains("不是已生效的法律政策"))
        assertTrue(body.contains("不禁止当前私有调试"))
        assertTrue(body.contains("不发起网络请求"))
        assertTrue(body.contains("studio.weichao.xiaohei"))
        assertFalse(copy.continueLabel.contains("同意"))
        assertFalse(body.contains("我已阅读并同意"))
        assertAttribution(body)
        assertNoFabricatedContacts(body)
        assertOperitIsNotOperator(body)
    }

    @Test
    fun enhancedCopy_isSideloadOnly_andStoreDraftDoesNotApply() {
        val copy = XiaoheiAgreementContent.copyFor(commonBase = true, commonStore = false)
        val body =
            copy.notice +
                "\n" +
                copy.title +
                "\n" +
                copy.subtitle +
                "\n" +
                copy.documentHeading
        assertEquals(XiaoheiAgreementContent.Profile.ENHANCED, copy.profile)
        assertEquals(XiaoheiAgreementContent.ENHANCED_NOTICE_VERSION, copy.version)
        assertEquals("已了解，继续测试", copy.continueLabel)
        assertTrue(copy.title.contains("增强版"))
        assertTrue(copy.title.contains("自用侧载"))
        assertTrue(body.contains("仅供自用侧载"))
        assertTrue(body.contains("不是商店产品"))
        assertTrue(body.contains("系统默认助手"))
        assertTrue(body.contains("DSP"))
        assertTrue(body.contains("明确授权"))
        assertTrue(body.contains("经过实测"))
        assertTrue(copy.documentHeading.contains("仅商店适用"))
        assertTrue(body.contains("只适用于商店版"))
        assertTrue(body.contains("不适用于本增强包"))
        assertTrue(body.contains("studio.weichao.xiaohei.common"))
        assertTrue(body.contains("待填"))
        assertTrue(body.contains("不是已生效的法律政策"))
        assertFalse(copy.continueLabel.contains("同意"))
        assertAttribution(body)
        assertNoFabricatedContacts(body)
        assertOperitIsNotOperator(body)
    }

    @Test
    fun bundledPolicyAsset_isCompleteUnpublishedDraft() {
        assertEquals(
            "xiaohei-privacy-policy.zh-CN.md",
            XiaoheiAgreementContent.BUNDLED_POLICY_ASSET
        )
        val document = readBundledPolicy()
        assertTrue(document.contains("# 隐私政策（定稿待填）"))
        assertTrue(document.contains("【待填：个人运营者姓名】"))
        assertTrue(document.contains("【待填：公开联系邮箱】"))
        assertTrue(document.contains("【待填：生效日期】"))
        assertTrue(document.contains("运营主体类型：个人"))
        assertTrue(document.contains("studio.weichao.xiaohei"))
        assertTrue(document.contains("studio.weichao.xiaohei.common"))
        assertTrue(document.contains("仅供自用侧载"))
        assertTrue(document.contains("本政策只适用于商店版"))
        assertTrue(document.contains("Operit v1.12.1"))
        assertTrue(document.contains("LGPL-3.0-only"))
        // Verify all sections, not an arbitrary character count (UTF-8 bytes != chars).
        val sections = Regex("^## (\\d+)\\.", RegexOption.MULTILINE)
            .findAll(document).map { it.groupValues[1].toInt() }.toList()
        assertEquals((1..11).toList(), sections)
        assertFalse(document.contains("我已阅读并同意"))
        assertNoFabricatedContacts(document)
        assertOperitIsNotOperator(document)
    }

    private fun assertAttribution(text: String) {
        assertTrue(text.contains("Operit v1.12.1"))
        assertTrue(text.contains("LGPL-3.0-only"))
    }

    private fun assertOperitIsNotOperator(text: String) {
        assertFalse(text.contains("Operit 运营"))
        assertFalse(text.contains("由 Operit 制定"))
        assertFalse(text.contains("Operit 官方发布"))
        if (text.contains("Operit")) {
            assertTrue(text.contains("不是小黑的运营者") || text.contains("上游固定基线"))
        }
    }

    private fun assertNoFabricatedContacts(text: String) {
        assertFalse(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").containsMatchIn(text))
        assertFalse(text.contains("weichao@"))
        assertFalse(text.contains("example.com"))
        assertFalse(text.contains("张三"))
        assertFalse(text.contains("李四"))
    }

    private fun readBundledPolicy(): String {
        val candidates =
            listOf(
                File("src/common/assets/xiaohei-privacy-policy.zh-CN.md"),
                File("app/src/common/assets/xiaohei-privacy-policy.zh-CN.md")
            )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("bundled policy asset must exist for tests: $candidates", file != null)
        return file!!.readText(Charsets.UTF_8)
    }
}
