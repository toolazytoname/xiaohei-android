package com.ai.assistance.operit.core.commonbase

import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager

object CommonBaseConfirmationSource {
    fun current(): CommonBaseConfirmation {
        val runtime = ToolExecutionManager.currentToolRuntimeContext()
        return if (runtime?.explicitUserConfirmation == true) {
            CommonBaseConfirmation.ExplicitUserGrant
        } else {
            CommonBaseConfirmation.None
        }
    }
}
