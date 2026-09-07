package com.linkvault.app.data.repository

import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.local.entity.Tag
import com.linkvault.app.testutil.FakeBackupDatabase
import com.linkvault.app.testutil.FakePreferenceRepository
import com.linkvault.app.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [BackupRepository]. Uses [FakeBackupDatabase] — a
 * transactional in-memory fake — plus [FakePreferenceRepository], and
 * focuses on the repository's logic: REPLACE/MERGE semantics, folder-ID
 * remapping, JSON round-trip, the `lastBackupTime` side-effect, the
 * HTML import path, the transactional rollback guarantee, and the
 * tag-table cleanup on REPLACE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: FakeBackupDatabase
    private lateinit var preferences: FakePreferenceRepository
    private lateinit var repository: BackupRepository

    // Convenience accessors — tests read/write through these so the
    // bodies stay focused on assertions rather than on `.bookmarks.foo`.
    private val folderDao get() = database.folders
    private val bookmarkDao get() = database.bookmarks
    private val tagDao get() = database.tags

    @Before
    fun setUp() {
        database = FakeBackupDatabase()
        preferences = FakePreferenceRepository()
        repository = BackupRepository(database, preferences)
    }

    @After
    fun tearDown() {
        database.clear()
    }

    private fun loadFixture(): String =
        javaClass.classLoader!!.getResourceAsStream("backup-sample-v1.json")!!
            .bufferedReader().use { it.readText() }

    private fun testFolder(name: String, parentFolderId: Long? = null) = Folder(
        name = name,
        parentFolderId = parentFolderId,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L,
        sortOrder = 0,
        colorTag = null
    )

    private fun testBookmark(
        url: String,
        title: String? = null,
        folderId: Long? = null,
        isArchived: Boolean = false,
        isPinned: Boolean = false
    ) = Bookmark(
        url = url,
        title = title,
        folderId = folderId,
        notes = null,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L,
        faviconUrl = null,
        previewImageUrl = null,
        isArchived = isArchived,
        isPinned = isPinned
    )

    // --- 1.1: export includes archived bookmarks ---

    @Test
    fun `1_1 exportToJson includes archived bookmarks`() = runTest {
        bookmarkDao.insert(testBookmark(url = "https://a.com", isArchived = false))
        bookmarkDao.insert(testBookmark(url = "https://b.com", isArchived = true))

        val json = repository.exportToJson()
        val parsed = Json.decodeFromString(ExportFile.serializer(), json)

        val urls = parsed.bookmarks.map { it.url }.toSet()
        assertEquals(setOf("https://a.com", "https://b.com"), urls)
    }

    // --- 1.2: round-trip preserves all fields ---

    @Test
    fun `1_2 exportToJson round-trips through importFromJson MERGE preserving all fields`() = runTest {
        repository.importFromJson(loadFixture(), ImportMode.MERGE)

        val exported = repository.exportToJson()
        val reparsed = Json.decodeFromString(ExportFile.serializer(), exported)

        assertEquals(3, reparsed.folders.size)
        assertEquals(4, reparsed.bookmarks.size)

        val pasta = reparsed.bookmarks.first { it.url == "https://example.com/pasta" }
        assertEquals("Best Pasta", pasta.title)
        assertEquals("tasty", pasta.notes)
        assertTrue(pasta.isPinned)
        assertFalse(pasta.isArchived)

        val archived = reparsed.bookmarks.first { it.url == "https://news.example.com/article" }
        assertNull(archived.title)
        assertTrue(archived.isArchived)
    }

    // --- 1.3: REPLACE wipes existing folders and bookmarks ---

    @Test
    fun `1_3 importFromJson REPLACE wipes existing folders and bookmarks`() = runTest {
        folderDao.insert(testFolder(name = "Pre-existing"))
        bookmarkDao.insert(testBookmark(url = "https://preexisting.com"))
        assertEquals(1, folderDao.getAllFoldersOnce().size)
        assertEquals(1, bookmarkDao.getAllBookmarksOnce().size)

        val result = repository.importFromJson(loadFixture(), ImportMode.REPLACE)

        assertEquals(3, result.foldersImported)
        assertEquals(4, result.bookmarksImported)
        assertTrue(folderDao.getAllFoldersOnce().none { it.name == "Pre-existing" })
        assertTrue(bookmarkDao.getAllBookmarksOnce().none { it.url == "https://preexisting.com" })
        assertEquals(3, folderDao.getAllFoldersOnce().size)
        assertEquals(4, bookmarkDao.getAllBookmarksOnce().size)
    }

    // --- 1.4: REPLACE now also wipes orphan tags (Task 4 fix) ---

    @Test
    fun `1_4 importFromJson REPLACE wipes orphan tags too`() = runTest {
        tagDao.insert(Tag(name = "orphan-tag"))
        assertEquals(1, tagDao.snapshotState().tags.size)

        repository.importFromJson(loadFixture(), ImportMode.REPLACE)

        assertEquals(0, tagDao.snapshotState().tags.size)
    }

    // --- 1.5: REPLACE wipes deeply-nested folders (the SET_NULL loop) ---

    @Test
    fun `1_5 importFromJson REPLACE wipes deeply-nested folders`() = runTest {
        val rootId = folderDao.insert(testFolder(name = "L0"))
        val midId = folderDao.insert(testFolder(name = "L1", parentFolderId = rootId))
        folderDao.insert(testFolder(name = "L2", parentFolderId = midId))
        assertEquals(3, folderDao.getAllFoldersOnce().size)

        val json = """{
            "formatVersion": 1,
            "exportedAt": 1,
            "folders": [{ "id": 99, "name": "Only", "parentFolderId": null, "createdAt": 1, "updatedAt": 1, "sortOrder": 0, "colorTag": null }],
            "bookmarks": []
        }""".trimIndent()
        repository.importFromJson(json, ImportMode.REPLACE)

        val remaining = folderDao.getAllFoldersOnce()
        assertEquals(1, remaining.size)
        assertEquals("Only", remaining.first().name)
    }

    // --- 1.6: MERGE dedupes root folders by case-insensitive name ---

    @Test
    fun `1_6 importFromJson MERGE dedupes root folders by case-insensitive name`() = runTest {
        folderDao.insert(testFolder(name = "RECIPES"))

        val result = repository.importFromJson(loadFixture(), ImportMode.MERGE)

        val recipes = folderDao.getAllFoldersOnce().filter { it.name.equals("recipes", ignoreCase = true) }
        assertEquals(1, recipes.size)
        assertEquals(3, result.foldersImported)
    }

    // --- 1.7: MERGE remaps folder IDs so bookmarks point to the right folder ---

    @Test
    fun `1_7 importFromJson MERGE remaps folder IDs so bookmarks point to the right folder`() = runTest {
        repository.importFromJson(loadFixture(), ImportMode.MERGE)

        val pasta = bookmarkDao.getAllBookmarksOnce().first { it.url == "https://example.com/pasta" }
        assertNotNull(pasta.folderId)
        val folder = folderDao.getFolderById(pasta.folderId!!)
        assertNotNull(folder)
        assertEquals("Sub-recipes", folder!!.name)
    }

    // --- 1.8: MERGE preserves child folder nesting via remap ---

    @Test
    fun `1_8 importFromJson MERGE preserves child folder nesting via remap`() = runTest {
        repository.importFromJson(loadFixture(), ImportMode.MERGE)

        val subRecipes = folderDao.getAllFoldersOnce().first { it.name == "Sub-recipes" }
        assertNotNull(subRecipes.parentFolderId)
        val parent = folderDao.getFolderById(subRecipes.parentFolderId!!)
        assertNotNull(parent)
        assertEquals("Recipes", parent!!.name)
    }

    // --- 1.9: unknown top-level JSON fields are tolerated ---

    @Test
    fun `1_9 importFromJson with an unknown top-level field still works (ignoreUnknownKeys)`() = runTest {
        val extendedJson = loadFixture().replaceFirst("{", """{ "futureFeatureFlag": true,""")
        repository.importFromJson(extendedJson, ImportMode.REPLACE)
        assertEquals(3, folderDao.getAllFoldersOnce().size)
    }

    // --- 1.10: HTML import end-to-end ---

    @Test
    fun `1_10 importFromHtml parses a Chrome-style Netscape export`() = runTest {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <HTML><BODY>
            <DL><p>
                <DT><H3>News</H3>
                <DL><p>
                    <DT><A HREF="https://news.example.com/a">Headline A</A>
                    <DT><A HREF="https://news.example.com/b">Headline B</A>
                </DL><p>
                <DT><H3>Recipes</H3>
                <DL><p>
                    <DT><A HREF="https://example.com/pasta">Pasta</A>
                </DL><p>
            </DL><p>
            </BODY></HTML>
        """.trimIndent()

        val result = repository.importFromHtml(html)
        assertEquals(2, result.foldersImported)
        assertEquals(3, result.bookmarksImported)

        val allBookmarks = bookmarkDao.getAllBookmarksOnce()
        assertEquals(3, allBookmarks.size)
        assertTrue(allBookmarks.any { it.url == "https://news.example.com/a" && it.title == "Headline A" })
    }

    // --- 1.11: HTML import recreates nested folder path ---

    @Test
    fun `1_11 importFromHtml recreates nested folder path`() = runTest {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <HTML><BODY>
            <DL><p>
                <DT><H3>Recipes</H3>
                <DL><p>
                    <DT><H3>Italian</H3>
                    <DL><p>
                        <DT><A HREF="https://example.com/pasta">Pasta</A>
                    </DL><p>
                </DL><p>
            </DL><p>
            </BODY></HTML>
        """.trimIndent()

        val result = repository.importFromHtml(html)
        assertEquals(2, result.foldersImported)
        assertEquals(1, result.bookmarksImported)

        val pasta = bookmarkDao.getAllBookmarksOnce().single { it.url == "https://example.com/pasta" }
        val italian = folderDao.getFolderById(pasta.folderId!!)!!
        assertEquals("Italian", italian.name)
        val recipes = folderDao.getFolderById(italian.parentFolderId!!)!!
        assertEquals("Recipes", recipes.name)
    }

    // --- 1.12: exportToJson updates lastBackupTime ---

    @Test
    fun `1_12 exportToJson updates lastBackupTime in preferences`() = runTest {
        assertEquals(0L, preferences.storedLastBackupTime)
        repository.exportToJson()
        val after = preferences.storedLastBackupTime
        assertTrue("lastBackupTime should be set after export", after > 0L)
    }

    // --- 1.13 (Task 4): import is atomic — a mid-import failure rolls back ---

    @Test
    fun `1_13 importFromJson rolls back all changes when the block throws mid-import`() = runTest {
        // Pre-seed: a folder, a bookmark, and a tag we expect to be UNTOUCHED
        // after the import fails partway through.
        folderDao.insert(testFolder(name = "Pre-existing"))
        bookmarkDao.insert(testBookmark(url = "https://keep.me"))
        val preTag = Tag(name = "keep-tag")
        val preTagId = tagDao.insert(preTag)
        assertEquals(1, folderDao.getAllFoldersOnce().size)
        assertEquals(1, bookmarkDao.getAllBookmarksOnce().size)
        assertEquals(1, tagDao.snapshotState().tags.size)

        // Configure the bookmark DAO to throw on its 2nd insert (the fixture
        // has 4 bookmarks; index 1 is the 2nd one — by then REPLACE has wiped
        // the pre-existing rows and inserted 1 folder, so the throw lands
        // mid-import). This simulates a real mid-import crash.
        bookmarkDao.failOnInsertIndex = 1

        try {
            repository.importFromJson(loadFixture(), ImportMode.REPLACE)
            fail("Expected the failing bookmark DAO to throw")
        } catch (e: IllegalStateException) {
            // Expected — the test hook throws this
        }

        // The repository should have rolled back: pre-existing data intact,
        // no rows from the file leaked through.
        val foldersAfter = folderDao.getAllFoldersOnce()
        val bookmarksAfter = bookmarkDao.getAllBookmarksOnce()
        val tagsAfter = tagDao.snapshotState().tags

        assertEquals(1, foldersAfter.size)
        assertEquals("Pre-existing", foldersAfter.first().name)
        assertEquals(1, bookmarksAfter.size)
        assertEquals("https://keep.me", bookmarksAfter.first().url)
        assertEquals(1, tagsAfter.size)
        assertEquals(preTagId, tagsAfter.first().id)
        assertTrue(bookmarksAfter.none { it.url == "https://example.com/pasta" })
    }
}
