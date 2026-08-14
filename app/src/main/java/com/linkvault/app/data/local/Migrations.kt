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
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `previewImageUrl` TEXT")
    }
}
