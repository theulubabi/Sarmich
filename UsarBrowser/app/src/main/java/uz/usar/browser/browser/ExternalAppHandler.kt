package uz.usar.browser.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URISyntaxException
import java.util.Locale

sealed interface NavDecision {
    data object AllowInWebView : NavDecision
    data object Block : NavDecision
    data class LoadUrl(val url: String) : NavDecision
    data class External(val intent: Intent, val label: String?, val fallbackUrl: String?) : NavDecision
}

/**
 * Validates non-web links (tel:, mailto:, intent:, whatsapp:, market: ...) before anything leaves the browser.
 * Intents are sanitized: explicit components and selectors are removed and BROWSABLE is required,
 * so a web page can never target a private, non-exported app component.
 */
object ExternalAppHandler {
    private val BLOCKED = setOf(
        "javascript", "file", "content", "data", "chrome", "chrome-extension",
        "filesystem", "jar", "android-app", "vbscript",
    )

    private val SCHEME_LABELS = mapOf(
        "tel" to "Phone", "mailto" to "Email", "sms" to "Messages", "smsto" to "Messages",
        "mms" to "Messages", "whatsapp" to "WhatsApp", "tg" to "Telegram", "geo" to "Maps",
        "vnd.youtube" to "YouTube", "youtube" to "YouTube", "viber" to "Viber",
        "instagram" to "Instagram", "fb" to "Facebook", "skype" to "Skype",
    )

    private val PACKAGE_LABELS = mapOf(
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.youtube" to "YouTube",
        "com.google.android.apps.maps" to "Google Maps",
        "com.android.vending" to "Google Play",
        "com.instagram.android" to "Instagram",
    )

    fun classify(url: String): NavDecision {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return NavDecision.Block
        return when (scheme) {
            "http", "https", "about", "blob" -> NavDecision.AllowInWebView
            in BLOCKED -> NavDecision.Block
            "intent" -> fromIntentUri(url)
            "market" -> NavDecision.External(
                browsable(Intent(Intent.ACTION_VIEW, uri)), "Google Play", playStoreFallback(uri)
            )
            else -> NavDecision.External(browsable(Intent(Intent.ACTION_VIEW, uri)), SCHEME_LABELS[scheme], null)
        }
    }

    private fun fromIntentUri(url: String): NavDecision {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            return NavDecision.Block
        }
        val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { UrlUtils.isWebUrl(it) }
        intent.component = null
        intent.selector = null
        intent.clipData = null
        intent.flags = intent.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            ).inv()
        browsable(intent)
        val dataScheme = intent.data?.scheme?.lowercase(Locale.ROOT)
        if (dataScheme != null && dataScheme in BLOCKED) return NavDecision.Block
        val pkg = intent.`package`
        if (pkg == null && (dataScheme == "http" || dataScheme == "https")) {
            return NavDecision.LoadUrl(intent.dataString ?: return NavDecision.Block)
        }
        return NavDecision.External(intent, pkg?.let { PACKAGE_LABELS[it] ?: it }, fallback)
    }

    private fun browsable(intent: Intent): Intent = intent.addCategory(Intent.CATEGORY_BROWSABLE)

    private fun playStoreFallback(uri: Uri): String {
        val path = uri.host ?: "details"
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        return "https://play.google.com/store/apps/$path$query"
    }

    fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
