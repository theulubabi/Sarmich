package uz.usar.browser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import uz.usar.browser.browser.CrashGuard
import uz.usar.browser.data.settings.SettingsRepository

class UsarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        AppCompatDelegate.setDefaultNightMode(SettingsRepository.nightModeFor(SettingsRepository.storedTheme(this)))
        container = AppContainer(this)
    }
}
