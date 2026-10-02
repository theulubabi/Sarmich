package uz.usar.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Caches site icons on disk (app-private) and in memory. Keyed by host. */
class FaviconStore(context: Context) {
    private val dir = File(context.filesDir, "favicons").apply { mkdirs() }
    private val memory = LruCache<String, Bitmap>(150)
    private val missing = HashSet<String>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun key(host: String) = host.lowercase().removePrefix("www.")
    private fun file(host: String) = File(dir, key(host).replace(Regex("[^a-z0-9.-]"), "_") + ".png")

    fun cached(host: String): Bitmap? = if (host.isBlank()) null else memory.get(key(host))

    suspend fun save(host: String, icon: Bitmap) {
        if (host.isBlank()) return
        withContext(Dispatchers.IO) {
            val scaled = if (icon.width > 96 || icon.height > 96) Bitmap.createScaledBitmap(icon, 96, 96, true) else icon
            runCatching { file(host).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            memory.put(key(host), scaled)
            synchronized(missing) { missing.remove(key(host)) }
        }
        _version.value = _version.value + 1
    }

    suspend fun load(host: String): Bitmap? {
        if (host.isBlank()) return null
        memory.get(key(host))?.let { return it }
        if (synchronized(missing) { key(host) in missing }) return null
        return withContext(Dispatchers.IO) {
            val f = file(host)
            val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() else null
            if (bmp != null) memory.put(key(host), bmp) else synchronized(missing) { missing.add(key(host)) }
            bmp
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
        memory.evictAll()
        synchronized(missing) { missing.clear() }
        _version.value = _version.value + 1
    }
}
