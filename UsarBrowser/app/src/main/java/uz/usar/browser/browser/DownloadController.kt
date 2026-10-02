package uz.usar.browser.browser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.usar.browser.data.db.DownloadDao
import uz.usar.browser.data.db.DownloadEntity
import uz.usar.browser.data.db.DownloadStatus
import java.io.File
import java.net.URLDecoder

sealed interface DownloadResult {
    data class Started(val fileName: String) : DownloadResult
    data object Unsupported : DownloadResult
    data object Failed : DownloadResult
}

/**
 * Uses Android DownloadManager. Files go to the public Downloads folder on Android 10+
 * (no storage permission needed) and to the app's own Downloads folder on older versions.
 */
class DownloadController(
    private val context: Context,
    private val dao: DownloadDao,
    private val scope: CoroutineScope,
) {
    private val dm = context.getSystemService(DownloadManager::class.java)
    private var pollJob: Job? = null

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String =
        sanitize(URLUtil.guessFileName(url, contentDisposition, mimeType))

    suspend fun enqueue(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        cookies: String?,
        referer: String?,
        wifiOnly: Boolean,
    ): DownloadResult {
        if (url.startsWith("data:", ignoreCase = true)) return saveDataUrl(url, mimeType)
        if (!UrlUtils.isWebUrl(url)) return DownloadResult.Unsupported
        val fileName = guessFileName(url, contentDisposition, mimeType)
        val mime = resolveMime(mimeType, fileName)
        val systemId = try {
            dm.enqueue(buildRequest(url, fileName, mime, userAgent, cookies, referer, wifiOnly))
        } catch (e: Exception) {
            return DownloadResult.Failed
        }
        dao.insert(
            DownloadEntity(
                systemId = systemId, url = url, fileName = fileName, mimeType = mime,
                totalBytes = contentLength.coerceAtLeast(0), downloadedBytes = 0,
                status = DownloadStatus.RUNNING, createdAt = System.currentTimeMillis(),
                userAgent = userAgent,
            )
        )
        startPolling()
        return DownloadResult.Started(fileName)
    }

    private fun buildRequest(
        url: String, fileName: String, mime: String, userAgent: String?,
        cookies: String?, referer: String?, wifiOnly: Boolean,
    ): DownloadManager.Request {
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mime)
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
        if (!referer.isNullOrBlank() && UrlUtils.isWebUrl(referer)) request.addRequestHeader("Referer", referer)
        if (wifiOnly) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        return request
    }

    private suspend fun saveDataUrl(url: String, fallbackMime: String?): DownloadResult {
        val comma = url.indexOf(',')
        if (comma < 0 || url.length > MAX_DATA_URL) return DownloadResult.Unsupported
        val meta = url.substring(5, comma)
        val isBase64 = meta.endsWith(";base64", ignoreCase = true)
        val mime = meta.substringBefore(';').ifBlank { fallbackMime ?: "application/octet-stream" }
        val payload = url.substring(comma + 1)
        val bytes = runCatching {
            if (isBase64) Base64.decode(payload, Base64.DEFAULT)
            else URLDecoder.decode(payload, "UTF-8").toByteArray()
        }.getOrNull() ?: return DownloadResult.Failed
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val name = "download_${System.currentTimeMillis()}.$ext"
        val uri = withContext(Dispatchers.IO) { runCatching { writeToDownloads(name, mime, bytes) }.getOrNull() }
            ?: return DownloadResult.Failed
        dao.insert(
            DownloadEntity(
                systemId = -1, url = "data:$mime", fileName = name, mimeType = mime,
                totalBytes = bytes.size.toLong(), downloadedBytes = bytes.size.toLong(),
                status = DownloadStatus.COMPLETED, createdAt = System.currentTimeMillis(),
                localUri = uri.toString(),
            )
        )
        return DownloadResult.Started(name)
    }

    private fun writeToDownloads(name: String, mime: String, bytes: ByteArray): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("open failed")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("no storage")
        val file = File(dir, name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val active = dao.getActive()
                if (active.isEmpty()) break
                refresh(active)
                delay(1000)
            }
        }
    }

    private suspend fun refresh(items: List<DownloadEntity>) {
        val tracked = items.filter { it.systemId >= 0 }
        if (tracked.isEmpty()) return
        data class Row(val status: Int, val total: Long, val done: Long, val local: String?)
        val rows = withContext(Dispatchers.IO) {
            val map = HashMap<Long, Row>()
            runCatching {
                dm.query(DownloadManager.Query().setFilterById(*tracked.map { it.systemId }.toLongArray()))?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                    val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val localCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                    while (c.moveToNext()) {
                        val status = when (c.getInt(statusCol)) {
                            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                            else -> DownloadStatus.RUNNING
                        }
                        map[c.getLong(idCol)] = Row(status, c.getLong(totalCol), c.getLong(doneCol), c.getString(localCol))
                    }
                }
            }
            map
        }
        tracked.forEach { item ->
            val row = rows[item.systemId]
            val updated = if (row == null) {
                item.copy(status = DownloadStatus.FAILED)
            } else {
                item.copy(
                    status = row.status,
                    totalBytes = if (row.total > 0) row.total else item.totalBytes,
                    downloadedBytes = row.done.coerceAtLeast(0),
                    localUri = row.local ?: item.localUri,
                )
            }
            if (updated != item) dao.update(updated)
        }
    }

    private fun contentUri(item: DownloadEntity): Uri? =
        if (item.systemId >= 0) runCatching { dm.getUriForDownloadedFile(item.systemId) }.getOrNull()
        else item.localUri?.let(Uri::parse)

    fun open(item: DownloadEntity): Boolean {
        val uri = contentUri(item) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun share(item: DownloadEntity): Boolean {
        val uri = contentUri(item) ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, item.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    suspend fun delete(item: DownloadEntity) {
        withContext(Dispatchers.IO) {
            if (item.systemId >= 0) {
                runCatching { dm.remove(item.systemId) }
            } else {
                item.localUri?.let { runCatching { context.contentResolver.delete(Uri.parse(it), null, null) } }
            }
        }
        dao.delete(item)
    }

    suspend fun retry(item: DownloadEntity, wifiOnly: Boolean): Boolean {
        if (item.systemId < 0 || !UrlUtils.isWebUrl(item.url)) return false
        runCatching { dm.remove(item.systemId) }
        val cookies = runCatching { CookieManager.getInstance().getCookie(item.url) }.getOrNull()
        val id = try {
            dm.enqueue(buildRequest(item.url, item.fileName, item.mimeType, item.userAgent, cookies, null, wifiOnly))
        } catch (e: Exception) {
            return false
        }
        dao.update(
            item.copy(
                systemId = id, status = DownloadStatus.RUNNING, downloadedBytes = 0,
                createdAt = System.currentTimeMillis(), localUri = null,
            )
        )
        startPolling()
        return true
    }

    private fun resolveMime(mimeType: String?, fileName: String): String {
        val given = mimeType?.substringBefore(';')?.trim()
        if (!given.isNullOrBlank() && given != "application/octet-stream") return given
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").take(150).ifBlank { "download" }

    companion object {
        private const val MAX_DATA_URL = 30 * 1024 * 1024
    }
}
