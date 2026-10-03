package com.ai.assistance.operit.ui.features.chat.components

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-death-safe camera capture metadata. Only the current TakePicture URI/path
 * is persisted — never image bytes or secrets.
 *
 * Compose [androidx.activity.compose.rememberLauncherForActivityResult] keys live under
 * [androidx.compose.runtime.saveable.SaveableStateHolder] keyed by a new
 * [com.ai.assistance.operit.ui.main.navigation.RouteEntry.instanceId] after restore,
 * so they cannot round-trip the ActivityResultRegistry key or pending file.
 */
data class PendingCameraCapture(val uri: Uri, val file: File)

interface CameraCaptureHost {
    fun launchExternalTakePicture(capture: PendingCameraCapture)
}

object CameraCaptureResults {
    private val _successUri = MutableStateFlow<Uri?>(null)
    val successUri: StateFlow<Uri?> = _successUri

    @Synchronized
    fun publishSuccess(uri: Uri) {
        _successUri.value = uri
    }

    @Synchronized
    fun consume(expected: Uri): Boolean {
        if (_successUri.value != expected) {
            return false
        }
        _successUri.value = null
        return true
    }
}

object CameraCaptureState {
    const val BUNDLE_URI_KEY = "camera_capture.pending_uri"
    const val BUNDLE_FILE_KEY = "camera_capture.pending_file"

    fun createPending(context: Context): PendingCameraCapture {
        val authority = "${context.applicationContext.packageName}.fileprovider"
        val tmpFile = File.createTempFile("temp_image_", ".jpg", context.cacheDir)
        return try {
            PendingCameraCapture(
                uri = FileProvider.getUriForFile(context, authority, tmpFile),
                file = tmpFile
            )
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    fun deletePending(capture: PendingCameraCapture?) {
        val file = capture?.file ?: return
        if (!file.exists() || !isThisCaptureTemp(file)) {
            return
        }
        file.delete()
    }

    fun savePending(outState: Bundle, capture: PendingCameraCapture?) {
        val file = capture?.file
        if (capture == null || file == null || !isThisCaptureTemp(file)) {
            outState.remove(BUNDLE_URI_KEY)
            outState.remove(BUNDLE_FILE_KEY)
            return
        }
        outState.putString(BUNDLE_URI_KEY, capture.uri.toString())
        outState.putString(BUNDLE_FILE_KEY, file.absolutePath)
    }

    fun restorePending(context: Context, state: Bundle?): PendingCameraCapture? {
        val uriValue = state?.getString(BUNDLE_URI_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val path = state.getString(BUNDLE_FILE_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.exists() || !isThisCaptureTemp(file, context.cacheDir)) {
            return null
        }
        val uri =
            try {
                val authority = "${context.applicationContext.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, file)
            } catch (_: Exception) {
                Uri.parse(uriValue)
            }
        return PendingCameraCapture(uri, file)
    }

    /**
     * Pending TakePicture files are only `cacheDir/temp_image_*.jpg`.
     * Copied attachments use `attachment_*` under a different directory.
     */
    private fun isThisCaptureTemp(file: File, cacheDir: File? = null): Boolean {
        if (!file.name.startsWith("temp_image_") || !file.name.endsWith(".jpg")) {
            return false
        }
        if (file.parentFile?.name != "cache") {
            return false
        }
        if (cacheDir == null) {
            return true
        }
        val cache =
            try {
                cacheDir.canonicalFile
            } catch (_: Exception) {
                return false
            }
        val target =
            try {
                file.canonicalFile
            } catch (_: Exception) {
                return false
            }
        return target.parentFile == cache
    }
}

fun Context.findCameraCaptureHost(): CameraCaptureHost? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is CameraCaptureHost) {
            return current
        }
        current = current.baseContext
    }
    return null
}
