package uz.usar.browser.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites")
    fun observeAll(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE isFavorite = 1 ORDER BY position ASC, dateAdded DESC")
    fun observeFavorites(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE isPinned = 1 ORDER BY position ASC, dateAdded DESC")
    fun observePinned(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE visitCount > 0 ORDER BY visitCount DESC, lastVisited DESC LIMIT :limit")
    fun observeMostVisited(limit: Int): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE url = :url LIMIT 1")
    fun observeByUrl(url: String): Flow<SiteEntity?>

    @Query("SELECT * FROM sites WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): SiteEntity?

    @Query("SELECT * FROM sites")
    suspend fun getAll(): List<SiteEntity>

    @Query("SELECT * FROM sites WHERE url LIKE :pattern OR title LIKE :pattern OR customName LIKE :pattern ORDER BY visitCount DESC LIMIT :limit")
    suspend fun search(pattern: String, limit: Int): List<SiteEntity>

    @Query("SELECT MAX(position) FROM sites")
    suspend fun maxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(site: SiteEntity): Long

    @Update
    suspend fun update(site: SiteEntity)

    @Update
    suspend fun updateAll(sites: List<SiteEntity>)

    @Delete
    suspend fun delete(site: SiteEntity)

    @Query("DELETE FROM sites")
    suspend fun deleteAll()

    @Query("UPDATE sites SET title = :title WHERE url = :url AND title = ''")
    suspend fun fillTitle(url: String, title: String)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Update
    suspend fun update(entry: HistoryEntity)

    @Query(
        "SELECT * FROM history WHERE (:query = '' OR url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') " +
            "ORDER BY visitedAt DESC LIMIT 3000"
    )
    fun observe(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY url) ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 1")
    suspend fun latest(): HistoryEntity?

    @Query("SELECT * FROM history WHERE url LIKE :pattern OR title LIKE :pattern ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun search(pattern: String, limit: Int): List<HistoryEntity>

    @Query("UPDATE history SET title = :title WHERE id = (SELECT MAX(id) FROM history WHERE url = :url)")
    suspend fun updateLatestTitle(url: String, title: String)

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM history WHERE visitedAt >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 0 OR status = 3")
    suspend fun getActive(): List<DownloadEntity>

    @Insert
    suspend fun insert(item: DownloadEntity): Long

    @Update
    suspend fun update(item: DownloadEntity)

    @Delete
    suspend fun delete(item: DownloadEntity)
}

@Dao
interface HostSettingsDao {
    @Query("SELECT * FROM host_settings ORDER BY host ASC")
    fun observeAll(): Flow<List<HostSettingsEntity>>

    @Query("SELECT * FROM host_settings WHERE host = :host")
    suspend fun get(host: String): HostSettingsEntity?

    @Upsert
    suspend fun upsert(entity: HostSettingsEntity)

    @Query("DELETE FROM host_settings WHERE host = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM host_settings")
    suspend fun deleteAll()
}

@Dao
abstract class TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    abstract suspend fun getAll(): List<TabEntity>

    @Query("DELETE FROM tabs")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(tabs: List<TabEntity>)

    @Transaction
    open suspend fun replaceAll(tabs: List<TabEntity>) {
        deleteAll()
        insertAll(tabs)
    }
}
