package com.helix.browser.data

import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val dao: BookmarkDao) {

    fun dao(): BookmarkDao = dao

    fun getAllBookmarks(): Flow<List<Bookmark>> = dao.getAllBookmarks()

    /** Children of [parentId] (null = root). Folders first, then bookmarks. */
    fun getChildren(parentId: Long?): Flow<List<Bookmark>> = dao.getChildren(parentId)

    /**
     * Flat search across all folders. The user's query is matched literally:
     * we escape the SQL LIKE wildcards `%` and `_` (and the escape char itself)
     * before wrapping it in `%…%`.
     */
    fun search(query: String): Flow<List<Bookmark>> = dao.search("%${escapeLike(query)}%")

    suspend fun getById(id: Long): Bookmark? = dao.getById(id)

    suspend fun getAllFolders(): List<Bookmark> = dao.getAllFolders()

    suspend fun addBookmark(
        title: String,
        url: String,
        faviconUrl: String? = null,
        parentId: Long? = null
    ): Long {
        return dao.insert(
            Bookmark(title = title, url = url, faviconUrl = faviconUrl, parentId = parentId)
        )
    }

    /** Create a folder under [parentId] (null = root). Returns its new id. */
    suspend fun addFolder(name: String, parentId: Long? = null): Long {
        return dao.insert(
            Bookmark(title = name, url = "", isFolder = true, parentId = parentId)
        )
    }

    /** Persist an edited bookmark/folder (title/url). Keeps id & timestamp. */
    suspend fun updateBookmark(bookmark: Bookmark) = dao.update(bookmark)

    /** Rename a folder in place. */
    suspend fun renameFolder(folder: Bookmark, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        dao.update(folder.copy(title = trimmed))
    }

    suspend fun removeBookmark(bookmark: Bookmark) = dao.delete(bookmark)

    suspend fun removeBookmarkById(id: Long) = dao.deleteById(id)

    /**
     * Chrome-style folder delete: the folder row is removed and its direct
     * children are re-parented up to the folder's own parent, so nothing in the
     * subtree is orphaned. (Grandchildren keep pointing at their own parent,
     * which is unaffected.) Runs as a single transaction-free pair of writes;
     * order matters — re-parent first, then delete the now-empty folder.
     */
    suspend fun deleteFolder(folder: Bookmark) {
        if (!folder.isFolder) {
            dao.delete(folder)
            return
        }
        dao.reparentChildren(folder.id, folder.parentId)
        dao.deleteById(folder.id)
    }

    /**
     * Move [item] into [targetParentId] (null = root). Returns false (no-op) if
     * the move would create a cycle, i.e. moving a folder into itself or into
     * one of its own descendants — that would orphan the subtree. Bookmarks
     * (non-folders) can always move anywhere.
     */
    suspend fun moveToFolder(item: Bookmark, targetParentId: Long?): Boolean {
        if (item.parentId == targetParentId) return true
        if (item.isFolder && targetParentId != null) {
            if (targetParentId == item.id) return false
            if (isDescendant(ancestorId = item.id, candidateId = targetParentId)) return false
        }
        dao.moveToFolder(item.id, targetParentId)
        return true
    }

    /**
     * Walks parent links upward from [candidateId]; true if [ancestorId] is met,
     * meaning [candidateId] lives somewhere inside [ancestorId]. A visited-set
     * guards against any pre-existing corrupt cycle so this can't loop forever.
     */
    suspend fun isDescendant(ancestorId: Long, candidateId: Long): Boolean {
        val seen = HashSet<Long>()
        var current: Long? = candidateId
        while (current != null && seen.add(current)) {
            if (current == ancestorId) return true
            current = dao.getById(current)?.parentId
        }
        return false
    }

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

    private companion object {
        /** Escape `\`, `%`, `_` so they match literally inside a LIKE pattern. */
        fun escapeLike(raw: String): String = raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
