package com.linkvault.app.ui.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.repository.BookmarkRepository
import com.linkvault.app.data.repository.TagRepository
import com.linkvault.app.ui.common.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchivedViewModel(
    private val bookmarkRepository: BookmarkRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    val archivedBookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getArchivedBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(bookmark) }
    }

    fun moveBookmark(bookmark: Bookmark, targetFolderId: Long?) {
        viewModelScope.launch { bookmarkRepository.moveToFolder(bookmark, targetFolderId) }
    }

    /** The only action that matters here is unarchiving — toggleArchived always un-archives from this screen. */
    fun unarchive(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkRepository.setArchived(bookmark, archived = false) }
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
            ArchivedViewModel(application.bookmarkRepository, application.tagRepository)
        }
    }
}
