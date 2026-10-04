package com.sina.uninotes.data.repository

import androidx.room.withTransaction
import com.sina.uninotes.data.local.db.SubjectDao
import com.sina.uninotes.data.local.db.SubjectEntity
import com.sina.uninotes.data.local.db.SubjectWithCounts
import com.sina.uninotes.data.local.db.NoteDao
import com.sina.uninotes.data.local.db.PhotoDao
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.util.Ids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.sina.uninotes.data.LibraryAccess

class SubjectRepository(
    private val subjectDao: SubjectDao,
    private val photoDao: PhotoDao,
    private val noteDao: NoteDao,
    private val photoStorage: PhotoStorage,
    private val database: UniNotesDatabase? = null,
) {
    suspend fun createSubject(name: String, colorArgb: Long): Result<SubjectEntity> = withContext(Dispatchers.IO) {
        LibraryAccess.mutex.withLock { createSubjectUnlocked(name, colorArgb) }
    }

    suspend fun renameSubject(id: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        LibraryAccess.mutex.withLock { renameSubjectUnlocked(id, name) }
    }

    suspend fun deleteSubject(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        LibraryAccess.mutex.withLock { deleteSubjectUnlocked(id) }
    }

    suspend fun touchSubject(id: String): Unit = withContext(Dispatchers.IO) {
        LibraryAccess.mutex.withLock { touchSubjectUnlocked(id) }
    }

    fun observeSubjects(): Flow<List<SubjectWithCounts>> = subjectDao.observeSubjectsWithCounts()

    fun observeSubject(id: String): Flow<SubjectEntity?> = subjectDao.observeById(id)

    private suspend fun createSubjectUnlocked(name: String, colorArgb: Long): Result<SubjectEntity> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Subject name cannot be blank"))
        val now = System.currentTimeMillis()
        val entity = SubjectEntity(
            id = Ids.newId(),
            name = trimmed,
            colorArgb = colorArgb,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        return runCatching {
            subjectDao.insert(entity)
            photoStorage.subjectDir(entity.id)
            entity
        }
    }

    private suspend fun renameSubjectUnlocked(id: String, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Subject name cannot be blank"))
        val existing = subjectDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Subject not found"))
        return runCatching {
            subjectDao.update(
                existing.copy(
                    name = trimmed,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun deleteSubjectUnlocked(id: String): Result<Unit> = runCatching {
        val db = database
        if (db != null) {
            db.withTransaction {
                photoDao.deleteForSubject(id)
                noteDao.deleteForSubject(id)
                subjectDao.deleteById(id)
            }
        } else {
            photoDao.deleteForSubject(id)
            noteDao.deleteForSubject(id)
            subjectDao.deleteById(id)
        }
        photoStorage.deleteSubjectFiles(id)
    }

    private suspend fun touchSubjectUnlocked(id: String) {
        val existing = subjectDao.getById(id) ?: return
        subjectDao.update(existing.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun getSubject(id: String): SubjectEntity? = subjectDao.getById(id)
}
