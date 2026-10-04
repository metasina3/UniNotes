package com.sina.uninotes.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.sina.uninotes.data.LibraryAccess
import com.sina.uninotes.data.local.db.PhotoDao
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.PhotoStatus
import com.sina.uninotes.data.local.db.RootFolder
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.data.local.files.ThumbnailGenerator
import com.sina.uninotes.util.DateFormatting
import com.sina.uninotes.util.Ids
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val photoStorage: PhotoStorage,
    private val thumbnailGenerator: ThumbnailGenerator,
) {
    private val captureMutex = LibraryAccess.mutex
    // A startup recovery job must never finalize/delete a capture CameraX is still writing.
    // This set is intentionally process-local: unfinished captures become recoverable after restart.
    private val activeCaptures = mutableSetOf<String>()

    fun pagingPhotos(
        subjectId: String,
        folderId: String = RootFolder.ID,
    ): Flow<PagingData<PhotoEntity>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 20, enablePlaceholders = false),
        pagingSourceFactory = { photoDao.pagingPhotosForFolder(subjectId, folderId) },
    ).flow

    fun observePhotos(
        subjectId: String,
        folderId: String = RootFolder.ID,
    ): Flow<List<PhotoEntity>> = photoDao.observePhotosForFolder(subjectId, folderId)

    fun observeLatest(
        subjectId: String,
        folderId: String = RootFolder.ID,
    ): Flow<PhotoEntity?> = photoDao.observeLatestPhoto(subjectId, folderId)

    suspend fun getPhoto(id: String): PhotoEntity? = photoDao.getById(id)

    suspend fun getReadyForFolder(
        subjectId: String,
        folderId: String = RootFolder.ID,
    ): List<PhotoEntity> = photoDao.getReadyForFolder(subjectId, folderId)

    suspend fun getReadyForSubject(subjectId: String): List<PhotoEntity> =
        photoDao.getReadyForSubject(subjectId)

    suspend fun beginCapture(
        subjectId: String,
        folderId: String = RootFolder.ID,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PhotoEntity = captureMutex.withLock {
        beginCaptureUnlocked(subjectId, folderId, capturedAtEpochMs, zoneId)
    }

    // Persist ownership BEFORE asking CameraX to write the original JPEG, so recovery can find it.
    private suspend fun beginCaptureUnlocked(
        subjectId: String,
        folderId: String = RootFolder.ID,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PhotoEntity = withContext(Dispatchers.IO) {
        photoStorage.ensureFreeSpace()
        val id = Ids.newId()
        val sortOrder = photoDao.maxSortOrder(subjectId, folderId) + 1
        val photo = PhotoEntity(
            id = id,
            subjectId = subjectId,
            folderId = folderId,
            relativePath = photoStorage.relativeOriginalPath(subjectId, id),
            thumbnailRelativePath = null,
            capturedAtEpochMs = capturedAtEpochMs,
            localDate = DateFormatting.localDateKeyFromEpoch(capturedAtEpochMs, zoneId),
            timezoneId = zoneId.id,
            width = 0,
            height = 0,
            orientationDegrees = 0,
            status = PhotoStatus.PENDING,
            createdAtEpochMs = capturedAtEpochMs,
            sortOrder = sortOrder,
        )
        photoDao.insert(photo)
        activeCaptures.add(id)
        photo
    }

    fun captureFile(photo: PhotoEntity): File = photoStorage.tempFile(photo.subjectId, photo.id)

    suspend fun completeCapture(photo: PhotoEntity): Result<PhotoEntity> = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            runCatching { finalizeCapture(photo) }.also { activeCaptures.remove(photo.id) }
        }
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
            width = bounds.outWidth,
            height = bounds.outHeight,
            thumbnailRelativePath = if (thumbOk) {
                photoStorage.relativeThumbnailPath(photo.subjectId, photo.id)
            } else {
                null
            },
            status = PhotoStatus.READY,
        )
        photoDao.update(ready)
        temp.delete()
        return ready
    }

    suspend fun discardCapture(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            try {
                photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
                photoDao.deleteById(photo.id)
            } finally {
                activeCaptures.remove(photo.id)
            }
        }
    }

    /**
     * Import a gallery/document image into private subject storage.
     * Uses only the picker-granted URI — no broad storage permission.
     */
    suspend fun importFromUri(
        resolver: ContentResolver,
        subjectId: String,
        uri: Uri,
        folderId: String = RootFolder.ID,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Result<PhotoEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val jpegBytes = readUriAsJpegBytes(resolver, uri)
            val capturedAt = readCaptureTimeMs(resolver, uri) ?: System.currentTimeMillis()
            val photo = beginCapture(subjectId, folderId, capturedAt, zoneId)
            try {
                captureFile(photo).outputStream().use { it.write(jpegBytes) }
                completeCapture(photo).getOrThrow()
            } catch (t: Throwable) {
                discardCapture(photo)
                throw t
            }
        }
    }

    suspend fun importFromUris(
        resolver: ContentResolver,
        subjectId: String,
        uris: List<Uri>,
        folderId: String = RootFolder.ID,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            uris.forEach { uri ->
                importFromUri(resolver, subjectId, uri, folderId).onSuccess { count++ }
            }
            count
        }
    }

    private fun readUriAsJpegBytes(resolver: ContentResolver, uri: Uri): ByteArray {
        val mime = resolver.getType(uri).orEmpty()
        resolver.openInputStream(uri)?.use { input ->
            val raw = input.readBytes()
            if (raw.isEmpty()) throw IllegalStateException("Empty image")
            if (mime.equals("image/jpeg", ignoreCase = true) || isJpeg(raw)) return raw
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                ?: throw IllegalStateException("Unsupported image")
            return ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    "Could not convert image"
                }
                bitmap.recycle()
                out.toByteArray()
            }
        } ?: throw IllegalStateException("Unable to open selected image")
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    private fun readCaptureTimeMs(resolver: ContentResolver, uri: Uri): Long? = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (date.isNullOrBlank()) return null
            val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            format.parse(date)?.time
        }
    } catch (_: Exception) {
        null
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

    suspend fun deletePhotos(photoIds: Collection<String>): Result<Int> = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            runCatching {
                var deleted = 0
                photoIds.forEach { id ->
                    val photo = photoDao.getById(id) ?: return@forEach
                    photoDao.update(photo.copy(status = PhotoStatus.DELETING))
                    photoStorage.deletePhotoFiles(photo.subjectId, photo.id)
                    photoDao.deleteById(id)
                    deleted++
                }
                deleted
            }
        }
    }

    /**
     * Persist a custom gallery order for photos in the same subject/folder.
     * [orderedIds] must contain every READY photo id exactly once.
     */
    suspend fun reorderPhotos(
        subjectId: String,
        folderId: String,
        orderedIds: List<String>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            runCatching {
                val current = photoDao.getReadyForFolder(subjectId, folderId)
                check(current.map { it.id }.toSet() == orderedIds.toSet()) {
                    "Photo order does not match gallery contents"
                }
                val byId = current.associateBy { it.id }
                orderedIds.forEachIndexed { index, id ->
                    val photo = byId.getValue(id)
                    if (photo.sortOrder != index) {
                        photoDao.update(photo.copy(sortOrder = index))
                    }
                }
            }
        }
    }

    suspend fun movePhoto(
        subjectId: String,
        folderId: String,
        photoId: String,
        delta: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            runCatching {
                val current = photoDao.getReadyForFolder(subjectId, folderId).toMutableList()
                val index = current.indexOfFirst { it.id == photoId }
                check(index >= 0) { "Photo not found" }
                val target = (index + delta).coerceIn(0, current.lastIndex)
                if (target == index) return@runCatching
                val item = current.removeAt(index)
                current.add(target, item)
                current.forEachIndexed { i, photo ->
                    if (photo.sortOrder != i) photoDao.update(photo.copy(sortOrder = i))
                }
            }
        }
    }

    suspend fun recoverInterruptedCaptures() = withContext(Dispatchers.IO) {
        captureMutex.withLock {
            photoDao.getUnfinished().forEach { photo ->
                if (photo.status == PhotoStatus.PENDING && photo.id in activeCaptures) return@forEach
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
