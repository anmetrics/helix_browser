package com.helix.browser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 500")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem): Long

    @Delete
    suspend fun delete(item: HistoryItem)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    // Time-range clear ("Clear browsing data"). Deletes every row visited at or
    // after :since. Callers pass 0 to wipe everything (every real timestamp is
    // > 0), which keeps the "All time" branch on the same code path instead of a
    // separate clearAll() call. Inclusive >= matches Chrome's behaviour where a
    // page visited exactly at the cutoff is removed.
    @Query("DELETE FROM history WHERE timestamp >= :since")
    suspend fun clearRange(since: Long)

    // Most-visited tiles consumed by the mainactivity unit. We have no per-row
    // visit counter, so frequency is derived as COUNT(*) over rows sharing the
    // same url (each page load that survives the repository dedup window is one
    // row). For each url group we surface the most-recent row via MAX(timestamp)
    // so the representative title/faviconUrl/id are the freshest known values.
    //
    // GROUP BY url with bare columns relies on SQLite's documented bare-column
    // rule: when MAX() is present, the other selected columns come from the row
    // that supplied that max, so id/title/faviconUrl/timestamp are mutually
    // consistent (all from the newest visit), not mixed across rows.
    //
    // Ordering: COUNT(*) DESC (frequency) then MAX(timestamp) DESC (recency
    // tie-break), exactly the "frequency then recency" contract. The SELECT list
    // is restricted to the HistoryItem columns (COUNT(*) is only used in ORDER
    // BY, never projected) so Room maps the cursor cleanly with no surplus-column
    // warning. Empty history and limit<=0 both yield an empty list (LIMIT 0
    // returns nothing), so the caller never has to special-case an empty table.
    @Query(
        "SELECT id, title, url, faviconUrl, MAX(timestamp) AS timestamp " +
            "FROM history GROUP BY url " +
            "ORDER BY COUNT(*) DESC, MAX(timestamp) DESC LIMIT :limit"
    )
    suspend fun getTopSites(limit: Int): List<HistoryItem>

    // Public search entry point. The raw user query is escaped so that the SQLite
    // LIKE wildcards `%` and `_` (and the escape char `\` itself) are treated as
    // literal characters typed by the user rather than as pattern metacharacters.
    // Without this, typing `%` matches every row and `_` matches any single char,
    // producing spurious results from normal typing.
    fun search(query: String): Flow<List<HistoryItem>> =
        searchEscaped(buildLikePattern(query))

    // Suggestion entry point for the omnibox autocomplete. Same escaping rationale
    // as search() above; this path is fed directly by typed address-bar text.
    suspend fun getSuggestions(query: String): List<HistoryItem> =
        getSuggestionsEscaped(buildLikePattern(query))

    // ESCAPE '\' makes the backslash the escape character for the LIKE expression,
    // so an escaped `\%` / `\_` matches a literal percent / underscore. The bound
    // :pattern argument already wraps the escaped term in leading/trailing `%`
    // (the only intentional wildcards) via buildLikePattern().
    @Query(
        "SELECT * FROM history WHERE title LIKE :pattern ESCAPE '\\' " +
            "OR url LIKE :pattern ESCAPE '\\' ORDER BY timestamp DESC LIMIT 100"
    )
    fun searchEscaped(pattern: String): Flow<List<HistoryItem>>

    @Query(
        "SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' " +
            "OR title LIKE :pattern ESCAPE '\\' GROUP BY url ORDER BY timestamp DESC LIMIT 5"
    )
    suspend fun getSuggestionsEscaped(pattern: String): List<HistoryItem>

    companion object {
        // Escape LIKE metacharacters in the user-supplied term, then wrap the
        // result in `%`...`%` for a contains-match. Order matters: the backslash
        // must be escaped first so the escapes we add for `%`/`_` are not
        // themselves doubled.
        private fun buildLikePattern(query: String): String {
            val escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
