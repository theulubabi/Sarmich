package uz.usar.browser.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.usar.browser.BuildConfig
import uz.usar.browser.R
import uz.usar.browser.ui.browser.openWebViewStore
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.SectionHeader
import uz.usar.browser.ui.components.SettingsItem
import uz.usar.browser.ui.components.SettingsSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        content = content,
    )
}

@Composable
fun ClearDataScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(ClearOptions()) }
    var confirm by remember { mutableStateOf(false) }
    SimpleScaffold(stringResource(R.string.clear_browsing_data), onBack) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
            item { SettingsSwitch(stringResource(R.string.cd_history), options.history, { options = options.copy(history = it) }) }
            item {
                SettingsSwitch(
                    stringResource(R.string.cd_cookies), options.cookies, { options = options.copy(cookies = it) },
                    summary = stringResource(R.string.cd_cookies_summary),
                )
            }
            item { SettingsSwitch(stringResource(R.string.cd_cache), options.cache, { options = options.copy(cache = it) }) }
            item { SettingsSwitch(stringResource(R.string.cd_site_storage), options.siteStorage, { options = options.copy(siteStorage = it) }) }
            item { SettingsSwitch(stringResource(R.string.cd_permissions), options.permissions, { options = options.copy(permissions = it) }) }
            item { SectionHeader(stringResource(R.string.cd_saved_header)) }
            item {
                SettingsSwitch(
                    stringResource(R.string.cd_saved_sites), options.savedSites, { options = options.copy(savedSites = it) },
                    summary = stringResource(R.string.cd_saved_sites_summary),
                )
            }
            item {
                Button(
                    onClick = { confirm = true },
                    enabled = options != ClearOptions(false, false, false, false, false, false),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.clear_data_button)) }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.clear_browsing_data),
            message = stringResource(if (options.savedSites) R.string.cd_confirm_with_saved else R.string.cd_confirm),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = {
                vm.clearBrowsingData(options) {
                    Toast.makeText(context, R.string.cd_done, Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun WebViewInfoScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember { vm.webViewInfo() }
    val yes = stringResource(R.string.supported)
    val no = stringResource(R.string.not_supported)
    SimpleScaffold(stringResource(R.string.webview_info), onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SettingsItem(stringResource(R.string.webview_package), info.packageName ?: "—")
            SettingsItem(stringResource(R.string.webview_version), info.version ?: "—")
            SettingsItem(stringResource(R.string.webview_private_isolation), if (info.privateIsolation) yes else no)
            SettingsItem(stringResource(R.string.webview_darkening), if (info.darkening) yes else no)
            SettingsItem(stringResource(R.string.webview_user_agent), info.userAgent ?: "—")
            OutlinedButton(onClick = { openWebViewStore(context) }, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.update_webview))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SimpleScaffold(stringResource(R.string.privacy_info), onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.privacy_heading), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

