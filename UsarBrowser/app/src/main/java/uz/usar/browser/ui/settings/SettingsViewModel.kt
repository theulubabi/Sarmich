package uz.usar.browser.ui.settings

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.browser.PrivateProfile
import uz.usar.browser.browser.SiteDataCleaner
import uz.usar.browser.browser.WebViewStatus
import uz.usar.browser.data.db.HostSettingsEntity
import uz.usar.browser.data.settings.AccountMode
import uz.usar.browser.data.settings.BrowserSettings
import uz.usar.browser.data.settings.PermType
import uz.usar.browser.data.settings.SettingsRepository
import uz.usar.browser.data.settings.SettingsRepository.Keys
import uz.usar.browser.data.settings.ThemeMode

data class ClearOptions(
    val history: Boolean = true,
    val cookies: Boolean = true,
    val cache: Boolean = true,
    val siteStorage: Boolean = true,
    val permissions: Boolean = false,
    val savedSites: Boolean = false,
)

data class WebViewInfo(
    val packageName: String?,
    val version: String?,
    val userAgent: String?,
    val privateIsolation: Boolean,
    val darkening: Boolean,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<BrowserSettings?> = c.settings.state
    val hosts: StateFlow<List<HostSettingsEntity>> =
        c.repository.observeHostSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { c.settings.set(key, value) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            c.settings.set(Keys.THEME, mode.name)
            AppCompatDelegate.setDefaultNightMode(SettingsRepository.nightModeFor(mode))
        }
    }

    /** null = follow the system language. */
    fun setLanguage(tag: String?) {
        val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun currentLanguage(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.language
    }

    fun setGlobalPermission(type: PermType, value: Int) = set(Keys.forPermission(type), value)

    fun setHostPermission(host: String, type: PermType, value: Int) {
        viewModelScope.launch { c.repository.setPermission(host, type, value) }
    }

    fun setHostDesktopMode(host: String, mode: Int) {
        viewModelScope.launch { c.repository.setDesktopMode(host, mode) }
    }

    fun resetHost(host: String) {
        viewModelScope.launch { c.repository.resetPermissions(host) }
    }

    fun clearSiteData(host: String) = SiteDataCleaner.clear(host)

    fun clearCache() {
        runCatching { WebView(c.app).apply { clearCache(true); destroy() } }
    }

    fun clearBrowsingData(options: ClearOptions, onDone: () -> Unit) {
        viewModelScope.launch {
            if (options.history) c.repository.clearHistory()
            if (options.cookies) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                runCatching { WebViewDatabase.getInstance(c.app).clearHttpAuthUsernamePassword() }
            }
            if (options.cache) clearCache()
            if (options.siteStorage) {
                WebStorage.getInstance().deleteAllData()
                GeolocationPermissions.getInstance().clearAll()
            }
            if (options.permissions) c.repository.clearHostSettings()
            if (options.savedSites) {
                c.repository.deleteAllSites()
                c.favicons.clearAll()
            }
            onDone()
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            c.auth.signOut()
            c.settings.setAccount(AccountMode.NONE)
            onDone()
        }
    }

    fun resetSettings(onDone: () -> Unit) {
        viewModelScope.launch {
            c.settings.resetToDefaults()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            onDone()
        }
    }

    fun export(uri: Uri, onResult: (Int?) -> Unit) {
        viewModelScope.launch { onResult(runCatching { c.backup.export(uri) }.getOrNull()) }
    }

    fun import(uri: Uri, onResult: (Int?) -> Unit) {
        viewModelScope.launch { onResult(runCatching { c.backup.import(uri) }.getOrNull()) }
    }

    fun webViewInfo(): WebViewInfo {
        val pkg = WebViewStatus.packageInfo(c.app)
        return WebViewInfo(
            packageName = pkg?.packageName,
            version = pkg?.versionName,
            userAgent = runCatching { WebSettings.getDefaultUserAgent(c.app) }.getOrNull(),
            privateIsolation = PrivateProfile.isSupported,
            darkening = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) }.getOrDefault(false),
        )
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { SettingsViewModel(c) } }
    }
}
