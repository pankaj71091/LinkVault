package com.linkvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.linkvault.app.data.local.entity.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    /**
     * [folderId] == null returns unsorted bookmarks. `IS` (not `=`) is
     * SQLite's null-safe equality, so this one query correctly handles
     * both "bookmarks in folder X" and "unsorted bookmarks".
     */
    @Query("SELECT * FROM bookmarks WHERE folderId IS :folderId AND isArchived = 0 ORDER BY createdAt DESC")
    fun getBookmarksInFolder(folderId: Long?): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE id = :bookmarkId")
    suspend fun getBookmarkById(bookmarkId: Long): Bookmark?

    @Query("SELECT folderId, COUNT(*) as count FROM bookmarks WHERE isArchived = 0 AND folderId IS NOT NULL GROUP BY folderId")
    fun getBookmarkCountsByFolder(): Flow<List<FolderBookmarkCount>>

    // --- Phase 4.5: archive / pin ---

    @Query("SELECT * FROM bookmarks WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE isPinned = 1 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getPinnedBookmarks(): Flow<List<Bookmark>>

    // --- Phase 4: backup/restore ---

    /** Every bookmark, unfiltered (including archived) — export needs a complete snapshot. */
    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksOnce(): List<Bookmark>

    /** Used only by replace-mode import, which wipes everything before restoring from the backup file. */
    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    // --- Phase 4.5 batch 2: duplicate detection / auto-suggest folder ---

    /** Exact URL match — used to warn on save rather than block it. */
    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByExactUrl(url: String): Bookmark?

    /** Broad SQL filter (not exact) to narrow down candidates before the
     *  repository does the actual domain comparison and folder-frequency
     *  count in Kotlin — there's no SQLite URL-parsing function to do that
     *  matching directly in SQL.
     */
    @Query("SELECT * FROM bookmarks WHERE url LIKE '%' || :domainFragment || '%' AND folderId IS NOT NULL")
    suspend fun findByUrlContaining(domainFragment: String): List<Bookmark>

    // --- Phase 5 Stage 1: Bulk Actions ---

    @Query("DELETE FROM bookmarks WHERE id IN (:ids)")
    suspend fun deleteMultiple(ids: List<Long>)

    @Query("UPDATE bookmarks SET isArchived = :archived, updatedAt = :timestamp WHERE id IN (:ids)")
    suspend fun archiveMultiple(ids: List<Long>, archived: Boolean, timestamp: Long)

    @Query("UPDATE bookmarks SET folderId = :folderId, updatedAt = :timestamp WHERE id IN (:ids)")
    suspend fun moveMultipleToFolder(ids: List<Long>, folderId: Long?, timestamp: Long)
}

data class FolderBookmarkCount(
    val folderId: Long,
    val count: Int
)
