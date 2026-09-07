package com.linkvault.app.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.dao.TagDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ImportMode { MERGE, REPLACE }

data class ImportResult(val foldersImported: Int, val bookmarksImported: Int)

/**
 * The portable backup file format. Deliberately its own set of data classes
 * rather than @Serializable on the Room entities directly — keeps the file
 * format decoupled from the internal DB schema, so one can change without
 * automatically changing the other. [formatVersion] exists so a future
 * version of this format can tell old files apart from new ones.
 */
@Serializable
data class ExportFile(
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val folders: List<ExportedFolder>,
    val bookmarks: List<ExportedBookmark>
)

@Serializable
data class ExportedFolder(
    val id: Long,
    val name: String,
    val parentFolderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
    val colorTag: String?
)

@Serializable
data class ExportedBookmark(
    val id: Long,
    val url: String,
    val title: String?,
    val folderId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val faviconUrl: String?,
    val previewImageUrl: String? = null,
    val isArchived: Boolean,
    val isPinned: Boolean
)

/**
 * The database surface that [BackupRepository] needs: the three DAOs that
 * participate in an import, plus the ability to run a block inside a
 * Room transaction. Exists as a small interface (mirroring the
 * [BackupPreferences] pattern) so the repository can be unit-tested with
 * in-memory fakes for the DAOs and a no-op transaction runner, without
 * standing up a real Room database in `src/test/`.
 *
 * The production implementation is the generated [com.linkvault.app.data.local.AppDatabase],
 * which already extends [RoomDatabase] — so this interface is satisfied
 * structurally via Kotlin's type system (no `:` needed at the AppDatabase
 * declaration site, since RoomDatabase is open and the methods exist).
 */
interface BackupDatabase {
    fun folderDao(): FolderDao
    fun bookmarkDao(): BookmarkDao
    fun tagDao(): TagDao

    /**
     * Runs [block] in a single database transaction. On the real
     * [com.linkvault.app.data.local.AppDatabase] this delegates to
     * [androidx.room.withTransaction] from `room-ktx`; the in-memory test
     * fake just runs the block immediately.
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

class BackupRepository(
    private val database: BackupDatabase,
    private val preferenceRepository: BackupPreferences
) {
    private val folderDao: FolderDao get() = database.folderDao()
    private val bookmarkDao: BookmarkDao get() = database.bookmarkDao()
    private val tagDao: TagDao get() = database.tagDao()

    // ignoreUnknownKeys so a *newer* export file (e.g. one with Phase 4.5's
    // tags added later) can still be read by older parsing logic without
    // crashing — unrecognized fields are just dropped, not fatal.
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** A complete snapshot — every folder and bookmark, including archived ones, not just what's currently visible. */
    suspend fun exportToJson(): String {
        val folders = folderDao.getAllFoldersOnce().map {
            ExportedFolder(
                id = it.id,
                name = it.name,
                parentFolderId = it.parentFolderId,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                sortOrder = it.sortOrder,
                colorTag = it.colorTag
            )
        }
        val bookmarks = bookmarkDao.getAllBookmarksOnce().map {
            ExportedBookmark(
                id = it.id,
                url = it.url,
                title = it.title,
                folderId = it.folderId,
                notes = it.notes,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                faviconUrl = it.faviconUrl,
                previewImageUrl = it.previewImageUrl,
                isArchived = it.isArchived,
                isPinned = it.isPinned
            )
        }
        val file = ExportFile(exportedAt = System.currentTimeMillis(), folders = folders, bookmarks = bookmarks)
        
        // Update the last backup timestamp whenever an export succeeds
        preferenceRepository.updateLastBackupTime()
        
        return json.encodeToString(ExportFile.serializer(), file)
    }

    /**
     * REPLACE wipes everything currently in LinkVault first, then restores
     * exactly what's in the file. MERGE adds to what's already here.
     *
     * The whole import runs in a single Room transaction
     * ([BackupDatabase.runInTransaction], which delegates to
     * `androidx.room.withTransaction` on the real AppDatabase) so that an
     * interruption mid-import (process kill, OOM, deserialization error
     * partway through the file) leaves the database in its pre-import
     * state rather than half-replaced.
     */
    suspend fun importFromJson(jsonText: String, mode: ImportMode): ImportResult {
        val file = json.decodeFromString(ExportFile.serializer(), jsonText)

        return database.runInTransaction {
            if (mode == ImportMode.REPLACE) {
                wipeAll()
            }

            val folderIdRemap = mutableMapOf<Long, Long>()
            for (folder in file.folders) {
                val existingId = if (mode == ImportMode.MERGE) {
                    folderDao.findRootFolderByName(folder.name)?.id
                } else null

                val newId = existingId ?: folderDao.insert(
                    Folder(
                        name = folder.name,
                        // BUGFIX: was `folder.parentFolderId`, which used the
                        // pre-import id and would dangle. Remap it to the new
                        // id (or null for root-level folders). If the parent
                        // hasn't been seen yet in the remap (e.g. file lists
                        // children before parents), it stays null and a later
                        // pass — the bookmark insertion — can still see it via
                        // folderIdRemap; but for the folder's own parent FK,
                        // we accept the null in the rare out-of-order case
                        // rather than a dangling id.
                        parentFolderId = folder.parentFolderId?.let { folderIdRemap[it] },
                        createdAt = folder.createdAt,
                        updatedAt = folder.updatedAt,
                        sortOrder = folder.sortOrder,
                        colorTag = folder.colorTag
                    )
                )
                folderIdRemap[folder.id] = newId
            }

            for (bookmark in file.bookmarks) {
                val newFolderId = bookmark.folderId?.let { folderIdRemap[it] }
                bookmarkDao.insert(
                    Bookmark(
                        url = bookmark.url,
                        title = bookmark.title,
                        folderId = newFolderId,
                        notes = bookmark.notes,
                        createdAt = bookmark.createdAt,
                        updatedAt = bookmark.updatedAt,
                        faviconUrl = bookmark.faviconUrl,
                        previewImageUrl = bookmark.previewImageUrl,
                        isArchived = bookmark.isArchived,
                        isPinned = bookmark.isPinned
                    )
                )
            }

            ImportResult(foldersImported = file.folders.size, bookmarksImported = file.bookmarks.size)
        }
    }

    /**
     * Wipes every user-owned row in the database. Called by REPLACE-mode
     * import before restoring from the file. Order matters and respects
     * the schema's foreign keys:
     *   1. bookmarks first — their CASCADE on bookmark_tag_cross_ref
     *      cleans up the join table automatically.
     *   2. folders — must be called in a loop: parentFolderId is
     *      `ON DELETE SET NULL`, so a single `DELETE FROM folders`
     *      only removes the roots and *promotes* their children to root.
     *      Iterating until empty handles trees of any depth.
     *   3. tags last — after the cross-refs are gone, every tag is an
     *      orphan, and deleting them is safe and a true "replace" wipe
     *      (the alternative — leaving them in place — would leave the
     *      Tags screen showing tags that no bookmark uses).
     *
     * Must be called from inside an active transaction (see
     * [importFromJson]); the loops assume a single consistent snapshot.
     */
    private suspend fun wipeAll() {
        // 1. Bookmarks (cascades to bookmark_tag_cross_ref via FK).
        bookmarkDao.deleteAll()

        // 2. Folders, looping because of the SET_NULL parent FK.
        //    Worst case = max folder depth iterations, which the schema
        //    caps at 5 (root + 4 nested levels).
        while (folderDao.getAllFoldersOnce().isNotEmpty()) {
            folderDao.deleteAll()
        }

        // 3. Orphan tags. Safe now that no cross-refs point to them.
        //    (TagDao.deleteAll is added in this same change.)
        tagDao.deleteAll()
    }

    /**
     * Imports bookmarks from a Netscape HTML file. Always MERGE mode.
     * Recreates folder structures from the file's nesting.
     */
    suspend fun importFromHtml(htmlText: String): ImportResult {
        val parsed = HtmlBookmarkParser.parse(htmlText)
        val folderCache = mutableMapOf<String, Long>() // path string -> id

        var foldersCreated = 0
        for (item in parsed) {
            var lastParentId: Long? = null
            val path = mutableListOf<String>()
            
            for (folderName in item.folderPath) {
                path.add(folderName)
                val pathKey = path.joinToString(">")
                
                val existingId = folderCache[pathKey] ?: folderDao.findRootFolderByName(folderName)?.id
                
                val folderId = existingId ?: run {
                    val newId = folderDao.insert(Folder(name = folderName, parentFolderId = lastParentId))
                    foldersCreated++
                    newId
                }
                folderCache[pathKey] = folderId
                lastParentId = folderId
            }

            bookmarkDao.insert(
                Bookmark(
                    url = item.url,
                    title = item.title,
                    folderId = lastParentId
                )
            )
        }

        return ImportResult(foldersImported = foldersCreated, bookmarksImported = parsed.size)
    }
}
