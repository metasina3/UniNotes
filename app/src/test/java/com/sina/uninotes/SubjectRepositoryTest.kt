package com.sina.uninotes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.PhotoStatus
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.data.repository.SubjectRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SubjectRepositoryTest {
    private lateinit var db: UniNotesDatabase
    private lateinit var subjects: SubjectRepository
    private lateinit var storage: PhotoStorage

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UniNotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = PhotoStorage(context)
        subjects = SubjectRepository(
            subjectDao = db.subjectDao(),
            photoDao = db.photoDao(),
            noteDao = db.noteDao(),
            photoStorage = storage,
            database = db,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun renamePreservesAssociations() = runBlocking {
        val created = subjects.createSubject("ساختمان داده", 0xFF3195FF).getOrThrow()
        val now = System.currentTimeMillis()
        db.noteDao().insert(
            NoteEntity(
                id = "n1",
                subjectId = created.id,
                localDate = "2026-10-04",
                title = "",
                body = "note",
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                timezoneId = "UTC",
            ),
        )
        db.photoDao().insert(
            PhotoEntity(
                id = "p1",
                subjectId = created.id,
                relativePath = "subjects/${created.id}/photos/p1.jpg",
                thumbnailRelativePath = null,
                capturedAtEpochMs = now,
                localDate = "2026-10-04",
                timezoneId = "UTC",
                width = 100,
                height = 100,
                orientationDegrees = 0,
                status = PhotoStatus.READY,
                createdAtEpochMs = now,
            ),
        )
        subjects.renameSubject(created.id, "Data Structures").getOrThrow()
        val renamed = db.subjectDao().getById(created.id)
        assertThat(renamed?.name).isEqualTo("Data Structures")
        assertThat(db.noteDao().getById("n1")?.subjectId).isEqualTo(created.id)
        assertThat(db.photoDao().getById("p1")?.subjectId).isEqualTo(created.id)
    }

    @Test
    fun blankNameRejected() = runBlocking {
        val result = subjects.createSubject("   ", 0xFF3195FF)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun deleteRemovesCounts() = runBlocking {
        val created = subjects.createSubject("Algo", 0xFF3195FF).getOrThrow()
        subjects.deleteSubject(created.id).getOrThrow()
        val list = subjects.observeSubjects().first()
        assertThat(list).isEmpty()
    }
}
