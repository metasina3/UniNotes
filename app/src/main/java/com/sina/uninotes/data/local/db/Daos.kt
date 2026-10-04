package com.sina.uninotes.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query(
        """
        SELECT s.id, s.name, s.color_argb, s.created_at_epoch_ms, s.updated_at_epoch_ms,
               (SELECT COUNT(*) FROM photos p WHERE p.subject_id = s.id AND p.status = 'READY') AS photo_count,
               (SELECT COUNT(*) FROM notes n WHERE n.subject_id = s.id AND (LENGTH(TRIM(n.body)) > 0 OR LENGTH(TRIM(n.title)) > 0)) AS note_count
        FROM subjects s
        ORDER BY s.updated_at_epoch_ms DESC
        """,
    )
    fun observeSubjectsWithCounts(): Flow<List<SubjectWithCounts>>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubjectEntity?

    @Query("SELECT * FROM subjects ORDER BY name ASC")
    suspend fun getAll(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subject: SubjectEntity)

    @Update
    suspend fun update(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface FolderDao {
    @Query(
        """
        SELECT f.id, f.subject_id, f.name, f.sort_order, f.created_at_epoch_ms, f.updated_at_epoch_ms,
               (SELECT COUNT(*) FROM photos p WHERE p.subject_id = f.subject_id AND p.folder_id = f.id AND p.status = 'READY') AS photo_count,
               (SELECT COUNT(*) FROM notes n WHERE n.subject_id = f.subject_id AND n.folder_id = f.id AND (LENGTH(TRIM(n.body)) > 0 OR LENGTH(TRIM(n.title)) > 0)) AS note_count
        FROM folders f
        WHERE f.subject_id = :subjectId
        ORDER BY f.sort_order ASC, f.created_at_epoch_ms ASC
        """,
    )
    fun observeFoldersWithCounts(subjectId: String): Flow<List<FolderWithCounts>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE subject_id = :subjectId ORDER BY sort_order ASC")
    suspend fun getForSubject(subjectId: String): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM folders WHERE subject_id = :subjectId")
    suspend fun maxSortOrder(subjectId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folders WHERE subject_id = :subjectId")
    suspend fun deleteForSubject(subjectId: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE subject_id = :subjectId
          AND folder_id = :folderId
          AND (LENGTH(TRIM(body)) > 0 OR LENGTH(TRIM(title)) > 0)
        ORDER BY local_date DESC, updated_at_epoch_ms DESC
        """,
    )
    fun observeNotesForFolder(subjectId: String, folderId: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE subject_id = :subjectId
          AND (LENGTH(TRIM(body)) > 0 OR LENGTH(TRIM(title)) > 0)
        ORDER BY local_date DESC, updated_at_epoch_ms DESC
        """,
    )
    fun observeNotesForSubject(subjectId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE subject_id = :subjectId AND folder_id = :folderId AND local_date = :localDate
        LIMIT 1
        """,
    )
    suspend fun getBySubjectFolderAndDate(
        subjectId: String,
        folderId: String,
        localDate: String,
    ): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes WHERE subject_id = :subjectId")
    suspend fun deleteForSubject(subjectId: String)

    @Query("DELETE FROM notes WHERE subject_id = :subjectId AND folder_id = :folderId")
    suspend fun deleteForFolder(subjectId: String, folderId: String)

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface PhotoDao {
    @Query(
        """
        SELECT * FROM photos
        WHERE subject_id = :subjectId AND folder_id = :folderId AND status = 'READY'
        ORDER BY sort_order ASC, captured_at_epoch_ms DESC, id DESC
        """,
    )
    fun pagingPhotosForFolder(subjectId: String, folderId: String): PagingSource<Int, PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE subject_id = :subjectId AND folder_id = :folderId AND status = 'READY'
        ORDER BY sort_order ASC, captured_at_epoch_ms DESC, id DESC
        """,
    )
    fun observePhotosForFolder(subjectId: String, folderId: String): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos
        WHERE subject_id = :subjectId AND status = 'READY'
        ORDER BY sort_order ASC, captured_at_epoch_ms DESC, id DESC
        """,
    )
    fun observePhotosForSubject(subjectId: String): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos
        WHERE subject_id = :subjectId AND folder_id = :folderId AND status = 'READY'
        ORDER BY sort_order DESC, captured_at_epoch_ms DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatestPhoto(subjectId: String, folderId: String): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PhotoEntity?

    @Query(
        """
        SELECT * FROM photos
        WHERE subject_id = :subjectId AND folder_id = :folderId AND status = 'READY'
        ORDER BY sort_order ASC, captured_at_epoch_ms DESC, id DESC
        """,
    )
    suspend fun getReadyForFolder(subjectId: String, folderId: String): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE subject_id = :subjectId AND status = 'READY'
        ORDER BY sort_order ASC, captured_at_epoch_ms DESC, id DESC
        """,
    )
    suspend fun getReadyForSubject(subjectId: String): List<PhotoEntity>

    @Query(
        """
        SELECT COALESCE(MAX(sort_order), -1) FROM photos
        WHERE subject_id = :subjectId AND folder_id = :folderId
        """,
    )
    suspend fun maxSortOrder(subjectId: String, folderId: String): Int

    @Query("SELECT * FROM photos WHERE status = 'PENDING'")
    suspend fun getPending(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE status != 'READY'")
    suspend fun getUnfinished(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: PhotoEntity)

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM photos WHERE subject_id = :subjectId")
    suspend fun deleteForSubject(subjectId: String)

    @Query("SELECT * FROM photos WHERE subject_id = :subjectId AND folder_id = :folderId")
    suspend fun getAllForFolder(subjectId: String, folderId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE status = 'READY'")
    suspend fun getAllReady(): List<PhotoEntity>

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
}
