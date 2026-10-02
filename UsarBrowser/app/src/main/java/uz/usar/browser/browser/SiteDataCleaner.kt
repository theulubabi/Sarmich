package uz.usar.browser.browser

import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * WebView has no per-site cookie API, so cookies are expired by name for the site's origins
 * and local storage is removed per origin. This covers the common cases.
 */
object SiteDataCleaner {
    fun clear(
        host: String,
        cookieManager: CookieManager = CookieManager.getInstance(),
        storage: WebStorage = WebStorage.getInstance(),
    ) {
        if (host.isBlank()) return
        val origins = listOf("https://$host", "http://$host", "https://www.$host", "http://www.$host")
        origins.forEach { origin ->
            cookieManager.getCookie(origin)
                ?.split(';')
                ?.map { it.substringBefore('=').trim() }
                ?.filter { it.isNotEmpty() }
                ?.forEach { name ->
                    cookieManager.setCookie(origin, "$name=; Max-Age=0; Path=/")
                    cookieManager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.$host")
                }
            storage.deleteOrigin(origin)
        }
        cookieManager.flush()
    }

    fun cookieCount(url: String, cookieManager: CookieManager): Int =
        cookieManager.getCookie(url)?.split(';')?.count { it.isNotBlank() } ?: 0
}
