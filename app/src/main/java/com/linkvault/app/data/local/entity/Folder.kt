package com.linkvault.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined folder used to organize [Bookmark]s.
 *
 * Folders can be nested via [parentFolderId] (self-referencing foreign key).
 * [parentFolderId] == null means the folder lives at the root level.
 *
 * Nesting isn't exposed in the UI until Phase 4.5, but the column exists now
 * so introducing that feature later doesn't require a schema migration.
 *
 * Delete behavior: if a parent folder is deleted, its child folders are NOT
 * deleted with it — they're promoted to the root level (onDelete = SET_NULL).
 * This mirrors the same "nothing just vanishes" philosophy used for
 * [Bookmark.folderId]: deleting a container should never silently destroy
 * what was inside it. This is an assumption made during Phase 0 — flag it if
 * you'd rather parent-delete cascade to children instead.
 */
@Immutable
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["parentFolderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentFolderId")]
)
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val parentFolderId: Long? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    /** Manual sort position within its parent, for future drag-to-reorder support. */
    val sortOrder: Int = 0,

    /** Optional hex color (e.g. "#4A86E8") for future folder personalization. */
    val colorTag: String? = null
)
