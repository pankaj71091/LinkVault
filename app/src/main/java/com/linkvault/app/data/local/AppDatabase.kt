package com.linkvault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.linkvault.app.data.local.dao.BookmarkDao
import com.linkvault.app.data.local.dao.FolderDao
import com.linkvault.app.data.local.dao.TagDao
import com.linkvault.app.data.local.entity.Bookmark
import com.linkvault.app.data.local.entity.BookmarkTagCrossRef
import com.linkvault.app.data.local.entity.Folder
import com.linkvault.app.data.local.entity.Tag
import com.linkvault.app.data.repository.BackupDatabase

/**
 * The app's single local Room database. Local-only by design — no remote
 * data source, no sync backend (see Phase 4 for portable import/export, and
 * Phase 5 for at-rest encryption).
 *
 * version 3 (Phase 5): added previewImageUrl to Bookmark. See
 * Migrations.kt (MIGRATION_2_3).
 *
 * Implements [BackupDatabase] so the destructive REPLACE-mode import
 * path can be wrapped in a single transaction (see BackupRepository
 * for why that matters). The bridge between Room's `withTransaction`
 * extension (from `room-ktx`) and the [BackupDatabase.runInTransaction]
 * method lives here.
 */
@Database(
    entities = [Folder::class, Bookmark::class, Tag::class, BookmarkTagCrossRef::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase(), BackupDatabase {

    abstract override fun folderDao(): FolderDao
    abstract override fun bookmarkDao(): BookmarkDao
    abstract override fun tagDao(): TagDao

    /**
     * Delegates to the `withTransaction` extension from `androidx.room.ktx`.
     * Exists on this class so the interface contract in [BackupDatabase]
     * is satisfied for callers that hold a `BackupDatabase` reference
     * rather than the concrete `AppDatabase`.
     */
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        withTransaction { block() }

    companion object {
        const val DATABASE_NAME = "linkvault.db"
    }
}
