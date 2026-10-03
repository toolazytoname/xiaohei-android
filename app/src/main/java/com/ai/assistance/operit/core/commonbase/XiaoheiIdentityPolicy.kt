package com.ai.assistance.operit.core.commonbase

/**
 * Common-base default character display identity.
 *
 * Does not change the default card id, prompts, custom names, custom avatars, or
 * non-default cards. Avatar rewrite is allowed only when the current URI is the
 * known upstream original and a branded replacement URI is supplied. Unknown
 * current URIs and a missing brand URI are left unchanged.
 */
object XiaoheiIdentityPolicy {
    const val UPSTREAM_DEFAULT_CHARACTER_NAME = "Operit"
    const val COMMON_BASE_DEFAULT_CHARACTER_NAME = "小黑"
    const val KNOWN_UPSTREAM_DEFAULT_AVATAR_URI = "file:///android_asset/operit.png"

    // Packaged only in the common/commonRelease asset overlay.
    const val brandedDefaultAvatarUri = "file:///android_asset/xiaohei.png"

    fun hasConflictingCardName(otherCardNames: List<String>): Boolean =
        otherCardNames.any {
            it == UPSTREAM_DEFAULT_CHARACTER_NAME || it == COMMON_BASE_DEFAULT_CHARACTER_NAME
        }

    fun defaultAvatarUri(commonBaseEnabled: Boolean): String =
        if (commonBaseEnabled) brandedDefaultAvatarUri else KNOWN_UPSTREAM_DEFAULT_AVATAR_URI

    fun defaultCharacterDisplayName(
        commonBaseEnabled: Boolean,
        upstreamDefaultName: String = UPSTREAM_DEFAULT_CHARACTER_NAME,
        brandedName: String = COMMON_BASE_DEFAULT_CHARACTER_NAME
    ): String {
        return if (commonBaseEnabled) brandedName else upstreamDefaultName
    }

    /**
     * Returns the name to persist, or null when this card must be left as-is.
     * Matching the stored name to the upstream default is the idempotency gate:
     * after a write to [brandedName], a later call sees a different name and
     * does not write again.
     */
    fun migratedDefaultDisplayName(
        commonBaseEnabled: Boolean,
        cardId: String,
        currentName: String?,
        defaultCardId: String,
        upstreamDefaultName: String = UPSTREAM_DEFAULT_CHARACTER_NAME,
        brandedName: String = COMMON_BASE_DEFAULT_CHARACTER_NAME
    ): String? {
        if (!commonBaseEnabled) {
            return null
        }
        if (cardId != defaultCardId) {
            return null
        }
        if (currentName != upstreamDefaultName) {
            return null
        }
        if (brandedName == currentName) {
            return null
        }
        return brandedName
    }

    /**
     * Returns the avatar URI to persist, or null when the avatar must be left
     * as-is. Blank, missing, custom, and unknown URIs are not the known
     * upstream original, so they are not rewritten.
     */
    fun migratedDefaultAvatarUri(
        commonBaseEnabled: Boolean,
        cardId: String,
        currentAvatarUri: String?,
        defaultCardId: String,
        knownUpstreamAvatarUri: String = KNOWN_UPSTREAM_DEFAULT_AVATAR_URI,
        brandedAvatarUri: String? = brandedDefaultAvatarUri
    ): String? {
        if (!commonBaseEnabled) {
            return null
        }
        if (cardId != defaultCardId) {
            return null
        }
        if (brandedAvatarUri.isNullOrBlank()) {
            return null
        }
        if (currentAvatarUri != knownUpstreamAvatarUri) {
            return null
        }
        if (currentAvatarUri == brandedAvatarUri) {
            return null
        }
        return brandedAvatarUri
    }
}
