package uz.usar.browser.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.usar.browser.BuildConfig
import uz.usar.browser.R
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.settings.AccountMode
import uz.usar.browser.data.settings.BrowserSettings
import uz.usar.browser.data.settings.PermType
import uz.usar.browser.data.settings.PermValue
import uz.usar.browser.data.settings.SearchEngine
import uz.usar.browser.data.settings.SettingsRepository.Keys
import uz.usar.browser.data.settings.StartupMode
import uz.usar.browser.data.settings.ThemeMode
import uz.usar.browser.ui.Routes
import uz.usar.browser.ui.components.ChoiceDialog
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.SectionHeader
import uz.usar.browser.ui.components.SettingsItem
import uz.usar.browser.ui.components.SettingsSwitch

private enum class SettingsDialog { SIGN_OUT, HOMEPAGE, ENGINE, LANGUAGE, THEME, STARTUP, TEXT_SCALE, DEFAULT_ZOOM, RESET, RESET_DATA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, navigate: (String) -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val state by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: BrowserSettings()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri) { count ->
            val msg = if (count != null) context.getString(R.string.export_done, count) else context.getString(R.string.export_failed)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri) { count ->
            val msg = if (count != null) context.getString(R.string.import_done, count) else context.getString(R.string.import_failed)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val themeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    val startupLabels = mapOf(
        StartupMode.START_PAGE to stringResource(R.string.startup_start_page),
        StartupMode.NEW_TAB to stringResource(R.string.startup_new_tab),
        StartupMode.LAST_WEBSITE to stringResource(R.string.startup_last_website),
        StartupMode.LAST_TABS to stringResource(R.string.startup_last_tabs),
        StartupMode.HOMEPAGE to stringResource(R.string.startup_homepage),
    )
    val languageLabels = linkedMapOf<String?, String>(
        null to stringResource(R.string.language_system),
        "en" to "English",
        "uz" to "O‘zbekcha",
        "ru" to "Русский",
    )
    val currentLanguage = vm.currentLanguage()
    val zoomOptions = listOf(0, 75, 90, 100, 110, 125, 150)
    val autoLabel = stringResource(R.string.zoom_automatic)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.account)) }
            item {
                if (s.accountMode == AccountMode.GOOGLE) {
                    SettingsItem(
                        s.accountName.ifBlank { s.accountEmail },
                        stringResource(R.string.account_google_summary, s.accountEmail),
                        Icons.Filled.AccountCircle,
                    ) { dialog = SettingsDialog.SIGN_OUT }
                } else {
                    SettingsItem(
                        stringResource(R.string.account_guest),
                        stringResource(R.string.account_guest_summary),
                        Icons.Filled.PersonOutline,
                    ) { navigate(Routes.SIGN_IN) }
                }
            }
            item { SectionHeader(stringResource(R.string.settings_general)) }
            item {
                SettingsItem(
                    stringResource(R.string.set_homepage),
                    s.homepage.ifBlank { stringResource(R.string.homepage_not_set) },
                    Icons.Filled.Home,
                ) { dialog = SettingsDialog.HOMEPAGE }
            }
            item { SettingsItem(stringResource(R.string.set_search_engine), s.searchEngine.label, Icons.Filled.Search) { dialog = SettingsDialog.ENGINE } }
            item {
                SettingsItem(stringResource(R.string.set_language), languageLabels[currentLanguage] ?: languageLabels[null], Icons.Filled.Language) {
                    dialog = SettingsDialog.LANGUAGE
                }
            }
            item { SettingsItem(stringResource(R.string.set_theme), themeLabels[s.theme], Icons.Filled.Palette) { dialog = SettingsDialog.THEME } }
            item { SettingsItem(stringResource(R.string.set_startup), startupLabels[s.startup], Icons.Filled.Restore) { dialog = SettingsDialog.STARTUP } }

            item { SectionHeader(stringResource(R.string.settings_browsing)) }
            item { SettingsSwitch(stringResource(R.string.set_desktop_default), s.desktopMode, { vm.set(Keys.DESKTOP_MODE, it) }, icon = Icons.Filled.DesktopWindows) }
            item {
                SettingsSwitch(
                    stringResource(R.string.set_javascript), s.javascript, { vm.set(Keys.JAVASCRIPT, it) },
                    summary = stringResource(R.string.set_javascript_summary),
                )
            }
            item { SettingsSwitch(stringResource(R.string.set_pull_refresh), s.pullToRefresh, { vm.set(Keys.PULL_TO_REFRESH, it) }) }
            item { SettingsSwitch(stringResource(R.string.set_auto_hide_toolbar), s.autoHideToolbar, { vm.set(Keys.AUTO_HIDE_TOOLBAR, it) }) }
            item {
                SettingsSwitch(
                    stringResource(R.string.set_open_new_tab), s.openLinksInNewTab, { vm.set(Keys.OPEN_LINKS_NEW_TAB, it) },
                    summary = stringResource(R.string.set_open_new_tab_summary),
                )
            }
            item { SettingsSwitch(stringResource(R.string.set_ask_external), s.askBeforeExternalApps, { vm.set(Keys.ASK_EXTERNAL_APPS, it) }) }
            item {
                SettingsSwitch(
                    stringResource(R.string.set_auto_save_sites), s.autoSaveSites, { vm.set(Keys.AUTO_SAVE_SITES, it) },
                    summary = stringResource(R.string.set_auto_save_sites_summary),
                )
            }
            item {
                SettingsSwitch(
                    stringResource(R.string.set_web_dark), s.forceDarkWeb, { vm.set(Keys.FORCE_DARK_WEB, it) },
                    summary = stringResource(R.string.set_web_dark_summary),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_privacy)) }
            item { SettingsItem(stringResource(R.string.clear_browsing_data), null, Icons.Filled.DeleteSweep) { navigate(Routes.CLEAR_DATA) } }
            item { SettingsItem(stringResource(R.string.site_permissions), stringResource(R.string.site_permissions_summary), Icons.Filled.Security) { navigate(Routes.SITE_PERMISSIONS) } }
            item { SettingsSwitch(stringResource(R.string.set_save_history), s.saveHistory, { vm.set(Keys.SAVE_HISTORY, it) }, icon = Icons.Filled.History) }
            item { SettingsSwitch(stringResource(R.string.set_cookies), s.cookies, { vm.set(Keys.COOKIES, it) }) }
            item {
                SettingsSwitch(
                    stringResource(R.string.set_third_party_cookies), s.thirdPartyCookies, { vm.set(Keys.THIRD_PARTY_COOKIES, it) },
                    summary = stringResource(R.string.set_third_party_cookies_summary),
                    enabled = s.cookies,
                )
            }
            item {
                SettingsItem(stringResource(R.string.clear_cache), stringResource(R.string.clear_cache_summary), Icons.Filled.Storage) {
                    vm.clearCache()
                    Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                }
            }

            item { SectionHeader(stringResource(R.string.settings_downloads)) }
            item { SettingsItem(stringResource(R.string.download_location), stringResource(R.string.download_location_summary), Icons.Filled.Download) { navigate(Routes.DOWNLOADS) } }
            item {
                SettingsSwitch(
                    stringResource(R.string.set_ask_download), s.permDownloads != PermValue.ALLOW,
                    { vm.setGlobalPermission(PermType.DOWNLOADS, if (it) PermValue.ASK else PermValue.ALLOW) },
                )
            }
            item { SettingsSwitch(stringResource(R.string.set_wifi_only), s.downloadWifiOnly, { vm.set(Keys.DOWNLOAD_WIFI_ONLY, it) }) }

            item { SectionHeader(stringResource(R.string.settings_accessibility)) }
            item { SettingsItem(stringResource(R.string.set_text_scale), "${s.textZoom}%", Icons.Filled.TextFields) { dialog = SettingsDialog.TEXT_SCALE } }
            item {
                SettingsItem(
                    stringResource(R.string.set_default_zoom),
                    if (s.defaultZoom == 0) autoLabel else "${s.defaultZoom}%",
                    Icons.Filled.ZoomIn,
                ) { dialog = SettingsDialog.DEFAULT_ZOOM }
            }
            item { SettingsSwitch(stringResource(R.string.set_pinch_zoom), s.pinchZoom, { vm.set(Keys.PINCH_ZOOM, it) }, icon = Icons.Filled.Accessibility) }

            item { SectionHeader(stringResource(R.string.settings_backup)) }
            item {
                SettingsItem(stringResource(R.string.export_sites), stringResource(R.string.export_sites_summary), Icons.Filled.Backup) {
                    exportLauncher.launch("usar-sites.json")
                }
            }
            item {
                SettingsItem(stringResource(R.string.import_sites), null, Icons.Filled.FileUpload) {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
            }

            item { SectionHeader(stringResource(R.string.settings_advanced)) }
            item { SettingsItem(stringResource(R.string.webview_info), null, Icons.Filled.Code) { navigate(Routes.WEBVIEW_INFO) } }
            item { SettingsItem(stringResource(R.string.reset_settings), stringResource(R.string.reset_settings_summary), Icons.Filled.Restore) { dialog = SettingsDialog.RESET } }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item { SettingsItem(stringResource(R.string.app_version), BuildConfig.VERSION_NAME, Icons.Filled.Info) }
            item { SettingsItem(stringResource(R.string.privacy_info), null, Icons.Filled.Security) { navigate(Routes.ABOUT) } }
        }
    }

    when (dialog) {
        SettingsDialog.SIGN_OUT -> ConfirmDialog(
            title = stringResource(R.string.sign_out),
            message = stringResource(R.string.sign_out_message, s.accountEmail),
            confirmLabel = stringResource(R.string.sign_out),
            onConfirm = { vm.signOut(onSignedOut) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.HOMEPAGE -> HomepageDialog(s.homepage, onSave = { vm.set(Keys.HOMEPAGE, it) }, onDismiss = { dialog = null })
        SettingsDialog.ENGINE -> ChoiceDialog(
            stringResource(R.string.set_search_engine),
            SearchEngine.entries.map { it to it.label },
            s.searchEngine,
            onSelect = { vm.set(Keys.SEARCH_ENGINE, it.name) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            stringResource(R.string.set_language),
            languageLabels.entries.map { it.key to it.value },
            currentLanguage,
            onSelect = { vm.setLanguage(it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            stringResource(R.string.set_theme),
            themeLabels.entries.map { it.key to it.value },
            s.theme,
            onSelect = { vm.setTheme(it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.STARTUP -> ChoiceDialog(
            stringResource(R.string.set_startup),
            startupLabels.entries.map { it.key to it.value },
            s.startup,
            onSelect = { vm.set(Keys.STARTUP, it.name) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.TEXT_SCALE -> TextScaleDialog(s.textZoom, onSave = { vm.set(Keys.TEXT_ZOOM, it) }, onDismiss = { dialog = null })
        SettingsDialog.DEFAULT_ZOOM -> ChoiceDialog(
            stringResource(R.string.set_default_zoom),
            zoomOptions.map { it to if (it == 0) autoLabel else "$it%" },
            s.defaultZoom,
            onSelect = { vm.set(Keys.DEFAULT_ZOOM, it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.RESET -> ConfirmDialog(
            title = stringResource(R.string.reset_settings),
            message = stringResource(R.string.reset_settings_message),
            confirmLabel = stringResource(R.string.reset),
            onConfirm = { vm.resetSettings { dialog = SettingsDialog.RESET_DATA } },
            onDismiss = { if (dialog == SettingsDialog.RESET) dialog = null },
        )
        SettingsDialog.RESET_DATA -> ConfirmDialog(
            title = stringResource(R.string.reset_done),
            message = stringResource(R.string.reset_data_question),
            confirmLabel = stringResource(R.string.clear_browsing_data),
            destructive = false,
            onConfirm = { navigate(Routes.CLEAR_DATA) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun HomepageDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val valid = text.isBlank() || UrlUtils.validHomepage(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_homepage)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("https://example.com") },
                isError = !valid,
                supportingText = { Text(stringResource(if (valid) R.string.homepage_help else R.string.homepage_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(if (text.isBlank()) "" else UrlUtils.validHomepage(text).orEmpty())
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextScaleDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_text_scale)) },
        text = {
            Column {
                Text("${value.toInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(value = value, onValueChange = { value = it }, valueRange = 50f..200f, steps = 14)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.text_scale_preview), fontSize = (16 * value / 100f).sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(((value / 10f).toInt() * 10).coerceIn(50, 200))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
