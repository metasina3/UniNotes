package com.sina.uninotes.ui.navigation

import android.net.Uri
import com.sina.uninotes.data.local.db.RootFolder

object Routes {
    private const val ROOT_TOKEN = "_root"

    const val Home = "home"
    const val Backup = "backup"
    const val Subject = "subject/{subjectId}?folderId={folderId}"
    const val NoteEditor = "note/{subjectId}?noteId={noteId}&folderId={folderId}"
    const val Camera = "camera/{subjectId}?folderId={folderId}"
    const val PhotoViewer = "photos/{subjectId}/{photoId}?folderId={folderId}"

    fun encodeFolderId(folderId: String): String =
        Uri.encode(if (folderId == RootFolder.ID) ROOT_TOKEN else folderId)

    fun decodeFolderId(raw: String?): String {
        val value = raw ?: ROOT_TOKEN
        return if (value == ROOT_TOKEN || value.isBlank()) RootFolder.ID else value
    }

    fun subject(subjectId: String, folderId: String = RootFolder.ID): String =
        "subject/$subjectId?folderId=${encodeFolderId(folderId)}"

    fun noteEditor(
        subjectId: String,
        noteId: String? = null,
        folderId: String = RootFolder.ID,
    ): String {
        val folder = encodeFolderId(folderId)
        return if (noteId.isNullOrBlank()) {
            "note/$subjectId?folderId=$folder"
        } else {
            "note/$subjectId?noteId=${Uri.encode(noteId)}&folderId=$folder"
        }
    }

    fun camera(subjectId: String, folderId: String = RootFolder.ID): String =
        "camera/$subjectId?folderId=${encodeFolderId(folderId)}"

    fun photoViewer(
        subjectId: String,
        photoId: String,
        folderId: String = RootFolder.ID,
    ): String = "photos/$subjectId/$photoId?folderId=${encodeFolderId(folderId)}"
}
