package com.linkvault.app.data.repository

import com.linkvault.app.data.local.dao.TagCount
import com.linkvault.app.data.local.dao.TagDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.BookmarkTagCrossRef
import com.linkvault.app.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

class TagRepository(private val tagDao: TagDao) {

    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    fun getTagCounts(): Flow<List<TagCount>> = tagDao.getTagCounts()

    fun getTagsForBookmark(bookmarkId: Long): Flow<List<Tag>> = tagDao.getTagsForBookmark(bookmarkId)

    fun getBookmarksForTag(tagId: Long): Flow<List<Bookmark>> = tagDao.getBookmarksForTag(tagId)

    /** Reuses an existing tag with the same name (case-insensitive) instead of creating a duplicate. */
    suspend fun getOrCreateTag(name: String): Tag {
        val trimmed = name.trim()
        return tagDao.findByName(trimmed) ?: run {
            val id = tagDao.insert(Tag(name = trimmed))
            Tag(id = id, name = trimmed)
        }
    }

    suspend fun addTagToBookmark(bookmarkId: Long, tagName: String) {
        val trimmed = tagName.trim()
        if (trimmed.isEmpty()) return
        val tag = getOrCreateTag(trimmed)
        tagDao.addTagToBookmark(BookmarkTagCrossRef(bookmarkId = bookmarkId, tagId = tag.id))
    }

    suspend fun removeTagFromBookmark(bookmarkId: Long, tagId: Long) {
        tagDao.removeTagFromBookmark(BookmarkTagCrossRef(bookmarkId = bookmarkId, tagId = tagId))
    }

    /** Removes the tag entirely — every bookmark loses it (see BookmarkTagCrossRef's CASCADE). */
    suspend fun deleteTag(tag: Tag) = tagDao.delete(tag)

    // --- Phase 5 Stage 1: Bulk Actions ---

    suspend fun bulkAddTagToBookmarks(bookmarkIds: List<Long>, tagName: String) {
        val trimmed = tagName.trim()
        if (trimmed.isEmpty()) return
        val tag = getOrCreateTag(trimmed)
        val crossRefs = bookmarkIds.map { bookmarkId ->
            BookmarkTagCrossRef(bookmarkId = bookmarkId, tagId = tag.id)
        }
        tagDao.insertMultipleCrossRefs(crossRefs)
    }
}
