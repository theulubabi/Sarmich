package uz.usar.browser.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Private tabs use a separate WebView profile (cookies, storage, cache) when the installed
 * WebView supports it. The profile is deleted when the last private tab closes.
 */
@SuppressLint("RequiresFeature")
object PrivateProfile {
    private const val NAME = "usar_private_session"

    val isSupported: Boolean
        get() = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    /** Must be called right after the WebView is constructed, before it loads anything. */
    fun apply(webView: WebView): Boolean {
        if (!isSupported) return false
        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(NAME)
            WebViewCompat.setProfile(webView, NAME)
            true
        }.getOrDefault(false)
    }

    fun cookieManager(): CookieManager =
        if (isSupported) {
            runCatching { ProfileStore.getInstance().getOrCreateProfile(NAME).cookieManager }
                .getOrElse { CookieManager.getInstance() }
        } else {
            CookieManager.getInstance()
        }

    fun webStorage(): WebStorage =
        if (isSupported) {
            runCatching { ProfileStore.getInstance().getOrCreateProfile(NAME).webStorage }
                .getOrElse { WebStorage.getInstance() }
        } else {
            WebStorage.getInstance()
        }

    /** Deletes all private-session data. Fails harmlessly if a private WebView is still alive. */
    fun delete() {
        if (!isSupported) return
        runCatching {
            val store = ProfileStore.getInstance()
            if (store.allProfileNames.contains(NAME)) store.deleteProfile(NAME)
        }
    }
}
