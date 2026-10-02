package uz.usar.browser.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

/** Implemented by the browser view model; receives every WebView event that needs a decision. */
interface BrowserHost {
    fun onPageStarted(tab: BrowserTab, url: String)
    fun onPageFinished(tab: BrowserTab, url: String)
    fun onTitleChanged(tab: BrowserTab, title: String)
    fun onFavicon(tab: BrowserTab, icon: Bitmap)
    fun onExternalNavigation(tab: BrowserTab, url: String, hasGesture: Boolean): Boolean
    fun onMainFrameError(tab: BrowserTab, error: PageError)
    fun onRendererGone(tab: BrowserTab, crashed: Boolean)
    fun onCreateWindow(tab: BrowserTab, isUserGesture: Boolean, resultMsg: Message): Boolean
    fun onCloseWindow(tab: BrowserTab)
    fun onPermissionRequest(tab: BrowserTab, request: PermissionRequest)
    fun onPermissionRequestCanceled(request: PermissionRequest)
    fun onGeolocationPrompt(tab: BrowserTab, origin: String, callback: GeolocationPermissions.Callback)
    fun onShowFileChooser(
        tab: BrowserTab,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean
    fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideCustomView()
}

class BrowserWebViewClient(private val tab: BrowserTab, private val host: BrowserHost) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase(Locale.ROOT)
        if (scheme == "http" || scheme == "https") {
            if (request.isForMainFrame) tab.lastRequestedUrl = url
            return false
        }
        if (scheme == "about" || scheme == "blob") return false
        // Sub-frames may never launch apps or load non-web schemes.
        if (!request.isForMainFrame) return true
        return host.onExternalNavigation(tab, url, request.hasGesture())
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        tab.isLoading = true
        tab.progress = 5
        tab.error = null
        tab.favicon = favicon
        if (url != null) tab.url = url
        host.onPageStarted(tab, url.orEmpty())
    }

    override fun onPageFinished(view: WebView, url: String?) {
        tab.isLoading = false
        tab.progress = 100
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
        if (url != null) tab.url = url
        host.onPageFinished(tab, url.orEmpty())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (url != null) tab.url = url
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        if (error.errorCode == ERROR_UNSUPPORTED_SCHEME || error.errorCode == ERROR_UNKNOWN) return
        host.onMainFrameError(tab, PageError.Network(error.errorCode, error.description?.toString().orEmpty()))
    }

    /** Certificate errors are never bypassed. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        val failing = error.url
        if (failing == tab.lastRequestedUrl || failing == view.url || view.url == null) {
            host.onMainFrameError(tab, PageError.Ssl(failing))
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.onRendererGone(tab, detail.didCrash())
        return true
    }
}

class BrowserChromeClient(private val tab: BrowserTab, private val host: BrowserHost) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        tab.progress = newProgress
        if (newProgress >= 100) tab.isLoading = false
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        tab.title = title.orEmpty()
        host.onTitleChanged(tab, tab.title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        if (icon != null) {
            tab.favicon = icon
            host.onFavicon(tab, icon)
        }
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
        host.onCreateWindow(tab, isUserGesture, resultMsg)

    override fun onCloseWindow(window: WebView) = host.onCloseWindow(tab)

    override fun onPermissionRequest(request: PermissionRequest) = host.onPermissionRequest(tab, request)

    override fun onPermissionRequestCanceled(request: PermissionRequest) = host.onPermissionRequestCanceled(request)

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        host.onGeolocationPrompt(tab, origin, callback)

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.onShowFileChooser(tab, filePathCallback, fileChooserParams)

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = host.onShowCustomView(view, callback)

    override fun onHideCustomView() = host.onHideCustomView()

    /** Avoids the grey placeholder WebView draws before a video starts. */
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    /** Page console output is never logged (it can contain sensitive values). */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
}
