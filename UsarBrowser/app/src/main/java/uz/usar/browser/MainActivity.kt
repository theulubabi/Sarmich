package uz.usar.browser

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.usar.browser.browser.ActivityBridge
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.settings.AccountMode
import uz.usar.browser.ui.AppNavigation
import uz.usar.browser.ui.browser.BrowserViewModel
import uz.usar.browser.ui.theme.UsarTheme

class MainActivity : AppCompatActivity() {

    private val container: AppContainer get() = (application as UsarApp).container
    private val bridge = ActivityBridge(this)
    private val browser: BrowserViewModel by viewModels { BrowserViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        browser.attach(this, bridge)

        val launchIntent = if (savedInstanceState == null) intent else null
        browser.initialize(launchIntent?.let(::urlFrom))
        launchIntent?.let(::searchFrom)?.let(browser::searchFromIntent)

        setContent {
            UsarTheme {
                val settings by container.settings.state.collectAsStateWithLifecycle()
                val loaded = settings
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    AppNavigation(
                        container = container,
                        browser = browser,
                        onboardingDone = loaded.onboardingDone,
                        needsAccount = loaded.accountMode == AccountMode.NONE,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        urlFrom(intent)?.let(browser::openFromIntent)
        searchFrom(intent)?.let(browser::searchFromIntent)
    }

    override fun onPause() {
        browser.onActivityPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        browser.onActivityResume()
    }

    override fun onDestroy() {
        browser.detach(this)
        super.onDestroy()
    }

    private fun urlFrom(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        return intent.dataString?.takeIf { UrlUtils.isWebUrl(it) }
    }

    private fun searchFrom(intent: Intent): String? {
        if (intent.action != Intent.ACTION_WEB_SEARCH) return null
        return intent.getStringExtra(SearchManager.QUERY)?.takeIf { it.isNotBlank() }
    }
}
