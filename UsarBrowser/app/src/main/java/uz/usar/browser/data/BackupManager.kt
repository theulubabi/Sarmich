package uz.usar.browser.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.db.SiteEntity

/**
 * Exports favorites and saved websites as plain JSON. Passwords, cookies, tokens and
 * private browsing data are never part of the backup.
 */
class BackupManager(private val context: Context, private val repository: BrowserRepository) {

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val sites = repository.allSites()
        val array = JSONArray()
        sites.forEach { s ->
            array.put(
                JSONObject()
                    .put("url", s.url)
                    .put("title", s.title)
                    .put("customName", s.customName ?: JSONObject.NULL)
                    .put("favorite", s.isFavorite)
                    .put("pinned", s.isPinned)
                    .put("position", s.position)
                    .put("visitCount", s.visitCount)
                    .put("lastVisited", s.lastVisited)
                    .put("dateAdded", s.dateAdded)
            )
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("sites", array)
        val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open output")
        stream.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        sites.size
    }

    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_BYTES) { "Backup too large" }
            String(bytes, Charsets.UTF_8)
        } ?: error("Cannot open input")
        val root = JSONObject(text)
        val array = root.getJSONArray("sites")
        val items = buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (!UrlUtils.isWebUrl(url)) continue
                add(
                    SiteEntity(
                        url = url,
                        title = o.optString("title").take(500),
                        host = UrlUtils.host(url),
                        customName = if (o.isNull("customName")) null else o.optString("customName").take(200),
                        isFavorite = o.optBoolean("favorite"),
                        isPinned = o.optBoolean("pinned"),
                        position = o.optInt("position"),
                        visitCount = o.optInt("visitCount").coerceAtLeast(0),
                        lastVisited = o.optLong("lastVisited"),
                        dateAdded = o.optLong("dateAdded", System.currentTimeMillis()),
                    )
                )
            }
        }
        repository.importSites(items)
    }

    companion object {
        const val FORMAT = "usar-browser-backup"
        private const val MAX_BYTES = 10 * 1024 * 1024
    }
}
