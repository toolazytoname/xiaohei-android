package com.ai.assistance.operit.ui.features.agreement

/**
 * COMMON_BASE first-launch copy and version ids.
 *
 * Operit 2026-07-15 text is the upstream operator agreement. Showing it on 小黑
 * first launch (see 2026-10-02 store dump) would present Operit as the operator.
 * This object is the only source for the replacement notice. It does not invent
 * a name, email, or effective date, and it does not treat the draft as in-force law.
 */
object XiaoheiAgreementContent {
    const val BUNDLED_POLICY_ASSET = "xiaohei-privacy-policy.zh-CN.md"
    const val CONTINUE_LABEL = "已了解，继续测试"

    const val UPSTREAM_AGREEMENT_VERSION = "2026-07-15"
    const val STORE_NOTICE_VERSION = "xiaohei-store-2026-10-02"
    const val ENHANCED_NOTICE_VERSION = "xiaohei-enhanced-2026-10-02"

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
        val continueLabel: String
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
                title = "小黑商店版说明（未发布草稿）",
                subtitle = "请阅读完整草稿后继续测试。这不是已生效的法律政策。",
                versionLabel = "说明版本：$version",
                notice = STORE_NOTICE,
                documentHeading = "捆绑的商店版隐私政策草稿（全文）",
                continueLabel = CONTINUE_LABEL
            )
        }
        return ScreenCopy(
            profile = profile,
            version = version,
            title = "小黑增强版说明（自用侧载）",
            subtitle = "请阅读说明和捆绑的商店草稿后继续测试。这不是已生效的法律政策。",
            versionLabel = "说明版本：$version",
            notice = ENHANCED_NOTICE,
            documentHeading = "捆绑的商店版隐私政策草稿（仅商店适用，全文）",
            continueLabel = CONTINUE_LABEL
        )
    }

    fun copyFor(commonBase: Boolean, commonStore: Boolean): ScreenCopy {
        return copyFor(resolveProfile(commonBase, commonStore))
    }

    private const val STORE_NOTICE =
        "这是「小黑」商店版（studio.weichao.xiaohei）的个人运营说明草稿，不是已生效的法律政策，也不是商店审核或上架声明。\n" +
            "\n" +
            "两品牌包均未公开发布。运营主体类型已确认为个人；运营者姓名、公开联系邮箱、生效日期仍为待填，此处不会填写或编造。\n" +
            "\n" +
            "继续测试不表示你接受了一份已生效政策。资料待补只阻塞公开发布，不禁止当前私有调试。本页不发起网络请求。\n" +
            "\n" +
            "本应用主体来自 Operit v1.12.1，以 LGPL-3.0-only 许可分发。Operit 及其原作者不是小黑的运营者。\n" +
            "\n" +
            "下方是捆绑的完整草稿，请整篇阅读。商店版草稿只适用于商店版。"

    private const val ENHANCED_NOTICE =
        "这是「小黑」增强版（studio.weichao.xiaohei.common），仅供自用侧载，不是商店产品。\n" +
            "\n" +
            "系统默认助手、DSP 唤醒及其他设备增强能力，只在你明确授权并且经过实测的范围内可用；未授权或未实测的能力不能当作已具备。\n" +
            "\n" +
            "下方捆绑的是商店版隐私政策草稿，只适用于商店版（studio.weichao.xiaohei），不适用于本增强包。展示全文是为了让你读到完整草稿，不把商店草稿改写成增强版已生效政策。\n" +
            "\n" +
            "两品牌包均未公开发布。运营主体类型已确认为个人；姓名、公开联系邮箱、生效日期仍为待填，此处不会填写或编造。继续测试不表示你接受了一份已生效政策。资料待补不禁止当前私有调试。本页不发起网络请求。\n" +
            "\n" +
            "本应用主体来自 Operit v1.12.1，以 LGPL-3.0-only 许可分发。Operit 及其原作者不是小黑的运营者。"
}
