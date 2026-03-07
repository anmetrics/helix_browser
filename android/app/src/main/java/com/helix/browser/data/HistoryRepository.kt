package com.helix.browser.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    fun getAllHistory(): Flow<List<HistoryItem>> = dao.getAllHistory()

    fun search(query: String): Flow<List<HistoryItem>> = dao.search(query)

    suspend fun addHistory(title: String, url: String, faviconUrl: String? = null) {
        // Avoid duplicates in quick succession (same URL within 5 minutes)
        dao.insert(HistoryItem(title = title, url = url, faviconUrl = faviconUrl))
    }

    suspend fun deleteItem(item: HistoryItem) = dao.delete(item)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    suspend fun deleteOlderThan(days: Int) {
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        dao.deleteOlderThan(timestamp)
    }
}
