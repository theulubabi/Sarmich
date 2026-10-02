package uz.usar.browser.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import uz.usar.browser.data.settings.BrowserSettings

/** Applies browser settings to a WebView. Security-relevant defaults are fixed here and never relaxed. */
object WebViewConfigurator {
    private var cachedDefaultUa: String? = null

    private fun defaultUserAgent(webView: WebView): String =
        cachedDefaultUa ?: WebSettings.getDefaultUserAgent(webView.context).also { cachedDefaultUa = it }

    fun desktopUserAgent(webView: WebView): String {
        val chrome = Regex("""Chrome/[\d.]+""").find(defaultUserAgent(webView))?.value ?: "Chrome/124.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, settings: BrowserSettings, incognito: Boolean, desktop: Boolean) {
        webView.settings.apply {
            javaScriptEnabled = settings.javascript
            domStorageEnabled = true
            databaseEnabled = true
            // Web pages must never read local files or app content providers.
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(settings.pinchZoom)
            textZoom = settings.textZoom
            cacheMode = if (incognito && !PrivateProfile.isSupported) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        applyUserAgent(webView, desktop)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, settings.forceDarkWeb)
        }
        val cookies = if (incognito) PrivateProfile.cookieManager() else CookieManager.getInstance()
        cookies.setAcceptCookie(settings.cookies)
        cookies.setAcceptThirdPartyCookies(webView, settings.cookies && settings.thirdPartyCookies)
        webView.setInitialScale(settings.defaultZoom.coerceIn(0, 300))
    }

    fun applyUserAgent(webView: WebView, desktop: Boolean) {
        val target = if (desktop) desktopUserAgent(webView) else null
        if (webView.settings.userAgentString != (target ?: defaultUserAgent(webView))) {
            webView.settings.userAgentString = target
        }
    }
}
