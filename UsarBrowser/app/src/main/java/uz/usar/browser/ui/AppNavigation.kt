package uz.usar.browser.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.data.settings.AccountMode
import uz.usar.browser.data.settings.SettingsRepository
import uz.usar.browser.ui.account.SignInScreen
import uz.usar.browser.ui.browser.BrowserScreen
import uz.usar.browser.ui.browser.BrowserViewModel
import uz.usar.browser.ui.components.LocalFaviconStore
import uz.usar.browser.ui.downloads.DownloadsScreen
import uz.usar.browser.ui.downloads.DownloadsViewModel
import uz.usar.browser.ui.history.HistoryScreen
import uz.usar.browser.ui.history.HistoryViewModel
import uz.usar.browser.ui.library.LibraryScreen
import uz.usar.browser.ui.library.LibraryViewModel
import uz.usar.browser.ui.onboarding.OnboardingScreen
import uz.usar.browser.ui.settings.AboutScreen
import uz.usar.browser.ui.settings.ClearDataScreen
import uz.usar.browser.ui.settings.SettingsScreen
import uz.usar.browser.ui.settings.SettingsViewModel
import uz.usar.browser.ui.settings.SitePermissionsScreen
import uz.usar.browser.ui.settings.SiteSettingsScreen
import uz.usar.browser.ui.settings.WebViewInfoScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "sign_in"
    const val BROWSER = "browser"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val SITE_PERMISSIONS = "site_permissions"
    const val SITE = "site/{host}"
    const val CLEAR_DATA = "clear_data"
    const val WEBVIEW_INFO = "webview_info"
    const val ABOUT = "about"

    fun site(host: String) = "site/${Uri.encode(host)}"
}

@Composable
fun AppNavigation(container: AppContainer, browser: BrowserViewModel, onboardingDone: Boolean, needsAccount: Boolean) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val start = remember {
        when {
            !onboardingDone -> Routes.ONBOARDING
            needsAccount -> Routes.SIGN_IN
            else -> Routes.BROWSER
        }
    }
    val navigate: (String) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStackSafely() }
    val backToBrowser: () -> Unit = { nav.popBackStack(Routes.BROWSER, inclusive = false) }

    CompositionLocalProvider(LocalFaviconStore provides container.favicons) {
        NavHost(navController = nav, startDestination = start) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinish = {
                    scope.launch { container.settings.set(SettingsRepository.Keys.ONBOARDING_DONE, true) }
                    val next = if (container.settings.current.accountMode == AccountMode.NONE) Routes.SIGN_IN else Routes.BROWSER
                    nav.navigate(next) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.SIGN_IN) {
                SignInScreen(container = container, onDone = {
                    if (nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.BROWSER) { popUpTo(Routes.SIGN_IN) { inclusive = true } }
                    }
                })
            }
            composable(Routes.BROWSER) {
                BrowserScreen(vm = browser, navigate = navigate)
            }
            composable(Routes.LIBRARY) {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
                LibraryScreen(vm = vm, browser = browser, onBack = back, onOpened = backToBrowser, navigate = navigate)
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
                HistoryScreen(vm = vm, browser = browser, onBack = back, onOpened = backToBrowser)
            }
            composable(Routes.DOWNLOADS) {
                val vm: DownloadsViewModel = viewModel(factory = DownloadsViewModel.factory(container))
                DownloadsScreen(vm = vm, onBack = back)
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                SettingsScreen(
                    vm = vm,
                    onBack = back,
                    navigate = navigate,
                    onSignedOut = {
                        nav.navigate(Routes.SIGN_IN) { popUpTo(nav.graph.id) { inclusive = true } }
                    },
                )
            }
            composable(Routes.SITE_PERMISSIONS) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                SitePermissionsScreen(vm = vm, onBack = back, navigate = navigate)
            }
            composable(Routes.SITE, arguments = listOf(navArgument("host") { type = NavType.StringType })) { entry ->
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                val host = entry.arguments?.getString("host").orEmpty()
                SiteSettingsScreen(host = host, vm = vm, onBack = back)
            }
            composable(Routes.CLEAR_DATA) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                ClearDataScreen(vm = vm, onBack = back)
            }
            composable(Routes.WEBVIEW_INFO) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                WebViewInfoScreen(vm = vm, onBack = back)
            }
            composable(Routes.ABOUT) { AboutScreen(onBack = back) }
        }
    }
}

/** Ignores double taps on "back" that would pop the last destination and leave a blank screen. */
private fun NavHostController.popBackStackSafely() {
    if (previousBackStackEntry != null) popBackStack()
}
