package uz.usar.browser.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.usar.browser.R
import uz.usar.browser.data.db.HostSettingsEntity
import uz.usar.browser.data.settings.BrowserSettings
import uz.usar.browser.data.settings.PermType
import uz.usar.browser.data.settings.PermValue
import uz.usar.browser.ui.Routes
import uz.usar.browser.ui.browser.permLabel
import uz.usar.browser.ui.browser.permValueLabel
import uz.usar.browser.ui.components.ChoiceDialog
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.SectionHeader
import uz.usar.browser.ui.components.SettingsItem
import uz.usar.browser.ui.components.SiteRow

private fun permIcon(type: PermType): ImageVector = when (type) {
    PermType.CAMERA -> Icons.Filled.PhotoCamera
    PermType.MICROPHONE -> Icons.Filled.Mic
    PermType.LOCATION -> Icons.Filled.LocationOn
    PermType.POPUPS -> Icons.Filled.OpenInBrowser
    PermType.DOWNLOADS -> Icons.Filled.Download
}

/** Global defaults never offer "Allow" for sensitive device permissions. */
private fun globalOptions(type: PermType): List<Int> = when (type) {
    PermType.CAMERA, PermType.MICROPHONE, PermType.LOCATION -> listOf(PermValue.ASK, PermValue.BLOCK)
    PermType.POPUPS -> listOf(PermValue.BLOCK, PermValue.ALLOW)
    PermType.DOWNLOADS -> listOf(PermValue.ASK, PermValue.ALLOW, PermValue.BLOCK)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePermissionsScreen(vm: SettingsViewModel, onBack: () -> Unit, navigate: (String) -> Unit) {
    val state by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: BrowserSettings()
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PermType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_permissions)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.default_behaviour)) }
            items(PermType.entries) { type ->
                SettingsItem(stringResource(permLabel(type)), stringResource(permValueLabel(s.defaultFor(type))), permIcon(type)) {
                    editing = type
                }
            }
            item {
                SettingsItem(
                    stringResource(R.string.perm_notifications),
                    stringResource(R.string.perm_not_supported),
                    Icons.Filled.Notifications,
                    enabled = false,
                )
            }
            item {
                SettingsItem(
                    stringResource(R.string.perm_clipboard),
                    stringResource(R.string.perm_clipboard_summary),
                    Icons.Filled.ContentPaste,
                    enabled = false,
                )
            }
            item { SectionHeader(stringResource(R.string.sites_with_settings)) }
            if (hosts.isEmpty()) {
                item { Text(stringResource(R.string.no_site_settings), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            items(hosts, key = { it.host }) { h ->
                SiteRow(
                    host = h.host,
                    title = h.host,
                    subtitle = summarize(h),
                    modifier = Modifier.clickable { navigate(Routes.site(h.host)) },
                )
            }
        }
    }

    editing?.let { type ->
        ChoiceDialog(
            title = stringResource(permLabel(type)),
            options = globalOptions(type).map { it to stringResource(permValueLabel(it)) },
            selected = s.defaultFor(type),
            onSelect = { vm.setGlobalPermission(type, it) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun summarize(h: HostSettingsEntity): String {
    val parts = PermType.entries.filter { h.get(it) != PermValue.DEFAULT }
        .map { "${stringResource(permLabel(it))}: ${stringResource(permValueLabel(h.get(it)))}" }
        .toMutableList()
    if (h.desktopMode != 0) parts += stringResource(if (h.desktopMode == 2) R.string.desktop_site else R.string.mobile_site)
    return parts.joinToString(", ").ifEmpty { stringResource(R.string.perm_default) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsScreen(host: String, vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val entry = hosts.firstOrNull { it.host == host } ?: HostSettingsEntity(host)
    var editing by remember { mutableStateOf<PermType?>(null) }
    var editingDesktop by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val desktopLabels = listOf(
        0 to stringResource(R.string.perm_default),
        1 to stringResource(R.string.mobile_site),
        2 to stringResource(R.string.desktop_site),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(host) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.site_permissions)) }
            items(PermType.entries) { type ->
                SettingsItem(stringResource(permLabel(type)), stringResource(permValueLabel(entry.get(type))), permIcon(type)) {
                    editing = type
                }
            }
            item { SectionHeader(stringResource(R.string.site_display)) }
            item {
                SettingsItem(
                    stringResource(R.string.site_version),
                    desktopLabels.first { it.first == entry.desktopMode }.second,
                    Icons.Filled.DesktopWindows,
                ) { editingDesktop = true }
            }
            item { SectionHeader(stringResource(R.string.site_data)) }
            item { SettingsItem(stringResource(R.string.clear_site_data), null, Icons.Filled.DeleteSweep) { confirmClear = true } }
            item {
                SettingsItem(stringResource(R.string.reset_permissions), null, Icons.Filled.Restore) {
                    vm.resetHost(host)
                    Toast.makeText(context, R.string.permissions_reset, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    editing?.let { type ->
        ChoiceDialog(
            title = stringResource(permLabel(type)),
            options = listOf(PermValue.DEFAULT, PermValue.ASK, PermValue.ALLOW, PermValue.BLOCK).map { it to stringResource(permValueLabel(it)) },
            selected = entry.get(type),
            onSelect = { vm.setHostPermission(host, type, it) },
            onDismiss = { editing = null },
        )
    }
    if (editingDesktop) {
        ChoiceDialog(
            title = stringResource(R.string.site_version),
            options = desktopLabels,
            selected = entry.desktopMode,
            onSelect = { vm.setHostDesktopMode(host, it) },
            onDismiss = { editingDesktop = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_site_data),
            message = stringResource(R.string.clear_site_data_message, host),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = {
                vm.clearSiteData(host)
                Toast.makeText(context, R.string.site_data_cleared, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

