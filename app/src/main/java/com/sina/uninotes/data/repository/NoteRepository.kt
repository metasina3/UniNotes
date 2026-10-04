package com.sina.uninotes.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.sina.uninotes.data.LibraryAccess
import com.sina.uninotes.data.local.db.NoteDao
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.RootFolder
import com.sina.uninotes.util.DateFormatting
import com.sina.uninotes.util.Ids
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

class NoteRepository(
    private val noteDao: NoteDao,
) {
    private val writeMutex = LibraryAccess.mutex

    fun observeNotes(
        subjectId: String,
        folderId: String = RootFolder.ID,
    ): Flow<List<NoteEntity>> = noteDao.observeNotesForFolder(subjectId, folderId)

    fun observeNote(id: String): Flow<NoteEntity?> = noteDao.observeById(id)

    suspend fun getNote(id: String): NoteEntity? = noteDao.getById(id)

    /**
     * Opens today's daily note for [subjectId]/[folderId], creating it only when needed for editing.
     * Race-safe via unique (subject_id, folder_id, local_date) and retry on conflict.
     */
    suspend fun openOrCreateTodayNote(
        subjectId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = DateFormatting.todayLocalDate(zoneId),
        folderId: String = RootFolder.ID,
    ): NoteEntity = writeMutex.withLock {
        val dateKey = DateFormatting.localDateKey(today)
        noteDao.getBySubjectFolderAndDate(subjectId, folderId, dateKey)?.let { return it }

        val now = System.currentTimeMillis()
        val created = NoteEntity(
            id = Ids.newId(),
            subjectId = subjectId,
            folderId = folderId,
            localDate = dateKey,
            title = "",
            body = "",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            timezoneId = zoneId.id,
        )
        try {
            noteDao.insert(created)
            created
        } catch (t: Throwable) {
            val conflict = t is SQLiteConstraintException ||
                t.cause is SQLiteConstraintException ||
                (t.message?.contains("UNIQUE", ignoreCase = true) == true)
            if (!conflict) throw t
            noteDao.getBySubjectFolderAndDate(subjectId, folderId, dateKey)
                ?: throw IllegalStateException("Unable to open daily note")
        }
    }

    suspend fun saveNoteContent(
        noteId: String,
        title: String,
        body: String,
        expectedUpdatedAt: Long? = null,
    ): Result<NoteEntity> = writeMutex.withLock {
        runCatching {
            val existing = noteDao.getById(noteId)
                ?: throw IllegalArgumentException("Note not found")
            if (expectedUpdatedAt != null && existing.updatedAtEpochMs > expectedUpdatedAt) {
                // Newer content already persisted; keep newer.
                return@runCatching existing
            }
            val trimmedTitle = title.trim()
            val updated = existing.copy(
                title = trimmedTitle,
                body = body,
                updatedAtEpochMs = maxOf(System.currentTimeMillis(), existing.updatedAtEpochMs + 1),
            )
            noteDao.update(updated)
            updated
        }
    }

    suspend fun deleteEmptyPlaceholder(noteId: String) {
        writeMutex.withLock {
            val note = noteDao.getById(noteId) ?: return
            if (note.title.isBlank() && note.body.isBlank()) {
                noteDao.deleteById(noteId)
            }
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = writeMutex.withLock {
        runCatching { noteDao.deleteById(noteId) }
    }
}
