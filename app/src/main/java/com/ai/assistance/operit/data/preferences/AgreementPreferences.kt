package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.ui.features.agreement.XiaoheiAgreementContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manages agreement-related preferences for the application */
class AgreementPreferences(context: Context) {
    private val PREFS_NAME = "agreement_preferences"
    private val KEY_ACCEPTED_AGREEMENT_VERSION = "accepted_agreement_version"

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _agreementAcceptedFlow = MutableStateFlow(isAgreementAccepted())
    val agreementAcceptedFlow: StateFlow<Boolean> = _agreementAcceptedFlow.asStateFlow()

    /** Check whether the user has accepted the current agreement version. */
    fun isAgreementAccepted(): Boolean {
        return XiaoheiAgreementContent.isRecordedVersionCurrent(
            prefs.getString(KEY_ACCEPTED_AGREEMENT_VERSION, null),
            CURRENT_AGREEMENT_VERSION
        )
    }

    /** Records acknowledgement of the notice version bundled with this app build. */
    fun acceptCurrentAgreement() {
        prefs.edit()
            .putString(KEY_ACCEPTED_AGREEMENT_VERSION, CURRENT_AGREEMENT_VERSION)
            .apply()
        _agreementAcceptedFlow.value = true
    }

    companion object {
        /**
         * Build-specific notice id. Same SharedPreferences name and key as before so existing
         * stored values stay in place. A stored Operit 2026-07-15 value must not count as a new
         * 小黑 notice, so COMMON_BASE writes a different id per profile.
         */
        val CURRENT_AGREEMENT_VERSION: String
            get() =
                XiaoheiAgreementContent.resolveAgreementVersion(
                    commonBase = BuildConfig.COMMON_BASE,
                    commonStore = BuildConfig.COMMON_STORE
                )
    }
}
