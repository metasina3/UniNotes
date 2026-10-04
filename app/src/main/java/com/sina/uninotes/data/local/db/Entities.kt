package com.sina.uninotes.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subject_id", "local_date"], unique = true),
        Index(value = ["subject_id", "updated_at_epoch_ms"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    /** ISO local date yyyy-MM-dd in the device's default timezone at creation. */
    @ColumnInfo(name = "local_date") val localDate: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subject_id", "captured_at_epoch_ms"]),
        Index(value = ["subject_id", "local_date"]),
        Index(value = ["status"]),
    ],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "thumbnail_relative_path") val thumbnailRelativePath: String?,
    @ColumnInfo(name = "captured_at_epoch_ms") val capturedAtEpochMs: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "orientation_degrees") val orientationDegrees: Int,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

object PhotoStatus {
    const val PENDING = "PENDING"
    const val READY = "READY"
    const val FAILED = "FAILED"
}

data class SubjectWithCounts(
    val id: String,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
    @ColumnInfo(name = "photo_count") val photoCount: Int,
    @ColumnInfo(name = "note_count") val noteCount: Int,
)
