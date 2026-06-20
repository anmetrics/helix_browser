package com.helix.browser.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * True for folder rows. Folders are ordinary `bookmarks` rows that simply
     * carry no meaningful [url] (it is stored as the empty string). Added in
     * AppDatabase schema version 2 via MIGRATION_1_2 with a safe
     * `INTEGER NOT NULL DEFAULT 0`, so every pre-existing bookmark becomes a
     * non-folder automatically.
     */
    @ColumnInfo(name = "isFolder", defaultValue = "0")
    val isFolder: Boolean = false,
    /**
     * Id of the containing folder, or `null` for items living at the bookmarks
     * root. Added in schema version 2 as a nullable `INTEGER`. A row whose
     * [parentId] points at a deleted folder is treated as root by the queries
     * (see [BookmarkDao.getChildren]); deleting a folder re-parents its
     * children to the folder's own parent so the subtree is never orphaned.
     */
    @ColumnInfo(name = "parentId")
    val parentId: Long? = null
) {
    /**
     * Returns a copy carrying user-edited [title]/[url] while preserving the
     * primary key and original [timestamp], so an "edit bookmark" dialog can
     * persist changes via an `OnConflictStrategy.REPLACE` insert without
     * creating a duplicate row or reordering the list. Blank inputs fall back
     * to the existing values so an empty field can never wipe a bookmark.
     */
    fun withEdits(newTitle: String, newUrl: String): Bookmark = copy(
        title = newTitle.trim().ifBlank { title },
        url = newUrl.trim().ifBlank { url }
    )
}
