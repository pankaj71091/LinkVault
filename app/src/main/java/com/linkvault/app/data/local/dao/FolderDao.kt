package com.linkvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.linkvault.app.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    /**
     * Deleting a folder does NOT delete its contents. The `SET_NULL` foreign
     * keys on Bookmark.folderId and Folder.parentFolderId (see the entity
     * definitions) mean SQLite automatically promotes anything inside this
     * folder to root/unsorted as part of this single delete — no manual
     * cleanup query needed here. This relies on Room having foreign key
     * enforcement turned on, which it does by default.
     */
    @Delete
    suspend fun delete(folder: Folder)

    // Nested folders exist in the schema but aren't exposed in the UI until
    // Phase 4.5, so this only ever returns root-level folders for now.
    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getRootFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentId ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getFoldersByParent(parentId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): Folder?

    // --- Phase 4: backup/restore ---

    /** Every folder, unfiltered — export needs a complete snapshot, not just what's currently browsable. */
    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<Folder>

    /**
     * Used by merge-mode import to avoid creating a second "Recipes" folder
     * when one already exists. Only matches root-level folders, consistent
     * with the rest of the app not having nested-folder UI yet.
     */
    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findRootFolderByName(name: String): Folder?

    /** Used only by replace-mode import, which wipes everything before restoring from the backup file. */
    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}
