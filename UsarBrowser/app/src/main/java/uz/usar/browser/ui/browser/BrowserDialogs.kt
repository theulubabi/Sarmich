package uz.usar.browser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import uz.usar.browser.R
import uz.usar.browser.browser.BrowserTab
import uz.usar.browser.data.settings.PermType
import uz.usar.browser.data.settings.PermValue
import uz.usar.browser.ui.Routes
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.formatBytes

@Composable
fun BrowserDialogs(vm: BrowserViewModel, navigate: (String) -> Unit) {
    when (val d = vm.dialog) {
        is BrowserDialog.Permission -> PermissionDialog(d)
        is BrowserDialog.ExternalApp -> AlertDialog(
            onDismissRequest = d.onCancel,
            icon = { Icon(Icons.Filled.OpenInBrowser, null) },
            title = { Text(stringResource(R.string.external_title)) },
            text = { Text(stringResource(R.string.external_message, d.label ?: stringResource(R.string.external_app_generic))) },
            confirmButton = { TextButton(onClick = d.onOpen) { Text(stringResource(R.string.open)) } },
            dismissButton = { TextButton(onClick = d.onCancel) { Text(stringResource(R.string.cancel)) } },
        )
        is BrowserDialog.DownloadConfirm -> {
            val context = LocalContext.current
            val size = if (d.size > 0) formatBytes(context, d.size) else stringResource(R.string.size_unknown)
            AlertDialog(
                onDismissRequest = { vm.dialog = null },
                icon = { Icon(Icons.Filled.Download, null) },
                title = { Text(stringResource(R.string.download_confirm_title)) },
                text = { Text(stringResource(R.string.download_confirm_message, d.fileName, size)) },
                confirmButton = { TextButton(onClick = d.onConfirm) { Text(stringResource(R.string.download)) } },
                dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        is BrowserDialog.Upload -> UploadSheet(d.request, onPick = vm::onUploadSource, onDismiss = vm::cancelUpload)
        is BrowserDialog.SiteInfo -> SiteInfoSheet(
            info = d.info,
            onDismiss = { vm.dialog = null },
            onClearData = vm::clearCurrentSiteData,
            onResetPermissions = { vm.resetSitePermissions(d.info.host) },
            onSiteSettings = {
                vm.dialog = null
                navigate(Routes.site(d.info.host))
            },
        )
        null -> Unit
    }
}

fun permLabel(type: PermType): Int = when (type) {
    PermType.CAMERA -> R.string.perm_camera
    PermType.MICROPHONE -> R.string.perm_microphone
    PermType.LOCATION -> R.string.perm_location
    PermType.POPUPS -> R.string.perm_popups
    PermType.DOWNLOADS -> R.string.perm_downloads
}

fun permValueLabel(value: Int): Int = when (value) {
    PermValue.ASK -> R.string.perm_ask
    PermValue.ALLOW -> R.string.perm_allow
    PermValue.BLOCK -> R.string.perm_block
    else -> R.string.perm_default
}

private fun permIcon(type: PermType): ImageVector = when (type) {
    PermType.CAMERA -> Icons.Filled.PhotoCamera
    PermType.MICROPHONE -> Icons.Filled.Mic
    PermType.LOCATION -> Icons.Filled.LocationOn
    PermType.POPUPS -> Icons.Filled.OpenInBrowser
    PermType.DOWNLOADS -> Icons.Filled.Download
}

@Composable
private fun PermissionDialog(d: BrowserDialog.Permission) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { d.onResult(false, false) },
        icon = { Icon(Icons.Filled.Security, null) },
        title = { Text(stringResource(R.string.permission_title, d.host.ifBlank { "?" })) },
        text = {
            Column {
                d.types.forEach { type ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(permIcon(type), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(permUseLabel(type)))
                    }
                }
                if (d.protectedMedia) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.perm_use_protected_media))
                    }
                }
                if (!d.incognito) {
                    Row(
                        Modifier.fillMaxWidth().clickable { rememberChoice = !rememberChoice }.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                        Text(stringResource(R.string.perm_remember))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { d.onResult(true, rememberChoice) }) { Text(stringResource(R.string.perm_allow)) } },
        dismissButton = { TextButton(onClick = { d.onResult(false, rememberChoice) }) { Text(stringResource(R.string.perm_block)) } },
    )
}

private fun permUseLabel(type: PermType): Int = when (type) {
    PermType.CAMERA -> R.string.perm_use_camera
    PermType.MICROPHONE -> R.string.perm_use_microphone
    PermType.LOCATION -> R.string.perm_use_location
    PermType.POPUPS -> R.string.perm_popups
    PermType.DOWNLOADS -> R.string.perm_downloads
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(request: UploadRequest, onPick: (UploadSource) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.upload_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (request.showCamera) SheetRow(Icons.Filled.PhotoCamera, R.string.upload_camera) { onPick(UploadSource.CAMERA) }
            if (request.showVideo) SheetRow(Icons.Filled.Videocam, R.string.upload_video) { onPick(UploadSource.VIDEO) }
            if (request.showGallery) SheetRow(Icons.Filled.PhotoLibrary, R.string.upload_gallery) { onPick(UploadSource.GALLERY) }
            if (request.showDocuments) SheetRow(Icons.Filled.PictureAsPdf, R.string.upload_documents) { onPick(UploadSource.DOCUMENTS) }
            SheetRow(Icons.Filled.AttachFile, if (request.multiple) R.string.upload_files_multiple else R.string.upload_files) {
                onPick(UploadSource.FILES)
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteInfoSheet(
    info: SiteInfoData,
    onDismiss: () -> Unit,
    onClearData: () -> Unit,
    onResetPermissions: () -> Unit,
    onSiteSettings: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(info.host, style = MaterialTheme.typography.titleLarge)
            Text(info.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (info.https) Icons.Filled.Lock else Icons.Filled.Info,
                    null,
                    tint = if (info.https) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(if (info.https) R.string.conn_https else R.string.conn_http),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(if (info.https) R.string.conn_https_note else R.string.conn_http_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.cookies_count, info.cookieCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val overrides = info.hostSettings?.let { hs -> PermType.entries.filter { hs.get(it) != PermValue.DEFAULT }.map { it to hs.get(it) } }.orEmpty()
            if (overrides.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                overrides.forEach { (type, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(stringResource(permLabel(type)), modifier = Modifier.weight(1f))
                        Text(stringResource(permValueLabel(value)), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_site_data)) }
                if (!info.incognito) OutlinedButton(onClick = onSiteSettings) { Text(stringResource(R.string.site_settings)) }
            }
            if (overrides.isNotEmpty() && !info.incognito) {
                TextButton(onClick = onResetPermissions) { Text(stringResource(R.string.reset_permissions)) }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_site_data),
            message = stringResource(R.string.clear_site_data_message, info.host),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = onClearData,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun FindInPageBar(vm: BrowserViewModel, tab: BrowserTab) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = vm.findQuery,
                    onValueChange = vm::find,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.findNext(true) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (vm.findQuery.isEmpty()) {
                            Text(stringResource(R.string.find_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                )
            }
            if (vm.findQuery.isNotEmpty()) {
                Text(
                    stringResource(R.string.find_count, if (tab.findCount > 0) tab.findActive + 1 else 0, tab.findCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.findNext(false) }, enabled = tab.findCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous))
            }
            IconButton(onClick = { vm.findNext(true) }, enabled = tab.findCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next))
            }
            IconButton(onClick = vm::closeFind) {
                Icon(Icons.Filled.Close, stringResource(R.string.close), modifier = Modifier.size(20.dp))
            }
        }
    }
}
