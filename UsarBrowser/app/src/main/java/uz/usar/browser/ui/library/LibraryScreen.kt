package uz.usar.browser.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.R
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.db.SiteEntity
import uz.usar.browser.data.db.displayName
import uz.usar.browser.data.settings.SettingsRepository
import uz.usar.browser.data.settings.SortMode
import uz.usar.browser.ui.Routes
import uz.usar.browser.ui.browser.BrowserViewModel
import uz.usar.browser.ui.components.EmptyState
import uz.usar.browser.ui.components.SiteRow

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    val sort: StateFlow<SortMode> = c.settings.state.map { it?.sitesSort ?: SortMode.RECENT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.RECENT)

    val favorites: StateFlow<List<SiteEntity>> = combine(c.repository.observeFavorites(), query) { list, q ->
        list.filter { it.matches(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val saved: StateFlow<List<SiteEntity>> = combine(c.repository.observeAllSites(), query, sort) { list, q, s ->
        val filtered = list.filter { it.matches(q) }
        when (s) {
            SortMode.RECENT -> filtered.sortedByDescending { maxOf(it.lastVisited, it.dateAdded) }
            SortMode.MOST_VISITED -> filtered.sortedWith(compareByDescending<SiteEntity> { it.visitCount }.thenByDescending { it.lastVisited })
            SortMode.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun SiteEntity.matches(q: String): Boolean {
        if (q.isBlank()) return true
        val t = q.trim()
        return displayName.contains(t, true) || url.contains(t, true) || title.contains(t, true)
    }

    fun setSort(mode: SortMode) {
        viewModelScope.launch { c.settings.set(SettingsRepository.Keys.SITES_SORT, mode.name) }
    }

    fun move(list: List<SiteEntity>, index: Int, up: Boolean) {
        viewModelScope.launch { c.repository.move(list, index, up) }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { LibraryViewModel(c) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    browser: BrowserViewModel,
    onBack: () -> Unit,
    onOpened: () -> Unit,
    navigate: (String) -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SiteEntity?>(null) }
    var deleting by remember { mutableStateOf<SiteEntity?>(null) }
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val list = if (tabIndex == 0) favorites else saved

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { vm.query.value = it },
                                placeholder = { Text(stringResource(R.string.search_saved)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(stringResource(R.string.saved_sites))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searching) vm.query.value = ""
                            searching = !searching
                        }) {
                            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, stringResource(R.string.search))
                        }
                        if (tabIndex == 1) {
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.SwapVert, stringResource(R.string.sort_by)) }
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    SortMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(sortLabel(mode))) },
                                            leadingIcon = { RadioButton(selected = mode == sort, onClick = null) },
                                            onClick = {
                                                vm.setSort(mode)
                                                sortMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text(stringResource(R.string.favorites)) })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(stringResource(R.string.saved_websites)) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (list.isEmpty()) {
                EmptyState(
                    if (tabIndex == 0) Icons.Filled.StarBorder else Icons.Filled.Public,
                    when {
                        query.isNotBlank() -> R.string.no_results
                        tabIndex == 0 -> R.string.empty_favorites
                        else -> R.string.empty_saved
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(list, key = { _, s -> s.id }) { index, site ->
                        var menu by remember { mutableStateOf(false) }
                        val reorderable = tabIndex == 0 && query.isBlank()
                        val actions = SiteActions(
                            onOpen = { browser.openUrl(site.url); onOpened() },
                            onOpenInNewTab = { browser.openUrl(site.url, inNewTab = true); onOpened() },
                            onTogglePin = { browser.togglePinned(site) },
                            onToggleFavorite = { browser.toggleFavorite(site) },
                            onEdit = { editing = site },
                            onShare = { browser.share(site.url, site.displayName) },
                            onCopy = { browser.copy(site.url) },
                            onDelete = { deleting = site },
                            onSiteSettings = { navigate(Routes.site(site.host)) },
                            onMoveUp = if (reorderable && index > 0) ({ vm.move(list, index, true) }) else null,
                            onMoveDown = if (reorderable && index < list.lastIndex) ({ vm.move(list, index, false) }) else null,
                        )
                        Box {
                            SiteRow(
                                host = site.host,
                                title = site.displayName,
                                subtitle = UrlUtils.displayUrl(site.url),
                                modifier = Modifier.combinedClickable(onClick = actions.onOpen, onLongClick = { menu = true }),
                                trailing = {
                                    if (site.isPinned) {
                                        Icon(Icons.Filled.PushPin, stringResource(R.string.pinned), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { browser.toggleFavorite(site) }) {
                                        Icon(
                                            if (site.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            stringResource(if (site.isFavorite) R.string.remove_favorite else R.string.add_favorite),
                                            tint = if (site.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                },
                            )
                            SiteActionsMenu(expanded = menu, onDismiss = { menu = false }, site = site, actions = actions)
                        }
                    }
                }
            }
        }
    }

    editing?.let { site ->
        EditSiteDialog(site, onSave = { name, url -> browser.editSite(site, name, url) }, onDismiss = { editing = null })
    }
    deleting?.let { site ->
        DeleteSiteDialog(site, onConfirm = { browser.deleteSite(site) }, onDismiss = { deleting = null })
    }
}

fun sortLabel(mode: SortMode): Int = when (mode) {
    SortMode.RECENT -> R.string.sort_recent
    SortMode.MOST_VISITED -> R.string.sort_most_visited
    SortMode.NAME -> R.string.sort_name
    SortMode.DATE_ADDED -> R.string.sort_date_added
}
