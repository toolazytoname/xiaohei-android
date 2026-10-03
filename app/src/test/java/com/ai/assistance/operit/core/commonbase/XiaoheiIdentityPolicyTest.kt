package com.ai.assistance.operit.core.commonbase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XiaoheiIdentityPolicyTest {

    private val defaultId = "default_character"
    private val otherId = "custom_card_1"
    private val brandedAvatar = "file:///android_asset/xiaohei.png"

    @Test
    fun conflictingNamesPreventRebindingAnotherCustomCard() {
        org.junit.Assert.assertTrue(XiaoheiIdentityPolicy.hasConflictingCardName(listOf("Operit")))
        org.junit.Assert.assertTrue(XiaoheiIdentityPolicy.hasConflictingCardName(listOf("小黑")))
        org.junit.Assert.assertFalse(XiaoheiIdentityPolicy.hasConflictingCardName(listOf("我的助手")))
        org.junit.Assert.assertFalse(XiaoheiIdentityPolicy.hasConflictingCardName(emptyList()))
    }

    @Test
    fun newAndResetDefaultAvatarIsProfileSpecific() {
        assertEquals(brandedAvatar, XiaoheiIdentityPolicy.defaultAvatarUri(true))
        assertEquals("file:///android_asset/operit.png", XiaoheiIdentityPolicy.defaultAvatarUri(false))
    }

    @Test
    fun commonBase_newInstallDisplayNameIsXiaohei() {
        assertEquals(
            "小黑",
            XiaoheiIdentityPolicy.defaultCharacterDisplayName(commonBaseEnabled = true)
        )
    }

    @Test
    fun originalProfile_newInstallDisplayNameStaysOperit() {
        assertEquals(
            "Operit",
            XiaoheiIdentityPolicy.defaultCharacterDisplayName(commonBaseEnabled = false)
        )
    }

    @Test
    fun commonBase_uncustomizedDefaultCardRenamesOperitOnce() {
        val first =
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentName = "Operit",
                defaultCardId = defaultId
            )
        assertEquals("小黑", first)

        val second =
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentName = first,
                defaultCardId = defaultId
            )
        assertNull(second)
    }

    @Test
    fun commonBase_alreadyBrandedDefaultNameIsIdempotent() {
        val result =
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentName = "小黑",
                defaultCardId = defaultId
            )
        assertNull(result)
    }

    @Test
    fun commonBase_customDefaultCardNameIsUnchanged() {
        val result =
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentName = "我的助手",
                defaultCardId = defaultId
            )
        assertNull(result)
    }

    @Test
    fun commonBase_otherCardNamedOperitIsUnchanged() {
        val result =
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = otherId,
                currentName = "Operit",
                defaultCardId = defaultId
            )
        assertNull(result)
    }

    @Test
    fun originalProfile_defaultOperitNameIsUnchanged() {
        val result =
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = false,
                cardId = defaultId,
                currentName = "Operit",
                defaultCardId = defaultId
            )
        assertNull(result)
    }

    @Test
    fun missingOrBlankNameIsNotTreatedAsUpstreamDefault() {
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentName = null,
                defaultCardId = defaultId
            )
        )
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultDisplayName(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentName = "",
                defaultCardId = defaultId
            )
        )
    }

    @Test
    fun commonBase_knownUpstreamAvatarReplacesOnlyWhenBrandUriIsKnown() {
        val result =
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentAvatarUri = XiaoheiIdentityPolicy.KNOWN_UPSTREAM_DEFAULT_AVATAR_URI,
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        assertEquals(brandedAvatar, result)

        val again =
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentAvatarUri = result,
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        assertNull(again)
    }

    @Test
    fun commonBase_unknownBrandAvatarDoesNotRewriteKnownUpstream() {
        val result =
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentAvatarUri = XiaoheiIdentityPolicy.KNOWN_UPSTREAM_DEFAULT_AVATAR_URI,
                defaultCardId = defaultId,
                brandedAvatarUri = null
            )
        assertNull(result)
        assertEquals(brandedAvatar, XiaoheiIdentityPolicy.brandedDefaultAvatarUri)
    }

    @Test
    fun commonBase_customOrUnknownAvatarIsUnchanged() {
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentAvatarUri = "content://media/custom.png",
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        )
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentAvatarUri = null,
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        )
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = defaultId,
                currentAvatarUri = "",
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        )
    }

    @Test
    fun originalProfile_andOtherCards_doNotRewriteAvatar() {
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = false,
                cardId = defaultId,
                currentAvatarUri = XiaoheiIdentityPolicy.KNOWN_UPSTREAM_DEFAULT_AVATAR_URI,
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        )
        assertNull(
            XiaoheiIdentityPolicy.migratedDefaultAvatarUri(
                commonBaseEnabled = true,
                cardId = otherId,
                currentAvatarUri = XiaoheiIdentityPolicy.KNOWN_UPSTREAM_DEFAULT_AVATAR_URI,
                defaultCardId = defaultId,
                brandedAvatarUri = brandedAvatar
            )
        )
    }
}
