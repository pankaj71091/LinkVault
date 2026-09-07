package com.linkvault.app.testutil

import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [FolderDao] for unit tests. Implements only the methods
 * [com.linkvault.app.data.repository.BackupRepository] uses; the rest
 * throw to surface accidental test-scope creep into unrelated code paths.
 *
 * Mimics Room's `parentFolderId IS NULL` and `COLLATE NOCASE` behaviors
 * for the queries BackupRepository depends on.
 */
class FakeFolderDao : FolderDao {

    private val foldersState = MutableStateFlow<List<Folder>>(emptyList())
    private val folders: MutableList<Folder> = mutableListOf()
    private val foldersById: MutableMap<Long, Folder> = mutableMapOf()
    private var nextId: Long = 1L

    private fun snapshot(): List<Folder> = folders.toList()

    fun clear() {
        folders.clear()
        foldersById.clear()
        nextId = 1L
        foldersState.value = emptyList()
    }

    /** Returns a value-typed snapshot the [FakeBackupDatabase] can restore
     *  on a rolled-back transaction. */
    fun snapshotState(): FolderSnapshot = FolderSnapshot(
        folders = folders.toList(),
        foldersById = foldersById.toMap(),
        nextId = nextId
    )

    fun restore(snap: FolderSnapshot) {
        folders.clear()
        folders.addAll(snap.folders)
        foldersById.clear()
        foldersById.putAll(snap.foldersById)
        nextId = snap.nextId
        foldersState.value = folders.toList()
    }

    // --- Methods used by BackupRepository ---

    override suspend fun insert(folder: Folder): Long {
        val newId = nextId++
        val stored = folder.copy(id = newId)
        folders.add(stored)
        foldersById[newId] = stored
        foldersState.value = snapshot()
        return newId
    }

    override suspend fun update(folder: Folder) {
        val idx = folders.indexOfFirst { it.id == folder.id }
        if (idx >= 0) {
            folders[idx] = folder
            foldersById[folder.id] = folder
            foldersState.value = snapshot()
        }
    }

    override suspend fun delete(folder: Folder) {
        // Simulate Room's SET_NULL foreign keys on Folder.parentFolderId:
        // children are promoted to root (parentFolderId = null).
        folders.replaceAll { f ->
            if (f.parentFolderId == folder.id) f.copy(parentFolderId = null) else f
        }
        folders.removeAll { it.id == folder.id }
        foldersById.remove(folder.id)
        foldersState.value = snapshot()
    }

    override suspend fun getAllFoldersOnce(): List<Folder> = snapshot()

    override suspend fun getFolderById(folderId: Long): Folder? = foldersById[folderId]

    override suspend fun findRootFolderByName(name: String): Folder? =
        folders.firstOrNull { it.parentFolderId == null && it.name.equals(name, ignoreCase = true) }

    override suspend fun deleteAll() {
        folders.clear()
        foldersById.clear()
        nextId = 1L
        foldersState.value = emptyList()
    }

    // --- Methods unused by BackupRepository but required by the interface ---

    override fun getRootFolders(): Flow<List<Folder>> =
        foldersState.map { list -> list.filter { it.parentFolderId == null } }

    override fun getFoldersByParent(parentId: Long): Flow<List<Folder>> =
        foldersState.map { list -> list.filter { it.parentFolderId == parentId } }
}

data class FolderSnapshot(
    val folders: List<Folder>,
    val foldersById: Map<Long, Folder>,
    val nextId: Long
)