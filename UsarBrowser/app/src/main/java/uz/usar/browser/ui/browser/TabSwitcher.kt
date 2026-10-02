package uz.usar.browser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.usar.browser.R
import uz.usar.browser.browser.BrowserTab
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.EmptyState
import uz.usar.browser.ui.components.FaviconImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcher(vm: BrowserViewModel, onDismiss: () -> Unit) {
    var privateMode by remember { mutableStateOf(vm.activeTab?.incognito == true) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val list = vm.tabs.filter { it.incognito == privateMode }
    val normalCount = vm.tabs.count { !it.incognito }
    val privateCount = vm.tabs.size - normalCount

    BackHandler(onBack = onDismiss)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopAppBar(
                title = { Text(stringResource(R.string.tabs)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                },
                actions = {
                    TextButton(onClick = { confirmCloseAll = true }, enabled = list.isNotEmpty()) {
                        Text(stringResource(R.string.close_all_tabs))
                    }
                },
            )
            TabRow(selectedTabIndex = if (privateMode) 1 else 0) {
                Tab(
                    selected = !privateMode,
                    onClick = { privateMode = false },
                    text = { Text(stringResource(R.string.tabs_normal, normalCount)) },
                    icon = { Icon(Icons.Filled.Tab, null) },
                )
                Tab(
                    selected = privateMode,
                    onClick = { privateMode = true },
                    text = { Text(stringResource(R.string.tabs_private, privateCount)) },
                    icon = { Icon(Icons.Filled.VisibilityOff, null) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (list.isEmpty()) {
                    EmptyState(Icons.Filled.Tab, if (privateMode) R.string.no_private_tabs else R.string.no_tabs)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(170.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.id }) { tab ->
                            TabCard(
                                tab = tab,
                                active = tab.id == vm.activeTabId,
                                onClick = { vm.selectTab(tab) },
                                onClose = { vm.closeTab(tab) },
                            )
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { vm.newTabWithFocus(privateMode) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(if (privateMode) R.string.new_private_tab else R.string.new_tab)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    if (confirmCloseAll) {
        ConfirmDialog(
            title = stringResource(R.string.close_all_tabs),
            message = stringResource(if (privateMode) R.string.close_all_private_message else R.string.close_all_message),
            confirmLabel = stringResource(R.string.close_all_tabs),
            onConfirm = { vm.closeAllTabs(privateMode) },
            onDismiss = { confirmCloseAll = false },
        )
    }
}

@Composable
private fun TabCard(tab: BrowserTab, active: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val startLabel = stringResource(if (tab.incognito) R.string.private_tab else R.string.start_page)
    val title = if (tab.showStartPage) startLabel else tab.displayTitle
    Card(
        onClick = onClick,
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FaviconImage(host = UrlUtils.host(tab.url), size = 20.dp, bitmap = tab.favicon)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.close_tab), modifier = Modifier.size(18.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
                .padding(10.dp),
        ) {
            Text(
                if (tab.showStartPage) "" else UrlUtils.displayUrl(tab.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
