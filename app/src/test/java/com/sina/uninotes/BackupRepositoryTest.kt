package com.sina.uninotes

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sina.uninotes.data.backup.BackupRepository
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.PhotoStatus
import com.sina.uninotes.data.local.db.SubjectEntity
import com.sina.uninotes.data.local.db.UniNotesDatabase
import com.sina.uninotes.data.local.files.PhotoStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: UniNotesDatabase
    private lateinit var storage: PhotoStorage
    private lateinit var backup: BackupRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, UniNotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = PhotoStorage(context)
        backup = BackupRepository(
            context = context,
            database = db,
            photoStorage = storage,
            subjectDao = db.subjectDao(),
            noteDao = db.noteDao(),
            photoDao = db.photoDao(),
            folderDao = db.folderDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportImportRoundTrip() = runBlocking {
        val now = System.currentTimeMillis()
        val subject = SubjectEntity("s1", "ساختمان داده", 0xFF3195FF, now, now)
        db.subjectDao().insert(subject)
        db.noteDao().insert(
            NoteEntity(
                id = "n1",
                subjectId = "s1",
                folderId = "",
                localDate = "2026-10-04",
                title = "T",
                body = "body",
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                timezoneId = "UTC",
            ),
        )
        val photoFile = storage.originalFile("s1", "p1")
        photoFile.parentFile?.mkdirs()
        photoFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        db.photoDao().insert(
            PhotoEntity(
                id = "p1",
                subjectId = "s1",
                relativePath = storage.relativeOriginalPath("s1", "p1"),
                thumbnailRelativePath = null,
                capturedAtEpochMs = now,
                localDate = "2026-10-04",
                timezoneId = "UTC",
                width = 10,
                height = 10,
                orientationDegrees = 0,
                status = PhotoStatus.READY,
                createdAtEpochMs = now,
            ),
        )

        val out = File(context.cacheDir, "roundtrip.zip")
        val exportUri = Uri.fromFile(out)
        backup.exportBackup(exportUri).getOrThrow()

        db.photoDao().deleteAll()
        db.noteDao().deleteAll()
        db.subjectDao().deleteAll()
        storage.deleteSubjectFiles("s1")

        backup.importBackup(exportUri).getOrThrow()
        assertThat(db.subjectDao().getById("s1")?.name).isEqualTo("ساختمان داده")
        assertThat(db.noteDao().getById("n1")?.body).isEqualTo("body")
        assertThat(storage.originalFile("s1", "p1").exists()).isTrue()
    }

    @Test
    fun rejectsPathTraversalBackup() = runBlocking {
        val bad = File(context.cacheDir, "bad.zip")
        ZipOutputStream(bad.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                """{"format":"uninotes-backup","schemaVersion":1}""".toByteArray(),
            )
            zip.closeEntry()
        }
        val result = backup.importBackup(Uri.fromFile(bad))
        assertThat(result.isFailure).isTrue()
    }
    @Test
    fun malformedBackupPreservesExistingLibrary() = runBlocking {
        db.subjectDao().insert(SubjectEntity("keep", "Existing", 0xFF3195FF, 1, 1))
        val original = storage.originalFile("keep", "photo")
        original.writeText("original")
        val bad = File(context.cacheDir, "invalid.zip")
        ZipOutputStream(bad.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        assertThat(backup.importBackup(Uri.fromFile(bad)).isFailure).isTrue()
        assertThat(db.subjectDao().getById("keep")?.name).isEqualTo("Existing")
        assertThat(original.readText()).isEqualTo("original")
    }

    @Test
    fun interruptedRestoreRecoversPreviousFiles() = runBlocking {
        db.subjectDao().insert(SubjectEntity("keep", "Existing", 0xFF3195FF, 1, 1))
        storage.originalFile("keep", "photo").writeText("original")
        val live = File(context.filesDir, "subjects")
        val old = File(context.filesDir, "restore-old")
        old.deleteRecursively()
        check(live.renameTo(old))
        live.mkdirs()
        File(context.filesDir, "restore-state.json").writeText(
            """{"expectedFingerprint":"uncommitted-new-library"}""",
        )
        backup.recoverRestore()
        assertThat(storage.originalFile("keep", "photo").readText()).isEqualTo("original")
        assertThat(old.exists()).isFalse()
    }

}
