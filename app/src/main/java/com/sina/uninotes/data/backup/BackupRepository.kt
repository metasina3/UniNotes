package com.sina.uninotes.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sina.uninotes.data.local.db.NoteDao
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoDao
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.PhotoStatus
import com.sina.uninotes.data.local.db.SubjectDao
import com.sina.uninotes.data.local.db.SubjectEntity
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.local.files.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import com.sina.uninotes.data.LibraryAccess
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepository(
    private val context: Context,
    private val database: UniNotesDatabase,
    private val photoStorage: PhotoStorage,
    private val subjectDao: SubjectDao,
    private val noteDao: NoteDao,
    private val photoDao: PhotoDao,
) {
    suspend fun exportBackup(destination: Uri, onProgress: (Progress) -> Unit = {}): Result<Unit> =
        LibraryAccess.mutex.withLock { exportBackupUnlocked(destination, onProgress) }

    suspend fun importBackup(source: Uri, onProgress: (Progress) -> Unit = {}): Result<Unit> =
        LibraryAccess.mutex.withLock { importBackupUnlocked(source, onProgress) }

    private fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER)
            var count: Int
            while (input.read(buffer).also { count = it } >= 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fingerprint(subjects: List<SubjectEntity>, notes: List<NoteEntity>, photos: List<PhotoEntity>): String {
        val text = subjectsToJson(subjects.sortedBy { it.id }).toString() +
            notesToJson(notes.sortedBy { it.id }).toString() + photosToJson(photos.sortedBy { it.id }).toString()
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun recoverRestore() = withContext(Dispatchers.IO) {
        LibraryAccess.mutex.withLock {
            val journal = File(context.filesDir, "restore-state.json")
            val oldRoot = File(context.filesDir, "restore-old")
            val newRoot = File(context.filesDir, "restore-new")
            val liveRoot = File(context.filesDir, "subjects")
            if (journal.exists()) {
                val expected = JSONObject(journal.readText()).getString("expectedFingerprint")
                val actual = fingerprint(subjectDao.getAll(), noteDao.getAll(), photoDao.getAllReady())
                if (oldRoot.exists() && actual != expected) {
                    check(!liveRoot.exists() || liveRoot.deleteRecursively())
                    check(oldRoot.renameTo(liveRoot)) { "Could not recover previous photo library" }
                } else if (actual == expected && !liveRoot.exists() && newRoot.exists()) {
                    check(newRoot.renameTo(liveRoot))
                }
                oldRoot.deleteRecursively()
                newRoot.deleteRecursively()
                journal.delete()
            }
        }
    }

    data class Progress(val message: String, val fraction: Float)

    private suspend fun exportBackupUnlocked(
        destination: Uri,
        onProgress: (Progress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(Progress("Preparing snapshot…", 0.05f))
            val (subjects, notes, photos) = database.withTransaction {
                Triple(subjectDao.getAll(), noteDao.getAll(), photoDao.getAllReady())
            }
            val checksums = JSONObject()
            photos.forEach { checksums.put(it.id, checksum(photoStorage.resolve(it.relativePath))) }

            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("backupVersion", 2)
                .put("photoChecksums", checksums)
                .put("schemaVersion", UniNotesDatabase.SCHEMA_VERSION)
                .put("exportedAtEpochMs", System.currentTimeMillis())
                .put("subjectCount", subjects.size)
                .put("noteCount", notes.size)
                .put("photoCount", photos.size)

            context.contentResolver.openOutputStream(destination)?.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(SUBJECTS))
                    zip.write(subjectsToJson(subjects).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(NOTES))
                    zip.write(notesToJson(notes).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry(PHOTOS_META))
                    zip.write(photosToJson(photos).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    photos.forEachIndexed { index, photo ->
                        val file = photoStorage.resolve(photo.relativePath)
                        if (!file.exists()) {
                            throw IllegalStateException("Missing photo file for ${photo.id}")
                        }
                        val entryName = "photos/${photo.subjectId}/${photo.id}.jpg"
                        zip.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        val fraction = 0.15f + 0.8f * ((index + 1f) / photos.size.coerceAtLeast(1))
                        onProgress(Progress("Exporting photos…", fraction))
                    }
                }
            } ?: error("Unable to open destination")
            onProgress(Progress("Backup complete", 1f))
        }
    }

    private suspend fun importBackupUnlocked(
        source: Uri,
        onProgress: (Progress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(Progress("Validating backup…", 0.05f))
            val staging = File(context.cacheDir, "backup_stage_${System.currentTimeMillis()}").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            try {
                val extracted = extractValidated(source, staging, onProgress)
                onProgress(Progress("Replacing library…", 0.75f))
                val liveRoot = File(context.filesDir, "subjects")
                val oldRoot = File(context.filesDir, "restore-old")
                val newRoot = File(context.filesDir, "restore-new")
                val journal = File(context.filesDir, "restore-state.json")
                check(!journal.exists() && !oldRoot.exists()) { "A previous restore needs recovery. Restart UniNotes." }
                newRoot.deleteRecursively()
                newRoot.mkdirs()
                extracted.subjects.forEach { File(newRoot, it.id).mkdirs() }
                extracted.photos.forEach { photo ->
                    val staged = File(staging, "photos/${photo.subjectId}/${photo.id}.jpg")
                    val dest = File(newRoot, "${photo.subjectId}/photos/${photo.id}.jpg")
                    dest.parentFile!!.mkdirs()
                    check(staged.renameTo(dest)) { "Could not stage photo library" }
                }
                // From this point, finish the swap/transaction even if the UI is recreated.
                withContext(NonCancellable) {
                    val tempJournal = File(context.filesDir, "restore-state.tmp")
                    tempJournal.writeText(JSONObject().put("expectedFingerprint",
                        fingerprint(extracted.subjects, extracted.notes, extracted.photos)).toString())
                    check(tempJournal.renameTo(journal))
                    var movedOld = false
                    try {
                        if (liveRoot.exists()) {
                            check(liveRoot.renameTo(oldRoot)) { "Could not preserve current photo library" }
                            movedOld = true
                        }
                        check(newRoot.renameTo(liveRoot)) { "Could not activate restored photos" }
                        database.withTransaction {
                            photoDao.deleteAll()
                            noteDao.deleteAll()
                            subjectDao.deleteAll()
                            extracted.subjects.forEach { subjectDao.insert(it) }
                            extracted.notes.forEach { noteDao.insert(it) }
                            extracted.photos.forEach { photoDao.insert(it) }
                        }
                    } catch (error: Exception) {
                        if (movedOld) {
                            liveRoot.deleteRecursively()
                            check(oldRoot.renameTo(liveRoot)) { "Restart UniNotes to recover the old library" }
                        }
                        newRoot.deleteRecursively()
                        journal.delete()
                        throw error
                    }
                    oldRoot.deleteRecursively()
                    journal.delete()
                }
                onProgress(Progress("Restore complete", 1f))
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    private data class Extracted(
        val subjects: List<SubjectEntity>,
        val notes: List<NoteEntity>,
        val photos: List<PhotoEntity>,
    )

    private fun extractValidated(
        source: Uri,
        staging: File,
        onProgress: (Progress) -> Unit,
    ): Extracted {
        var manifestJson: String? = null
        var subjectsJson: String? = null
        var notesJson: String? = null
        var photosJson: String? = null
        var totalBytes = 0L
        val seen = mutableSetOf<String>()

        context.contentResolver.openInputStream(source)?.use { rawIn ->
            ZipInputStream(BufferedInputStream(rawIn)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (name.contains("..") || name.startsWith("/")) {
                        throw IllegalArgumentException("Unsafe path in backup: $name")
                    }
                    check(seen.add(name)) { "Duplicate backup entry" }
                    check(seen.size <= 50000) { "Too many backup entries" }
                    if (entry.size > MAX_ENTRY_BYTES) {
                        throw IllegalArgumentException("Backup entry too large: $name")
                    }
                    fun readMetadata(): String {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER)
                        var count: Int
                        while (zip.read(buffer).also { count = it } >= 0) {
                            totalBytes += count
                            check(output.size() + count <= 16 * 1024 * 1024 && totalBytes <= MAX_ARCHIVE_BYTES) {
                                "Backup metadata exceeds size limits"
                            }
                            output.write(buffer, 0, count)
                        }
                        return output.toString("UTF-8")
                    }
                    when (name) {
                        MANIFEST -> manifestJson = readMetadata()
                        SUBJECTS -> subjectsJson = readMetadata()
                        NOTES -> notesJson = readMetadata()
                        PHOTOS_META -> photosJson = readMetadata()
                        else -> {
                            if (name.matches(Regex("photos/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\.jpg"))) {
                                val out = File(staging, name)
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { dest ->
                                    val buffer = ByteArray(DEFAULT_BUFFER)
                                    var read: Int
                                    var written = 0L
                                    while (zip.read(buffer).also { read = it } != -1) {
                                        written += read
                                        totalBytes += read
                                        if (written > MAX_ENTRY_BYTES || totalBytes > MAX_ARCHIVE_BYTES) {
                                            throw IllegalArgumentException("Backup exceeds size limits")
                                        }
                                        dest.write(buffer, 0, read)
                                    }
                                }
                            } else if (!entry.isDirectory) error("Unexpected backup entry: $name")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Unable to open backup file")

        val manifest = JSONObject(manifestJson ?: error("Missing manifest"))
        if (manifest.optString("format") != FORMAT) {
            throw IllegalArgumentException("Unsupported backup format")
        }
        val backupVersion = manifest.optInt("backupVersion", 1)
        check(backupVersion in 1..2) { "Unsupported backup version" }
        val schema = manifest.optInt("schemaVersion", -1)
        if (schema != UniNotesDatabase.SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup schema version: $schema")
        }

        val subjects = parseSubjects(subjectsJson ?: error("Missing subjects.json"))
        val notes = parseNotes(notesJson ?: error("Missing notes.json"))
        val photos = parsePhotos(photosJson ?: error("Missing photos.json"))

        fun safeId(id: String) = id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
        check(subjects.all { safeId(it.id) && it.name.isNotBlank() }) { "Invalid subject" }
        check(subjects.map { it.id }.toSet().size == subjects.size) { "Duplicate subjects" }
        val subjectIds = subjects.map { it.id }.toSet()
        check(notes.map { it.id }.toSet().size == notes.size &&
            notes.map { it.subjectId to it.localDate }.toSet().size == notes.size) { "Duplicate daily notes" }
        check(photos.map { it.id }.toSet().size == photos.size) { "Duplicate photos" }
        notes.forEach { check(safeId(it.id) && it.subjectId in subjectIds); LocalDate.parse(it.localDate); ZoneId.of(it.timezoneId) }
        photos.forEach { check(safeId(it.id) && it.subjectId in subjectIds); LocalDate.parse(it.localDate); ZoneId.of(it.timezoneId) }
        check(manifest.optInt("subjectCount", subjects.size) == subjects.size &&
            manifest.optInt("noteCount", notes.size) == notes.size &&
            manifest.optInt("photoCount", photos.size) == photos.size) { "Backup counts do not match" }
        val hashes = manifest.optJSONObject("photoChecksums")
        photos.forEach { photo ->
            val file = File(staging, "photos/${photo.subjectId}/${photo.id}.jpg")
            if (!file.exists() || file.length() == 0L) {
                throw IllegalArgumentException("Missing photo payload for ${photo.id}")
            }
            if (backupVersion == 2) check(hashes?.optString(photo.id) == checksum(file)) { "Photo checksum mismatch" }
        }
        onProgress(Progress("Validation succeeded", 0.55f))
        return Extracted(subjects, notes, photos)
    }

    private fun subjectsToJson(subjects: List<SubjectEntity>): JSONArray =
        JSONArray().also { arr ->
            subjects.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("colorArgb", s.colorArgb)
                        .put("createdAtEpochMs", s.createdAtEpochMs)
                        .put("updatedAtEpochMs", s.updatedAtEpochMs),
                )
            }
        }

    private fun notesToJson(notes: List<NoteEntity>): JSONArray =
        JSONArray().also { arr ->
            notes.forEach { n ->
                arr.put(
                    JSONObject()
                        .put("id", n.id)
                        .put("subjectId", n.subjectId)
                        .put("localDate", n.localDate)
                        .put("title", n.title)
                        .put("body", n.body)
                        .put("createdAtEpochMs", n.createdAtEpochMs)
                        .put("updatedAtEpochMs", n.updatedAtEpochMs)
                        .put("timezoneId", n.timezoneId),
                )
            }
        }

    private fun photosToJson(photos: List<PhotoEntity>): JSONArray =
        JSONArray().also { arr ->
            photos.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("subjectId", p.subjectId)
                        .put("capturedAtEpochMs", p.capturedAtEpochMs)
                        .put("localDate", p.localDate)
                        .put("timezoneId", p.timezoneId)
                        .put("width", p.width)
                        .put("height", p.height)
                        .put("orientationDegrees", p.orientationDegrees)
                        .put("createdAtEpochMs", p.createdAtEpochMs),
                )
            }
        }

    private fun parseSubjects(json: String): List<SubjectEntity> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    SubjectEntity(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        colorArgb = o.getLong("colorArgb"),
                        createdAtEpochMs = o.getLong("createdAtEpochMs"),
                        updatedAtEpochMs = o.getLong("updatedAtEpochMs"),
                    ),
                )
            }
        }
    }

    private fun parseNotes(json: String): List<NoteEntity> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    NoteEntity(
                        id = o.getString("id"),
                        subjectId = o.getString("subjectId"),
                        localDate = o.getString("localDate"),
                        title = o.optString("title", ""),
                        body = o.optString("body", ""),
                        createdAtEpochMs = o.getLong("createdAtEpochMs"),
                        updatedAtEpochMs = o.getLong("updatedAtEpochMs"),
                        timezoneId = o.optString("timezoneId", "UTC"),
                    ),
                )
            }
        }
    }

    private fun parsePhotos(json: String): List<PhotoEntity> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val subjectId = o.getString("subjectId")
                add(
                    PhotoEntity(
                        id = id,
                        subjectId = subjectId,
                        relativePath = "subjects/$subjectId/photos/$id.jpg",
                        thumbnailRelativePath = null,
                        capturedAtEpochMs = o.getLong("capturedAtEpochMs"),
                        localDate = o.getString("localDate"),
                        timezoneId = o.optString("timezoneId", "UTC"),
                        width = o.optInt("width", 0),
                        height = o.optInt("height", 0),
                        orientationDegrees = o.optInt("orientationDegrees", 0),
                        status = PhotoStatus.READY,
                        createdAtEpochMs = o.optLong("createdAtEpochMs", o.getLong("capturedAtEpochMs")),
                    ),
                )
            }
        }
    }

    companion object {
        const val FORMAT = "uninotes-backup"
        const val MANIFEST = "manifest.json"
        const val SUBJECTS = "subjects.json"
        const val NOTES = "notes.json"
        const val PHOTOS_META = "photos.json"
        private const val MAX_ENTRY_BYTES = 80L * 1024L * 1024L
        private const val MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L
        private const val DEFAULT_BUFFER = 64 * 1024
    }
}
