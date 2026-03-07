package com.helix.browser.data

import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val dao: BookmarkDao) {

    fun getAllBookmarks(): Flow<List<Bookmark>> = dao.getAllBookmarks()

    fun search(query: String): Flow<List<Bookmark>> = dao.search(query)

    suspend fun addBookmark(title: String, url: String, faviconUrl: String? = null): Long {
        return dao.insert(Bookmark(title = title, url = url, faviconUrl = faviconUrl))
    }

    suspend fun removeBookmark(bookmark: Bookmark) = dao.delete(bookmark)

    suspend fun removeBookmarkById(id: Long) = dao.deleteById(id)

    suspend fun isBookmarked(url: String): Boolean = dao.isBookmarked(url)

    suspend fun toggleBookmark(title: String, url: String, faviconUrl: String? = null): Boolean {
        val existing = dao.getBookmarkByUrl(url)
        return if (existing != null) {
            dao.delete(existing)
            false
        } else {
            dao.insert(Bookmark(title = title, url = url, faviconUrl = faviconUrl))
            true
        }
    }
}
