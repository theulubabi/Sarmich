package uz.usar.browser.browser

import android.content.Context
import android.content.pm.PackageInfo
import androidx.webkit.WebViewCompat

sealed interface WebViewProblem {
    data object Missing : WebViewProblem
    data class Outdated(val version: String) : WebViewProblem
}

object WebViewStatus {
    private const val MIN_MAJOR_VERSION = 100

    fun packageInfo(context: Context): PackageInfo? =
        runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()

    fun check(context: Context): WebViewProblem? {
        val pkg = packageInfo(context) ?: return WebViewProblem.Missing
        val version = pkg.versionName ?: return null
        val major = version.substringBefore('.').toIntOrNull() ?: return null
        return if (major < MIN_MAJOR_VERSION) WebViewProblem.Outdated(version) else null
    }
}
