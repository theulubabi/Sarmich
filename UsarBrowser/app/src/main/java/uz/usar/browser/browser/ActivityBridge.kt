package uz.usar.browser.browser

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * Activity-result launchers wrapped as suspend functions so the browser engine can ask for
 * runtime permissions, camera capture, pickers and the QR scanner only at the moment they are needed.
 * Must be constructed during Activity initialization.
 */
class ActivityBridge(private val activity: ComponentActivity) {

    private class Pending<T> {
        private var deferred: CompletableDeferred<T>? = null
        fun start(cancelValue: T): CompletableDeferred<T> {
            deferred?.complete(cancelValue)
            return CompletableDeferred<T>().also { deferred = it }
        }
        fun complete(value: T) {
            deferred?.complete(value)
            deferred = null
        }
    }

    private val permPending = Pending<Map<String, Boolean>>()
    private val permLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permPending.complete(it) }

    private val photoPending = Pending<Boolean>()
    private val photoLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) {
        photoPending.complete(it)
    }

    private val videoPending = Pending<Boolean>()
    private val videoLauncher = activity.registerForActivityResult(ActivityResultContracts.CaptureVideo()) {
        videoPending.complete(it)
    }

    private val mediaPending = Pending<List<Uri>>()
    private val mediaMultiLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { mediaPending.complete(it) }
    private val mediaSingleLauncher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        mediaPending.complete(listOfNotNull(it))
    }

    private val docsPending = Pending<List<Uri>>()
    private val docsMultiLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { docsPending.complete(it) }
    private val docSingleLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        docsPending.complete(listOfNotNull(it))
    }

    private val scanPending = Pending<String?>()
    private val scanLauncher = activity.registerForActivityResult(ScanContract()) { scanPending.complete(it.contents) }

    private suspend fun <I, T> launch(pending: Pending<T>, cancel: T, launcher: ActivityResultLauncher<I>, input: I): T {
        val deferred = pending.start(cancel)
        try {
            launcher.launch(input)
        } catch (e: ActivityNotFoundException) {
            pending.complete(cancel)
        }
        return deferred.await()
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    /** Returns the grant state of every requested permission. Only missing ones are requested. */
    suspend fun requestPermissions(permissions: List<String>): Map<String, Boolean> {
        val missing = permissions.filterNot(::hasPermission)
        if (missing.isEmpty()) return permissions.associateWith { true }
        val result = launch(permPending, emptyMap(), permLauncher, missing.toTypedArray())
        return permissions.associateWith { hasPermission(it) || result[it] == true }
    }

    suspend fun capturePhoto(): Uri? {
        if (requestPermissions(listOf(Manifest.permission.CAMERA))[Manifest.permission.CAMERA] != true) return null
        val uri = newCaptureUri("jpg")
        return if (launch(photoPending, false, photoLauncher, uri)) uri else null
    }

    suspend fun captureVideo(): Uri? {
        if (requestPermissions(listOf(Manifest.permission.CAMERA))[Manifest.permission.CAMERA] != true) return null
        val uri = newCaptureUri("mp4")
        return if (launch(videoPending, false, videoLauncher, uri)) uri else null
    }

    suspend fun pickMedia(multiple: Boolean, type: ActivityResultContracts.PickVisualMedia.VisualMediaType): List<Uri> {
        val request = PickVisualMediaRequest(type)
        return if (multiple) launch(mediaPending, emptyList(), mediaMultiLauncher, request)
        else launch(mediaPending, emptyList(), mediaSingleLauncher, request)
    }

    suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<Uri> {
        val types = mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray()
        return if (multiple) launch(docsPending, emptyList(), docsMultiLauncher, types)
        else launch(docsPending, emptyList(), docSingleLauncher, types)
    }

    suspend fun scanQr(prompt: String): String? {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(prompt)
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        return launch(scanPending, null, scanLauncher, options)
    }

    private fun newCaptureUri(extension: String): Uri {
        val dir = File(activity.cacheDir, "captures").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val file = File.createTempFile("capture_", ".$extension", dir)
        return FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    }
}
