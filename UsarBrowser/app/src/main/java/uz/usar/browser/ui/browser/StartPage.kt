package uz.usar.browser.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.usar.browser.R
import uz.usar.browser.browser.BrowserTab
import uz.usar.browser.browser.PrivateProfile
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.db.SiteEntity
import uz.usar.browser.data.db.displayName
import uz.usar.browser.ui.Routes
import uz.usar.browser.ui.components.FaviconImage
import uz.usar.browser.ui.components.SiteRow
import uz.usar.browser.ui.library.DeleteSiteDialog
import uz.usar.browser.ui.library.EditSiteDialog
import uz.usar.browser.ui.library.SiteActions
import uz.usar.browser.ui.library.SiteActionsMenu

@Composable
fun StartPage(vm: BrowserViewModel, tab: BrowserTab, navigate: (String) -> Unit) {
    if (tab.incognito) {
        PrivateStartPage(vm)
        return
    }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val mostVisited by vm.mostVisited.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SiteEntity?>(null) }
    var deleting by remember { mutableStateOf<SiteEntity?>(null) }
    val qrPrompt = stringResource(R.string.qr_prompt)

    fun actionsFor(site: SiteEntity) = SiteActions(
        onOpen = { vm.openUrl(site.url) },
        onOpenInNewTab = { vm.openUrl(site.url, inNewTab = true) },
        onTogglePin = { vm.togglePinned(site) },
        onToggleFavorite = { vm.toggleFavorite(site) },
        onEdit = { editing = site },
        onShare = { vm.share(site.url, site.displayName) },
        onCopy = { vm.copy(site.url) },
        onDelete = { deleting = site },
        onSiteSettings = { navigate(Routes.site(site.host)) },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.start_greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                StartSearchField(onSubmit = vm::loadInput)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Shortcut(Icons.Filled.Bookmarks, R.string.saved_sites) { navigate(Routes.LIBRARY) }
                    Shortcut(Icons.Filled.History, R.string.history) { navigate(Routes.HISTORY) }
                    Shortcut(Icons.Filled.Download, R.string.downloads) { navigate(Routes.DOWNLOADS) }
                    Shortcut(Icons.Filled.QrCodeScanner, R.string.scan_qr) { vm.scanQr(qrPrompt) }
                }
            }
        }
        if (pinned.isNotEmpty()) {
            item { TileSection(R.string.section_pinned, pinned, ::actionsFor) }
        }
        if (favorites.isNotEmpty()) {
            item { TileSection(R.string.section_favorites, favorites, ::actionsFor) }
        }
        if (mostVisited.isNotEmpty()) {
            item { TileSection(R.string.section_most_visited, mostVisited, ::actionsFor) }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle(R.string.section_recent) }
            items(recent, key = { "recent-${it.id}" }) { h ->
                SiteRow(
                    host = h.host,
                    title = h.title.ifBlank { UrlUtils.displayUrl(h.url) },
                    subtitle = UrlUtils.displayUrl(h.url),
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.openUrl(h.url) },
                )
            }
        }
        if (pinned.isEmpty() && favorites.isEmpty() && mostVisited.isEmpty() && recent.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.start_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
    }

    editing?.let { site ->
        EditSiteDialog(site, onSave = { name, url -> vm.editSite(site, name, url) }, onDismiss = { editing = null })
    }
    deleting?.let { site ->
        DeleteSiteDialog(site, onConfirm = { vm.deleteSite(site) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun StartSearchField(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.address_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = {
            if (text.isNotBlank()) {
                onSubmit(text)
                text = ""
                focusManager.clearFocus()
            }
        }),
    )
}

@Composable
private fun Shortcut(icon: ImageVector, label: Int, onClick: () -> Unit) {
    val text = stringResource(label)
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = text)
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileSection(label: Int, sites: List<SiteEntity>, actionsFor: (SiteEntity) -> SiteActions) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        SectionTitle(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sites.forEach { site -> SiteTile(site, actionsFor(site)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(site: SiteEntity, actions: SiteActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .width(78.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = actions.onOpen, onLongClick = { menu = true })
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FaviconImage(host = site.host, size = 44.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                site.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        SiteActionsMenu(expanded = menu, onDismiss = { menu = false }, site = site, actions = actions)
    }
}

@Composable
private fun PrivateStartPage(vm: BrowserViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.private_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.private_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        if (!PrivateProfile.isSupported) {
            Spacer(Modifier.height(20.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 520.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.private_not_isolated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        StartSearchField(onSubmit = vm::loadInput)
    }
}
