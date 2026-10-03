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
        assertFalse(
            XiaoheiAgreementContent.isRecordedVersionCurrent(
                "xiaohei-store-2026-10-02",
                store
            )
        )
        assertFalse(
            XiaoheiAgreementContent.isRecordedVersionCurrent(
                "xiaohei-enhanced-2026-10-02",
                enhanced
            )
        )
    }

    @Test
    fun copyForUpstream_isRejected() {
        try {
            XiaoheiAgreementContent.copyFor(XiaoheiAgreementContent.Profile.UPSTREAM)
            throw AssertionError("upstream copy must not be produced")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("COMMON_BASE"))
        }
        try {
            XiaoheiAgreementContent.policyAssetFor(XiaoheiAgreementContent.Profile.UPSTREAM)
            throw AssertionError("upstream policy asset must not be produced")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("COMMON_BASE"))
        }
    }

    @Test
    fun storeCopy_isPublicPreview_notStoreApproval() {
        val copy = XiaoheiAgreementContent.copyFor(commonBase = true, commonStore = true)
        val body = copy.notice + "\n" + copy.title + "\n" + copy.subtitle + "\n" + copy.versionLabel
        assertEquals(XiaoheiAgreementContent.Profile.STORE, copy.profile)
        assertEquals(XiaoheiAgreementContent.STORE_NOTICE_VERSION, copy.version)
        assertEquals(XiaoheiAgreementContent.CONTINUE_LABEL, copy.continueLabel)
        assertEquals("已了解，继续测试", copy.continueLabel)
        assertEquals(XiaoheiAgreementContent.STORE_POLICY_ASSET, copy.policyAsset)
        assertTrue(copy.title.contains("普通版"))
        assertTrue(copy.title.contains("公开预览"))
        assertTrue(copy.subtitle.contains("不是商店批准"))
        assertTrue(copy.subtitle.contains("模型需自行配置"))
        assertTrue(copy.subtitle.contains("语音未完整验收"))
        assertTrue(body.contains("公开预览"))
        assertTrue(body.contains("不是商店审核通过"))
        assertTrue(body.contains("韦超"))
        assertTrue(body.contains("lazywc@gmail.com"))
        assertTrue(body.contains("2026-10-03"))
        assertTrue(body.contains("明文"))
        assertTrue(body.contains("不发起网络请求"))
        assertTrue(body.contains("studio.weichao.xiaohei"))
        assertTrue(copy.documentHeading.contains("普通版"))
        assertFalse(copy.documentHeading.contains("增强版"))
        assertFalse(copy.continueLabel.contains("同意"))
        assertFalse(body.contains("我已阅读并同意"))
        assertFalse(body.contains("待填"))
        assertFalse(body.contains("未发布草稿"))
        assertFalse(body.contains("不禁止当前私有调试"))
        assertAttribution(body)
        assertOnlyConfirmedPublicEmail(body)
        assertOperitIsNotOperator(body)
    }

    @Test
    fun enhancedCopy_bindsEnhancedPolicy_notStorePolicy() {
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
        assertEquals(XiaoheiAgreementContent.ENHANCED_POLICY_ASSET, copy.policyAsset)
        assertNotEquals(XiaoheiAgreementContent.STORE_POLICY_ASSET, copy.policyAsset)
        assertTrue(copy.title.contains("增强版"))
        assertTrue(copy.title.contains("公开预览"))
        assertTrue(copy.subtitle.contains("OnePlus 8T"))
        assertTrue(copy.subtitle.contains("模型需自行配置"))
        assertTrue(copy.subtitle.contains("语音与 DSP 未完整验收"))
        assertTrue(body.contains("不是商店产品"))
        assertTrue(body.contains("系统默认助手"))
        assertTrue(body.contains("DSP"))
        assertTrue(body.contains("明确授权"))
        assertTrue(body.contains("明文 HTTP"))
        assertTrue(copy.documentHeading.contains("增强版"))
        assertFalse(copy.documentHeading.contains("普通版"))
        assertFalse(copy.documentHeading.contains("仅商店适用"))
        assertTrue(body.contains("不把普通版政策冒充"))
        assertTrue(body.contains("studio.weichao.xiaohei.common"))
        assertTrue(body.contains("韦超"))
        assertFalse(body.contains("待填"))
        assertFalse(copy.continueLabel.contains("同意"))
        assertAttribution(body)
        assertOnlyConfirmedPublicEmail(body)
        assertOperitIsNotOperator(body)
    }

    @Test
    fun bundledStorePolicy_isCompletePublicPreviewWithoutPlaceholders() {
        assertEquals(
            "xiaohei-privacy-policy.zh-CN.md",
            XiaoheiAgreementContent.BUNDLED_POLICY_ASSET
        )
        val document = readBundledPolicy(XiaoheiAgreementContent.STORE_POLICY_ASSET)
        assertTrue(document.contains("# 小黑普通版隐私政策（公开预览）"))
        assertTrue(document.contains("韦超"))
        assertTrue(document.contains("lazywc@gmail.com"))
        assertTrue(document.contains("2026-10-03"))
        assertTrue(document.contains("运营主体类型：个人"))
        assertTrue(document.contains("studio.weichao.xiaohei"))
        assertTrue(document.contains("studio.weichao.xiaohei.common"))
        assertTrue(document.contains("本文件不适用于增强版"))
        assertTrue(document.contains("Operit v1.12.1"))
        assertTrue(document.contains("LGPL-3.0-only"))
        assertTrue(document.contains("不是小黑的运营者"))
        assertTrue(document.contains("按明文写入"))
        assertTrue(document.contains("没有另行加密"))
        assertTrue(document.contains("填写 `http://` 地址会被网络安全配置拒绝"))
        assertTrue(document.contains("改选云端 STT"))
        assertTrue(document.contains("系统 TTS"))
        assertTrue(document.contains("16KB 真机运行仍未完整验收") || document.contains("16KB 真机运行未验收") || document.contains("16KB 页面对齐的静态打包门禁不等于 16KB 真机已验收"))
        assertFalse(document.contains("待填"))
        assertFalse(document.contains("【待填"))
        assertFalse(document.contains("定稿待填"))
        assertFalse(document.contains("2026-09-19"))
        assertFalse(document.contains("计划排除"))
        assertFalse(document.contains("已加密"))
        assertFalse(document.contains("密钥加密"))
        assertFalse(document.contains("我已阅读并同意"))
        assertFalse(document.contains("本政策只适用于商店版"))
        val sections = Regex("^## (\\d+)\\.", RegexOption.MULTILINE)
            .findAll(document).map { it.groupValues[1].toInt() }.toList()
        assertEquals((1..11).toList(), sections)
        assertOnlyConfirmedPublicEmail(document)
        assertOperitIsNotOperator(document)
    }

    @Test
    fun bundledEnhancedPolicy_isDistinctAndHasEnhancedBoundaries() {
        val store = readBundledPolicy(XiaoheiAgreementContent.STORE_POLICY_ASSET)
        val enhanced = readBundledPolicy(XiaoheiAgreementContent.ENHANCED_POLICY_ASSET)
        assertNotEquals(store, enhanced)
        assertTrue(enhanced.contains("# 小黑增强版隐私政策（公开预览）"))
        assertTrue(enhanced.contains("studio.weichao.xiaohei.common"))
        assertTrue(enhanced.contains("commonEnhancedRelease"))
        assertTrue(enhanced.contains("仅建议在 OnePlus 8T 上实验"))
        assertTrue(enhanced.contains("韦超"))
        assertTrue(enhanced.contains("lazywc@gmail.com"))
        assertTrue(enhanced.contains("按明文写入"))
        assertTrue(enhanced.contains("usesCleartextTraffic` 为 true") || enhanced.contains("`usesCleartextTraffic` 为 true"))
        assertTrue(enhanced.contains("用户安装的证书"))
        assertTrue(enhanced.contains("get_device_status"))
        assertTrue(enhanced.contains("set_media_volume"))
        assertTrue(enhanced.contains("DSP"))
        assertTrue(enhanced.contains("普通版政策不适用于本增强包"))
        assertFalse(enhanced.contains("待填"))
        assertFalse(enhanced.contains("本政策只适用于商店版"))
        assertFalse(enhanced.contains("填写 `http://` 地址会被网络安全配置拒绝"))
        assertFalse(enhanced.contains("已加密"))
        assertFalse(enhanced.contains("我已阅读并同意"))
        val sections = Regex("^## (\\d+)\\.", RegexOption.MULTILINE)
            .findAll(enhanced).map { it.groupValues[1].toInt() }.toList()
        assertEquals((1..11).toList(), sections)
        assertOnlyConfirmedPublicEmail(enhanced)
        assertOperitIsNotOperator(enhanced)
        assertAttribution(enhanced)
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

    private fun assertOnlyConfirmedPublicEmail(text: String) {
        val emails =
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
                .findAll(text)
                .map { it.value }
                .toSet()
        assertEquals(setOf(XiaoheiAgreementContent.OPERATOR_EMAIL), emails)
        assertFalse(text.contains("weichao@"))
        assertFalse(text.contains("example.com"))
        assertFalse(text.contains("张三"))
        assertFalse(text.contains("李四"))
    }

    private fun readBundledPolicy(assetName: String): String {
        val candidates =
            listOf(
                File("src/common/assets/$assetName"),
                File("app/src/common/assets/$assetName")
            )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("bundled policy asset must exist for tests: $candidates", file != null)
        return file!!.readText(Charsets.UTF_8)
    }
}
