package com.ai.assistance.operit.core.commonbase

/** Per-invocation confirmation. Absence is a refusal, not an implicit grant. */
sealed class CommonBaseConfirmation {
    data object None : CommonBaseConfirmation()

    data object ExplicitUserGrant : CommonBaseConfirmation()
}
