package com.linkvault.app.data.repository

import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
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

class BackupRepository(
    private val folderDao: FolderDao,
    private val bookmarkDao: BookmarkDao,
    private val preferenceRepository: PreferenceRepository
) {
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
     */
    suspend fun importFromJson(jsonText: String, mode: ImportMode): ImportResult {
        val file = json.decodeFromString(ExportFile.serializer(), jsonText)

        if (mode == ImportMode.REPLACE) {
            bookmarkDao.deleteAll()
            folderDao.deleteAll()
        }

        val folderIdRemap = mutableMapOf<Long, Long>()
        for (folder in file.folders) {
            val existingId = if (mode == ImportMode.MERGE) {
                folderDao.findRootFolderByName(folder.name)?.id
            } else null

            val newId = existingId ?: folderDao.insert(
                Folder(
                    name = folder.name,
                    parentFolderId = folder.parentFolderId, // Now supports nesting
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

        return ImportResult(foldersImported = file.folders.size, bookmarksImported = file.bookmarks.size)
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
