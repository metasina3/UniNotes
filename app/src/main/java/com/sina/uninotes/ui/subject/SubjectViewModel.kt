package com.sina.uninotes.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sina.uninotes.data.local.db.FolderEntity
import com.sina.uninotes.data.local.db.FolderWithCounts
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.RootFolder
import com.sina.uninotes.data.local.db.SubjectEntity
import com.sina.uninotes.data.repository.FolderRepository
import com.sina.uninotes.data.repository.NoteRepository
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.data.repository.SubjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubjectUiState(
    val selectionMode: Boolean = false,
    val selectedPhotoIds: Set<String> = emptySet(),
    val createFolderDialog: Boolean = false,
    val deleteFolderConfirm: FolderWithCounts? = null,
    val deletePhotosConfirm: Boolean = false,
    val message: String? = null,
)

class SubjectViewModel(
    val subjectId: String,
    val folderId: String,
    subjectRepository: SubjectRepository,
    private val folderRepository: FolderRepository,
    noteRepository: NoteRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {
    val isRoot: Boolean get() = folderId == RootFolder.ID

    val subject: StateFlow<SubjectEntity?> =
        subjectRepository.observeSubject(subjectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folder: StateFlow<FolderEntity?> =
        if (isRoot) {
            MutableStateFlow(null)
        } else {
            folderRepository.observeFolder(folderId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    val folders: StateFlow<List<FolderWithCounts>> =
        if (isRoot) {
            folderRepository.observeFolders(subjectId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    val notes: StateFlow<List<NoteEntity>> =
        noteRepository.observeNotes(subjectId, folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos: StateFlow<List<PhotoEntity>> =
        photoRepository.observePhotos(subjectId, folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(SubjectUiState())
    val ui: StateFlow<SubjectUiState> = _ui.asStateFlow()

    fun enterSelection(photoId: String? = null) {
        _ui.update {
            it.copy(
                selectionMode = true,
                selectedPhotoIds = if (photoId == null) emptySet() else setOf(photoId),
            )
        }
    }

    fun exitSelection() {
        _ui.update { it.copy(selectionMode = false, selectedPhotoIds = emptySet(), deletePhotosConfirm = false) }
    }

    fun togglePhotoSelection(photoId: String) {
        _ui.update { state ->
            val next = state.selectedPhotoIds.toMutableSet()
            if (!next.add(photoId)) next.remove(photoId)
            state.copy(selectionMode = true, selectedPhotoIds = next)
        }
    }

    fun selectAllPhotos() {
        val all = photos.value.map { it.id }.toSet()
        _ui.update { it.copy(selectionMode = true, selectedPhotoIds = all) }
    }

    fun requestDeleteSelected() {
        if (_ui.value.selectedPhotoIds.isEmpty()) return
        _ui.update { it.copy(deletePhotosConfirm = true) }
    }

    fun dismissDeletePhotos() {
        _ui.update { it.copy(deletePhotosConfirm = false) }
    }

    fun confirmDeleteSelected() {
        val ids = _ui.value.selectedPhotoIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            photoRepository.deletePhotos(ids)
                .onSuccess { count ->
                    _ui.update {
                        it.copy(
                            selectionMode = false,
                            selectedPhotoIds = emptySet(),
                            deletePhotosConfirm = false,
                            message = if (count == 1) "Deleted 1 photo" else "Deleted $count photos",
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            deletePhotosConfirm = false,
                            message = error.message ?: "Could not delete photos",
                        )
                    }
                }
        }
    }

    fun moveSelected(delta: Int) {
        val selected = _ui.value.selectedPhotoIds
        if (selected.size != 1) return
        val photoId = selected.first()
        viewModelScope.launch {
            photoRepository.movePhoto(subjectId, folderId, photoId, delta)
                .onFailure { error ->
                    _ui.update { it.copy(message = error.message ?: "Could not reorder photo") }
                }
        }
    }

    fun showCreateFolder() {
        _ui.update { it.copy(createFolderDialog = true) }
    }

    fun dismissCreateFolder() {
        _ui.update { it.copy(createFolderDialog = false) }
    }

    fun createFolder(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            folderRepository.createFolder(subjectId, name)
                .onSuccess { folder ->
                    _ui.update {
                        it.copy(
                            createFolderDialog = false,
                            message = "Created “${folder.name}”",
                        )
                    }
                    onCreated(folder.id)
                }
                .onFailure { error ->
                    _ui.update { it.copy(message = error.message ?: "Could not create folder") }
                }
        }
    }

    fun requestDeleteFolder(folder: FolderWithCounts) {
        _ui.update { it.copy(deleteFolderConfirm = folder) }
    }

    fun dismissDeleteFolder() {
        _ui.update { it.copy(deleteFolderConfirm = null) }
    }

    fun confirmDeleteFolder() {
        val folder = _ui.value.deleteFolderConfirm ?: return
        viewModelScope.launch {
            folderRepository.deleteFolder(subjectId, folder.id)
                .onSuccess {
                    _ui.update {
                        it.copy(
                            deleteFolderConfirm = null,
                            message = "Deleted “${folder.name}”",
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            deleteFolderConfirm = null,
                            message = error.message ?: "Could not delete folder",
                        )
                    }
                }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            subjectId: String,
            folderId: String,
            subjectRepository: SubjectRepository,
            folderRepository: FolderRepository,
            noteRepository: NoteRepository,
            photoRepository: PhotoRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SubjectViewModel(
                    subjectId,
                    folderId,
                    subjectRepository,
                    folderRepository,
                    noteRepository,
                    photoRepository,
                ) as T
        }
    }
}
