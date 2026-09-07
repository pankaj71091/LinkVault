package com.linkvault.app.testutil

import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderBookmarkCount
import com.linkvault.app.data.local.entity.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [BookmarkDao] for unit tests. Implements only the methods
 * [com.linkvault.app.data.repository.BackupRepository] uses; the rest
 * throw to surface accidental test-scope creep into unrelated code paths.
 */
class FakeBookmarkDao : BookmarkDao {

    private val bookmarks: MutableList<Bookmark> = mutableListOf()
    private val bookmarksById: MutableMap<Long, Bookmark> = mutableMapOf()
    private val bookmarksState = MutableStateFlow<List<Bookmark>>(emptyList())
    private var nextId: Long = 1L

    private fun snapshot(): List<Bookmark> = bookmarks.toList()

    fun clear() {
        bookmarks.clear()
        bookmarksById.clear()
        nextId = 1L
        bookmarksState.value = emptyList()
        insertCount = 0
        failOnInsertIndex = null
    }

    fun snapshotState(): BookmarkSnapshot = BookmarkSnapshot(
        bookmarks = bookmarks.toList(),
        bookmarksById = bookmarksById.toMap(),
        nextId = nextId
    )

    fun restore(snap: BookmarkSnapshot) {
        bookmarks.clear()
        bookmarks.addAll(snap.bookmarks)
        bookmarksById.clear()
        bookmarksById.putAll(snap.bookmarksById)
        nextId = snap.nextId
        bookmarksState.value = bookmarks.toList()
    }

    /**
     * Test hook: when set to a non-null value, the next call to [insert]
     * with that zero-based index (across the lifetime of this fake) will
     * throw the configured [nextInsertException]. Used to simulate a
     * mid-import crash for the rollback test. Counter resets with [clear].
     */
    var failOnInsertIndex: Int? = null
    var nextInsertException: Throwable = IllegalStateException("simulated mid-import failure")
    private var insertCount = 0

    /** Test hook: reset the insert counter (called automatically by [clear]). */
    private fun resetInsertCount() { insertCount = 0 }

    // --- Methods used by BackupRepository ---

    override suspend fun insert(bookmark: Bookmark): Long {
        val current = insertCount++
        val failAt = failOnInsertIndex
        if (failAt != null && current == failAt) {
            throw nextInsertException
        }
        val newId = nextId++
        val stored = bookmark.copy(id = newId)
        bookmarks.add(stored)
        bookmarksById[newId] = stored
        bookmarksState.value = snapshot()
        return newId
    }

    override suspend fun update(bookmark: Bookmark) {
        val idx = bookmarks.indexOfFirst { it.id == bookmark.id }
        if (idx >= 0) {
            bookmarks[idx] = bookmark
            bookmarksById[bookmark.id] = bookmark
            bookmarksState.value = snapshot()
        }
    }

    override suspend fun delete(bookmark: Bookmark) {
        bookmarks.removeAll { it.id == bookmark.id }
        bookmarksById.remove(bookmark.id)
        bookmarksState.value = snapshot()
    }

    override suspend fun getAllBookmarksOnce(): List<Bookmark> = snapshot()

    override suspend fun getBookmarkById(bookmarkId: Long): Bookmark? = bookmarksById[bookmarkId]

    override suspend fun deleteAll() {
        bookmarks.clear()
        bookmarksById.clear()
        nextId = 1L
        bookmarksState.value = emptyList()
    }

    // --- Methods unused by BackupRepository but required by the interface ---

    override fun getBookmarksInFolder(folderId: Long?): Flow<List<Bookmark>> =
        bookmarksState.map { list ->
            list.filter { it.folderId == folderId && !it.isArchived }
                .sortedByDescending { it.createdAt }
        }

    override fun getBookmarkCountsByFolder(): Flow<List<FolderBookmarkCount>> =
        bookmarksState.map { list ->
            list.filter { it.folderId != null && !it.isArchived }
                .groupBy { it.folderId!! }
                .map { (id, items) -> FolderBookmarkCount(id, items.size) }
        }

    override fun getArchivedBookmarks(): Flow<List<Bookmark>> =
        bookmarksState.map { list -> list.filter { it.isArchived }.sortedByDescending { it.updatedAt } }

    override fun getPinnedBookmarks(): Flow<List<Bookmark>> =
        bookmarksState.map { list ->
            list.filter { it.isPinned && !it.isArchived }.sortedByDescending { it.updatedAt }
        }

    override suspend fun findByExactUrl(url: String): Bookmark? =
        bookmarks.firstOrNull { it.url == url }

    override suspend fun findByUrlContaining(domainFragment: String): List<Bookmark> =
        bookmarks.filter { it.url.contains(domainFragment) && it.folderId != null }

    override suspend fun deleteMultiple(ids: List<Long>) {
        bookmarks.removeAll { it.id in ids }
        ids.forEach { bookmarksById.remove(it) }
        bookmarksState.value = snapshot()
    }

    override suspend fun archiveMultiple(ids: List<Long>, archived: Boolean, timestamp: Long) {
        bookmarks.replaceAll { b -> if (b.id in ids) b.copy(isArchived = archived, updatedAt = timestamp) else b }
        bookmarksState.value = snapshot()
    }

    override suspend fun moveMultipleToFolder(ids: List<Long>, folderId: Long?, timestamp: Long) {
        bookmarks.replaceAll { b -> if (b.id in ids) b.copy(folderId = folderId, updatedAt = timestamp) else b }
        bookmarksState.value = snapshot()
    }
}

data class BookmarkSnapshot(
    val bookmarks: List<Bookmark>,
    val bookmarksById: Map<Long, Bookmark>,
    val nextId: Long
)