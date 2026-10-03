package com.ai.assistance.operit.core.commonbase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonBaseUiResiduePolicyTest {

    @Test
    fun originalProfile_keepsEveryResidueExtra() {
        assertTrue(CommonBaseUiResiduePolicy.allowsAiComputer(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsWorkspace(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsScreenContentAttach(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsNotificationAttach(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsLocationAttach(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsScreenOcr(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.showTtsAccessibilityNote(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("screen_capture", enabled = false))
        assertTrue(
            CommonBaseUiResiduePolicy.allowsAttachmentToken("notifications_capture", enabled = false)
        )
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("location_capture", enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("/sdcard/photo.jpg", enabled = false))
    }

    @Test
    fun commonEnabled_refusesComputerWorkspaceContextAttachAndAccessibilityNote() {
        assertFalse(CommonBaseUiResiduePolicy.allowsAiComputer(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsWorkspace(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsScreenContentAttach(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsNotificationAttach(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsLocationAttach(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsScreenOcr(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.showTtsAccessibilityNote(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsAttachmentToken("screen_capture", enabled = true))
        assertFalse(
            CommonBaseUiResiduePolicy.allowsAttachmentToken("notifications_capture", enabled = true)
        )
        assertFalse(CommonBaseUiResiduePolicy.allowsAttachmentToken("location_capture", enabled = true))
    }

    @Test
    fun commonEnabled_stillAllowsPhotoAndGenericAttachmentTokens() {
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("/sdcard/photo.jpg", enabled = true))
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("content://media/1", enabled = true))
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("", enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.allowsAttachmentToken("package_attach:demo", enabled = true))
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("package_attach:demo", enabled = false))
        assertFalse(CommonBaseUiResiduePolicy.allowsPackageAttach(enabled = true))
        assertTrue(CommonBaseUiResiduePolicy.allowsAttachmentToken("camera_123.jpg", enabled = true))
    }

    @Test
    fun commonEnabled_usesExternalTakePictureAndHidesEmptyFloatingAttachmentPanel() {
        assertFalse(CommonBaseUiResiduePolicy.requestsOwnCameraPermission(enabled = true))
        assertFalse(CommonBaseUiResiduePolicy.hasFloatingWindowAttachmentOptions(enabled = true))
    }

    @Test
    fun originalProfile_stillRequestsCameraAndShowsFloatingAttachmentOptions() {
        assertTrue(CommonBaseUiResiduePolicy.requestsOwnCameraPermission(enabled = false))
        assertTrue(CommonBaseUiResiduePolicy.hasFloatingWindowAttachmentOptions(enabled = false))
    }
}
