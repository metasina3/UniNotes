package com.sina.uninotes.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `folders` (
                    `id` TEXT NOT NULL,
                    `subject_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_subject_id_sort_order` ON `folders` (`subject_id`, `sort_order`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_subject_id_name` ON `folders` (`subject_id`, `name`)")

            db.execSQL("ALTER TABLE `notes` ADD COLUMN `folder_id` TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS `index_notes_subject_id_local_date`")
            db.execSQL("DROP INDEX IF EXISTS `index_notes_subject_id_updated_at_epoch_ms`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_subject_id_folder_id_local_date` " +
                    "ON `notes` (`subject_id`, `folder_id`, `local_date`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notes_subject_id_folder_id_updated_at_epoch_ms` " +
                    "ON `notes` (`subject_id`, `folder_id`, `updated_at_epoch_ms`)",
            )

            db.execSQL("ALTER TABLE `photos` ADD COLUMN `folder_id` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("DROP INDEX IF EXISTS `index_photos_subject_id_captured_at_epoch_ms`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_photos_subject_id_folder_id_sort_order` " +
                    "ON `photos` (`subject_id`, `folder_id`, `sort_order`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_photos_subject_id_folder_id_captured_at_epoch_ms` " +
                    "ON `photos` (`subject_id`, `folder_id`, `captured_at_epoch_ms`)",
            )

            // Preserve previous capture-time order as an explicit sort_order per subject.
            db.execSQL(
                """
                UPDATE photos
                SET sort_order = (
                    SELECT COUNT(*)
                    FROM photos AS older
                    WHERE older.subject_id = photos.subject_id
                      AND older.folder_id = photos.folder_id
                      AND (
                        older.captured_at_epoch_ms < photos.captured_at_epoch_ms
                        OR (older.captured_at_epoch_ms = photos.captured_at_epoch_ms AND older.id <= photos.id)
                      )
                ) - 1
                """.trimIndent(),
            )
        }
    }
}
