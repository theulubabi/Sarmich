package uz.usar.browser.ui.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.flowOf
import uz.usar.browser.R
import uz.usar.browser.browser.BrowserTab
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.ui.Routes
import uz.usar.browser.ui.theme.PrivateColors

@Composable
fun BrowserToolbar(vm: BrowserViewModel, tab: BrowserTab?, navigate: (String) -> Unit) {
    val incognito = tab?.incognito == true
    val contentColor = if (incognito) PrivateColors.content else MaterialTheme.colorScheme.onSurface
    val fieldColor = if (incognito) PrivateColors.field else MaterialTheme.colorScheme.surfaceContainerHighest
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val pageUrl = if (tab == null || tab.showStartPage) "" else tab.url
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val hint = stringResource(if (incognito) R.string.address_hint_private else R.string.address_hint)

    LaunchedEffect(pageUrl, focused) {
        if (!focused) text = TextFieldValue(UrlUtils.displayUrl(pageUrl))
    }
    LaunchedEffect(vm.focusAddressBar) {
        if (vm.focusAddressBar) {
            vm.focusAddressBar = false
            runCatching { focusRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (focused) {
            IconButton(onClick = { focusManager.clearFocus() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel), tint = contentColor)
            }
        } else if (incognito) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = stringResource(R.string.private_tab),
                tint = contentColor,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        } else {
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = fieldColor,
            modifier = Modifier.weight(1f).height(46.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                if (!focused && pageUrl.isNotEmpty()) {
                    val secure = UrlUtils.isHttps(pageUrl)
                    IconButton(onClick = { vm.showSiteInfo() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (secure) Icons.Filled.Lock else Icons.Filled.Info,
                            contentDescription = stringResource(R.string.site_info),
                            tint = if (secure) contentColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp).size(20.dp),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        vm.querySuggestions(it.text)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        vm.loadInput(text.text)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused && !focused) {
                                text = TextFieldValue(pageUrl, TextRange(0, pageUrl.length))
                            }
                            focused = state.isFocused
                            vm.addressFocused = state.isFocused
                            if (!state.isFocused) vm.clearSuggestions()
                        }
                        .semantics { contentDescription = hint },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.text.isEmpty()) {
                                Text(hint, color = contentColor.copy(alpha = 0.6f), maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                            }
                            inner()
                        }
                    },
                )
                when {
                    focused && text.text.isNotEmpty() -> IconButton(
                        onClick = { text = TextFieldValue(""); vm.clearSuggestions() },
                        modifier = Modifier.size(40.dp),
                    ) { Icon(Icons.Filled.Close, stringResource(R.string.clear), tint = contentColor) }

                    !focused && tab != null && !tab.showStartPage -> IconButton(
                        onClick = { if (tab.isLoading) vm.stopLoading() else vm.reload() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = stringResource(if (tab.isLoading) R.string.stop else R.string.reload),
                            tint = contentColor,
                        )
                    }
                }
            }
        }

        if (!focused) {
            val count = vm.tabs.count { it.incognito == incognito }
            val tabsLabel = stringResource(R.string.open_tabs_count, count)
            IconButton(onClick = { vm.showTabSwitcher = true }, modifier = Modifier.semantics { contentDescription = tabsLabel }) {
                Box(
                    modifier = Modifier.size(22.dp).border(1.8.dp, contentColor, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 99) "∞" else count.toString(),
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = { navigate(Routes.LIBRARY) }) {
                Icon(Icons.Filled.Bookmarks, stringResource(R.string.saved_sites), tint = contentColor)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = contentColor)
                }
                BrowserMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, vm = vm, tab = tab, navigate = navigate)
            }
        }
    }
}

@Composable
private fun BrowserMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    vm: BrowserViewModel,
    tab: BrowserTab?,
    navigate: (String) -> Unit,
) {
    val onPage = tab != null && !tab.showStartPage && UrlUtils.isWebUrl(tab.url)
    val favFlow = remember(tab?.url, onPage) { if (onPage && tab != null) vm.observeIsFavorite(tab.url) else flowOf(false) }
    val isFavorite by favFlow.collectAsState(initial = false)
    val qrPrompt = stringResource(R.string.qr_prompt)

    fun act(action: () -> Unit) {
        onDismiss()
        action()
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            IconButton(enabled = tab != null && (tab.canGoBack || tab.showStartPage && tab.webView != null), onClick = { act(vm::goBack) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            IconButton(enabled = tab != null && tab.canGoForward && !tab.showStartPage, onClick = { act(vm::goForward) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward))
            }
            IconButton(enabled = onPage, onClick = { act { vm.toggleFavorite() } }) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(enabled = onPage, onClick = { act(vm::reload) }) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.reload))
            }
            IconButton(enabled = onPage, onClick = { act(vm::showSiteInfo) }) {
                Icon(Icons.Filled.Info, stringResource(R.string.site_info))
            }
        }
        HorizontalDivider()
        MenuItem(R.string.new_tab, Icons.Filled.Add) { act { vm.newTabWithFocus(false) } }
        MenuItem(R.string.new_private_tab, Icons.Filled.VisibilityOff) { act { vm.newTabWithFocus(true) } }
        MenuItem(R.string.home, Icons.Filled.Home) { act(vm::goHome) }
        MenuItem(R.string.history, Icons.Filled.History) { act { navigate(Routes.HISTORY) } }
        MenuItem(R.string.downloads, Icons.Filled.Download) { act { navigate(Routes.DOWNLOADS) } }
        MenuItem(R.string.scan_qr, Icons.Filled.QrCodeScanner) { act { vm.scanQr(qrPrompt) } }
        if (onPage && tab != null) {
            HorizontalDivider()
            MenuItem(R.string.share, Icons.Filled.Share) { act(vm::shareCurrent) }
            MenuItem(R.string.copy_url, Icons.Filled.ContentCopy) { act(vm::copyUrl) }
            MenuItem(R.string.copy_title_url, Icons.Filled.ContentCopy) { act(vm::copyTitleAndUrl) }
            MenuItem(R.string.find_in_page, Icons.Filled.FindInPage) { act(vm::openFind) }
            MenuItem(R.string.pin_to_start, Icons.Filled.PushPin) { act(vm::pinCurrent) }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.desktop_site)) },
                leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
                trailingIcon = { Checkbox(checked = tab.desktopMode, onCheckedChange = null) },
                onClick = { act(vm::toggleDesktopMode) },
            )
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::zoomOut) { Icon(Icons.Filled.ZoomOut, stringResource(R.string.zoom_out)) }
                Text(
                    stringResource(R.string.zoom),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = vm::resetZoom) { Icon(Icons.Filled.Refresh, stringResource(R.string.zoom_reset)) }
                IconButton(onClick = vm::zoomIn) { Icon(Icons.Filled.ZoomIn, stringResource(R.string.zoom_in)) }
            }
            MenuItem(
                if (vm.fullscreen) R.string.exit_fullscreen else R.string.fullscreen,
                if (vm.fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            ) { act { vm.setFullscreen(!vm.fullscreen) } }
            MenuItem(R.string.open_in_other_app, Icons.Filled.OpenInBrowser) { act(vm::openExternally) }
        }
        HorizontalDivider()
        MenuItem(R.string.settings, Icons.Filled.Settings) { act { navigate(Routes.SETTINGS) } }
    }
}

@Composable
private fun MenuItem(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
