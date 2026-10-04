package com.sina.uninotes.data.repository

import androidx.room.withTransaction
import com.sina.uninotes.data.LibraryAccess
import com.sina.uninotes.data.local.db.FolderDao
import com.sina.uninotes.data.local.db.FolderEntity
import com.sina.uninotes.data.local.db.FolderWithCounts
import com.sina.uninotes.data.local.db.NoteDao
import com.sina.uninotes.data.local.db.PhotoDao
import com.sina.uninotes.data.local.db.PhotoStatus
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.util.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FolderRepository(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val photoDao: PhotoDao,
    private val photoStorage: PhotoStorage,
    private val database: UniNotesDatabase,
) {
    fun observeFolders(subjectId: String): Flow<List<FolderWithCounts>> =
        folderDao.observeFoldersWithCounts(subjectId)

    fun observeFolder(id: String): Flow<FolderEntity?> = folderDao.observeById(id)

    suspend fun getFolder(id: String): FolderEntity? = folderDao.getById(id)

    suspend fun createFolder(subjectId: String, name: String): Result<FolderEntity> =
        withContext(Dispatchers.IO) {
            LibraryAccess.mutex.withLock {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    return@withLock Result.failure(IllegalArgumentException("Folder name cannot be blank"))
                }
                val now = System.currentTimeMillis()
                val entity = FolderEntity(
                    id = Ids.newId(),
                    subjectId = subjectId,
                    name = trimmed,
                    sortOrder = folderDao.maxSortOrder(subjectId) + 1,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                )
                runCatching {
                    folderDao.insert(entity)
                    entity
                }
            }
        }

    suspend fun renameFolder(id: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        LibraryAccess.mutex.withLock {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return@withLock Result.failure(IllegalArgumentException("Folder name cannot be blank"))
            }
            val existing = folderDao.getById(id)
                ?: return@withLock Result.failure(IllegalArgumentException("Folder not found"))
            runCatching {
                folderDao.update(
                    existing.copy(
                        name = trimmed,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun deleteFolder(subjectId: String, folderId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            LibraryAccess.mutex.withLock {
                runCatching {
                    database.withTransaction {
                        val photos = photoDao.getAllForFolder(subjectId, folderId)
                        photos.forEach { photo ->
                            photoDao.update(photo.copy(status = PhotoStatus.DELETING))
                            photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
                            photoDao.deleteById(photo.id)
                        }
                        noteDao.deleteForFolder(subjectId, folderId)
                        folderDao.deleteById(folderId)
                    }
                }
            }
        }
}
