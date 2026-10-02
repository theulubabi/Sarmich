package uz.usar.browser.browser

import android.content.MutableContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

sealed interface PageError {
    data class Network(val code: Int, val description: String) : PageError
    data class Ssl(val url: String) : PageError
    data object Crashed : PageError
}

/** UI-observable state of one browser tab. The WebView is created lazily when the tab is first shown. */
class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val incognito: Boolean,
    val parentId: String? = null,
    initialUrl: String = "",
    initialTitle: String = "",
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var desktopMode by mutableStateOf(false)
    var showStartPage by mutableStateOf(initialUrl.isBlank())
    var error by mutableStateOf<PageError?>(null)
    var findActive by mutableIntStateOf(0)
    var findCount by mutableIntStateOf(0)
    var webView by mutableStateOf<BrowserWebView?>(null)

    internal var contextWrapper: MutableContextWrapper? = null
    internal var pendingUserVisit = false
    internal var lastRequestedUrl: String? = null
    internal var crashCount = 0
    internal val popupTimes = ArrayDeque<Long>()

    val displayTitle: String
        get() = title.ifBlank { UrlUtils.displayUrl(url) }
}
