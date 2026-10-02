package uz.usar.browser.ui.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import uz.usar.browser.R
import uz.usar.browser.browser.BrowserWebView
import uz.usar.browser.browser.PageError
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.browser.WebViewProblem
import uz.usar.browser.data.settings.BrowserSettings
import uz.usar.browser.ui.theme.PrivateColors

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun BrowserScreen(vm: BrowserViewModel, navigate: (String) -> Unit) {
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val settings = settingsState ?: BrowserSettings()
    val online by vm.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val tab = vm.activeTab
    val incognito = tab?.incognito == true

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val text = context.getString(m.text, *m.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = m.actionLabel?.let { context.getString(it) },
                withDismissAction = m.actionLabel == null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    BackHandler {
        if (!vm.handleBack()) activity?.moveTaskToBack(true)
    }

    val immersive = vm.fullscreen || vm.customView != null
    val view = LocalView.current
    DisposableEffect(immersive, activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (immersive) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }

    val toolbarColor = if (incognito) PrivateColors.toolbar else MaterialTheme.colorScheme.surfaceContainer

    Box(Modifier.fillMaxSize().background(toolbarColor)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            AnimatedVisibility(visible = vm.toolbarVisible) {
                BrowserToolbar(vm = vm, tab = tab, navigate = navigate)
            }
            if (tab != null && tab.isLoading && !tab.showStartPage) {
                LinearProgressIndicator(
                    progress = { (tab.progress.coerceIn(5, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.Transparent,
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
            if (!online) OfflineBanner(onRetry = vm::reload)
            val problem = vm.webViewProblem
            if (problem is WebViewProblem.Outdated && !vm.outdatedDismissed) {
                OutdatedBanner(problem.version, onUpdate = { openWebViewStore(context) }, onDismiss = { vm.outdatedDismissed = true })
            }
            if (vm.findVisible && tab != null) FindInPageBar(vm, tab)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when {
                    problem == WebViewProblem.Missing -> WebViewMissing(onUpdate = { openWebViewStore(context) })
                    tab == null -> Unit
                    tab.showStartPage -> StartPage(vm = vm, tab = tab, navigate = navigate)
                    else -> {
                        val webView = tab.webView
                        if (webView != null) {
                            key(webView) {
                                WebContainer(webView, settings.pullToRefresh, onRefresh = vm::reload)
                            }
                        }
                        tab.error?.let { ErrorOverlay(it, tab.url, onRetry = vm::reload) }
                    }
                }

                if (vm.addressFocused && vm.suggestions.isNotEmpty()) {
                    SuggestionList(vm) { url ->
                        focusManager.clearFocus()
                        vm.openUrl(url)
                    }
                }

                if (vm.fullscreen) {
                    SmallFloatingActionButton(
                        onClick = { vm.toolbarVisible = !vm.toolbarVisible },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    ) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = stringResource(R.string.show_controls))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 8.dp),
        )

        if (vm.showTabSwitcher) {
            TabSwitcher(vm = vm, onDismiss = { vm.showTabSwitcher = false })
        }

        vm.customView?.let { videoView ->
            key(videoView) {
            AndroidView(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (videoView.parent as? ViewGroup)?.removeView(videoView)
                        addView(videoView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { it.removeAllViews() },
            )
            }
        }
    }

    BrowserDialogs(vm = vm, navigate = navigate)
}

@Composable
private fun WebContainer(webView: BrowserWebView, pullToRefresh: Boolean, onRefresh: () -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            SwipeRefreshLayout(ctx).apply {
                (webView.parent as? ViewGroup)?.removeView(webView)
                addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || !webView.overscrolledTop }
                setOnRefreshListener {
                    onRefresh()
                    isRefreshing = false
                }
            }
        },
        update = { it.isEnabled = pullToRefresh },
        onRelease = { it.removeView(webView) },
    )
}

@Composable
private fun SuggestionList(vm: BrowserViewModel, onPick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
    ) {
        LazyColumn {
            items(vm.suggestions, key = { it.url }) { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s.url) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (s.isSaved) Icons.Filled.Star else Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title.ifBlank { UrlUtils.displayUrl(s.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            UrlUtils.displayUrl(s.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun OutdatedBanner(version: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Text(
                stringResource(R.string.webview_outdated, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                TextButton(onClick = onUpdate) { Text(stringResource(R.string.update)) }
            }
        }
    }
}

@Composable
private fun WebViewMissing(onUpdate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.webview_missing_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.webview_missing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUpdate) { Text(stringResource(R.string.update_webview)) }
    }
}

@Composable
private fun ErrorOverlay(error: PageError, url: String, onRetry: () -> Unit) {
    val (title, body) = when (error) {
        is PageError.Ssl -> stringResource(R.string.error_ssl_title) to stringResource(R.string.error_ssl_body)
        PageError.Crashed -> stringResource(R.string.error_crashed_title) to stringResource(R.string.error_crashed_body)
        is PageError.Network -> stringResource(R.string.error_network_title) to
            stringResource(R.string.error_network_body, UrlUtils.host(url).ifBlank { url })
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (error is PageError.Network) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (error is PageError.Network) {
                Spacer(Modifier.height(4.dp))
                Text(error.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (error !is PageError.Ssl) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

fun openWebViewStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
}
