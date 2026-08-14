package com.linkvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join row for the bookmark<->tag many-to-many relationship.
 *
 * Uses CASCADE, not the SET_NULL pattern used elsewhere in this schema
 * (Folder/Bookmark) — deliberately different, because a cross-reference
 * row has no meaning on its own once either side is gone. SET_NULL exists
 * elsewhere to avoid silently destroying the *contained* thing (a
 * bookmark, when its folder is deleted); here there's nothing analogous to
 * preserve — if the bookmark or the tag is deleted, this association
 * should simply disappear with it.
 */
@Entity(
    tableName = "bookmark_tag_cross_ref",
    primaryKeys = ["bookmarkId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Bookmark::class,
            parentColumns = ["id"],
            childColumns = ["bookmarkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class BookmarkTagCrossRef(
    val bookmarkId: Long,
    val tagId: Long
)
