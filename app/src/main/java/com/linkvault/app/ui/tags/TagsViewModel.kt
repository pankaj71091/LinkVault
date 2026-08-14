package com.linkvault.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Tag
import com.linkvault.app.data.repository.BookmarkRepository
import com.linkvault.app.data.repository.TagRepository
import com.linkvault.app.ui.common.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(
    private val tagRepository: TagRepository,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    val allTags: StateFlow<List<Tag>> = tagRepository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagCounts: StateFlow<Map<Long, Int>> = tagRepository.getTagCounts()
        .map { counts -> counts.associate { it.tagId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()

    val selectedTag: StateFlow<Tag?> = combine(allTags, _selectedTagId) { tags, id ->
        tags.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bookmarksForSelectedTag: StateFlow<List<Bookmark>> = _selectedTagId
        .flatMapLatest { tagId ->
            if (tagId == null) kotlinx.coroutines.flow.flowOf(emptyList()) else tagRepository.getBookmarksForTag(tagId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTag(tagId: Long?) {
        _selectedTagId.value = tagId
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            if (_selectedTagId.value == tag.id) _selectedTagId.value = null
            tagRepository.deleteTag(tag)
        }
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

    fun addTag(bookmarkId: Long, tagName: String) {
        viewModelScope.launch { tagRepository.addTagToBookmark(bookmarkId, tagName) }
    }

    fun removeTag(bookmarkId: Long, tagId: Long) {
        viewModelScope.launch { tagRepository.removeTagFromBookmark(bookmarkId, tagId) }
    }

    fun tagsForBookmark(bookmarkId: Long) = tagRepository.getTagsForBookmark(bookmarkId)

    companion object {
        fun factory(application: LinkVaultApplication): ViewModelProvider.Factory = viewModelFactory {
            TagsViewModel(application.tagRepository, application.bookmarkRepository)
        }
    }
}
