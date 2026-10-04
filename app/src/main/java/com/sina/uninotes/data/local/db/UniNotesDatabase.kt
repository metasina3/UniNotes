package com.sina.uninotes.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        NoteEntity::class,
        PhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UniNotesDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun noteDao(): NoteDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val NAME = "uninotes.db"
        const val SCHEMA_VERSION = 1
    }
}
