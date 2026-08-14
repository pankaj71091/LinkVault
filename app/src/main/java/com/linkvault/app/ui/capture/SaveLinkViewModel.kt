package com.linkvault.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.repository.BookmarkRepository
import com.linkvault.app.data.repository.FolderRepository
import com.linkvault.app.data.work.MetadataFetchScheduler
import com.linkvault.app.ui.common.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SaveLinkViewModel(
    initialUrl: String,
    private val initialTitle: String?,
    private val folderRepository: FolderRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val metadataFetchScheduler: MetadataFetchScheduler
) : ViewModel() {

    private val _url = MutableStateFlow(initialUrl)
    val url: StateFlow<String> = _url.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    /** Tracks the navigation hierarchy for browsing folders. Empty = root. */
    private val _navigationPath = MutableStateFlow<List<Folder>>(emptyList())
    val navigationPath: StateFlow<List<Folder>> = _navigationPath.asStateFlow()

    /** Non-null when the current URL exactly matches something already saved — a warning, not a block. */
    private val _duplicateOf = MutableStateFlow<Bookmark?>(null)
    val duplicateOf: StateFlow<Bookmark?> = _duplicateOf.asStateFlow()

    /** Folders available at the current navigation level. */
    val folders: StateFlow<List<Folder>> = _navigationPath.flatMapLatest { path ->
        val currentParentId = path.lastOrNull()?.id
        if (currentParentId == null) {
            folderRepository.getRootFolders()
        } else {
            folderRepository.getFoldersByParent(currentParentId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All root folders, used to resolve suggested folder names if they aren't in view. */
    val rootFolders: StateFlow<List<Folder>> = folderRepository.getRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        checkForDuplicate(initialUrl)
        // Runs once, from the original captured URL only — re-suggesting on
        // every keystroke while the user edits the field would make the
        // selected folder flicker unpredictably underneath them.
        suggestFolder(initialUrl)
    }

    fun onUrlChange(newUrl: String) {
        _url.value = newUrl
        checkForDuplicate(newUrl)
    }

    private fun checkForDuplicate(url: String) {
        viewModelScope.launch {
            _duplicateOf.value = bookmarkRepository.findExistingBookmark(url.trim())
        }
    }

    private fun suggestFolder(url: String) {
        viewModelScope.launch {
            val suggestedFolderId = bookmarkRepository.suggestFolderForUrl(url.trim())
            if (suggestedFolderId != null && _selectedFolderId.value == null) {
                _selectedFolderId.value = suggestedFolderId
            }
        }
    }

    fun onFolderSelected(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun navigateIn(folder: Folder) {
        _navigationPath.value = _navigationPath.value + folder
        // Auto-select when navigating into a folder, similar to how PC
        // browsers assume you want to save in the folder you're browsing.
        _selectedFolderId.value = folder.id
    }

    fun navigateOut() {
        val current = _navigationPath.value
        if (current.isNotEmpty()) {
            val next = current.dropLast(1)
            _navigationPath.value = next
            _selectedFolderId.value = next.lastOrNull()?.id
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        if (index == -1) {
            _navigationPath.value = emptyList()
            _selectedFolderId.value = null
        } else {
            val next = _navigationPath.value.take(index + 1)
            _navigationPath.value = next
            _selectedFolderId.value = next.lastOrNull()?.id
        }
    }

    fun createAndSelectFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val parentId = _navigationPath.value.lastOrNull()?.id
            val newFolderId = folderRepository.createFolder(trimmed, parentId)
            if (newFolderId != -1L) {
                _selectedFolderId.value = newFolderId
            }
        }
    }

    /**
     * Suspends until the insert actually completes — deliberately not
     * fire-and-forget. The caller (LinkCaptureActivity) awaits this before
     * calling finish(); if it didn't, the Activity could finish and clear
     * this ViewModel's viewModelScope before the write lands, silently
     * dropping the share.
     */
    suspend fun save(): Boolean {
        val trimmedUrl = _url.value.trim()
        if (trimmedUrl.isBlank()) return false
        val bookmarkId = bookmarkRepository.saveNewBookmark(
            url = trimmedUrl,
            title = initialTitle,
            folderId = _selectedFolderId.value
        )
        // Scheduled regardless of whether initialTitle is already set — the
        // favicon still needs fetching either way, and applyFetchedMetadata
        // already knows not to overwrite an existing title.
        metadataFetchScheduler.scheduleFetch(bookmarkId)
        return true
    }

    companion object {
        fun factory(
            application: LinkVaultApplication,
            initialUrl: String,
            initialTitle: String?
        ): ViewModelProvider.Factory = viewModelFactory {
            SaveLinkViewModel(
                initialUrl,
                initialTitle,
                application.folderRepository,
                application.bookmarkRepository,
                application.metadataFetchScheduler
            )
        }
    }
}
