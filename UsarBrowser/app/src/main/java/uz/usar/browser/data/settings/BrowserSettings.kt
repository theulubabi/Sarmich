package uz.usar.browser.data.settings

enum class SearchEngine(val label: String, private val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p=%s");

    fun searchUrl(encodedQuery: String): String = template.replace("%s", encodedQuery)
}

/** NONE = the user has not chosen yet, so the sign-in screen is shown. */
enum class AccountMode { NONE, GUEST, GOOGLE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class StartupMode { START_PAGE, NEW_TAB, LAST_WEBSITE, LAST_TABS, HOMEPAGE }

enum class SortMode { RECENT, MOST_VISITED, NAME, DATE_ADDED }

/** Permission kinds the browser lets the user control per site. */
enum class PermType { CAMERA, MICROPHONE, LOCATION, POPUPS, DOWNLOADS }

/** Stored values for a permission decision. DEFAULT means "follow the global setting". */
object PermValue {
    const val DEFAULT = 0
    const val ASK = 1
    const val ALLOW = 2
    const val BLOCK = 3
}

data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val startup: StartupMode = StartupMode.START_PAGE,
    val homepage: String = "",
    val desktopMode: Boolean = false,
    val javascript: Boolean = true,
    val pinchZoom: Boolean = true,
    val textZoom: Int = 100,
    /** Initial page scale in percent; 0 lets WebView pick (fit to width). */
    val defaultZoom: Int = 0,
    val pullToRefresh: Boolean = true,
    val autoHideToolbar: Boolean = false,
    val openLinksInNewTab: Boolean = true,
    val forceDarkWeb: Boolean = false,
    val cookies: Boolean = true,
    val thirdPartyCookies: Boolean = false,
    val saveHistory: Boolean = true,
    val autoSaveSites: Boolean = true,
    val askBeforeExternalApps: Boolean = true,
    val downloadWifiOnly: Boolean = false,
    val sitesSort: SortMode = SortMode.RECENT,
    val permCamera: Int = PermValue.ASK,
    val permMicrophone: Int = PermValue.ASK,
    val permLocation: Int = PermValue.ASK,
    val permPopups: Int = PermValue.BLOCK,
    val permDownloads: Int = PermValue.ASK,
    val onboardingDone: Boolean = false,
    val accountMode: AccountMode = AccountMode.NONE,
    val accountName: String = "",
    val accountEmail: String = "",
) {
    fun defaultFor(type: PermType): Int = when (type) {
        PermType.CAMERA -> permCamera
        PermType.MICROPHONE -> permMicrophone
        PermType.LOCATION -> permLocation
        PermType.POPUPS -> permPopups
        PermType.DOWNLOADS -> permDownloads
    }
}
