package uz.usar.browser.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uz.usar.browser.data.settings.PermType
import uz.usar.browser.data.settings.PermValue

/** A saved website. Favorites are saved websites with [isFavorite] set. */
@Entity(tableName = "sites", indices = [Index(value = ["url"], unique = true)])
data class SiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val customName: String? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val position: Int = 0,
    val visitCount: Int = 0,
    val lastVisited: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
)

val SiteEntity.displayName: String
    get() = customName?.takeIf { it.isNotBlank() } ?: title.ifBlank { host.ifBlank { url } }

@Entity(tableName = "history", indices = [Index("visitedAt"), Index("url")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val visitedAt: Long,
)

object DownloadStatus {
    const val RUNNING = 0
    const val COMPLETED = 1
    const val FAILED = 2
    const val PAUSED = 3
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** DownloadManager id, or -1 for files written directly (data: URLs). */
    val systemId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: Int,
    val createdAt: Long,
    val userAgent: String? = null,
    val localUri: String? = null,
)

/** Per-site overrides. Every permission column uses [PermValue]. desktopMode: 0 default, 1 mobile, 2 desktop. */
@Entity(tableName = "host_settings")
data class HostSettingsEntity(
    @PrimaryKey val host: String,
    val desktopMode: Int = 0,
    val camera: Int = PermValue.DEFAULT,
    val microphone: Int = PermValue.DEFAULT,
    val location: Int = PermValue.DEFAULT,
    val popups: Int = PermValue.DEFAULT,
    val downloads: Int = PermValue.DEFAULT,
) {
    fun get(type: PermType): Int = when (type) {
        PermType.CAMERA -> camera
        PermType.MICROPHONE -> microphone
        PermType.LOCATION -> location
        PermType.POPUPS -> popups
        PermType.DOWNLOADS -> downloads
    }

    fun with(type: PermType, value: Int): HostSettingsEntity = when (type) {
        PermType.CAMERA -> copy(camera = value)
        PermType.MICROPHONE -> copy(microphone = value)
        PermType.LOCATION -> copy(location = value)
        PermType.POPUPS -> copy(popups = value)
        PermType.DOWNLOADS -> copy(downloads = value)
    }
}

/** Open (non-private) tabs, saved so they can be restored after a restart. */
@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val position: Int,
    val isActive: Boolean,
)
