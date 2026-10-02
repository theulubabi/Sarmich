package uz.usar.browser.browser

import android.net.Uri
import uz.usar.browser.data.settings.SearchEngine
import java.net.URLEncoder
import java.util.Locale

/** Smart address bar logic: decides whether input is a web address or a search. */
object UrlUtils {
    private val DOMAIN = Regex("""^(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+\p{L}{2,63}\.?(?::\d{1,5})?(?:[/?#]\S*)?$""")
    private val IPV4 = Regex("""^(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?(?:[/?#]\S*)?$""")
    private val LOCALHOST = Regex("""^localhost(?::\d{1,5})?(?:[/?#]\S*)?$""", RegexOption.IGNORE_CASE)

    fun resolveInput(input: String, engine: SearchEngine): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return if (!text.contains(' ') && isWebUrl(text)) text else searchUrl(text, engine)
        }
        if (!text.contains(' ')) {
            if (DOMAIN.matches(text)) return "https://$text"
            if (IPV4.matches(text) || LOCALHOST.matches(text)) return "http://$text"
        }
        return searchUrl(text, engine)
    }

    fun searchUrl(query: String, engine: SearchEngine): String =
        engine.searchUrl(URLEncoder.encode(query, "UTF-8"))

    fun isWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    /** Host without "www." used as the key for per-site settings and favicons. */
    fun host(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return Uri.parse(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: ""
    }

    fun isHttps(url: String?): Boolean = url != null && Uri.parse(url).scheme.equals("https", ignoreCase = true)

    /** Removes the fragment and a trailing slash on bare origins so the same site is stored once. */
    fun normalize(url: String): String {
        val noFragment = url.substringBefore('#')
        val uri = Uri.parse(noFragment)
        val path = uri.path
        return if ((path.isNullOrEmpty() || path == "/") && uri.query == null) noFragment.trimEnd('/') else noFragment
    }

    fun displayUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }

    fun origin(url: String): String? {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
    }

    /** Validates text from a QR code. Only http(s) addresses are accepted. */
    fun fromQrText(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.contains(' ') || t.contains('\n')) return null
        if (isWebUrl(t)) return t
        if (DOMAIN.matches(t)) return "https://$t"
        return null
    }

    /** Accepts only full http(s) URLs for the custom homepage setting. */
    fun validHomepage(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        if (isWebUrl(t)) return t
        if (DOMAIN.matches(t)) return "https://$t"
        return null
    }
}
