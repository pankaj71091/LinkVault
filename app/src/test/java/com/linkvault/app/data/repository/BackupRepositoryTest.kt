package com.linkvault.app.data.repository

import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.testutil.FakeBookmarkDao
import com.linkvault.app.testutil.FakeFolderDao
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [BackupRepository]. Uses in-memory fakes for the two
 * DAOs (real Room would need an Android Context, which is unavailable
 * in `src/test/`) and focuses on the repository's logic: REPLACE/MERGE
 * semantics, folder-ID remapping, JSON round-trip, the `lastBackupTime`
 * side-effect, and the HTML import path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var folderDao: FakeFolderDao
    private lateinit var bookmarkDao: FakeBookmarkDao
    private lateinit var preferences: FakePreferenceRepository
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        folderDao = FakeFolderDao()
        bookmarkDao = FakeBookmarkDao()
        preferences = FakePreferenceRepository()
        repository = BackupRepository(folderDao, bookmarkDao, preferences)
    }

    @After
    fun tearDown() {
        folderDao.clear()
        bookmarkDao.clear()
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

    // --- 1.4: tag cleanup on REPLACE is currently incomplete (known gap) ---

    @Test
    fun `1_4 importFromJson REPLACE cleans bookmarks and folders (tag-table cleanup is a known gap)`() = runTest {
        // This test documents the current behavior. When Task 4 wraps importFromJson
        // in a transaction and adds tag-table cleanup, this test will still pass —
        // the bookmark + folder wipe is what we lock down here.
        repository.importFromJson(loadFixture(), ImportMode.REPLACE)
        assertEquals(4, bookmarkDao.getAllBookmarksOnce().size)
        assertEquals(3, folderDao.getAllFoldersOnce().size)
    }

    // --- 1.5 + 1.6: MERGE dedupes root folders by case-insensitive name ---

    @Test
    fun `1_5 importFromJson MERGE dedupes root folders by case-insensitive name`() = runTest {
        folderDao.insert(testFolder(name = "RECIPES")) // pre-existing, different casing

        val result = repository.importFromJson(loadFixture(), ImportMode.MERGE)

        // Should NOT have created a second "Recipes" — the existing "RECIPES" is reused
        val recipes = folderDao.getAllFoldersOnce().filter { it.name.equals("recipes", ignoreCase = true) }
        assertEquals(1, recipes.size)
        // The import result reports the count from the file, not the count actually inserted.
        assertEquals(3, result.foldersImported)
    }

    // --- 1.7: MERGE remaps folder IDs so bookmarks point to the right folder ---

    @Test
    fun `1_6 importFromJson MERGE remaps folder IDs so bookmarks point to the right folder`() = runTest {
        repository.importFromJson(loadFixture(), ImportMode.MERGE)

        // The "pasta" bookmark was in folder id 11 in the fixture. After import, the
        // actual id will be different — find the bookmark in the new DB and check it
        // points to a folder whose name is "Sub-recipes".
        val pasta = bookmarkDao.getAllBookmarksOnce().first { it.url == "https://example.com/pasta" }
        assertNotNull(pasta.folderId)
        val folder = folderDao.getFolderById(pasta.folderId!!)
        assertNotNull(folder)
        assertEquals("Sub-recipes", folder!!.name)
    }

    // --- 1.8: MERGE preserves child folder nesting via remap ---

    @Test
    fun `1_7 importFromJson MERGE preserves child folder nesting via remap`() = runTest {
        repository.importFromJson(loadFixture(), ImportMode.MERGE)

        val subRecipes = folderDao.getAllFoldersOnce().first { it.name == "Sub-recipes" }
        assertNotNull(subRecipes.parentFolderId)
        val parent = folderDao.getFolderById(subRecipes.parentFolderId!!)
        assertNotNull(parent)
        assertEquals("Recipes", parent!!.name)
    }

    // --- 1.9: unknown top-level JSON fields are tolerated ---

    @Test
    fun `1_8 importFromJson with an unknown top-level field still works (ignoreUnknownKeys)`() = runTest {
        val extendedJson = loadFixture().replaceFirst("{", """{ "futureFeatureFlag": true,""")
        repository.importFromJson(extendedJson, ImportMode.REPLACE)
        assertEquals(3, folderDao.getAllFoldersOnce().size)
    }

    // --- 1.10: HTML import end-to-end ---

    @Test
    fun `1_9 importFromHtml parses a Chrome-style Netscape export`() = runTest {
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
    fun `1_10 importFromHtml recreates nested folder path`() = runTest {
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
    fun `1_11 exportToJson updates lastBackupTime in preferences`() = runTest {
        assertEquals(0L, preferences.storedLastBackupTime)
        repository.exportToJson()
        val after = preferences.storedLastBackupTime
        assertTrue("lastBackupTime should be set after export", after > 0L)
    }
}