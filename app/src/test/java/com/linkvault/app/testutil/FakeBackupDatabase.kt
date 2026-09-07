package com.linkvault.app.testutil

import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.dao.TagDao
import com.linkvault.app.data.repository.BackupDatabase

/**
 * In-memory [BackupDatabase] for unit tests. Bundles the three fake DAOs
 * and provides a real (in-memory) transaction simulator:
 *
 *  - [runInTransaction] snapshots every DAO's state, runs the block, and
 *    on any thrown exception restores the snapshots. This lets us test
 *    the "mid-import crash leaves the DB untouched" guarantee that
 *    [com.linkvault.app.data.repository.BackupRepository.importFromJson]
 *    relies on.
 *
 *  - Successful blocks are committed (no restore), matching the
 *    production `withTransaction` semantics.
 */
class FakeBackupDatabase(
    val folders: FakeFolderDao = FakeFolderDao(),
    val bookmarks: FakeBookmarkDao = FakeBookmarkDao(),
    val tags: FakeTagDao = FakeTagDao()
) : BackupDatabase {

    override fun folderDao(): FolderDao = folders
    override fun bookmarkDao(): BookmarkDao = bookmarks
    override fun tagDao(): TagDao = tags

    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        val folderSnap = folders.snapshotState()
        val bookmarkSnap = bookmarks.snapshotState()
        val tagSnap = tags.snapshotState()
        return try {
            block()
        } catch (t: Throwable) {
            // Roll back. We restore in the same order the original
            // wipeAll runs (bookmarks → folders → tags) so re-entry
            // behaves consistently.
            bookmarks.restore(bookmarkSnap)
            folders.restore(folderSnap)
            tags.restore(tagSnap)
            throw t
        }
    }

    fun clear() {
        folders.clear()
        bookmarks.clear()
        tags.clear()
    }
}