package com.sina.uninotes.ui.navigation

object Routes {
    const val Home = "home"
    const val Backup = "backup"
    const val Subject = "subject/{subjectId}"
    const val NoteEditor = "note/{subjectId}?noteId={noteId}"
    const val Camera = "camera/{subjectId}"
    const val PhotoViewer = "photos/{subjectId}/{photoId}"

    fun subject(subjectId: String) = "subject/$subjectId"
    fun noteEditor(subjectId: String, noteId: String? = null): String =
        if (noteId.isNullOrBlank()) {
            "note/$subjectId"
        } else {
            "note/$subjectId?noteId=$noteId"
        }

    fun camera(subjectId: String) = "camera/$subjectId"
    fun photoViewer(subjectId: String, photoId: String) = "photos/$subjectId/$photoId"
}
