package com.sina.uninotes.data.repository

import android.graphics.BitmapFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.sina.uninotes.data.local.db.PhotoDao
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.PhotoStatus
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.data.local.files.ThumbnailGenerator
import com.sina.uninotes.util.DateFormatting
import com.sina.uninotes.util.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import com.sina.uninotes.data.LibraryAccess
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val photoStorage: PhotoStorage,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    private val captureMutex = LibraryAccess.mutex

    fun pagingPhotos(subjectId: String): Flow<PagingData<PhotoEntity>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 20, enablePlaceholders = false),
        pagingSourceFactory = { photoDao.pagingPhotosForSubject(subjectId) },
    ).flow

    fun observePhotos(subjectId: String): Flow<List<PhotoEntity>> = photoDao.observePhotosForSubject(subjectId)
    fun observeLatest(subjectId: String): Flow<PhotoEntity?> = photoDao.observeLatestPhoto(subjectId)
    suspend fun getPhoto(id: String): PhotoEntity? = photoDao.getById(id)
    suspend fun getReadyForSubject(subjectId: String): List<PhotoEntity> = photoDao.getReadyForSubject(subjectId)

    suspend fun beginCapture(subjectId: String, capturedAtEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()): PhotoEntity = captureMutex.withLock {
        beginCaptureUnlocked(subjectId, capturedAtEpochMs, zoneId)
    }

    // Persist ownership BEFORE asking CameraX to write the original JPEG, so recovery can find it.
    private suspend fun beginCaptureUnlocked(
        subjectId: String,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PhotoEntity = withContext(Dispatchers.IO) {
        photoStorage.ensureFreeSpace()
        val id = Ids.newId()
        val photo = PhotoEntity(
            id, subjectId, photoStorage.relativeOriginalPath(subjectId, id), null,
            capturedAtEpochMs, DateFormatting.localDateKeyFromEpoch(capturedAtEpochMs, zoneId),
            zoneId.id, 0, 0, 0, PhotoStatus.PENDING, capturedAtEpochMs,
        )
        photoDao.insert(photo)
        photo
    }

    fun captureFile(photo: PhotoEntity): File = photoStorage.tempFile(photo.subjectId, photo.id)

    suspend fun completeCapture(photo: PhotoEntity): Result<PhotoEntity> = withContext(Dispatchers.IO) {
        captureMutex.withLock { runCatching { finalizeCapture(photo) } }
    }

    private suspend fun finalizeCapture(photo: PhotoEntity): PhotoEntity {
        check(photoDao.getById(photo.id) != null) { "Subject or capture no longer exists" }
        val original = photoStorage.originalFile(photo.subjectId, photo.id)
        val temp = photoStorage.tempFile(photo.subjectId, photo.id)
        if (!original.exists()) photoStorage.finalizeTemp(temp, original)
        original.inputStream().use { input ->
            check(input.read() == 0xFF && input.read() == 0xD8 && input.read() == 0xFF) {
                "Captured JPEG is incomplete"
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Captured photo is incomplete" }
        val thumb = photoStorage.thumbnailFile(photo.subjectId, photo.id)
        val thumbOk = thumbnailGenerator.generate(original, thumb)
        val ready = photo.copy(
            width = bounds.outWidth, height = bounds.outHeight,
            thumbnailRelativePath = if (thumbOk) photoStorage.relativeThumbnailPath(photo.subjectId, photo.id) else null,
            status = PhotoStatus.READY,
        )
        photoDao.update(ready)
        temp.delete()
        return ready
    }

    suspend fun discardCapture(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
            photoDao.deleteById(photo.id)
        }
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            runCatching {
                val photo = photoDao.getById(photoId) ?: return@runCatching
                // Tombstone remains in Room until file deletion succeeds, including across restarts.
                photoDao.update(photo.copy(status = PhotoStatus.DELETING))
                photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
                photoDao.deleteById(photoId)
            }
        }
    }

    suspend fun recoverInterruptedCaptures() = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            photoDao.getUnfinished().forEach { photo ->
                runCatching {
                    if (photo.status == PhotoStatus.PENDING) {
                        finalizeCapture(photo)
                    } else {
                        photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
                        photoDao.deleteById(photo.id)
                    }
                }.onFailure {
                    if (photo.status == PhotoStatus.PENDING) {
                        runCatching {
                            photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
                            photoDao.deleteById(photo.id)
                        }
                    }
                }
            }
        }
    }

    fun resolveFile(relativePath: String): File = photoStorage.resolve(relativePath)
}
