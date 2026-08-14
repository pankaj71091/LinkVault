package com.linkvault.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single saved link.
 *
 * [folderId] == null means the bookmark is "unsorted" — sitting at the root
 * level rather than inside a folder. This is a deliberate, real state (not
 * an error case): saving a link never requires picking a folder first, the
 * same way a file can sit loose in Windows Explorer without being inside a
 * folder.
 *
 * Delete behavior: if the folder a bookmark belongs to is deleted, the
 * bookmark is NOT deleted with it — it becomes unsorted (onDelete = SET_NULL).
 * Deleting a folder should never silently destroy the links inside it.
 */
@Immutable
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val url: String,

    /** Nullable because Phase 1's capture flow saves instantly; the page title
     *  gets fetched in the background and filled in by Phase 3. */
    val title: String? = null,

    val folderId: Long? = null,

    /** Personal note about why this was saved. Unused until Phase 4.5. */
    val notes: String? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    /** Favicon URL or local cached path. Populated starting Phase 3. */
    val faviconUrl: String? = null,

    /** Large preview image URL (og:image). Added Phase 5 Stage 1. */
    val previewImageUrl: String? = null,

    /** Soft-archive flag (Phase 4.5) — kept separate from deletion. */
    val isArchived: Boolean = false,

    /** Quick-access flag (Phase 4.5). */
    val isPinned: Boolean = false
)
