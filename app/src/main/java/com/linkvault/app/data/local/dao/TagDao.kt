package com.linkvault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.BookmarkTagCrossRef
import com.linkvault.app.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Tag?

    /** Deleting a tag removes it from every bookmark it was on (see BookmarkTagCrossRef's CASCADE). */
    @Delete
    suspend fun delete(tag: Tag)

    /**
     * Used by REPLACE-mode import (after bookmarks are deleted, the
     * cross-ref table is empty, so every tag is an orphan). Not
     * surfaced in the UI; the regular delete-tag flow is per-tag and
     * goes through [delete] with CASCADE behavior.
     */
    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("""
        SELECT tagId, COUNT(*) as count FROM bookmark_tag_cross_ref
        INNER JOIN bookmarks ON bookmarks.id = bookmark_tag_cross_ref.bookmarkId
        WHERE bookmarks.isArchived = 0
        GROUP BY tagId
    """)
    fun getTagCounts(): Flow<List<TagCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToBookmark(crossRef: BookmarkTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMultipleCrossRefs(crossRefs: List<BookmarkTagCrossRef>)

    @Delete
    suspend fun removeTagFromBookmark(crossRef: BookmarkTagCrossRef)

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN bookmark_tag_cross_ref ON tags.id = bookmark_tag_cross_ref.tagId
        WHERE bookmark_tag_cross_ref.bookmarkId = :bookmarkId
        ORDER BY tags.name COLLATE NOCASE ASC
    """)
    fun getTagsForBookmark(bookmarkId: Long): Flow<List<Tag>>

    @Query("""
        SELECT bookmarks.* FROM bookmarks
        INNER JOIN bookmark_tag_cross_ref ON bookmarks.id = bookmark_tag_cross_ref.bookmarkId
        WHERE bookmark_tag_cross_ref.tagId = :tagId AND bookmarks.isArchived = 0
        ORDER BY bookmarks.createdAt DESC
    """)
    fun getBookmarksForTag(tagId: Long): Flow<List<Bookmark>>
}

data class TagCount(
    val tagId: Long,
    val count: Int
)
