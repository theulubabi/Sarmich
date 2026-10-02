package uz.usar.browser.browser

import android.content.Context

/**
 * Records app crashes so the next start can skip restoring tabs if the app keeps crashing,
 * which prevents an endless crash loop caused by a bad page.
 */
object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val KEY_LAST = "last_crash"
    private const val KEY_COUNT = "crash_count"
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong(KEY_LAST, System.currentTimeMillis())
                    .putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1)
                    .commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun recentCrashCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST, 0L)
        return if (System.currentTimeMillis() - last < WINDOW_MS) prefs.getInt(KEY_COUNT, 0) else 0
    }

    fun shouldSkipRestore(context: Context): Boolean = recentCrashCount(context) >= 2

    /** Called once the app has been running normally for a while. */
    fun markStable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COUNT, 0).apply()
    }
}
