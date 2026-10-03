package com.ai.assistance.operit.ui.features.agreement

/**
 * COMMON_BASE first-launch copy and version ids.
 *
 * Operit 2026-07-15 text is the upstream operator agreement. Showing it on 小黑
 * first launch would present Operit as the operator. This object is the only
 * source for the replacement notice. Store and enhanced each bind their own
 * preview policy; the store document is not the enhanced policy.
 */
object XiaoheiAgreementContent {
    const val STORE_POLICY_ASSET = "xiaohei-privacy-policy.zh-CN.md"
    const val ENHANCED_POLICY_ASSET = "xiaohei-privacy-policy-enhanced.zh-CN.md"
    const val BUNDLED_POLICY_ASSET = STORE_POLICY_ASSET
    const val CONTINUE_LABEL = "已了解，继续测试"

    const val UPSTREAM_AGREEMENT_VERSION = "2026-07-15"
    const val STORE_NOTICE_VERSION = "xiaohei-store-2026-10-03"
    const val ENHANCED_NOTICE_VERSION = "xiaohei-enhanced-2026-10-03"

    const val OPERATOR_NAME = "韦超"
    const val OPERATOR_EMAIL = "lazywc@gmail.com"
    const val POLICY_EFFECTIVE_DATE = "2026-10-03"

    enum class Profile {
        UPSTREAM,
        STORE,
        ENHANCED
    }

    data class ScreenCopy(
        val profile: Profile,
        val version: String,
        val title: String,
        val subtitle: String,
        val versionLabel: String,
        val notice: String,
        val documentHeading: String,
        val continueLabel: String,
        val policyAsset: String
    )

    fun usesXiaoheiContent(commonBase: Boolean): Boolean = commonBase

    fun resolveProfile(commonBase: Boolean, commonStore: Boolean): Profile {
        if (!commonBase) {
            return Profile.UPSTREAM
        }
        if (commonStore) {
            return Profile.STORE
        }
        return Profile.ENHANCED
    }

    fun versionFor(profile: Profile): String {
        return when (profile) {
            Profile.UPSTREAM -> UPSTREAM_AGREEMENT_VERSION
            Profile.STORE -> STORE_NOTICE_VERSION
            Profile.ENHANCED -> ENHANCED_NOTICE_VERSION
        }
    }

    fun policyAssetFor(profile: Profile): String {
        return when (profile) {
            Profile.UPSTREAM -> {
                throw IllegalStateException("Xiaohei policy assets are only for COMMON_BASE profiles")
            }
            Profile.STORE -> STORE_POLICY_ASSET
            Profile.ENHANCED -> ENHANCED_POLICY_ASSET
        }
    }

    fun resolveAgreementVersion(commonBase: Boolean, commonStore: Boolean): String {
        return versionFor(resolveProfile(commonBase, commonStore))
    }

    fun isRecordedVersionCurrent(recorded: String?, current: String): Boolean {
        return recorded == current
    }

    fun copyFor(profile: Profile): ScreenCopy {
        check(profile != Profile.UPSTREAM) {
            "Xiaohei agreement copy is only for COMMON_BASE profiles"
        }
        val version = versionFor(profile)
        if (profile == Profile.STORE) {
            return ScreenCopy(
                profile = profile,
                version = version,
                title = "小黑普通版说明（公开预览）",
                subtitle = "请阅读完整预览政策后继续。这不是商店批准或稳定版声明。模型需自行配置；语音未完整验收。",
                versionLabel = "说明版本：$version",
                notice = STORE_NOTICE,
                documentHeading = "捆绑的普通版隐私政策（全文）",
                continueLabel = CONTINUE_LABEL,
                policyAsset = STORE_POLICY_ASSET
            )
        }
        return ScreenCopy(
            profile = profile,
            version = version,
            title = "小黑增强版说明（公开预览）",
            subtitle = "请阅读增强版预览政策后继续。仅建议在 OnePlus 8T 上实验。模型需自行配置；语音与 DSP 未完整验收。",
            versionLabel = "说明版本：$version",
            notice = ENHANCED_NOTICE,
            documentHeading = "捆绑的增强版隐私政策（全文）",
            continueLabel = CONTINUE_LABEL,
            policyAsset = ENHANCED_POLICY_ASSET
        )
    }

    fun copyFor(commonBase: Boolean, commonStore: Boolean): ScreenCopy {
        return copyFor(resolveProfile(commonBase, commonStore))
    }

    private const val STORE_NOTICE =
        "这是「小黑」普通版（studio.weichao.xiaohei）的公开预览说明，不是商店审核通过或稳定版声明。\n" +
            "\n" +
            "运营者为个人韦超，公开联系邮箱 lazywc@gmail.com，本预览政策自 2026-10-03 起适用。本页不发起网络请求。\n" +
            "\n" +
            "模型接口由你自行配置。默认在本机转写语音；若改选云端 STT / TTS，音频或文本会发往你填写的地址。密钥按明文保存在本机应用配置中。\n" +
            "\n" +
            "麦克风连续语音、自然插话和 16KB 真机运行尚未完整验收。继续测试表示你已阅读本预览说明，不表示本应用已上架。\n" +
            "\n" +
            "本应用主体来自 Operit v1.12.1，以 LGPL-3.0-only 许可分发。Operit 及其原作者不是小黑的运营者。\n" +
            "\n" +
            "下方是捆绑的普通版隐私政策全文，只适用于普通版。"

    private const val ENHANCED_NOTICE =
        "这是「小黑·增强」（studio.weichao.xiaohei.common）的公开预览说明，不是商店产品，仅建议在 OnePlus 8T 上实验。\n" +
            "\n" +
            "运营者为个人韦超，公开联系邮箱 lazywc@gmail.com，本预览政策自 2026-10-03 起适用。本页不发起网络请求。\n" +
            "\n" +
            "模型接口由你自行配置。增强版允许明文 HTTP，并信任用户安装的证书；密钥按明文保存在本机应用配置中。系统默认助手、DSP 唤醒及其他设备增强能力，只在你明确授权并且经过实测的范围内可用。\n" +
            "\n" +
            "麦克风连续语音、自然插话、DSP 与 16KB 真机运行尚未完整验收。下方捆绑的是增强版隐私政策全文，不把普通版政策冒充为本包政策。\n" +
            "\n" +
            "本应用主体来自 Operit v1.12.1，以 LGPL-3.0-only 许可分发。Operit 及其原作者不是小黑的运营者。"
}
