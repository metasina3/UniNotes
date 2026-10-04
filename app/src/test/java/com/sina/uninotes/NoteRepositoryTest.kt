package com.sina.uninotes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sina.uninotes.data.local.db.SubjectEntity
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.repository.NoteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoteRepositoryTest {
    private lateinit var db: UniNotesDatabase
    private lateinit var notes: NoteRepository
    private val subjectId = "subject-1"
    private val zone = ZoneId.of("Asia/Tehran")

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UniNotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notes = NoteRepository(db.noteDao())
        val now = System.currentTimeMillis()
        db.subjectDao().insert(
            SubjectEntity(subjectId, "ساختمان داده", 0xFF3195FF, now, now),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun oneNotePerSubjectPerDate() = runBlocking {
        val today = LocalDate.of(2026, 10, 4)
        val first = notes.openOrCreateTodayNote(subjectId, zone, today)
        val second = notes.openOrCreateTodayNote(subjectId, zone, today)
        assertThat(second.id).isEqualTo(first.id)
    }

    @Test
    fun raceSafeDailyCreation() = runBlocking {
        val today = LocalDate.of(2026, 10, 4)
        val created = (1..8).map {
            async { notes.openOrCreateTodayNote(subjectId, zone, today) }
        }.awaitAll()
        assertThat(created.map { it.id }.toSet()).hasSize(1)
    }

    @Test
    fun reopenRetainsContent() = runBlocking {
        val today = LocalDate.of(2026, 10, 4)
        val note = notes.openOrCreateTodayNote(subjectId, zone, today)
        notes.saveNoteContent(
            noteId = note.id,
            title = "Linked List",
            body = "امروز دربارهٔ Linked List و تفاوتش با Array صحبت کردیم.",
        )
        val again = notes.openOrCreateTodayNote(subjectId, zone, today)
        assertThat(again.body).contains("Linked List")
        assertThat(again.title).isEqualTo("Linked List")
    }

    @Test
    fun midnightKeepsOpenNoteDate() = runBlocking {
        val dayOne = LocalDate.of(2026, 10, 4)
        val dayTwo = LocalDate.of(2026, 10, 5)
        val open = notes.openOrCreateTodayNote(subjectId, zone, dayOne)
        notes.saveNoteContent(open.id, "", "night notes")
        val nextDay = notes.openOrCreateTodayNote(subjectId, zone, dayTwo)
        assertThat(nextDay.id).isNotEqualTo(open.id)
        assertThat(notes.getNote(open.id)?.localDate).isEqualTo("2026-10-04")
    }

    @Test
    fun autosaveOrderingKeepsNewerText() = runBlocking {
        val note = notes.openOrCreateTodayNote(subjectId, zone, LocalDate.of(2026, 10, 4))
        val older = notes.saveNoteContent(note.id, "", "old", expectedUpdatedAt = note.updatedAtEpochMs).getOrThrow()
        val newer = notes.saveNoteContent(note.id, "", "new", expectedUpdatedAt = older.updatedAtEpochMs).getOrThrow()
        // Stale write using outdated expected timestamp should not clobber newer body if newer already stored with higher updatedAt
        val stale = notes.saveNoteContent(
            noteId = note.id,
            title = "",
            body = "stale",
            expectedUpdatedAt = older.updatedAtEpochMs,
        ).getOrThrow()
        assertThat(stale.body).isEqualTo("new")
        assertThat(newer.body).isEqualTo("new")
    }

    @Test
    fun subjectIsolation() = runBlocking {
        val other = "subject-2"
        val now = System.currentTimeMillis()
        db.subjectDao().insert(SubjectEntity(other, "Other", 0xFFF5A35C, now, now))
        val a = notes.openOrCreateTodayNote(subjectId, zone, LocalDate.of(2026, 10, 4))
        notes.saveNoteContent(a.id, "", "A")
        val b = notes.openOrCreateTodayNote(other, zone, LocalDate.of(2026, 10, 4))
        notes.saveNoteContent(b.id, "", "B")
        assertThat(notes.getNote(a.id)?.body).isEqualTo("A")
        assertThat(notes.getNote(b.id)?.body).isEqualTo("B")
        assertThat(a.id).isNotEqualTo(b.id)
    }
}
