package uz.usar.browser.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.db.AppDatabase
import uz.usar.browser.data.db.HistoryEntity
import uz.usar.browser.data.db.HostSettingsEntity
import uz.usar.browser.data.db.SiteEntity
import uz.usar.browser.data.db.TabEntity
import uz.usar.browser.data.settings.PermType

data class Suggestion(val url: String, val title: String, val isSaved: Boolean)

/** All locally stored browsing data. Nothing here is ever sent off the device. */
class BrowserRepository(db: AppDatabase) {
    private val sites = db.siteDao()
    private val history = db.historyDao()
    private val hosts = db.hostSettingsDao()
    private val tabs = db.tabDao()

    // ---- Saved websites & favorites ----
    fun observeAllSites(): Flow<List<SiteEntity>> = sites.observeAll()
    fun observeFavorites(): Flow<List<SiteEntity>> = sites.observeFavorites()
    fun observePinned(): Flow<List<SiteEntity>> = sites.observePinned()
    fun observeMostVisited(limit: Int): Flow<List<SiteEntity>> = sites.observeMostVisited(limit)
    fun observeIsFavorite(url: String): Flow<Boolean> =
        sites.observeByUrl(UrlUtils.normalize(url)).map { it?.isFavorite == true }

    suspend fun allSites(): List<SiteEntity> = sites.getAll()

    suspend fun recordVisit(url: String, title: String, saveHistory: Boolean, saveSite: Boolean) {
        if (!UrlUtils.isWebUrl(url)) return
        val now = System.currentTimeMillis()
        val host = UrlUtils.host(url)
        if (saveHistory) {
            val last = history.latest()
            if (last != null && last.url == url && now - last.visitedAt < 30_000) {
                history.update(last.copy(title = title.ifBlank { last.title }, visitedAt = now))
            } else {
                history.insert(HistoryEntity(url = url, title = title, host = host, visitedAt = now))
            }
        }
        val key = UrlUtils.normalize(url)
        val existing = sites.findByUrl(key)
        if (existing != null) {
            sites.update(
                existing.copy(
                    visitCount = existing.visitCount + 1,
                    lastVisited = now,
                    title = existing.title.ifBlank { title },
                )
            )
        } else if (saveSite) {
            sites.insert(
                SiteEntity(url = key, title = title, host = host, visitCount = 1, lastVisited = now, dateAdded = now)
            )
        }
    }

    suspend fun updateTitle(url: String, title: String, inHistory: Boolean) {
        if (title.isBlank() || !UrlUtils.isWebUrl(url)) return
        if (inHistory) history.updateLatestTitle(url, title)
        sites.fillTitle(UrlUtils.normalize(url), title)
    }

    /** Returns the new favorite state. */
    suspend fun toggleFavorite(url: String, title: String): Boolean {
        val key = UrlUtils.normalize(url)
        val existing = sites.findByUrl(key)
        val nextPosition = (sites.maxPosition() ?: 0) + 1
        return if (existing == null) {
            sites.insert(
                SiteEntity(
                    url = key, title = title, host = UrlUtils.host(url),
                    isFavorite = true, position = nextPosition,
                )
            )
            true
        } else {
            val fav = !existing.isFavorite
            sites.update(existing.copy(isFavorite = fav, position = if (fav) nextPosition else existing.position))
            fav
        }
    }

    suspend fun setFavorite(site: SiteEntity, favorite: Boolean) {
        val pos = if (favorite) (sites.maxPosition() ?: 0) + 1 else site.position
        sites.update(site.copy(isFavorite = favorite, position = pos))
    }

    suspend fun setPinned(site: SiteEntity, pinned: Boolean) {
        val pos = if (pinned) (sites.maxPosition() ?: 0) + 1 else site.position
        sites.update(site.copy(isPinned = pinned, position = pos))
    }

    suspend fun pinUrl(url: String, title: String) {
        val key = UrlUtils.normalize(url)
        val existing = sites.findByUrl(key)
        if (existing == null) {
            sites.insert(
                SiteEntity(
                    url = key, title = title, host = UrlUtils.host(url),
                    isPinned = true, position = (sites.maxPosition() ?: 0) + 1,
                )
            )
        } else {
            setPinned(existing, true)
        }
    }

    /** Returns false if the new URL is already used by another saved site. */
    suspend fun editSite(site: SiteEntity, customName: String, url: String): Boolean {
        val key = UrlUtils.normalize(url)
        if (key != site.url && sites.findByUrl(key) != null) return false
        sites.update(site.copy(customName = customName.ifBlank { null }, url = key, host = UrlUtils.host(key)))
        return true
    }

    suspend fun deleteSite(site: SiteEntity) = sites.delete(site)
    suspend fun deleteAllSites() = sites.deleteAll()

    /** Moves an item one step inside an ordered list (favorites or pinned). */
    suspend fun move(ordered: List<SiteEntity>, index: Int, up: Boolean) {
        val target = if (up) index - 1 else index + 1
        if (index !in ordered.indices || target !in ordered.indices) return
        val list = ordered.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        sites.updateAll(list.mapIndexed { i, s -> s.copy(position = i) })
    }

    suspend fun importSites(items: List<SiteEntity>): Int {
        var count = 0
        items.forEach { incoming ->
            val key = UrlUtils.normalize(incoming.url)
            val existing = sites.findByUrl(key)
            if (existing == null) {
                sites.insert(incoming.copy(id = 0, url = key, host = UrlUtils.host(key)))
            } else {
                sites.update(
                    existing.copy(
                        isFavorite = existing.isFavorite || incoming.isFavorite,
                        isPinned = existing.isPinned || incoming.isPinned,
                        customName = existing.customName ?: incoming.customName,
                        title = existing.title.ifBlank { incoming.title },
                    )
                )
            }
            count++
        }
        return count
    }

    suspend fun suggestions(query: String): List<Suggestion> {
        val pattern = "%${query.trim()}%"
        val saved = sites.search(pattern, 4).map { Suggestion(it.url, it.title.ifBlank { it.host }, true) }
        val visited = history.search(pattern, 8).map { Suggestion(it.url, it.title, false) }
        return (saved + visited).distinctBy { UrlUtils.normalize(it.url) }.take(6)
    }

    // ---- History ----
    fun observeHistory(query: String): Flow<List<HistoryEntity>> = history.observe(query)
    fun observeRecentHistory(limit: Int): Flow<List<HistoryEntity>> = history.observeRecent(limit)
    suspend fun latestHistory(): HistoryEntity? = history.latest()
    suspend fun deleteHistory(ids: List<Long>) = history.deleteIds(ids)
    suspend fun deleteHistorySince(since: Long) = history.deleteSince(since)
    suspend fun clearHistory() = history.deleteAll()

    // ---- Per-site settings ----
    fun observeHostSettings(): Flow<List<HostSettingsEntity>> = hosts.observeAll()

    suspend fun setPermission(host: String, type: PermType, value: Int) {
        val current = hosts.get(host) ?: HostSettingsEntity(host)
        hosts.upsert(current.with(type, value))
    }

    suspend fun setDesktopMode(host: String, mode: Int) {
        val current = hosts.get(host) ?: HostSettingsEntity(host)
        hosts.upsert(current.copy(desktopMode = mode))
    }

    suspend fun resetPermissions(host: String) {
        val current = hosts.get(host) ?: return
        val reset = HostSettingsEntity(host = host, desktopMode = current.desktopMode)
        if (reset.desktopMode == 0) hosts.delete(host) else hosts.upsert(reset)
    }

    suspend fun clearHostSettings() = hosts.deleteAll()

    // ---- Tabs ----
    suspend fun saveTabs(list: List<TabEntity>) = tabs.replaceAll(list)
    suspend fun loadTabs(): List<TabEntity> = tabs.getAll()
}
