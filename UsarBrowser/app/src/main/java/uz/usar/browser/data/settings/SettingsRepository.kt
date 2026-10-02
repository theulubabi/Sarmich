package uz.usar.browser.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore by preferencesDataStore(name = "browser_settings")

class SettingsRepository(private val context: Context, scope: CoroutineScope) {

    object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val THEME = stringPreferencesKey("theme")
        val STARTUP = stringPreferencesKey("startup")
        val HOMEPAGE = stringPreferencesKey("homepage")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val JAVASCRIPT = booleanPreferencesKey("javascript")
        val PINCH_ZOOM = booleanPreferencesKey("pinch_zoom")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val DEFAULT_ZOOM = intPreferencesKey("default_zoom")
        val PULL_TO_REFRESH = booleanPreferencesKey("pull_to_refresh")
        val AUTO_HIDE_TOOLBAR = booleanPreferencesKey("auto_hide_toolbar")
        val OPEN_LINKS_NEW_TAB = booleanPreferencesKey("open_links_new_tab")
        val FORCE_DARK_WEB = booleanPreferencesKey("force_dark_web")
        val COOKIES = booleanPreferencesKey("cookies")
        val THIRD_PARTY_COOKIES = booleanPreferencesKey("third_party_cookies")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val AUTO_SAVE_SITES = booleanPreferencesKey("auto_save_sites")
        val ASK_EXTERNAL_APPS = booleanPreferencesKey("ask_external_apps")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SITES_SORT = stringPreferencesKey("sites_sort")
        val PERM_CAMERA = intPreferencesKey("perm_camera")
        val PERM_MICROPHONE = intPreferencesKey("perm_microphone")
        val PERM_LOCATION = intPreferencesKey("perm_location")
        val PERM_POPUPS = intPreferencesKey("perm_popups")
        val PERM_DOWNLOADS = intPreferencesKey("perm_downloads")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ACCOUNT_MODE = stringPreferencesKey("account_mode")
        val ACCOUNT_NAME = stringPreferencesKey("account_name")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")

        fun forPermission(type: PermType): Preferences.Key<Int> = when (type) {
            PermType.CAMERA -> PERM_CAMERA
            PermType.MICROPHONE -> PERM_MICROPHONE
            PermType.LOCATION -> PERM_LOCATION
            PermType.POPUPS -> PERM_POPUPS
            PermType.DOWNLOADS -> PERM_DOWNLOADS
        }
    }

    /** null until the first read from disk completes. */
    val state: StateFlow<BrowserSettings?> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val current: BrowserSettings get() = state.value ?: BrowserSettings()

    suspend fun awaitLoaded(): BrowserSettings = state.filterNotNull().first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
        if (key == Keys.THEME) mirrorTheme(value as String)
    }

    /** Only the display name and e-mail are kept. Google ID tokens are never stored. */
    suspend fun setAccount(mode: AccountMode, name: String = "", email: String = "") {
        context.dataStore.edit {
            it[Keys.ACCOUNT_MODE] = mode.name
            it[Keys.ACCOUNT_NAME] = name
            it[Keys.ACCOUNT_EMAIL] = email
        }
    }

    /** Restores defaults; onboarding and account state are kept. */
    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            val onboarding = prefs[Keys.ONBOARDING_DONE]
            val mode = prefs[Keys.ACCOUNT_MODE]
            val name = prefs[Keys.ACCOUNT_NAME]
            val email = prefs[Keys.ACCOUNT_EMAIL]
            prefs.clear()
            if (onboarding != null) prefs[Keys.ONBOARDING_DONE] = onboarding
            if (mode != null) prefs[Keys.ACCOUNT_MODE] = mode
            if (name != null) prefs[Keys.ACCOUNT_NAME] = name
            if (email != null) prefs[Keys.ACCOUNT_EMAIL] = email
        }
        mirrorTheme(ThemeMode.SYSTEM.name)
    }

    private fun mirrorTheme(value: String) {
        context.getSharedPreferences(MIRROR, Context.MODE_PRIVATE).edit().putString("theme", value).apply()
    }

    private fun Preferences.toSettings(): BrowserSettings {
        val d = BrowserSettings()
        return BrowserSettings(
            searchEngine = this[Keys.SEARCH_ENGINE].toEnum(d.searchEngine),
            theme = this[Keys.THEME].toEnum(d.theme),
            startup = this[Keys.STARTUP].toEnum(d.startup),
            homepage = this[Keys.HOMEPAGE] ?: d.homepage,
            desktopMode = this[Keys.DESKTOP_MODE] ?: d.desktopMode,
            javascript = this[Keys.JAVASCRIPT] ?: d.javascript,
            pinchZoom = this[Keys.PINCH_ZOOM] ?: d.pinchZoom,
            textZoom = (this[Keys.TEXT_ZOOM] ?: d.textZoom).coerceIn(50, 200),
            defaultZoom = this[Keys.DEFAULT_ZOOM] ?: d.defaultZoom,
            pullToRefresh = this[Keys.PULL_TO_REFRESH] ?: d.pullToRefresh,
            autoHideToolbar = this[Keys.AUTO_HIDE_TOOLBAR] ?: d.autoHideToolbar,
            openLinksInNewTab = this[Keys.OPEN_LINKS_NEW_TAB] ?: d.openLinksInNewTab,
            forceDarkWeb = this[Keys.FORCE_DARK_WEB] ?: d.forceDarkWeb,
            cookies = this[Keys.COOKIES] ?: d.cookies,
            thirdPartyCookies = this[Keys.THIRD_PARTY_COOKIES] ?: d.thirdPartyCookies,
            saveHistory = this[Keys.SAVE_HISTORY] ?: d.saveHistory,
            autoSaveSites = this[Keys.AUTO_SAVE_SITES] ?: d.autoSaveSites,
            askBeforeExternalApps = this[Keys.ASK_EXTERNAL_APPS] ?: d.askBeforeExternalApps,
            downloadWifiOnly = this[Keys.DOWNLOAD_WIFI_ONLY] ?: d.downloadWifiOnly,
            sitesSort = this[Keys.SITES_SORT].toEnum(d.sitesSort),
            permCamera = this[Keys.PERM_CAMERA] ?: d.permCamera,
            permMicrophone = this[Keys.PERM_MICROPHONE] ?: d.permMicrophone,
            permLocation = this[Keys.PERM_LOCATION] ?: d.permLocation,
            permPopups = this[Keys.PERM_POPUPS] ?: d.permPopups,
            permDownloads = this[Keys.PERM_DOWNLOADS] ?: d.permDownloads,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: d.onboardingDone,
            accountMode = this[Keys.ACCOUNT_MODE].toEnum(d.accountMode),
            accountName = this[Keys.ACCOUNT_NAME] ?: d.accountName,
            accountEmail = this[Keys.ACCOUNT_EMAIL] ?: d.accountEmail,
        )
    }

    companion object {
        private const val MIRROR = "ui_mirror"

        /** Synchronous read so the correct day/night theme is applied before the first frame. */
        fun storedTheme(context: Context): ThemeMode =
            context.getSharedPreferences(MIRROR, Context.MODE_PRIVATE).getString("theme", null).toEnum(ThemeMode.SYSTEM)

        fun nightModeFor(mode: ThemeMode): Int = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}

inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
