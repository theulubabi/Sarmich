package uz.usar.browser.ui.browser

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.R
import uz.usar.browser.browser.ActivityBridge
import uz.usar.browser.browser.BrowserChromeClient
import uz.usar.browser.browser.BrowserHost
import uz.usar.browser.browser.BrowserTab
import uz.usar.browser.browser.BrowserWebView
import uz.usar.browser.browser.BrowserWebViewClient
import uz.usar.browser.browser.CrashGuard
import uz.usar.browser.browser.DownloadResult
import uz.usar.browser.browser.ExternalAppHandler
import uz.usar.browser.browser.NavDecision
import uz.usar.browser.browser.PageError
import uz.usar.browser.browser.PrivateProfile
import uz.usar.browser.browser.SiteDataCleaner
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.browser.WebViewConfigurator
import uz.usar.browser.browser.WebViewProblem
import uz.usar.browser.browser.WebViewStatus
import uz.usar.browser.data.Suggestion
import uz.usar.browser.data.db.HistoryEntity
import uz.usar.browser.data.db.HostSettingsEntity
import uz.usar.browser.data.db.SiteEntity
import uz.usar.browser.data.db.TabEntity
import uz.usar.browser.data.settings.BrowserSettings
import uz.usar.browser.data.settings.PermType
import uz.usar.browser.data.settings.PermValue
import uz.usar.browser.data.settings.StartupMode

data class UiMessage(
    @StringRes val text: Int,
    val args: List<String> = emptyList(),
    @StringRes val actionLabel: Int? = null,
    val action: (() -> Unit)? = null,
)

data class UploadRequest(
    val showCamera: Boolean,
    val showVideo: Boolean,
    val showGallery: Boolean,
    val showDocuments: Boolean,
    val multiple: Boolean,
    val mimeTypes: List<String>,
)

enum class UploadSource { CAMERA, VIDEO, GALLERY, DOCUMENTS, FILES }

data class SiteInfoData(
    val url: String,
    val host: String,
    val https: Boolean,
    val cookieCount: Int,
    val incognito: Boolean,
    val hostSettings: HostSettingsEntity?,
)

sealed interface BrowserDialog {
    data class Permission(
        val host: String,
        val types: List<PermType>,
        val protectedMedia: Boolean,
        val incognito: Boolean,
        val onResult: (allow: Boolean, remember: Boolean) -> Unit,
    ) : BrowserDialog

    data class ExternalApp(val label: String?, val onOpen: () -> Unit, val onCancel: () -> Unit) : BrowserDialog
    data class DownloadConfirm(val fileName: String, val size: Long, val onConfirm: () -> Unit) : BrowserDialog
    data class Upload(val request: UploadRequest) : BrowserDialog
    data class SiteInfo(val info: SiteInfoData) : BrowserDialog
}

class BrowserViewModel(private val c: AppContainer) : ViewModel(), BrowserHost {

    val tabs = mutableStateListOf<BrowserTab>()
    var activeTabId by mutableStateOf<String?>(null)
        private set
    val activeTab: BrowserTab? get() = tabs.firstOrNull { it.id == activeTabId }

    val settings: StateFlow<BrowserSettings?> = c.settings.state
    private val currentSettings: BrowserSettings get() = c.settings.current
    val isOnline: StateFlow<Boolean> = c.network.isOnline
    val favicons = c.favicons

    var dialog by mutableStateOf<BrowserDialog?>(null)
    var customView by mutableStateOf<View?>(null)
        private set
    var toolbarVisible by mutableStateOf(true)
    var fullscreen by mutableStateOf(false)
        private set
    var findVisible by mutableStateOf(false)
        private set
    var findQuery by mutableStateOf("")
        private set
    var showTabSwitcher by mutableStateOf(false)
    var focusAddressBar by mutableStateOf(false)
    var addressFocused by mutableStateOf(false)
    var webViewProblem by mutableStateOf<WebViewProblem?>(null)
    var outdatedDismissed by mutableStateOf(false)
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val favorites: StateFlow<List<SiteEntity>> =
        c.repository.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pinned: StateFlow<List<SiteEntity>> =
        c.repository.observePinned().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostVisited: StateFlow<List<SiteEntity>> =
        c.repository.observeMostVisited(8).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent: StateFlow<List<HistoryEntity>> =
        c.repository.observeRecentHistory(6).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activity: Activity? = null
    private var bridge: ActivityBridge? = null
    private var initialized = false
    private var hostCache: Map<String, HostSettingsEntity> = emptyMap()
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFileRequest: UploadRequest? = null
    private var pendingPermission: PermissionRequest? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var persistJob: Job? = null
    private var suggestJob: Job? = null
    private var scrollAccumulator = 0

    private val context: Context get() = activity ?: c.app

    init {
        viewModelScope.launch {
            c.repository.observeHostSettings().collect { list -> hostCache = list.associateBy { it.host } }
        }
        viewModelScope.launch {
            c.settings.state.filterNotNull().distinctUntilChanged().collect { s ->
                CookieManager.getInstance().setAcceptCookie(s.cookies)
                tabs.forEach { tab -> tab.webView?.let { WebViewConfigurator.apply(it, s, tab.incognito, tab.desktopMode) } }
                if (!s.autoHideToolbar && !fullscreen) toolbarVisible = true
            }
        }
        c.downloads.startPolling()
    }

    // ------------------------------------------------------------------ lifecycle

    fun attach(activity: Activity, bridge: ActivityBridge) {
        this.activity = activity
        this.bridge = bridge
        tabs.forEach { it.contextWrapper?.baseContext = activity }
        // Results of pickers started by a previous Activity instance cannot be delivered; release the page.
        if (dialog !is BrowserDialog.Upload) finishFileChooser(null)
    }

    fun detach(activity: Activity) {
        if (this.activity !== activity) return
        this.activity = null
        this.bridge = null
        tabs.forEach { it.contextWrapper?.baseContext = c.app }
    }

    fun onActivityPause() {
        activeTab?.webView?.onPause()
        CookieManager.getInstance().flush()
        persistTabsNow()
    }

    fun onActivityResume() {
        activeTab?.webView?.onResume()
        c.downloads.startPolling()
    }

    fun initialize(intentUrl: String?) {
        if (initialized) {
            intentUrl?.let(::openFromIntent)
            return
        }
        initialized = true
        webViewProblem = WebViewStatus.check(c.app)
        if (webViewProblem == WebViewProblem.Missing) return
        // Remove anything a previous, killed private session may have left behind.
        PrivateProfile.delete()
        viewModelScope.launch {
            val s = c.settings.awaitLoaded()
            val crashes = CrashGuard.recentCrashCount(c.app)
            val safeMode = CrashGuard.shouldSkipRestore(c.app)
            if (!safeMode) {
                when (s.startup) {
                    StartupMode.LAST_TABS -> restoreTabs()
                    StartupMode.LAST_WEBSITE -> c.repository.latestHistory()?.let { newTab(it.url, userInitiated = false) }
                    StartupMode.HOMEPAGE -> UrlUtils.validHomepage(s.homepage)?.let { newTab(it, userInitiated = false) }
                    StartupMode.NEW_TAB -> Unit
                    StartupMode.START_PAGE -> Unit
                }
            }
            if (tabs.isEmpty()) newTab()
            if (s.startup == StartupMode.NEW_TAB && intentUrl == null) focusAddressBar = true
            intentUrl?.let(::openFromIntent)
            if (crashes > 0) emit(UiMessage(if (safeMode) R.string.recovered_safe_mode else R.string.recovered_from_crash))
            delay(15_000)
            CrashGuard.markStable(c.app)
        }
    }

    override fun onCleared() {
        tabs.forEach(::destroyWebView)
        PrivateProfile.delete()
        super.onCleared()
    }

    // ------------------------------------------------------------------ tabs

    private fun ensureWebView(tab: BrowserTab): BrowserWebView? {
        tab.webView?.let { return it }
        val wrapper = MutableContextWrapper(activity ?: c.app)
        val webView = try {
            BrowserWebView(wrapper)
        } catch (t: Throwable) {
            // WebView is missing, disabled, or being updated.
            webViewProblem = WebViewProblem.Missing
            return null
        }
        if (tab.incognito) PrivateProfile.apply(webView)
        WebViewConfigurator.apply(webView, currentSettings, tab.incognito, tab.desktopMode)
        webView.webViewClient = BrowserWebViewClient(tab, this)
        webView.webChromeClient = BrowserChromeClient(tab, this)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            onDownload(tab, url, userAgent, contentDisposition, mimeType, contentLength)
        }
        webView.setFindListener { activeMatch, matches, _ ->
            tab.findActive = activeMatch
            tab.findCount = matches
        }
        webView.onScrollDelta = { dy -> if (tab.id == activeTabId) onScroll(dy, webView.scrollY) }
        webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        tab.contextWrapper = wrapper
        tab.webView = webView
        return webView
    }

    private fun destroyWebView(tab: BrowserTab) {
        val webView = tab.webView ?: return
        tab.webView = null
        tab.contextWrapper = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.onScrollDelta = null
        webView.stopLoading()
        webView.destroy()
    }

    fun newTab(
        url: String? = null,
        incognito: Boolean = false,
        parent: BrowserTab? = null,
        userInitiated: Boolean = true,
    ): BrowserTab {
        val tab = BrowserTab(incognito = incognito, parentId = parent?.id)
        val index = parent?.let { tabs.indexOf(it) + 1 } ?: tabs.size
        tabs.add(index.coerceIn(0, tabs.size), tab)
        selectTab(tab)
        if (url != null) navigate(tab, url, userInitiated)
        persistTabs()
        return tab
    }

    fun newTabWithFocus(incognito: Boolean = false) {
        newTab(incognito = incognito)
        focusAddressBar = true
    }

    fun selectTab(tab: BrowserTab, closeSwitcher: Boolean = true) {
        val previous = activeTab
        if (previous != null && previous !== tab) previous.webView?.onPause()
        activeTabId = tab.id
        if (closeSwitcher) showTabSwitcher = false
        toolbarVisible = true
        closeFind()
        tab.webView?.onResume()
        if (!tab.showStartPage && tab.webView == null && UrlUtils.isWebUrl(tab.url)) {
            navigate(tab, tab.url, userInitiated = false)
        }
        persistTabs()
    }

    fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        val wasActive = tab.id == activeTabId
        destroyWebView(tab)
        tabs.removeAt(index)
        if (tab.incognito && tabs.none { it.incognito }) endPrivateSession()
        if (wasActive) {
            val next = tabs.firstOrNull { it.id == tab.parentId }
                ?: tabs.lastOrNull { it.incognito == tab.incognito }
                ?: tabs.lastOrNull()
            if (next != null) selectTab(next, closeSwitcher = false) else newTab()
        }
        persistTabs()
    }

    fun closeAllTabs(incognito: Boolean) {
        tabs.filter { it.incognito == incognito }.forEach { tab ->
            destroyWebView(tab)
            tabs.remove(tab)
        }
        if (incognito) endPrivateSession()
        val next = tabs.lastOrNull()
        if (next != null) selectTab(next, closeSwitcher = false) else newTab()
        persistTabs()
    }

    private fun endPrivateSession() {
        viewModelScope.launch {
            delay(500)
            if (tabs.none { it.incognito }) PrivateProfile.delete()
        }
    }

    private fun persistTabs() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(800)
            persistTabsNow()
        }
    }

    private fun persistTabsNow() {
        val snapshot = tabs.filter { !it.incognito }.mapIndexed { i, t ->
            TabEntity(t.id, if (t.showStartPage) "" else t.url, t.title, i, t.id == activeTabId)
        }
        c.appScope.launch { runCatching { c.repository.saveTabs(snapshot) } }
    }

    private suspend fun restoreTabs() {
        val saved = c.repository.loadTabs()
        saved.forEach { e ->
            val url = e.url.takeIf { UrlUtils.isWebUrl(it) } ?: ""
            tabs.add(BrowserTab(id = e.id, incognito = false, initialUrl = url, initialTitle = e.title))
        }
        val active = saved.firstOrNull { it.isActive }?.id?.let { id -> tabs.firstOrNull { it.id == id } } ?: tabs.lastOrNull()
        active?.let { selectTab(it) }
    }

    // ------------------------------------------------------------------ navigation

    fun loadInput(text: String) {
        val url = UrlUtils.resolveInput(text, currentSettings.searchEngine) ?: return
        suggestions = emptyList()
        val tab = activeTab ?: newTab()
        navigate(tab, url, userInitiated = true)
    }

    fun openUrl(url: String, inNewTab: Boolean = false) {
        if (inNewTab) newTab(url) else navigate(activeTab ?: newTab(), url, userInitiated = true)
    }

    fun openFromIntent(url: String) {
        if (UrlUtils.isWebUrl(url)) newTab(url)
    }

    fun searchFromIntent(query: String) {
        UrlUtils.resolveInput(query, currentSettings.searchEngine)?.let { newTab(it) }
    }

    private fun navigate(tab: BrowserTab, url: String, userInitiated: Boolean) {
        if (!UrlUtils.isWebUrl(url)) {
            when (val decision = ExternalAppHandler.classify(url)) {
                is NavDecision.External -> requestExternal(tab, decision)
                is NavDecision.LoadUrl -> navigate(tab, decision.url, userInitiated)
                else -> emit(UiMessage(R.string.link_blocked))
            }
            return
        }
        val webView = ensureWebView(tab) ?: return
        tab.showStartPage = false
        tab.error = null
        tab.url = url
        tab.lastRequestedUrl = url
        tab.pendingUserVisit = userInitiated && !tab.incognito
        val desired = when (hostCache[UrlUtils.host(url)]?.desktopMode) {
            1 -> false
            2 -> true
            else -> currentSettings.desktopMode
        }
        if (desired != tab.desktopMode) {
            tab.desktopMode = desired
            WebViewConfigurator.applyUserAgent(webView, desired)
        }
        webView.loadUrl(url)
    }

    fun reload() {
        val tab = activeTab ?: return
        if (tab.showStartPage) return
        tab.error = null
        val webView = tab.webView
        if (webView == null || tab.crashCount > 0 && webView.url == null) {
            navigate(tab, tab.url, userInitiated = false)
        } else {
            webView.reload()
        }
    }

    fun stopLoading() {
        activeTab?.webView?.stopLoading()
        activeTab?.isLoading = false
    }

    fun goBack() {
        val tab = activeTab ?: return
        if (tab.showStartPage && tab.webView != null && UrlUtils.isWebUrl(tab.url)) {
            tab.showStartPage = false
            return
        }
        tab.webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        activeTab?.webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun goHome() {
        val tab = activeTab ?: return
        val home = UrlUtils.validHomepage(currentSettings.homepage)
        if (currentSettings.startup == StartupMode.HOMEPAGE && home != null) {
            navigate(tab, home, userInitiated = false)
        } else {
            tab.showStartPage = true
            tab.error = null
            closeFind()
        }
    }

    /** Returns true if the back press was consumed. */
    fun handleBack(): Boolean {
        if (customView != null) {
            exitVideoFullscreen()
            return true
        }
        if (showTabSwitcher) {
            showTabSwitcher = false
            return true
        }
        if (findVisible) {
            closeFind()
            return true
        }
        if (fullscreen) {
            setFullscreen(false)
            return true
        }
        val tab = activeTab ?: return false
        if (tab.showStartPage) {
            if (tab.webView != null && UrlUtils.isWebUrl(tab.url)) {
                tab.showStartPage = false
                return true
            }
        } else {
            val webView = tab.webView
            if (webView != null && webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        if (tab.parentId != null || tabs.size > 1 && tab.incognito) {
            closeTab(tab)
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ toolbar, find, zoom

    private fun onScroll(dy: Int, scrollY: Int) {
        if (!currentSettings.autoHideToolbar || fullscreen || addressFocused) return
        if (scrollY <= 0) {
            toolbarVisible = true
            scrollAccumulator = 0
            return
        }
        scrollAccumulator = if ((scrollAccumulator > 0) == (dy > 0)) scrollAccumulator + dy else dy
        if (scrollAccumulator > 48 && toolbarVisible) toolbarVisible = false
        if (scrollAccumulator < -48 && !toolbarVisible) toolbarVisible = true
    }

    fun setFullscreen(on: Boolean) {
        fullscreen = on
        toolbarVisible = !on
    }

    fun openFind() {
        if (activeTab?.showStartPage != false) return
        findVisible = true
        findQuery = ""
    }

    fun find(query: String) {
        findQuery = query
        val webView = activeTab?.webView ?: return
        if (query.isBlank()) {
            webView.clearMatches()
            activeTab?.findCount = 0
        } else {
            webView.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        activeTab?.webView?.findNext(forward)
    }

    fun closeFind() {
        if (!findVisible) return
        findVisible = false
        findQuery = ""
        activeTab?.webView?.clearMatches()
        activeTab?.findCount = 0
    }

    fun zoomIn() { activeTab?.webView?.zoomIn() }
    fun zoomOut() { activeTab?.webView?.zoomOut() }

    fun resetZoom() {
        val webView = activeTab?.webView ?: return
        repeat(30) { if (!webView.zoomOut()) return }
    }

    fun toggleDesktopMode() {
        val tab = activeTab ?: return
        val webView = tab.webView ?: return
        tab.desktopMode = !tab.desktopMode
        WebViewConfigurator.applyUserAgent(webView, tab.desktopMode)
        webView.reload()
    }

    // ------------------------------------------------------------------ share, copy, favorites

    fun share(url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .putExtra(Intent.EXTRA_SUBJECT, title)
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun shareCurrent() {
        val tab = activeTab ?: return
        if (!tab.showStartPage) share(tab.url, tab.title)
    }

    fun copy(text: String) {
        val cm = c.app.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("url", text))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) emit(UiMessage(R.string.copied))
    }

    fun copyUrl() {
        val tab = activeTab ?: return
        if (!tab.showStartPage) copy(tab.url)
    }

    fun copyTitleAndUrl() {
        val tab = activeTab ?: return
        if (!tab.showStartPage) copy("${tab.displayTitle}\n${tab.url}")
    }

    fun openExternally() {
        val tab = activeTab ?: return
        if (tab.showStartPage || !UrlUtils.isWebUrl(tab.url)) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val ok = runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) emit(UiMessage(R.string.no_app_found))
    }

    fun observeIsFavorite(url: String): Flow<Boolean> = c.repository.observeIsFavorite(url)

    fun toggleFavorite() {
        val tab = activeTab ?: return
        if (tab.showStartPage || !UrlUtils.isWebUrl(tab.url)) return
        val url = tab.url
        val title = tab.title
        viewModelScope.launch {
            val fav = c.repository.toggleFavorite(url, title)
            emit(UiMessage(if (fav) R.string.added_to_favorites else R.string.removed_from_favorites))
        }
    }

    fun pinCurrent() {
        val tab = activeTab ?: return
        if (tab.showStartPage || !UrlUtils.isWebUrl(tab.url)) return
        val url = tab.url
        val title = tab.title
        viewModelScope.launch {
            c.repository.pinUrl(url, title)
            emit(UiMessage(R.string.pinned_to_start))
        }
    }

    fun toggleFavorite(site: SiteEntity) {
        viewModelScope.launch { c.repository.setFavorite(site, !site.isFavorite) }
    }

    fun togglePinned(site: SiteEntity) {
        viewModelScope.launch { c.repository.setPinned(site, !site.isPinned) }
    }

    fun deleteSite(site: SiteEntity) {
        viewModelScope.launch { c.repository.deleteSite(site) }
    }

    fun editSite(site: SiteEntity, name: String, url: String) {
        viewModelScope.launch {
            val ok = UrlUtils.isWebUrl(url) && c.repository.editSite(site, name, url)
            if (!ok) emit(UiMessage(R.string.edit_url_invalid))
        }
    }

    // ------------------------------------------------------------------ suggestions, QR

    fun querySuggestions(text: String) {
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(120)
            suggestions = runCatching { c.repository.suggestions(text) }.getOrDefault(emptyList())
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        suggestions = emptyList()
    }

    fun scanQr(prompt: String) {
        val bridge = bridge ?: return
        viewModelScope.launch {
            val text = bridge.scanQr(prompt) ?: return@launch
            val url = UrlUtils.fromQrText(text)
            if (url == null) emit(UiMessage(R.string.qr_invalid)) else newTab(url)
        }
    }

    // ------------------------------------------------------------------ site info

    fun showSiteInfo() {
        val tab = activeTab ?: return
        if (tab.showStartPage || !UrlUtils.isWebUrl(tab.url)) return
        val host = UrlUtils.host(tab.url)
        val cookies = if (tab.incognito) PrivateProfile.cookieManager() else CookieManager.getInstance()
        dialog = BrowserDialog.SiteInfo(
            SiteInfoData(
                url = tab.url,
                host = host,
                https = UrlUtils.isHttps(tab.url),
                cookieCount = SiteDataCleaner.cookieCount(tab.url, cookies),
                incognito = tab.incognito,
                hostSettings = hostCache[host],
            )
        )
    }

    fun clearCurrentSiteData() {
        val tab = activeTab ?: return
        val host = UrlUtils.host(tab.url)
        if (tab.incognito) {
            SiteDataCleaner.clear(host, PrivateProfile.cookieManager(), PrivateProfile.webStorage())
        } else {
            SiteDataCleaner.clear(host)
        }
        dialog = null
        emit(UiMessage(R.string.site_data_cleared))
        tab.webView?.reload()
    }

    fun resetSitePermissions(host: String) {
        viewModelScope.launch { c.repository.resetPermissions(host) }
        dialog = null
        emit(UiMessage(R.string.permissions_reset))
    }

    // ------------------------------------------------------------------ messages

    private fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    private fun resolvePermission(host: String, type: PermType): Int {
        val site = hostCache[host]?.get(type) ?: PermValue.DEFAULT
        return if (site != PermValue.DEFAULT) site else currentSettings.defaultFor(type)
    }

    // ------------------------------------------------------------------ BrowserHost: page events

    override fun onPageStarted(tab: BrowserTab, url: String) {
        if (tab.id == activeTabId && !fullscreen) toolbarVisible = true
    }

    override fun onPageFinished(tab: BrowserTab, url: String) {
        val saveSite = tab.pendingUserVisit && currentSettings.autoSaveSites
        tab.pendingUserVisit = false
        if (tab.incognito || tab.error != null || !UrlUtils.isWebUrl(url)) return
        val title = tab.title
        val saveHistory = currentSettings.saveHistory
        viewModelScope.launch { runCatching { c.repository.recordVisit(url, title, saveHistory, saveSite) } }
        persistTabs()
    }

    override fun onTitleChanged(tab: BrowserTab, title: String) {
        if (tab.incognito) return
        val url = tab.url
        val inHistory = currentSettings.saveHistory
        viewModelScope.launch { runCatching { c.repository.updateTitle(url, title, inHistory) } }
        persistTabs()
    }

    override fun onFavicon(tab: BrowserTab, icon: Bitmap) {
        if (tab.incognito) return
        val host = UrlUtils.host(tab.url)
        viewModelScope.launch { c.favicons.save(host, icon) }
    }

    override fun onMainFrameError(tab: BrowserTab, error: PageError) {
        tab.error = error
        tab.isLoading = false
    }

    override fun onRendererGone(tab: BrowserTab, crashed: Boolean) {
        val url = tab.url
        destroyWebView(tab)
        tab.crashCount++
        if (tab.id == activeTabId && tab.crashCount <= 2 && UrlUtils.isWebUrl(url)) {
            emit(UiMessage(R.string.page_crashed_reloaded))
            navigate(tab, url, userInitiated = false)
        } else {
            // Background tabs reload lazily when selected; repeated crashes stop automatic reloads.
            tab.error = if (tab.id == activeTabId) PageError.Crashed else null
        }
    }

    // ------------------------------------------------------------------ BrowserHost: external apps

    override fun onExternalNavigation(tab: BrowserTab, url: String, hasGesture: Boolean): Boolean {
        when (val decision = ExternalAppHandler.classify(url)) {
            NavDecision.AllowInWebView -> return false
            NavDecision.Block -> if (hasGesture) emit(UiMessage(R.string.link_blocked))
            is NavDecision.LoadUrl -> navigate(tab, decision.url, userInitiated = false)
            is NavDecision.External -> requestExternal(tab, decision)
        }
        return true
    }

    private fun requestExternal(tab: BrowserTab, decision: NavDecision.External) {
        if (dialog != null) return
        if (!currentSettings.askBeforeExternalApps) {
            launchExternal(tab, decision)
            return
        }
        dialog = BrowserDialog.ExternalApp(
            label = decision.label,
            onOpen = {
                dialog = null
                launchExternal(tab, decision)
            },
            onCancel = { dialog = null },
        )
    }

    private fun launchExternal(tab: BrowserTab, decision: NavDecision.External) {
        if (ExternalAppHandler.launch(context, decision.intent)) return
        val fallback = decision.fallbackUrl
        if (fallback != null) navigate(tab, fallback, userInitiated = false) else emit(UiMessage(R.string.no_app_found))
    }

    // ------------------------------------------------------------------ BrowserHost: pop-ups

    override fun onCreateWindow(tab: BrowserTab, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val host = UrlUtils.host(tab.url)
        val now = SystemClock.elapsedRealtime()
        while (tab.popupTimes.isNotEmpty() && now - tab.popupTimes.first() > 10_000) tab.popupTimes.removeFirst()
        val allowedBySite = resolvePermission(host, PermType.POPUPS) == PermValue.ALLOW
        if (tab.popupTimes.size >= 5 || (!isUserGesture && !allowedBySite)) {
            val canAllow = host.isNotBlank() && !tab.incognito
            val allowAction: (() -> Unit)? = if (canAllow) {
                {
                    viewModelScope.launch { c.repository.setPermission(host, PermType.POPUPS, PermValue.ALLOW) }
                    Unit
                }
            } else {
                null
            }
            emit(
                UiMessage(
                    R.string.popup_blocked,
                    actionLabel = if (canAllow) R.string.always_allow else null,
                    action = allowAction,
                )
            )
            return false
        }
        tab.popupTimes.addLast(now)
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

        if (currentSettings.openLinksInNewTab || !isUserGesture) {
            val child = BrowserTab(incognito = tab.incognito, parentId = tab.id)
            child.showStartPage = false
            val webView = ensureWebView(child) ?: return false
            tabs.add((tabs.indexOf(tab) + 1).coerceIn(0, tabs.size), child)
            selectTab(child)
            transport.webView = webView
            resultMsg.sendToTarget()
            persistTabs()
            return true
        }

        // Open the target in the current tab: a throwaway WebView captures the first URL.
        val catcher = WebView(context)
        if (tab.incognito) PrivateProfile.apply(catcher)
        var handled = false
        fun capture(view: WebView, url: String?) {
            if (handled || url.isNullOrBlank() || url == "about:blank") return
            handled = true
            view.stopLoading()
            view.post { view.destroy() }
            navigate(tab, url, userInitiated = false)
        }
        catcher.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                capture(view, request.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                capture(view, url)
            }
        }
        transport.webView = catcher
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(tab: BrowserTab) {
        closeTab(tab)
    }

    // ------------------------------------------------------------------ BrowserHost: permissions

    override fun onPermissionRequest(tab: BrowserTab, request: PermissionRequest) {
        val host = UrlUtils.host(request.origin?.toString())
        val wanted = request.resources.toList()
        val types = wanted.mapNotNull {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> PermType.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> PermType.MICROPHONE
                else -> null
            }
        }.distinct()
        val protectedMedia = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in wanted
        if (types.isEmpty() && !protectedMedia) {
            request.deny()
            return
        }
        val decisions = types.map { resolvePermission(host, it) }
        if (decisions.any { it == PermValue.BLOCK }) {
            request.deny()
            emit(UiMessage(R.string.permission_blocked_for_site))
            return
        }
        val grantable = wanted.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
        }
        if (decisions.all { it == PermValue.ALLOW } && !protectedMedia) {
            grantMedia(request, types, grantable)
            return
        }
        pendingPermission?.deny()
        pendingPermission = request
        dialog = BrowserDialog.Permission(host, types, protectedMedia, tab.incognito) { allow, remember ->
            dialog = null
            pendingPermission = null
            if (remember && !tab.incognito && host.isNotBlank()) {
                viewModelScope.launch {
                    types.forEach { c.repository.setPermission(host, it, if (allow) PermValue.ALLOW else PermValue.BLOCK) }
                }
            }
            if (allow) grantMedia(request, types, grantable) else request.deny()
        }
    }

    private fun grantMedia(request: PermissionRequest, types: List<PermType>, resources: List<String>) {
        val androidPerms = types.map {
            if (it == PermType.CAMERA) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
        }
        if (androidPerms.isEmpty()) {
            request.grant(resources.toTypedArray())
            return
        }
        val bridge = bridge
        if (bridge == null) {
            request.deny()
            return
        }
        viewModelScope.launch {
            val result = bridge.requestPermissions(androidPerms)
            if (result.values.all { it }) {
                request.grant(resources.toTypedArray())
            } else {
                request.deny()
                emit(UiMessage(R.string.android_permission_denied))
            }
        }
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (pendingPermission === request) {
            pendingPermission = null
            if (dialog is BrowserDialog.Permission) dialog = null
        }
    }

    override fun onGeolocationPrompt(tab: BrowserTab, origin: String, callback: GeolocationPermissions.Callback) {
        val host = UrlUtils.host(origin)
        fun grantWithAndroid() {
            val bridge = bridge
            if (bridge == null) {
                callback.invoke(origin, false, false)
                return
            }
            viewModelScope.launch {
                val result = bridge.requestPermissions(
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
                val granted = result.values.any { it }
                if (!granted) emit(UiMessage(R.string.android_permission_denied))
                callback.invoke(origin, granted, false)
            }
        }
        when (resolvePermission(host, PermType.LOCATION)) {
            PermValue.BLOCK -> callback.invoke(origin, false, false)
            PermValue.ALLOW -> grantWithAndroid()
            else -> {
                if (dialog != null) {
                    callback.invoke(origin, false, false)
                    return
                }
                dialog = BrowserDialog.Permission(host, listOf(PermType.LOCATION), false, tab.incognito) { allow, remember ->
                    dialog = null
                    if (remember && !tab.incognito && host.isNotBlank()) {
                        viewModelScope.launch {
                            c.repository.setPermission(host, PermType.LOCATION, if (allow) PermValue.ALLOW else PermValue.BLOCK)
                        }
                    }
                    if (allow) grantWithAndroid() else callback.invoke(origin, false, false)
                }
            }
        }
    }

    // ------------------------------------------------------------------ downloads

    private fun onDownload(
        tab: BrowserTab,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ) {
        if (url.startsWith("blob:", ignoreCase = true)) {
            emit(UiMessage(R.string.download_unsupported))
            return
        }
        val host = UrlUtils.host(tab.url)
        val start = {
            val cookies = if (UrlUtils.isWebUrl(url)) {
                runCatching {
                    (if (tab.incognito) PrivateProfile.cookieManager() else CookieManager.getInstance()).getCookie(url)
                }.getOrNull()
            } else null
            val referer = tab.url
            val wifiOnly = currentSettings.downloadWifiOnly
            viewModelScope.launch {
                when (val result = c.downloads.enqueue(url, userAgent, contentDisposition, mimeType, contentLength, cookies, referer, wifiOnly)) {
                    is DownloadResult.Started -> emit(UiMessage(R.string.download_started, listOf(result.fileName)))
                    DownloadResult.Unsupported -> emit(UiMessage(R.string.download_unsupported))
                    DownloadResult.Failed -> emit(UiMessage(R.string.download_failed))
                }
            }
            Unit
        }
        when (resolvePermission(host, PermType.DOWNLOADS)) {
            PermValue.BLOCK -> emit(UiMessage(R.string.download_blocked))
            PermValue.ALLOW -> start()
            else -> {
                if (dialog != null) return
                val name = if (url.startsWith("data:", true)) "download" else c.downloads.guessFileName(url, contentDisposition, mimeType)
                dialog = BrowserDialog.DownloadConfirm(name, contentLength) {
                    dialog = null
                    start()
                }
            }
        }
        // A tab opened only to start a download would stay blank; close it.
        if (tab.parentId != null && tab.webView?.url == null && tab.id == activeTabId) {
            closeTab(tab)
        }
    }

    // ------------------------------------------------------------------ uploads

    override fun onShowFileChooser(
        tab: BrowserTab,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        finishFileChooser(null)
        pendingFileCallback = callback
        val accept = (params.acceptTypes ?: emptyArray<String>()).toList()
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { type ->
                if (type.startsWith(".")) MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1)) ?: "*/*" else type
            }
            .distinct()
        val any = accept.isEmpty() || "*/*" in accept
        val images = any || accept.any { it.startsWith("image/") }
        val videos = any || accept.any { it.startsWith("video/") }
        val docs = any || accept.any { it.startsWith("application/") || it.startsWith("text/") }
        val request = UploadRequest(
            showCamera = images,
            showVideo = videos,
            showGallery = images || videos,
            showDocuments = docs,
            multiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
            mimeTypes = if (any) emptyList() else accept,
        )
        pendingFileRequest = request
        if (params.isCaptureEnabled && images != videos) {
            onUploadSource(if (images) UploadSource.CAMERA else UploadSource.VIDEO)
        } else {
            dialog = BrowserDialog.Upload(request)
        }
        return true
    }

    fun onUploadSource(source: UploadSource) {
        val request = pendingFileRequest
        val bridge = bridge
        dialog = null
        if (request == null || bridge == null) {
            finishFileChooser(null)
            return
        }
        viewModelScope.launch {
            val uris: List<Uri> = runCatching {
                when (source) {
                    UploadSource.CAMERA -> listOfNotNull(bridge.capturePhoto())
                    UploadSource.VIDEO -> listOfNotNull(bridge.captureVideo())
                    UploadSource.GALLERY -> {
                        val wantsImages = request.mimeTypes.isEmpty() || request.mimeTypes.any { it.startsWith("image/") }
                        val wantsVideos = request.mimeTypes.isEmpty() || request.mimeTypes.any { it.startsWith("video/") }
                        val type = when {
                            wantsImages && !wantsVideos -> ActivityResultContracts.PickVisualMedia.ImageOnly
                            wantsVideos && !wantsImages -> ActivityResultContracts.PickVisualMedia.VideoOnly
                            else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        }
                        bridge.pickMedia(request.multiple, type)
                    }
                    UploadSource.DOCUMENTS -> bridge.openDocuments(DOCUMENT_TYPES, request.multiple)
                    UploadSource.FILES -> bridge.openDocuments(request.mimeTypes, request.multiple)
                }
            }.getOrDefault(emptyList())
            if (source == UploadSource.CAMERA || source == UploadSource.VIDEO) {
                if (uris.isEmpty() && bridge.hasPermission(Manifest.permission.CAMERA).not()) {
                    emit(UiMessage(R.string.android_permission_denied))
                }
            }
            finishFileChooser(uris)
        }
    }

    fun cancelUpload() {
        dialog = null
        finishFileChooser(null)
    }

    /** The WebView callback must always be answered, otherwise the page cannot open a picker again. */
    private fun finishFileChooser(uris: List<Uri>?) {
        val callback = pendingFileCallback ?: return
        pendingFileCallback = null
        pendingFileRequest = null
        callback.onReceiveValue(uris?.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    // ------------------------------------------------------------------ fullscreen video

    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onHideCustomView() {
        (customView?.parent as? ViewGroup)?.removeView(customView)
        customView = null
        customViewCallback = null
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun exitVideoFullscreen() {
        val callback = customViewCallback
        onHideCustomView()
        callback?.onCustomViewHidden()
    }

    companion object {
        private val DOCUMENT_TYPES = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "application/rtf",
        )

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BrowserViewModel(container) }
        }
    }
}
