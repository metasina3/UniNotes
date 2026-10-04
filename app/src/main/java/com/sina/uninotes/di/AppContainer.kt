package com.sina.uninotes.di

import android.content.Context
import androidx.room.Room
import com.sina.uninotes.data.backup.BackupRepository
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.data.local.files.ThumbnailGenerator
import com.sina.uninotes.data.repository.NoteRepository
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.data.repository.SubjectRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: UniNotesDatabase = Room.databaseBuilder(
        appContext,
        UniNotesDatabase::class.java,
        UniNotesDatabase.NAME,
    ).build()

    val photoStorage = PhotoStorage(appContext)
    val thumbnailGenerator = ThumbnailGenerator(appContext)

    val subjectRepository = SubjectRepository(
        subjectDao = database.subjectDao(),
        photoDao = database.photoDao(),
        noteDao = database.noteDao(),
        photoStorage = photoStorage,
        database = database,
    )

    val noteRepository = NoteRepository(
        noteDao = database.noteDao(),
    )

    val photoRepository = PhotoRepository(
        photoDao = database.photoDao(),
        photoStorage = photoStorage,
        thumbnailGenerator = thumbnailGenerator,
    )

    val backupRepository = BackupRepository(
        context = appContext,
        database = database,
        photoStorage = photoStorage,
        subjectDao = database.subjectDao(),
        noteDao = database.noteDao(),
        photoDao = database.photoDao(),
    )
}
