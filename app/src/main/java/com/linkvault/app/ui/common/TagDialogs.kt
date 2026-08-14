package com.linkvault.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.linkvault.app.data.local.entity.Tag

/**
 * [allTags] minus [currentTags] are shown as tappable "existing tags" so
 * reusing a tag doesn't mean retyping it (and risking a near-duplicate,
 * e.g. "recipe" vs "recipes").
 */
@Composable
fun EditTagsDialog(
    currentTags: List<Tag>,
    allTags: List<Tag>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Tag) -> Unit,
    onDismiss: () -> Unit
) {
    var newTagText by remember { mutableStateOf("") }
    val suggestions = allTags.filter { tag -> currentTags.none { it.id == tag.id } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Tags", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (currentTags.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(currentTags, key = { it.id }) { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tag.name, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                                IconButton(onClick = { onRemoveTag(tag) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove ${tag.name}")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        placeholder = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newTagText.isNotBlank()) {
                                onAddTag(newTagText)
                                newTagText = ""
                            }
                        },
                        enabled = newTagText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Existing tags",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(suggestions, key = { it.id }) { tag ->
                            Text(
                                text = tag.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddTag(tag.name) }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}
