package com.linkvault.app.ui.archived

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.ui.common.BookmarkListItem
import com.linkvault.app.ui.common.ConfirmDeleteBookmarkDialog
import com.linkvault.app.ui.common.EditNotesDialog
import com.linkvault.app.ui.common.EditTagsDialog
import com.linkvault.app.ui.common.MoveToFolderDialog
import com.linkvault.app.ui.common.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    viewModel: ArchivedViewModel,
    foldersForPicker: List<Folder>,
    allTags: List<com.linkvault.app.data.local.entity.Tag>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val archivedBookmarks by viewModel.archivedBookmarks.collectAsStateWithLifecycle()

    var bookmarkPendingMove by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingDelete by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingNotes by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkPendingTags by remember { mutableStateOf<Bookmark?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Archived") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (archivedBookmarks.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing archived", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Archived links stay out of your folders but aren't deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(archivedBookmarks, key = { it.id }) { bookmark ->
                    BookmarkListItem(
                        bookmark = bookmark,
                        onClick = { openUrl(context, bookmark.url) },
                        onLongClick = { },
                        onMoveClick = { bookmarkPendingMove = bookmark },
                        onDeleteClick = { bookmarkPendingDelete = bookmark },
                        onToggleArchiveClick = { viewModel.unarchive(bookmark) },
                        onTogglePinClick = { viewModel.togglePinned(bookmark) },
                        onEditTagsClick = { bookmarkPendingTags = bookmark },
                        onEditNotesClick = { bookmarkPendingNotes = bookmark },
                        showUnarchive = true
                    )
                }
            }
        }
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
}
