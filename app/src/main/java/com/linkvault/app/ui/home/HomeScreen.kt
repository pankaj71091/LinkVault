package com.linkvault.app.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.repository.ImportMode
import com.linkvault.app.ui.common.BookmarkListItem
import com.linkvault.app.ui.common.ConfirmDeleteBookmarkDialog
import com.linkvault.app.ui.common.ConfirmDeleteFolderDialog
import com.linkvault.app.ui.common.EditNotesDialog
import com.linkvault.app.ui.common.EditTagsDialog
import com.linkvault.app.ui.common.FolderColorDialog
import com.linkvault.app.ui.common.FolderListItem
import com.linkvault.app.ui.common.MoveToFolderDialog
import com.linkvault.app.ui.common.SortOption
import com.linkvault.app.ui.common.TextInputDialog
import com.linkvault.app.ui.common.openUrl
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onFolderClick: (Folder) -> Unit,
    onViewArchived: () -> Unit,
    onViewTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val folders by viewModel.visibleFolders.collectAsStateWithLifecycle()
    val unsortedBookmarks by viewModel.visibleUnsortedBookmarks.collectAsStateWithLifecycle()
    val pinnedBookmarks by viewModel.visiblePinnedBookmarks.collectAsStateWithLifecycle()
    val folderCounts by viewModel.folderBookmarkCounts.collectAsStateWithLifecycle()
    val foldersForPicker by viewModel.foldersForPicker.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()

    val selectedIds by viewModel.selectedBookmarkIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val showBackupReminder by viewModel.showBackupReminder.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var folderPendingRename by remember { mutableStateOf<Folder?>(null) }
    var folderPendingDelete by remember { mutableStateOf<Folder?>(null) }
    var folderPendingColor by remember { mutableStateOf<Folder?>(null) }
    var bookmarkPendingMove by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingDelete by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingNotes by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingTags by remember { mutableStateOf<Bookmark?>(null) }

    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showImportModeDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val jsonText = viewModel.exportData()
                context.contentResolver.openOutputStream(uri)?.use { it.write(jsonText.toByteArray()) }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: ""

                        if (fileName.endsWith(".html", ignoreCase = true)) {
                            val result = viewModel.importHtml(content)
                            Toast.makeText(context, "Imported ${result.bookmarksImported} links from HTML", Toast.LENGTH_SHORT).show()
                        } else {
                            // JSON requires mode selection, for simplicity assume MERGE if coming from here
                            // or trigger a new dialog.
                            val result = viewModel.importData(content, ImportMode.MERGE)
                            Toast.makeText(context, "Imported ${result.bookmarksImported} links from JSON", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                    title = { Text("LinkVault") },
                    actions = {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Tags") },
                                leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
                                onClick = { menuExpanded = false; onViewTags() }
                            )
                            DropdownMenuItem(
                                text = { Text("Archived") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = { menuExpanded = false; onViewArchived() }
                            )
                            DropdownMenuItem(
                                text = { Text("Export backup") },
                                onClick = { menuExpanded = false; exportLauncher.launch(defaultExportFilename()) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import backup") },
                                onClick = { menuExpanded = false; importLauncher.launch(arrayOf("application/json", "text/html")) }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
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
                placeholder = { Text("Search") },
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

            if (folders.isEmpty() && unsortedBookmarks.isEmpty() && pinnedBookmarks.isEmpty()) {
                EmptyState(hasActiveSearch = searchQuery.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pinnedBookmarks.isNotEmpty()) {
                        item(key = "pinned-header") { SectionHeader("Pinned") }
                        items(pinnedBookmarks, key = { "pinned-${it.id}" }) { bookmark ->
                            val isSelected = bookmark.id in selectedIds
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
                    if (folders.isNotEmpty()) {
                        item(key = "folders-header") { SectionHeader("Folders") }
                        items(folders, key = { "folder-${it.id}" }) { folder ->
                            FolderListItem(
                                folder = folder,
                                bookmarkCount = folderCounts[folder.id] ?: 0,
                                onClick = { onFolderClick(folder) },
                                onRenameClick = { folderPendingRename = folder },
                                onDeleteClick = { folderPendingDelete = folder },
                                onChangeColorClick = { folderPendingColor = folder }
                            )
                        }
                    }
                    if (unsortedBookmarks.isNotEmpty()) {
                        item(key = "unsorted-header") { SectionHeader("Unsorted") }
                        items(unsortedBookmarks, key = { "bookmark-${it.id}" }) { bookmark ->
                            val isSelected = bookmark.id in selectedIds
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
    }

    if (showBackupReminder) {
        Dialog(onDismissRequest = { viewModel.dismissBackupReminder() }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "Don't forget to create a backup of your bookmarks periodically to keep your data safe.",
                    modifier = Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "New folder",
            confirmLabel = "Create",
            onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    if (showSortDialog) {
        SortOptionDialog(
            current = sortOption,
            onSelect = { viewModel.onSortOptionChange(it); showSortDialog = false },
            onDismiss = { showSortDialog = false }
        )
    }

    folderPendingRename?.let { folder ->
        TextInputDialog(
            title = "Rename folder",
            initialText = folder.name,
            confirmLabel = "Rename",
            onConfirm = { name -> viewModel.renameFolder(folder, name); folderPendingRename = null },
            onDismiss = { folderPendingRename = null }
        )
    }

    folderPendingDelete?.let { folder ->
        ConfirmDeleteFolderDialog(
            folderName = folder.name,
            onConfirm = { viewModel.deleteFolder(folder); folderPendingDelete = null },
            onDismiss = { folderPendingDelete = null }
        )
    }

    folderPendingColor?.let { folder ->
        FolderColorDialog(
            currentColor = folder.colorTag,
            onSelect = { color -> viewModel.setFolderColor(folder, color) },
            onDismiss = { folderPendingColor = null }
        )
    }

    bookmarkPendingMove?.let { bookmark ->
        MoveToFolderDialog(
            folders = foldersForPicker,
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
            folders = foldersForPicker,
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun EmptyState(hasActiveSearch: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = if (hasActiveSearch) Icons.Default.Search else Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasActiveSearch) "No matches" else "Nothing here yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasActiveSearch) {
                    "Try a different search"
                } else {
                    "Tap + to create a folder, or share a link to LinkVault to save your first one"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SortOptionDialog(current: SortOption, onSelect: (SortOption) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column {
                SortOption.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = option == current, onClick = { onSelect(option) })
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun defaultExportFilename(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
    return "linkvault-backup-${formatter.format(Date())}.json"
}
