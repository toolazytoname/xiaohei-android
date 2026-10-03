package com.ai.assistance.operit.ui.features.chat.screens

/**
 * COMMON_BASE empty-config welcome copy. Does not embed keys, pick a vendor, or
 * start network I/O. Store and enhanced share the self-config model flow and
 * differ only in preview-boundary notes.
 */
object XiaoheiWelcomeContent {
    const val TITLE = "让小黑连接你的模型"
    const val SUBTITLE = "这是公开预览，不是稳定版。支持 GLM 等兼容接口。使用你自己的服务地址、模型和密钥。"
    const val STORE_EDITION_NOTE =
        "普通版公开预览建议一般用户使用，不需要 Root；手机操作会先向你确认。模型由你自行配置。语音未完整验收。"
    const val ENHANCED_EDITION_NOTE =
        "增强版公开预览仅建议在 OnePlus 8T 上实验。模型由你自行配置。语音与 DSP 未完整验收，相关能力需单独授权。"
    const val PRIMARY_ACTION = "配置模型服务"
    const val SAVING_LABEL = "请稍候"
    const val PREP_TITLE = "需要准备什么？"
    const val PREP_ITEM_ENDPOINT = "1. 服务地址：填写你自己的兼容接口地址，在下一页完成。"
    const val PREP_ITEM_MODEL = "2. 模型名称：由你指定，例如 GLM 或其他兼容模型。"
    const val PREP_ITEM_KEY = "3. 密钥：在下一页输入。本页不收集、不保存密钥。"
    const val AFTER_CONFIG_HINT = "配置后可先发文字检查，再自行开启语音。语音未完整验收。可随时停止。"
    const val LOGO_DESCRIPTION = "小黑角色图标"
    const val EXPAND_LABEL = "展开准备说明"
    const val COLLAPSE_LABEL = "收起准备说明"
    const val EXPANDED_STATE = "已展开"
    const val COLLAPSED_STATE = "已收起"

    fun editionNote(commonStore: Boolean): String {
        return if (commonStore) STORE_EDITION_NOTE else ENHANCED_EDITION_NOTE
    }
}
