package com.linkvault.app.ui.capture

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.ui.common.TextInputDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveLinkScreen(
    viewModel: SaveLinkViewModel,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val url by viewModel.url.collectAsStateWithLifecycle()
    val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
    val navigationPath by viewModel.navigationPath.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()
    val duplicateOf by viewModel.duplicateOf.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Resolve the name of the selected folder (even if it's suggested and not in the current view)
    val selectedFolderName = if (selectedFolderId == null) {
        "Unsorted"
    } else {
        // Try current level folders first, then search root folders, then path (last resort)
        folders.find { it.id == selectedFolderId }?.name
            ?: rootFolders.find { it.id == selectedFolderId }?.name
            ?: navigationPath.find { it.id == selectedFolderId }?.name
            ?: "Folder"
    }

    ModalBottomSheet(
        onDismissRequest = onCancelled,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Link", style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (viewModel.save()) {
                                Toast.makeText(context, "Saved to $selectedFolderName", Toast.LENGTH_SHORT).show()
                                onSaved()
                            }
                        }
                    },
                    enabled = url.isNotBlank()
                ) {
                    Text("Save")
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            duplicateOf?.let { existing ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Already saved" + (existing.title?.takeIf { it.isNotBlank() }?.let { " as \"$it\"" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(text = "Select Folder", style = MaterialTheme.typography.titleMedium)
            
            CaptureBreadcrumbs(
                path = navigationPath,
                onBreadcrumbClick = { viewModel.navigateToBreadcrumb(it) }
            )

            Box(modifier = Modifier.height(250.dp)) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // Unsorted is only shown at the root level
                    if (navigationPath.isEmpty()) {
                        item {
                            FolderItem(
                                name = "Unsorted",
                                isSelected = selectedFolderId == null,
                                onSelect = { viewModel.onFolderSelected(null) }
                            )
                        }
                    }

                    items(folders, key = { it.id }) { folder ->
                        FolderItem(
                            name = folder.name,
                            isSelected = selectedFolderId == folder.id,
                            hasChildren = true, // We don't strictly know without a query, but assume yes for navigation
                            onSelect = { viewModel.onFolderSelected(folder.id) },
                            onNavigateIn = { viewModel.navigateIn(folder) }
                        )
                    }

                    item {
                        ListItem(
                            headlineContent = { Text("New folder...", color = MaterialTheme.colorScheme.primary) },
                            leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { showCreateFolderDialog = true }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "New folder",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createAndSelectFolder(name)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
}

@Composable
private fun FolderItem(
    name: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    hasChildren: Boolean = false,
    onNavigateIn: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { 
            Text(
                text = name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
                if (onNavigateIn != null) {
                    IconButton(onClick = onNavigateIn) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Navigate in")
                    }
                }
            }
        },
        modifier = Modifier.clickable { onSelect() }
    )
}

@Composable
fun CaptureBreadcrumbs(
    path: List<Folder>,
    onBreadcrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text(
                text = "Home",
                style = MaterialTheme.typography.bodyMedium,
                color = if (path.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (path.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onBreadcrumbClick(-1) }
            )
        }
        itemsIndexed(path) { index, folder ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(10.dp).padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (index == path.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (index == path.size - 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onBreadcrumbClick(index) }
            )
        }
    }
}
