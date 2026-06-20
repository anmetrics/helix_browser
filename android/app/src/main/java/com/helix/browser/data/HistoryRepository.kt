package com.helix.browser.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class HistoryRepository(private val dao: HistoryDao) {

    fun getAllHistory(): Flow<List<HistoryItem>> = dao.getAllHistory()

    fun search(query: String): Flow<List<HistoryItem>> = dao.search(query)

    suspend fun addHistory(title: String, url: String, faviconUrl: String? = null) {
        // Avoid duplicates in quick succession (same URL within the dedup window).
        // Look up the most-recent existing entry for this exact URL; getAllHistory()
        // is ordered by timestamp DESC, so the first exact match is the newest.
        val now = System.currentTimeMillis()
        val existing = dao.getAllHistory().first().firstOrNull { it.url == url }
        if (existing != null && now - existing.timestamp < DEDUP_WINDOW_MS) {
            // Same URL revisited within the window: refresh the existing entry
            // (timestamp/title/favicon) instead of recording a duplicate row.
            dao.deleteById(existing.id)
        }
        dao.insert(HistoryItem(title = title, url = url, faviconUrl = faviconUrl, timestamp = now))
    }

    suspend fun deleteItem(item: HistoryItem) = dao.delete(item)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    suspend fun deleteOlderThan(days: Int) {
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        dao.deleteOlderThan(timestamp)
    }

    // Time-range "Clear browsing data". Deletes every visit at or after
    // sinceMillis (an absolute epoch-millis cutoff the caller computes from the
    // chosen range, e.g. now - 1h). Pass 0 for "All time": every real visit has
    // timestamp > 0, so >= 0 removes the whole table. Negative inputs are clamped
    // to 0 so a bad cutoff can only ever clear more, never silently no-op while
    // leaving data the user asked to delete.
    suspend fun clearRange(sinceMillis: Long) = dao.clearRange(sinceMillis.coerceAtLeast(0L))

    suspend fun getSuggestions(query: String): List<HistoryItem> = dao.getSuggestions(query)

    // Top N most-visited sites for the home-screen tiles (mainactivity unit).
    // Grouped by url, ordered by visit frequency then recency; each returned
    // HistoryItem is the most-recent row for its url, so title/faviconUrl are the
    // freshest values. limit is clamped to >= 0 to keep the SQL LIMIT well-formed
    // (a negative LIMIT in SQLite means "unbounded", which would be a surprising
    // result for a caller asking for a bounded tile count); limit 0 returns an
    // empty list.
    suspend fun getTopSites(limit: Int): List<HistoryItem> =
        dao.getTopSites(limit.coerceAtLeast(0))

    companion object {
        // Repeat visits to the same URL within this window collapse into a single
        // (refreshed) entry rather than recording a new row on every page load.
        private const val DEDUP_WINDOW_MS = 5 * 60 * 1000L
    }
}
