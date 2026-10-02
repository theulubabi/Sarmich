package uz.usar.browser.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.R
import uz.usar.browser.data.db.DownloadEntity
import uz.usar.browser.data.db.DownloadStatus
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.EmptyState
import uz.usar.browser.ui.components.formatBytes
import uz.usar.browser.ui.components.formatDate

class DownloadsViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<DownloadEntity>> =
        c.downloads.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        c.downloads.startPolling()
    }

    fun open(item: DownloadEntity): Boolean = c.downloads.open(item)
    fun share(item: DownloadEntity): Boolean = c.downloads.share(item)

    fun delete(item: DownloadEntity) {
        viewModelScope.launch { c.downloads.delete(item) }
    }

    fun retry(item: DownloadEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(c.downloads.retry(item, c.settings.current.downloadWifiOnly)) }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { DownloadsViewModel(c) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: DownloadsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<DownloadEntity?>(null) }

    fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.Download, R.string.downloads_empty)
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(items, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onOpen = { if (!vm.open(item)) toast(R.string.open_failed) },
                            onShare = { if (!vm.share(item)) toast(R.string.open_failed) },
                            onRetry = { vm.retry(item) { ok -> toast(if (ok) R.string.download_restarted else R.string.download_failed) } },
                            onDelete = { deleting = item },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_download_title),
            message = stringResource(R.string.delete_download_message, item.fileName),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.delete(item) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DownloadRow(
    item: DownloadEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val completed = item.status == DownloadStatus.COMPLETED
    val status = when (item.status) {
        DownloadStatus.COMPLETED -> stringResource(R.string.status_completed, formatBytes(context, item.totalBytes))
        DownloadStatus.FAILED -> stringResource(R.string.status_failed)
        DownloadStatus.PAUSED -> stringResource(R.string.status_paused)
        else -> if (item.totalBytes > 0) {
            stringResource(R.string.status_progress, formatBytes(context, item.downloadedBytes), formatBytes(context, item.totalBytes))
        } else {
            stringResource(R.string.status_running)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = completed, onClick = onOpen)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(item.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$status  ${formatDate(context, item.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PAUSED) {
                if (item.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (completed) {
                    MenuRow(R.string.open, Icons.Filled.OpenInBrowser) { menu = false; onOpen() }
                    MenuRow(R.string.share, Icons.Filled.Share) { menu = false; onShare() }
                }
                if (item.status == DownloadStatus.FAILED && item.systemId >= 0) {
                    MenuRow(R.string.retry, Icons.Filled.Refresh) { menu = false; onRetry() }
                }
                MenuRow(R.string.delete, Icons.Filled.Delete) { menu = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuRow(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

private fun iconFor(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Filled.Image
    mime.startsWith("video/") -> Icons.Filled.Movie
    mime.startsWith("audio/") -> Icons.Filled.MusicNote
    mime == "application/pdf" -> Icons.Filled.PictureAsPdf
    mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") -> Icons.Filled.Archive
    else -> Icons.Filled.Description
}
