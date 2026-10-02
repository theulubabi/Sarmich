package uz.usar.browser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SiteEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        HostSettingsEntity::class,
        TabEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun hostSettingsDao(): HostSettingsDao
    abstract fun tabDao(): TabDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "browser.db").build()
    }
}
