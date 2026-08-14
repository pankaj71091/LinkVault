package com.linkvault.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.linkvault.app.data.local.entity.Bookmark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleArchiveClick: () -> Unit,
    onTogglePinClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onEditNotesClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectionModeActive: Boolean = false,
    showUnarchive: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Optimization: Only parse the domain once per bookmark instance
    val displayDomain = remember(bookmark.url) { displayDomain(bookmark.url) }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // "Super Light" optimization: Removed Card wrapper to flatten UI hierarchy
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionModeActive) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(bookmark.faviconUrl)
                    .size(64) 
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape),
                placeholder = rememberVectorPainter(Icons.Default.Language),
                error = rememberVectorPainter(Icons.Default.Language),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bookmark.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = bookmark.title?.takeIf { it.isNotBlank() } ?: bookmark.url,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!bookmark.notes.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Notes,
                        contentDescription = "Has a note",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = displayDomain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!selectionModeActive) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (bookmark.isPinned) "Unpin" else "Pin") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = if (bookmark.isPinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { menuExpanded = false; onTogglePinClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Tags") },
                        leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditTagsClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (bookmark.notes.isNullOrBlank()) "Add note" else "Edit note") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditNotesClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        onClick = { menuExpanded = false; onMoveClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (showUnarchive) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(
                                if (showUnarchive) Icons.Default.Unarchive else Icons.Default.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = { menuExpanded = false; onToggleArchiveClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuExpanded = false; onDeleteClick() }
                    )
                }
            }
        }
    }
}
