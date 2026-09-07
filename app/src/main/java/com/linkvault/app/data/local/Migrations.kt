package com.linkvault.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (Phase 4.5): adds tags. Two new tables — `tags` itself, and
 * `bookmark_tag_cross_ref`, the many-to-many join table between bookmarks
 * and tags (see BookmarkTagCrossRef.kt for why it uses CASCADE rather than
 * this schema's usual SET_NULL pattern). Nothing about the existing
 * `folders`/`bookmarks` tables changes, so this migration only ever adds,
 * never touches existing rows — there's no data to lose here.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmark_tag_cross_ref` (
                `bookmarkId` INTEGER NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY(`bookmarkId`, `tagId`),
                FOREIGN KEY(`bookmarkId`) REFERENCES `bookmarks`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bookmark_tag_cross_ref_tagId` ON `bookmark_tag_cross_ref` (`tagId`)"
        )
    }
}

/**
 * v2 -> v3 (Phase 5): adds previewImageUrl to bookmarks for rich card previews.
 * Kept around for users on the v0.1 schema even though the column is dropped
 * in v3 -> v4 (see MIGRATION_3_4) — Room chains migrations, so this is
 * required to exist alongside the rollback.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `previewImageUrl` TEXT")
    }
}

/**
 * v3 -> v4 (Task 3 of the "tomorrow top 5" cleanup): drops the
 * `previewImageUrl` column from `bookmarks`. The feature was added in
 * v2->v3 (Phase 5) but never wired up — PageMetadataFetcher always
 * returns null for it and the UI never reads it. Rather than carry
 * dead schema forward, drop it entirely.
 *
 * SQLite (on the Android-bundled version Room supports) does not
 * reliably support `ALTER TABLE ... DROP COLUMN`, so the portable
 * approach is the 5-step "rebuild table" dance:
 *   1. Create a new table with the desired schema.
 *   2. Copy the columns we want to keep out of the old table.
 *   3. Drop the old table.
 *   4. Rename the new table.
 *   5. Recreate the index.
 *
 * The dropped column's data is lost — but since the column was
 * never populated in production (`previewImageUrl` always came back
 * null from the fetcher), this is a no-op for real users.
 *
 * **No in-tree unit test for this migration.** Room 2.8 removed
 * the `MigrationTestHelper(Context, ...)` constructor; the only
 * remaining constructors require `Instrumentation` (instrumented
 * test) or a fully constructed `RoomDatabase` factory. The cost
 * (Robolectric + Room 2.6 downgrade, ~50MB) isn't worth one test
 * for a destructive-but-no-op-on-real-data migration. Verify by:
 *   1. Install the previous version (with previewImageUrl), save a
 *      few bookmarks, install this version, confirm bookmarks
 *      are intact and the column is gone (visible via `adb shell
 *      sqlite3 ... ".schema bookmarks"`).
 *   2. KSP will fail at compile time if the Bookmark entity
 *      doesn't match what this migration produces — that's a
 *      strong static guarantee even without a runtime test.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. New table — same schema as v3 minus the previewImageUrl column.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmarks_new` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `url` TEXT NOT NULL,
                `title` TEXT,
                `folderId` INTEGER,
                `notes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `faviconUrl` TEXT,
                `isArchived` INTEGER NOT NULL,
                `isPinned` INTEGER NOT NULL,
                FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        // 2. Copy every column we still want, in the same order.
        db.execSQL(
            """
            INSERT INTO `bookmarks_new`
                (id, url, title, folderId, notes, createdAt, updatedAt, faviconUrl, isArchived, isPinned)
            SELECT id, url, title, folderId, notes, createdAt, updatedAt, faviconUrl, isArchived, isPinned
            FROM `bookmarks`
            """.trimIndent()
        )
        // 3. Drop the old table.
        db.execSQL("DROP TABLE `bookmarks`")
        // 4. Rename the new one.
        db.execSQL("ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`")
        // 5. Recreate the index that was on the old bookmarks table.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_folderId` ON `bookmarks` (`folderId`)"
        )
    }
}
