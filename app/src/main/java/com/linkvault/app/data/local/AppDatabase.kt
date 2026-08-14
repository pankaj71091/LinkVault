package com.linkvault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.dao.TagDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.BookmarkTagCrossRef
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.local.entity.Tag

/**
 * The app's single local Room database. Local-only by design — no remote
 * data source, no sync backend (see Phase 4 for portable import/export, and
 * Phase 5 for at-rest encryption).
 *
 * version 3 (Phase 5): added previewImageUrl to Bookmark. See
 * Migrations.kt (MIGRATION_2_3).
 */
@Database(
    entities = [Folder::class, Bookmark::class, Tag::class, BookmarkTagCrossRef::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "linkvault.db"
    }
}
