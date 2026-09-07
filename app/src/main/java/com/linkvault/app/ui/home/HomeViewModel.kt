package com.linkvault.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.local.entity.Tag
import com.linkvault.app.data.repository.BackupRepository
import com.linkvault.app.data.repository.BookmarkRepository
import com.linkvault.app.data.repository.FolderRepository
import com.linkvault.app.data.repository.ImportMode
import com.linkvault.app.data.repository.ImportResult
import com.linkvault.app.data.repository.PreferenceRepository
import com.linkvault.app.data.repository.TagRepository
import com.linkvault.app.ui.common.SortOption
import com.linkvault.app.ui.common.sortedByOption
import com.linkvault.app.ui.common.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val folderRepository: FolderRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val backupRepository: BackupRepository,
    private val tagRepository: TagRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NEWEST_FIRST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedBookmarkIds = MutableStateFlow(setOf<Long>())
    val selectedBookmarkIds: StateFlow<Set<Long>> = _selectedBookmarkIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedBookmarkIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _showBackupReminder = MutableStateFlow(false)
    val showBackupReminder: StateFlow<Boolean> = _showBackupReminder.asStateFlow()

    init {
        checkBackupReminder()
    }

    private fun checkBackupReminder() {
        // Since the DataStore migration (Task 5), `getLastBackupTime()`
        // is a suspend function — launching in viewModelScope rather than
        // calling it synchronously from init also avoids the SharedPreferences
        // first-read main-thread stall that this method used to do.
        viewModelScope.launch {
            val lastBackup = preferenceRepository.getLastBackupTime()
            // Remind every 7 days if no backup was made
            val threshold = 7L * 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - lastBackup > threshold) {
                _showBackupReminder.value = true
            }
        }
    }

    fun dismissBackupReminder() {
        _showBackupReminder.value = false
    }

    private val allFolders: StateFlow<List<Folder>> = folderRepository.getRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allUnsortedBookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getBookmarksInFolder(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allPinnedBookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getPinnedBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unfiltered — used for "move to folder" pickers, independent of the active search. */
    val foldersForPicker: StateFlow<List<Folder>> = allFolders

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagCounts: StateFlow<Map<Long, Int>> = tagRepository.getTagCounts()
        .map { counts -> counts.associate { it.tagId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val folderBookmarkCounts: StateFlow<Map<Long, Int>> = bookmarkRepository.getBookmarkCountsByFolder()
        .map { counts -> counts.associate { it.folderId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val visibleFolders: StateFlow<List<Folder>> = combine(allFolders, _searchQuery) { folders, query ->
        if (query.isBlank()) folders
        else folders.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visiblePinnedBookmarks: StateFlow<List<Bookmark>> = combine(allPinnedBookmarks, _searchQuery, _sortOption) { bookmarks, query, sort ->
        matchingBookmarks(bookmarks, query).sortedByOption(sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleUnsortedBookmarks: StateFlow<List<Bookmark>> = combine(allUnsortedBookmarks, _searchQuery, _sortOption) { bookmarks, query, sort ->
        matchingBookmarks(bookmarks, query).sortedByOption(sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matchingBookmarks(bookmarks: List<Bookmark>, query: String): List<Bookmark> {
        if (query.isBlank()) return bookmarks
        return bookmarks.filter {
            it.title?.contains(query, ignoreCase = true) == true ||
                it.url.contains(query, ignoreCase = true) ||
                it.notes?.contains(query, ignoreCase = true) == true
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChange(option: SortOption) {
        _sortOption.value = option
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { folderRepository.createFolder(trimmed) }
    }

    fun renameFolder(folder: Folder, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { folderRepository.renameFolder(folder, trimmed) }
    }

    /** Contents of [folder] are not deleted — they become unsorted. See FolderDao.delete. */
    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { folderRepository.deleteFolder(folder) }
    }

    fun setFolderColor(folder: Folder, colorHex: String?) {
        viewModelScope.launch { folderRepository.setColor(folder, colorHex) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(bookmark) }
    }

    fun moveBookmark(bookmark: Bookmark, targetFolderId: Long?) {
        viewModelScope.launch { bookmarkRepository.moveToFolder(bookmark, targetFolderId) }
    }

    fun toggleArchived(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.setArchived(bookmark, archived = !bookmark.isArchived) }
    }

    fun togglePinned(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.setPinned(bookmark, pinned = !bookmark.isPinned) }
    }

    fun updateNotes(bookmark: Bookmark, notes: String) {
        viewModelScope.launch { bookmarkRepository.updateNotes(bookmark, notes) }
    }

    // --- Phase 5 Stage 1: Selection & Bulk Actions ---

    fun toggleBookmarkSelection(bookmarkId: Long) {
        val current = _selectedBookmarkIds.value
        _selectedBookmarkIds.value = if (bookmarkId in current) {
            current - bookmarkId
        } else {
            current + bookmarkId
        }
    }

    fun clearSelection() {
        _selectedBookmarkIds.value = emptySet()
    }

    fun bulkDelete() {
        val ids = _selectedBookmarkIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookmarkRepository.bulkDelete(ids)
            clearSelection()
        }
    }

    fun bulkArchive(archived: Boolean) {
        val ids = _selectedBookmarkIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookmarkRepository.bulkArchive(ids, archived)
            clearSelection()
        }
    }

    fun bulkMove(targetFolderId: Long?) {
        val ids = _selectedBookmarkIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookmarkRepository.bulkMoveToFolder(ids, targetFolderId)
            clearSelection()
        }
    }

    fun bulkAddTag(tagName: String) {
        val ids = _selectedBookmarkIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            tagRepository.bulkAddTagToBookmarks(ids, tagName)
            clearSelection()
        }
    }

    fun addTag(bookmarkId: Long, tagName: String) {
        viewModelScope.launch { tagRepository.addTagToBookmark(bookmarkId, tagName) }
    }

    fun removeTag(bookmarkId: Long, tagId: Long) {
        viewModelScope.launch { tagRepository.removeTagFromBookmark(bookmarkId, tagId) }
    }

    fun tagsForBookmark(bookmarkId: Long) = tagRepository.getTagsForBookmark(bookmarkId)

    suspend fun exportData(): String = backupRepository.exportToJson()

    suspend fun importData(jsonText: String, mode: ImportMode): ImportResult =
        backupRepository.importFromJson(jsonText, mode)

    suspend fun importHtml(htmlText: String): ImportResult =
        backupRepository.importFromHtml(htmlText)

    companion object {
        fun factory(application: LinkVaultApplication): ViewModelProvider.Factory = viewModelFactory {
            HomeViewModel(
                application.folderRepository,
                application.bookmarkRepository,
                application.backupRepository,
                application.tagRepository,
                application.preferenceRepository
            )
        }
    }
}
