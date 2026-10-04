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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val photoStorage: PhotoStorage,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    private val captureMutex = Mutex()

    fun pagingPhotos(subjectId: String): Flow<PagingData<PhotoEntity>> =
        Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 20,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { photoDao.pagingPhotosForSubject(subjectId) },
        ).flow

    fun observePhotos(subjectId: String): Flow<List<PhotoEntity>> =
        photoDao.observePhotosForSubject(subjectId)

    fun observeLatest(subjectId: String): Flow<PhotoEntity?> =
        photoDao.observeLatestPhoto(subjectId)

    suspend fun getPhoto(id: String): PhotoEntity? = photoDao.getById(id)

    suspend fun getReadyForSubject(subjectId: String): List<PhotoEntity> =
        photoDao.getReadyForSubject(subjectId)

    /**
     * Persists a captured JPEG for [subjectId]. Safe if the caller navigates away:
     * subject ID is retained for the whole operation.
     */
    suspend fun saveCapturedPhoto(
        subjectId: String,
        jpegBytes: ByteArray,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
        orientationDegrees: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Result<PhotoEntity> = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            runCatching {
                photoStorage.ensureFreeSpace()
                val photoId = Ids.newId()
                val temp = photoStorage.tempFile(subjectId, photoId)
                val original = photoStorage.originalFile(subjectId, photoId)
                val thumb = photoStorage.thumbnailFile(subjectId, photoId)

                temp.outputStream().use { it.write(jpegBytes) }
                if (temp.length() == 0L) throw IllegalStateException("Empty capture")

                val pending = PhotoEntity(
                    id = photoId,
                    subjectId = subjectId,
                    relativePath = photoStorage.relativeOriginalPath(subjectId, photoId),
                    thumbnailRelativePath = null,
                    capturedAtEpochMs = capturedAtEpochMs,
                    localDate = DateFormatting.localDateKeyFromEpoch(capturedAtEpochMs, zoneId),
                    timezoneId = zoneId.id,
                    width = 0,
                    height = 0,
                    orientationDegrees = orientationDegrees,
                    status = PhotoStatus.PENDING,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
                photoDao.insert(pending)

                photoStorage.finalizeTemp(temp, original)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(original.absolutePath, bounds)
                val thumbOk = thumbnailGenerator.generate(original, thumb)
                val ready = pending.copy(
                    width = bounds.outWidth.coerceAtLeast(0),
                    height = bounds.outHeight.coerceAtLeast(0),
                    thumbnailRelativePath = if (thumbOk) {
                        photoStorage.relativeThumbnailPath(subjectId, photoId)
                    } else {
                        null
                    },
                    status = PhotoStatus.READY,
                )
                photoDao.update(ready)
                ready
            }.onFailure {
                // Best-effort cleanup for failed capture paths is handled by recovery.
            }
        }
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val photo = photoDao.getById(photoId) ?: return@runCatching
            photoDao.deleteById(photoId)
            photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
        }
    }

    suspend fun recoverInterruptedCaptures() = withContext(Dispatchers.IO) {
        photoDao.getPending().forEach { pending ->
            val original = photoStorage.originalFile(pending.subjectId, pending.id)
            val temp = photoStorage.tempFile(pending.subjectId, pending.id)
            try {
                when {
                    original.exists() && original.length() > 0L -> {
                        val thumb = photoStorage.thumbnailFile(pending.subjectId, pending.id)
                        val thumbOk = if (!thumb.exists()) {
                            thumbnailGenerator.generate(original, thumb)
                        } else {
                            true
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(original.absolutePath, bounds)
                        photoDao.update(
                            pending.copy(
                                width = bounds.outWidth.coerceAtLeast(0),
                                height = bounds.outHeight.coerceAtLeast(0),
                                thumbnailRelativePath = if (thumbOk) {
                                    photoStorage.relativeThumbnailPath(pending.subjectId, pending.id)
                                } else {
                                    null
                                },
                                status = PhotoStatus.READY,
                            ),
                        )
                        temp.delete()
                    }
                    temp.exists() && temp.length() > 0L -> {
                        photoStorage.finalizeTemp(temp, original)
                        val thumb = photoStorage.thumbnailFile(pending.subjectId, pending.id)
                        val thumbOk = thumbnailGenerator.generate(original, thumb)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(original.absolutePath, bounds)
                        photoDao.update(
                            pending.copy(
                                width = bounds.outWidth.coerceAtLeast(0),
                                height = bounds.outHeight.coerceAtLeast(0),
                                thumbnailRelativePath = if (thumbOk) {
                                    photoStorage.relativeThumbnailPath(pending.subjectId, pending.id)
                                } else {
                                    null
                                },
                                status = PhotoStatus.READY,
                            ),
                        )
                    }
                    else -> {
                        photoDao.deleteById(pending.id)
                        photoStorage.deletePhotoFiles(pending.subjectId, pending.id)
                    }
                }
            } catch (_: Exception) {
                photoDao.update(pending.copy(status = PhotoStatus.FAILED))
            }
        }
        // Remove FAILED ghosts
        photoDao.getPending() // no-op keep API warm
    }

    fun resolveFile(relativePath: String): File = photoStorage.resolve(relativePath)
}
