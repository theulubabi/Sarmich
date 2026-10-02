package uz.usar.browser.ui.history

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.R
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.db.HistoryEntity
import uz.usar.browser.ui.browser.BrowserViewModel
import uz.usar.browser.ui.components.ConfirmDialog
import uz.usar.browser.ui.components.EmptyState
import uz.usar.browser.ui.components.SectionHeader
import uz.usar.browser.ui.components.SiteRow
import uz.usar.browser.ui.components.formatDate
import uz.usar.browser.ui.components.formatTime
import java.util.Calendar

enum class ClearRange(val label: Int) {
    LAST_HOUR(R.string.clear_last_hour),
    TODAY(R.string.clear_today),
    LAST_24_HOURS(R.string.clear_last_24h),
    LAST_7_DAYS(R.string.clear_last_7_days),
    ALL(R.string.clear_all_time),
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    val items: StateFlow<List<HistoryEntity>> = query
        .debounce(150)
        .flatMapLatest { c.repository.observeHistory(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch { c.repository.deleteHistory(ids.toList()) }
    }

    fun clear(range: ClearRange) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (range) {
                ClearRange.LAST_HOUR -> c.repository.deleteHistorySince(now - 3_600_000L)
                ClearRange.TODAY -> c.repository.deleteHistorySince(startOfToday())
                ClearRange.LAST_24_HOURS -> c.repository.deleteHistorySince(now - 86_400_000L)
                ClearRange.LAST_7_DAYS -> c.repository.deleteHistorySince(now - 7 * 86_400_000L)
                ClearRange.ALL -> c.repository.clearHistory()
            }
        }
    }

    companion object {
        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun factory(c: AppContainer) = viewModelFactory { initializer { HistoryViewModel(c) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, browser: BrowserViewModel, onBack: () -> Unit, onOpened: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var clearMenu by remember { mutableStateOf(false) }
    var confirmRange by remember { mutableStateOf<ClearRange?>(null) }
    var confirmSelected by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { selected = emptySet() }

    val today = HistoryViewModel.startOfToday()
    val yesterday = today - 86_400_000L
    val grouped = remember(items) {
        items.groupBy { h ->
            when {
                h.visitedAt >= today -> context.getString(R.string.today)
                h.visitedAt >= yesterday -> context.getString(R.string.yesterday)
                else -> formatDate(context, h.visitedAt)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        selecting -> Text(stringResource(R.string.selected_count, selected.size))
                        searching -> TextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            placeholder = { Text(stringResource(R.string.search_history)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> Text(stringResource(R.string.history))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selecting) selected = emptySet() else onBack() }) {
                        Icon(
                            if (selecting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(if (selecting) R.string.cancel else R.string.back),
                        )
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { confirmSelected = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete_selected))
                        }
                    } else {
                        IconButton(onClick = {
                            if (searching) vm.query.value = ""
                            searching = !searching
                        }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, stringResource(R.string.search)) }
                        Box {
                            IconButton(onClick = { clearMenu = true }) {
                                Icon(Icons.Filled.DeleteSweep, stringResource(R.string.clear_history))
                            }
                            DropdownMenu(expanded = clearMenu, onDismissRequest = { clearMenu = false }) {
                                ClearRange.entries.forEach { range ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(range.label)) },
                                        onClick = {
                                            clearMenu = false
                                            confirmRange = range
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.History, if (query.isBlank()) R.string.history_empty else R.string.no_results)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (day, entries) ->
                        item(key = "header-$day") { SectionHeader(day) }
                        items(entries, key = { it.id }) { h ->
                            val isSelected = h.id in selected
                            SiteRow(
                                host = h.host,
                                title = h.title.ifBlank { UrlUtils.displayUrl(h.url) },
                                subtitle = "${formatTime(context, h.visitedAt)}  ${UrlUtils.displayUrl(h.url)}",
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (selecting) {
                                            selected = if (isSelected) selected - h.id else selected + h.id
                                        } else {
                                            browser.openUrl(h.url)
                                            onOpened()
                                        }
                                    },
                                    onLongClick = { selected = if (isSelected) selected - h.id else selected + h.id },
                                ),
                                trailing = {
                                    if (selecting) {
                                        Checkbox(checked = isSelected, onCheckedChange = {
                                            selected = if (it) selected + h.id else selected - h.id
                                        })
                                    } else {
                                        IconButton(onClick = { vm.delete(listOf(h.id)) }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmRange?.let { range ->
        ConfirmDialog(
            title = stringResource(R.string.clear_history),
            message = stringResource(R.string.clear_history_message, stringResource(range.label)),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = { vm.clear(range) },
            onDismiss = { confirmRange = null },
        )
    }
    if (confirmSelected) {
        ConfirmDialog(
            title = stringResource(R.string.delete_selected),
            message = stringResource(R.string.delete_selected_message, selected.size),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                vm.delete(selected)
                selected = emptySet()
            },
            onDismiss = { confirmSelected = false },
        )
    }
}

@Suppress("unused")
private fun isToday(millis: Long) = DateUtils.isToday(millis)
