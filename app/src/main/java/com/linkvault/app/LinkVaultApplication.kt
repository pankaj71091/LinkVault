package com.linkvault.app

import android.app.Application
import androidx.room.Room
import com.linkvault.app.data.local.AppDatabase
import com.linkvault.app.data.local.MIGRATION_1_2
import com.linkvault.app.data.local.MIGRATION_2_3
import com.linkvault.app.data.local.MIGRATION_3_4
import com.linkvault.app.data.repository.BackupRepository
import com.linkvault.app.data.repository.BookmarkRepository
import com.linkvault.app.data.repository.FolderRepository
import com.linkvault.app.data.repository.PreferenceRepository
import com.linkvault.app.data.repository.TagRepository
import com.linkvault.app.data.work.MetadataFetchScheduler

/**
 * Holds the app's singleton dependencies (database + repositories) so
 * screens/ViewModels can reach them without a DI framework. This is
 * deliberately simple manual DI, not Hilt/Koin — the dependency graph here
 * is small enough that a framework would add more ceremony than it saves.
 */
class LinkVaultApplication : Application() {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    val folderRepository: FolderRepository by lazy { FolderRepository(database.folderDao()) }
    val bookmarkRepository: BookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao()) }
    val preferenceRepository: PreferenceRepository by lazy { PreferenceRepository(applicationContext) }
    val backupRepository: BackupRepository by lazy {
        // Pass the database itself, not the individual DAOs, so
        // REPLACE-mode import can run inside a single Room transaction
        // (see BackupRepository.wipeAll / importFromJson for the
        // ordering constraints that make this matter).
        BackupRepository(database, preferenceRepository)
    }
    val tagRepository: TagRepository by lazy { TagRepository(database.tagDao()) }
    val metadataFetchScheduler: MetadataFetchScheduler by lazy { MetadataFetchScheduler(applicationContext) }
}
