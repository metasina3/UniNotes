package com.sina.uninotes.data.local.files

import android.content.Context
import java.io.File
import java.io.IOException

class PhotoStorage(context: Context) {
    private val root: File = File(context.filesDir, "subjects").also { it.mkdirs() }

    fun subjectDir(subjectId: String): File =
        File(root, subjectId).also { it.mkdirs() }

    fun photosDir(subjectId: String): File =
        File(subjectDir(subjectId), "photos").also { it.mkdirs() }

    fun thumbnailsDir(subjectId: String): File =
        File(subjectDir(subjectId), "thumbs").also { it.mkdirs() }

    fun originalFile(subjectId: String, photoId: String): File =
        File(photosDir(subjectId), "$photoId.jpg")

    fun tempFile(subjectId: String, photoId: String): File =
        File(photosDir(subjectId), "$photoId.tmp")

    fun thumbnailFile(subjectId: String, photoId: String): File =
        File(thumbnailsDir(subjectId), "$photoId.jpg")

    fun relativeOriginalPath(subjectId: String, photoId: String): String =
        "subjects/$subjectId/photos/$photoId.jpg"

    fun relativeThumbnailPath(subjectId: String, photoId: String): String =
        "subjects/$subjectId/thumbs/$photoId.jpg"

    fun resolve(relativePath: String): File {
        require(!relativePath.contains("..")) { "Invalid path" }
        return File(root.parentFile, relativePath)
    }

    fun deleteSubjectFiles(subjectId: String) {
        subjectDir(subjectId).deleteRecursively()
    }

    fun deletePhotoFiles(subjectId: String, photoId: String) {
        listOf(originalFile(subjectId, photoId), thumbnailFile(subjectId, photoId), tempFile(subjectId, photoId))
            .forEach { file -> if (file.exists() && !file.delete()) throw IOException("Could not delete photo file") }
    }

    fun finalizeTemp(temp: File, destination: File) {
        if (!temp.exists()) throw IOException("Temp capture missing")
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
        if (!destination.exists() || destination.length() == 0L) {
            throw IOException("Failed to finalize photo")
        }
    }

    fun ensureFreeSpace(minBytes: Long = 5L * 1024L * 1024L) {
        val free = root.usableSpace
        if (free < minBytes) {
            throw IOException("Not enough storage space")
        }
    }
}
