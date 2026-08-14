package com.linkvault.app.data.repository

import android.net.Uri
import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderBookmarkCount
import com.linkvault.app.data.local.entity.Bookmark
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    /** [folderId] == null returns unsorted (root-level) bookmarks. */
    fun getBookmarksInFolder(folderId: Long?): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksInFolder(folderId)

    suspend fun getBookmarkById(bookmarkId: Long): Bookmark? = bookmarkDao.getBookmarkById(bookmarkId)

    fun getBookmarkCountsByFolder(): Flow<List<FolderBookmarkCount>> =
        bookmarkDao.getBookmarkCountsByFolder()

    fun getArchivedBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getArchivedBookmarks()

    fun getPinnedBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getPinnedBookmarks()

    suspend fun moveToFolder(bookmark: Bookmark, folderId: Long?) =
        bookmarkDao.update(bookmark.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    /** Archiving a bookmark hides it from folder/unsorted/pinned views (all filter isArchived=0) without deleting it. */
    suspend fun setArchived(bookmark: Bookmark, archived: Boolean) =
        bookmarkDao.update(bookmark.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))

    suspend fun setPinned(bookmark: Bookmark, pinned: Boolean) =
        bookmarkDao.update(bookmark.copy(isPinned = pinned, updatedAt = System.currentTimeMillis()))

    suspend fun updateNotes(bookmark: Bookmark, notes: String?) =
        bookmarkDao.update(bookmark.copy(notes = notes?.trim()?.takeIf { it.isNotBlank() }, updatedAt = System.currentTimeMillis()))

    /** Used by the Phase 1 capture flow — the only place a brand-new Bookmark gets created. */
    suspend fun saveNewBookmark(url: String, title: String?, folderId: Long?): Long =
        bookmarkDao.insert(Bookmark(url = url, title = title, folderId = folderId))

    /** Exact match only — a warning, not a hard block, so this stays a simple yes/no rather than fuzzy matching. */
    suspend fun findExistingBookmark(url: String): Bookmark? = bookmarkDao.findByExactUrl(url)

    /**
     * Looks at previously-saved bookmarks from the same domain and suggests
     * whichever folder they most often ended up in — e.g. always filing
     * youtube.com links into "Watch Later". Returns null when there's no
     * domain to extract or no prior bookmarks from it to learn from (in
     * which case the capture screen just falls back to its normal default
     * of Unsorted, nothing breaks).
     */
    suspend fun suggestFolderForUrl(url: String): Long? {
        val domain = extractDomain(url) ?: return null
        val matches = bookmarkDao.findByUrlContaining(domain)
        return matches
            .groupBy { it.folderId }
            .maxByOrNull { it.value.size }
            ?.key
    }

    private fun extractDomain(url: String): String? {
        val host = try {
            Uri.parse(url).host
        } catch (e: Exception) {
            null
        }
        return host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }

    /**
     * Applied by MetadataFetchWorker once a background fetch finds a title
     * and/or favicon for a bookmark. [fetchedTitle] only fills in a
     * currently-blank title — it never overwrites one the user already had
     * (e.g. from a share's EXTRA_SUBJECT). [fetchedFaviconUrl] and
     * [fetchedPreviewImageUrl] apply if non-null.
     */
    suspend fun applyFetchedMetadata(
        bookmarkId: Long,
        fetchedTitle: String?,
        fetchedFaviconUrl: String?,
        fetchedPreviewImageUrl: String? = null
    ) {
        val current = bookmarkDao.getBookmarkById(bookmarkId) ?: return
        val newTitle = current.title?.takeIf { it.isNotBlank() } ?: fetchedTitle
        val newFavicon = fetchedFaviconUrl ?: current.faviconUrl
        val newPreview = fetchedPreviewImageUrl ?: current.previewImageUrl

        if (newTitle == current.title &&
            newFavicon == current.faviconUrl &&
            newPreview == current.previewImageUrl
        ) return

        bookmarkDao.update(
            current.copy(
                title = newTitle,
                faviconUrl = newFavicon,
                previewImageUrl = newPreview,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // --- Phase 5 Stage 1: Bulk Actions ---

    suspend fun bulkDelete(ids: List<Long>) = bookmarkDao.deleteMultiple(ids)

    suspend fun bulkArchive(ids: List<Long>, archived: Boolean) =
        bookmarkDao.archiveMultiple(ids, archived, System.currentTimeMillis())

    suspend fun bulkMoveToFolder(ids: List<Long>, folderId: Long?) =
        bookmarkDao.moveMultipleToFolder(ids, folderId, System.currentTimeMillis())
}
