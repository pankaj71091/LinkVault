package com.linkvault.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.linkvault.app.data.local.entity.Folder

/** Covers both "create folder" (empty [initialText]) and "rename folder" (pre-filled). */
@Composable
fun TextInputDialog(
    title: String,
    initialText: String = "",
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmDeleteFolderDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$folderName\"?") },
        text = { Text("The links inside won't be deleted — they'll move to Unsorted.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmDeleteBookmarkDialog(
    bookmarkLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this link?") },
        text = { Text(text = bookmarkLabel, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Custom Dialog (not AlertDialog) since this is a scrollable pick-one-from-a-list
 * UI rather than a message-plus-two-buttons UI — AlertDialog's fixed
 * confirm/dismiss footer doesn't fit a list-selection flow naturally.
 *
 * [onCreateNewFolder], when provided, adds a "New folder" row above the
 * list — used by Phase 1's save screen so folder creation doesn't need a
 * second, near-identical dialog. Phase 2's call sites leave it null and get
 * exactly the old behavior.
 */
@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    onSelect: (folderId: Long?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Move to folder",
    onCreateNewFolder: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (onCreateNewFolder != null) {
                        item {
                            ListItem(
                                leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                headlineContent = { Text("New folder") },
                                modifier = Modifier.clickable { onCreateNewFolder() }
                            )
                        }
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Unsorted") },
                            modifier = Modifier.clickable { onSelect(null) }
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier = Modifier.clickable { onSelect(folder.id) }
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("Cancel") }
            }
        }
    }
}
