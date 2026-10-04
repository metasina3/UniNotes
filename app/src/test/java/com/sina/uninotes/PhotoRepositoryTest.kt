package com.sina.uninotes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sina.uninotes.data.local.db.*
import com.sina.uninotes.data.local.files.PhotoStorage
import com.sina.uninotes.data.local.files.ThumbnailGenerator
import com.sina.uninotes.data.repository.PhotoRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoRepositoryTest {
    private lateinit var db: UniNotesDatabase
    private lateinit var storage: PhotoStorage
    private lateinit var photos: PhotoRepository
    private lateinit var context: Context

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UniNotesDatabase::class.java).allowMainThreadQueries().build()
        storage = PhotoStorage(context)
        photos = PhotoRepository(db.photoDao(), storage, ThumbnailGenerator(context))
        db.subjectDao().insert(SubjectEntity("s1", "Physics", 0xFFF5A35C, 1, 1))
    }
    @After fun cleanup() { db.close() }

    @Test fun pendingCaptureRetainsSubjectBeforeFileIsWritten() = runBlocking {
        val photo = photos.beginCapture("s1")
        assertThat(db.photoDao().getById(photo.id)?.subjectId).isEqualTo("s1")
        assertThat(db.photoDao().getAllReady()).isEmpty()
        photos.discardCapture(photo)
        assertThat(db.photoDao().getById(photo.id)).isNull()
    }

    @Test fun incompleteCaptureIsRemovedDuringRecovery() = runBlocking {
        val photo = photos.beginCapture("s1")
        photos.captureFile(photo).writeBytes(byteArrayOf(1, 2, 3))
        // A new repository represents the next process, with no active CameraX writer.
        photos = PhotoRepository(db.photoDao(), storage, ThumbnailGenerator(context))
        photos.recoverInterruptedCaptures()
        assertThat(db.photoDao().getById(photo.id)).isNull()
        assertThat(photos.captureFile(photo).exists()).isFalse()
    }

    @Test fun startupRecoveryDoesNotRemoveAnActiveCapture() = runBlocking {
        val photo = photos.beginCapture("s1")
        photos.captureFile(photo).writeBytes(byteArrayOf(1, 2, 3))
        photos.recoverInterruptedCaptures()
        assertThat(db.photoDao().getById(photo.id)?.status).isEqualTo(PhotoStatus.PENDING)
        assertThat(photos.captureFile(photo).exists()).isTrue()
        photos.discardCapture(photo)
    }

    @Test fun deletionTombstoneRecoversAcrossRestart() = runBlocking {
        val photo = photos.beginCapture("s1")
        storage.originalFile("s1", photo.id).writeBytes(byteArrayOf(1, 2))
        db.photoDao().update(photo.copy(status = PhotoStatus.DELETING))
        photos.recoverInterruptedCaptures()
        assertThat(db.photoDao().getById(photo.id)).isNull()
        assertThat(storage.originalFile("s1", photo.id).exists()).isFalse()
    }
}
