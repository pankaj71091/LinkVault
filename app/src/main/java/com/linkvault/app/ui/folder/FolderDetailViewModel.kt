package com.linkvault.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.local.entity.Tag
import com.linkvault.app.data.repository.BookmarkRepository
import com.linkvault.app.data.repository.FolderRepository
import com.linkvault.app.data.repository.TagRepository
import com.linkvault.app.ui.common.SortOption
import com.linkvault.app.ui.common.sortedByOption
import com.linkvault.app.ui.common.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FolderDetailViewModel(
    private val folderId: Long,
    private val folderRepository: FolderRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val tagRepository: TagRepository
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

    val folder: StateFlow<Folder?> = folderRepository.getRootFolders()
        .map { folders -> 
            // Also search in all folders if root doesn't contain it (naive but works for now)
            folders.find { it.id == folderId } ?: folderRepository.getFolderById(folderId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folderPath: StateFlow<List<Folder>> = folder.flatMapLatest { current ->
        flow {
            val path = mutableListOf<Folder>()
            var f = current
            while (f != null) {
                path.add(0, f)
                val parentId = f.parentFolderId
                f = if (parentId != null) folderRepository.getFolderById(parentId) else null
            }
            emit(path)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val childFolders: StateFlow<List<Folder>> = combine(folderRepository.getFoldersByParent(folderId), _searchQuery) { folders, query ->
        if (query.isBlank()) folders
        else folders.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** For the "move to folder" picker — every folder except this one. */
    val otherFolders: StateFlow<List<Folder>> = folderRepository.getRootFolders()
        .map { folders -> folders.filter { it.id != folderId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allBookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getBookmarksInFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleBookmarks: StateFlow<List<Bookmark>> = combine(allBookmarks, _searchQuery, _sortOption) { bookmarks, query, sort ->
        val filtered = if (query.isBlank()) {
            bookmarks
        } else {
            bookmarks.filter {
                it.title?.contains(query, ignoreCase = true) == true ||
                    it.url.contains(query, ignoreCase = true) ||
                    it.notes?.contains(query, ignoreCase = true) == true
            }
        }
        filtered.sortedByOption(sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChange(option: SortOption) {
        _sortOption.value = option
    }

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

    fun createSubFolder(name: String) {
        viewModelScope.launch { folderRepository.createFolder(name, folderId) }
    }

    fun renameFolder(newName: String) {
        val trimmed = newName.trim()
        val current = folder.value ?: return
        if (trimmed.isEmpty()) return
        viewModelScope.launch { folderRepository.renameFolder(current, trimmed) }
    }

    fun deleteFolder() {
        val current = folder.value ?: return
        viewModelScope.launch { folderRepository.deleteFolder(current) }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(bookmark) }
    }

    fun moveBookmark(bookmark: Bookmark, targetFolderId: Long?) {
        viewModelScope.launch { bookmarkRepository.moveToFolder(bookmark, targetFolderId) }
    }

    fun bulkDelete() {
        val ids = _selectedBookmarkIds.value.toList()
        viewModelScope.launch {
            bookmarkRepository.bulkDelete(ids)
            clearSelection()
        }
    }

    fun bulkArchive(archived: Boolean) {
        val ids = _selectedBookmarkIds.value.toList()
        viewModelScope.launch {
            bookmarkRepository.bulkArchive(ids, archived)
            clearSelection()
        }
    }

    fun bulkMove(targetFolderId: Long?) {
        val ids = _selectedBookmarkIds.value.toList()
        viewModelScope.launch {
            bookmarkRepository.bulkMoveToFolder(ids, targetFolderId)
            clearSelection()
        }
    }

    fun bulkAddTag(tagName: String) {
        val ids = _selectedBookmarkIds.value.toList()
        viewModelScope.launch {
            tagRepository.bulkAddTagToBookmarks(ids, tagName)
            clearSelection()
        }
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

    fun addTag(bookmarkId: Long, tagName: String) {
        viewModelScope.launch { tagRepository.addTagToBookmark(bookmarkId, tagName) }
    }

    fun removeTag(bookmarkId: Long, tagId: Long) {
        viewModelScope.launch { tagRepository.removeTagFromBookmark(bookmarkId, tagId) }
    }

    fun tagsForBookmark(bookmarkId: Long) = tagRepository.getTagsForBookmark(bookmarkId)

    companion object {
        fun factory(application: LinkVaultApplication, folderId: Long): ViewModelProvider.Factory = viewModelFactory {
            FolderDetailViewModel(
                folderId,
                application.folderRepository,
                application.bookmarkRepository,
                application.tagRepository
            )
        }
    }
}
