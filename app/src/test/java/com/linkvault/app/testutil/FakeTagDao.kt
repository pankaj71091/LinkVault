package com.linkvault.app.testutil

import com.linkvault.app.data.local.dao.TagCount
import com.linkvault.app.data.local.dao.TagDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.BookmarkTagCrossRef
import com.linkvault.app.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [TagDao] for unit tests. Only the methods
 * [com.linkvault.app.data.repository.BackupRepository] uses are exercised;
 * the rest are minimal no-ops that satisfy the interface.
 *
 * Exposes its underlying state as `snapshot()` / `restore(snapshot)` so
 * [FakeBackupDatabase] can implement real rollback semantics in
 * `runInTransaction` (see that class for why).
 */
class FakeTagDao : TagDao {

    private val tags: MutableList<Tag> = mutableListOf()
    private val tagsById: MutableMap<Long, Tag> = mutableMapOf()
    private val crossRefs: MutableList<BookmarkTagCrossRef> = mutableListOf()
    private val tagsState = MutableStateFlow<List<Tag>>(emptyList())
    private var nextId: Long = 1L

    fun snapshotState(): TagSnapshot = TagSnapshot(
        tags = tags.toList(),
        tagsById = tagsById.toMap(),
        crossRefs = crossRefs.toList(),
        nextId = nextId
    )

    fun restore(snap: TagSnapshot) {
        tags.clear()
        tags.addAll(snap.tags)
        tagsById.clear()
        tagsById.putAll(snap.tagsById)
        crossRefs.clear()
        crossRefs.addAll(snap.crossRefs)
        nextId = snap.nextId
        tagsState.value = tags.toList()
    }

    fun clear() {
        tags.clear()
        tagsById.clear()
        crossRefs.clear()
        nextId = 1L
        tagsState.value = emptyList()
    }

    // --- Methods used by BackupRepository ---

    override suspend fun insert(tag: Tag): Long {
        val newId = nextId++
        val stored = tag.copy(id = newId)
        tags.add(stored)
        tagsById[newId] = stored
        tagsState.value = tags.toList()
        return newId
    }

    override suspend fun findByName(name: String): Tag? =
        tags.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun delete(tag: Tag) {
        tags.removeAll { it.id == tag.id }
        tagsById.remove(tag.id)
        // CASCADE: drop every cross-ref pointing at this tag.
        crossRefs.removeAll { it.tagId == tag.id }
        tagsState.value = tags.toList()
    }

    override suspend fun deleteAll() {
        tags.clear()
        tagsById.clear()
        crossRefs.clear()
        nextId = 1L
        tagsState.value = emptyList()
    }

    // --- Cross-ref methods (required by the interface) ---

    override suspend fun addTagToBookmark(crossRef: BookmarkTagCrossRef) {
        if (crossRefs.none { it.bookmarkId == crossRef.bookmarkId && it.tagId == crossRef.tagId }) {
            crossRefs.add(crossRef)
        }
    }

    override suspend fun insertMultipleCrossRefs(crossRefs: List<BookmarkTagCrossRef>) {
        crossRefs.forEach { addTagToBookmark(it) }
    }

    override suspend fun removeTagFromBookmark(crossRef: BookmarkTagCrossRef) {
        crossRefs.removeAll { it.bookmarkId == crossRef.bookmarkId && it.tagId == crossRef.tagId }
    }

    // --- Methods unused by BackupRepository but required by the interface ---

    override fun getAllTags(): Flow<List<Tag>> =
        tagsState.map { list -> list.sortedBy { it.name.lowercase() } }

    override fun getTagCounts(): Flow<List<TagCount>> = MutableStateFlow(emptyList<TagCount>())

    override fun getTagsForBookmark(bookmarkId: Long): Flow<List<Tag>> =
        tagsState.map { list ->
            list.filter { tag -> crossRefs.any { it.bookmarkId == bookmarkId && it.tagId == tag.id } }
        }

    override fun getBookmarksForTag(tagId: Long): Flow<List<Bookmark>> = MutableStateFlow(emptyList())
}

data class TagSnapshot(
    val tags: List<Tag>,
    val tagsById: Map<Long, Tag>,
    val crossRefs: List<BookmarkTagCrossRef>,
    val nextId: Long
)