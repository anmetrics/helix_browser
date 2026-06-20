package com.helix.browser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    suspend fun getAllBookmarksSnapshot(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE url = :url AND isFolder = 0 LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Bookmark?

    /**
     * Children of [parentId], folders first then bookmarks, each ordered newest
     * first. When [parentId] is null we return the root: rows with no parent AND
     * "orphan" rows whose parent folder no longer exists (so a bookmark left
     * behind by a deleted folder still shows up instead of vanishing).
     */
    @Query(
        """
        SELECT * FROM bookmarks
        WHERE (:parentId IS NULL AND (
                   parentId IS NULL
                   OR parentId NOT IN (SELECT id FROM bookmarks WHERE isFolder = 1)
               ))
           OR (:parentId IS NOT NULL AND parentId = :parentId)
        ORDER BY isFolder DESC, timestamp DESC
        """
    )
    fun getChildren(parentId: Long?): Flow<List<Bookmark>>

    /** All folders (for the "move to folder" picker), oldest-created first. */
    @Query("SELECT * FROM bookmarks WHERE isFolder = 1 ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllFolders(): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Re-parent every direct child of [oldParentId] to [newParentId]. */
    @Query("UPDATE bookmarks SET parentId = :newParentId WHERE parentId = :oldParentId")
    suspend fun reparentChildren(oldParentId: Long, newParentId: Long?)

    /** Move a single row into [newParentId] (null = root). */
    @Query("UPDATE bookmarks SET parentId = :newParentId WHERE id = :id")
    suspend fun moveToFolder(id: Long, newParentId: Long?)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url AND isFolder = 0)")
    suspend fun isBookmarked(url: String): Boolean

    /**
     * Flat search across every folder. Only real bookmarks (not folders) match
     * so the result reads like Chrome's flat search list. `\` escapes the LIKE
     * wildcards `%` and `_` (set up by [BookmarkRepository.search]) so a query
     * such as "100%" is treated literally.
     */
    @Query(
        """
        SELECT * FROM bookmarks
        WHERE isFolder = 0
          AND (title LIKE :pattern ESCAPE '\' OR url LIKE :pattern ESCAPE '\')
        ORDER BY timestamp DESC
        """
    )
    fun search(pattern: String): Flow<List<Bookmark>>
}
