package com.linkvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A tag a [Bookmark] can be labeled with. Unlike folders (single-parent —
 * a bookmark lives in exactly one folder or is unsorted), a bookmark can
 * have any number of tags, via [BookmarkTagCrossRef]. Added in schema
 * version 2 (see AppDatabase's MIGRATION_1_2).
 */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val createdAt: Long = System.currentTimeMillis()
)
