package com.linkvault.app.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.ui.common.BookmarkListItem
import com.linkvault.app.ui.common.ConfirmDeleteBookmarkDialog
import com.linkvault.app.ui.common.ConfirmDeleteFolderDialog
import com.linkvault.app.ui.common.EditNotesDialog
import com.linkvault.app.ui.common.EditTagsDialog
import com.linkvault.app.ui.common.FolderListItem
import com.linkvault.app.ui.common.MoveToFolderDialog
import com.linkvault.app.ui.common.SortOption
import com.linkvault.app.ui.common.TextInputDialog
import com.linkvault.app.ui.common.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    folderNameFallback: String,
    onBack: () -> Unit,
    onFolderClick: (Folder) -> Unit,
    onBreadcrumbClick: (Folder?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val folderPath by viewModel.folderPath.collectAsStateWithLifecycle()
    val childFolders by viewModel.childFolders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val bookmarks by viewModel.visibleBookmarks.collectAsStateWithLifecycle()
    val otherFolders by viewModel.otherFolders.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    
    val selectedIds by viewModel.selectedBookmarkIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    val displayName = folder?.name ?: folderNameFallback

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showCreateSubFolderDialog by remember { mutableStateOf(false) }
    
    var bookmarkPendingMove by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingDelete by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingNotes by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingTags by remember { mutableStateOf<Bookmark?>(null) }

    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    if (isSelectionMode) {
        BackHandler { viewModel.clearSelection() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBulkMoveDialog = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
                        }
                        IconButton(onClick = { showBulkTagDialog = true }) {
                            Icon(Icons.Default.Sell, contentDescription = "Tag")
                        }
                        IconButton(onClick = { viewModel.bulkArchive(true) }) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(text = displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { menuExpanded = false; showRenameDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete folder") },
                                onClick = { menuExpanded = false; showDeleteDialog = true }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = { showCreateSubFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New sub-folder")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search in this folder") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            FolderBreadcrumbs(
                path = folderPath,
                onFolderClick = onBreadcrumbClick
            )

            if (bookmarks.isEmpty() && childFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matches" else "This folder is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (childFolders.isNotEmpty()) {
                        items(childFolders, key = { "folder-${it.id}" }) { subFolder ->
                            FolderListItem(
                                folder = subFolder,
                                bookmarkCount = 0, // Simplified
                                onClick = { onFolderClick(subFolder) },
                                onRenameClick = { /* Handled in FolderDetailScreen of the subfolder */ },
                                onDeleteClick = { /* Handled in FolderDetailScreen of the subfolder */ },
                                onChangeColorClick = { /* Handled in FolderDetailScreen of the subfolder */ }
                            )
                        }
                    }
                    items(bookmarks, key = { it.id }) { bookmark ->
                        val isSelected = bookmark.id in selectedIds
                        
                        // Optimization: Remember all individual callbacks to prevent 
                        // unnecessary recompositions of the item when parent state changes.
                        val onItemClick = remember(bookmark.id, bookmark.url, isSelectionMode) {
                            {
                                if (isSelectionMode) viewModel.toggleBookmarkSelection(bookmark.id)
                                else openUrl(context, bookmark.url)
                            }
                        }
                        val onItemLongClick = remember(bookmark.id, isSelectionMode) {
                            {
                                if (!isSelectionMode) viewModel.toggleBookmarkSelection(bookmark.id)
                            }
                        }
                        val onMove = remember(bookmark) { { bookmarkPendingMove = bookmark } }
                        val onDelete = remember(bookmark) { { bookmarkPendingDelete = bookmark } }
                        val onArchive = remember(bookmark) { { viewModel.toggleArchived(bookmark) } }
                        val onPin = remember(bookmark) { { viewModel.togglePinned(bookmark) } }
                        val onEditTags = remember(bookmark) { { bookmarkPendingTags = bookmark } }
                        val onEditNotes = remember(bookmark) { { bookmarkPendingNotes = bookmark } }

                        BookmarkListItem(
                            bookmark = bookmark,
                            isSelected = isSelected,
                            selectionModeActive = isSelectionMode,
                            onClick = onItemClick,
                            onLongClick = onItemLongClick,
                            onMoveClick = onMove,
                            onDeleteClick = onDelete,
                            onToggleArchiveClick = onArchive,
                            onTogglePinClick = onPin,
                            onEditTagsClick = onEditTags,
                            onEditNotesClick = onEditNotes
                        )
                    }
                }
            }
        }
    }

    if (showCreateSubFolderDialog) {
        TextInputDialog(
            title = "New sub-folder",
            confirmLabel = "Create",
            onConfirm = { name -> viewModel.createSubFolder(name); showCreateSubFolderDialog = false },
            onDismiss = { showCreateSubFolderDialog = false }
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = "Rename folder",
            initialText = displayName,
            confirmLabel = "Rename",
            onConfirm = { name -> viewModel.renameFolder(name); showRenameDialog = false },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteFolderDialog(
            folderName = displayName,
            onConfirm = { viewModel.deleteFolder(); showDeleteDialog = false; onBack() },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    SortOption.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = option == sortOption,
                                onClick = { viewModel.onSortOptionChange(option); showSortDialog = false }
                            )
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) { Text("Close") }
            }
        )
    }

    bookmarkPendingMove?.let { bookmark ->
        MoveToFolderDialog(
            folders = otherFolders,
            onSelect = { folderId -> viewModel.moveBookmark(bookmark, folderId); bookmarkPendingMove = null },
            onDismiss = { bookmarkPendingMove = null }
        )
    }

    bookmarkPendingDelete?.let { bookmark ->
        ConfirmDeleteBookmarkDialog(
            bookmarkLabel = bookmark.title?.takeIf { it.isNotBlank() } ?: bookmark.url,
            onConfirm = { viewModel.deleteBookmark(bookmark); bookmarkPendingDelete = null },
            onDismiss = { bookmarkPendingDelete = null }
        )
    }

    bookmarkPendingNotes?.let { bookmark ->
        EditNotesDialog(
            initialNotes = bookmark.notes,
            onConfirm = { notes -> viewModel.updateNotes(bookmark, notes); bookmarkPendingNotes = null },
            onDismiss = { bookmarkPendingNotes = null }
        )
    }

    bookmarkPendingTags?.let { bookmark ->
        val currentTags by viewModel.tagsForBookmark(bookmark.id).collectAsStateWithLifecycle(initialValue = emptyList())
        EditTagsDialog(
            currentTags = currentTags,
            allTags = allTags,
            onAddTag = { name -> viewModel.addTag(bookmark.id, name) },
            onRemoveTag = { tag -> viewModel.removeTag(bookmark.id, tag.id) },
            onDismiss = { bookmarkPendingTags = null }
        )
    }

    if (showBulkMoveDialog) {
        MoveToFolderDialog(
            folders = otherFolders,
            onSelect = { folderId -> viewModel.bulkMove(folderId); showBulkMoveDialog = false },
            onDismiss = { showBulkMoveDialog = false }
        )
    }

    if (showBulkTagDialog) {
        TextInputDialog(
            title = "Tag selected links",
            confirmLabel = "Add Tag",
            onConfirm = { tagName -> viewModel.bulkAddTag(tagName); showBulkTagDialog = false },
            onDismiss = { showBulkTagDialog = false }
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete selected?") },
            text = { Text("This will permanently delete ${selectedIds.size} bookmarks.") },
            confirmButton = {
                TextButton(onClick = { viewModel.bulkDelete(); showBulkDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FolderBreadcrumbs(
    path: List<Folder>,
    onFolderClick: (Folder?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text(
                text = "Home",
                style = MaterialTheme.typography.bodyLarge,
                color = if (path.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (path.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onFolderClick(null) }
            )
        }
        itemsIndexed(path) { index, folder ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(12.dp).padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (index == path.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (index == path.size - 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onFolderClick(folder) }
            )
        }
    }
}
