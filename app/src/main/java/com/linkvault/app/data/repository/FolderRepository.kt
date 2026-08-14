package com.linkvault.app.data.repository

import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val folderDao: FolderDao) {

    fun getRootFolders(): Flow<List<Folder>> = folderDao.getRootFolders()

    fun getFoldersByParent(parentId: Long): Flow<List<Folder>> = folderDao.getFoldersByParent(parentId)

    suspend fun getFolderById(folderId: Long): Folder? = folderDao.getFolderById(folderId)

    /**
     * Creates a new folder. If [parentId] is provided, checks that the
     * resulting depth doesn't exceed the 5-level limit (root = level 0).
     * Returns the new folder's ID, or -1 if the depth limit was hit.
     */
    suspend fun createFolder(name: String, parentId: Long? = null): Long {
        if (parentId != null) {
            val parentDepth = getFolderDepth(parentId)
            if (parentDepth >= 4) return -1L // Max depth (0 to 4 = 5 levels)
        }
        return folderDao.insert(Folder(name = name, parentFolderId = parentId))
    }

    private suspend fun getFolderDepth(folderId: Long): Int {
        var currentId: Long? = folderId
        var depth = 0
        while (currentId != null && depth < 10) { // Safety break
            val folder = folderDao.getFolderById(currentId)
            currentId = folder?.parentFolderId
            if (currentId != null) depth++
        }
        return depth
    }

    suspend fun renameFolder(folder: Folder, newName: String) =
        folderDao.update(folder.copy(name = newName, updatedAt = System.currentTimeMillis()))

    suspend fun setColor(folder: Folder, colorHex: String?) =
        folderDao.update(folder.copy(colorTag = colorHex, updatedAt = System.currentTimeMillis()))

    suspend fun deleteFolder(folder: Folder) = folderDao.delete(folder)
}
