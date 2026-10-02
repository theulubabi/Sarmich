package uz.usar.browser

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uz.usar.browser.auth.GoogleAuth
import uz.usar.browser.browser.DownloadController
import uz.usar.browser.browser.NetworkMonitor
import uz.usar.browser.data.BackupManager
import uz.usar.browser.data.BrowserRepository
import uz.usar.browser.data.FaviconStore
import uz.usar.browser.data.db.AppDatabase
import uz.usar.browser.data.settings.SettingsRepository

/** Simple manual dependency container, created once per process. */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database: AppDatabase = AppDatabase.build(app)
    val settings = SettingsRepository(app, appScope)
    val repository = BrowserRepository(database)
    val favicons = FaviconStore(app)
    val downloads = DownloadController(app, database.downloadDao(), appScope)
    val network = NetworkMonitor(app, appScope)
    val backup = BackupManager(app, repository)
    val auth = GoogleAuth(app)
}
