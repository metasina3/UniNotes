package com.sina.uninotes.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sina.uninotes.data.repository.NoteRepository
import com.sina.uninotes.data.repository.SubjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveStatus { Idle, Saving, Saved, Failed }

data class NoteEditorUiState(
    val noteId: String? = null,
    val subjectName: String = "",
    val localDate: String = "",
    val title: String = "",
    val body: String = "",
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val ready: Boolean = false,
    val placeCursorAtEnd: Boolean = false,
    val error: String? = null,
    val deleteConfirm: Boolean = false,
)

class NoteEditorViewModel(
    private val subjectId: String,
    private val initialNoteId: String?,
    private val noteRepository: NoteRepository,
    private val subjectRepository: SubjectRepository,
    private val savedStateHandle: SavedStateHandle,
    private val persistenceScope: CoroutineScope,
) : ViewModel() {
    private val _ui = MutableStateFlow(NoteEditorUiState())
    val ui: StateFlow<NoteEditorUiState> = _ui.asStateFlow()

    private var saveJob: Job? = null
    private var lastKnownUpdatedAt: Long? = null
    private val saveMutex = Mutex()
    private var revision = 0L
    private var deleted = false

    private var loadedNoteId: String?
        get() = savedStateHandle["noteId"]
        set(value) {
            savedStateHandle["noteId"] = value
        }

    private var draftTitle: String?
        get() = savedStateHandle["draftTitle"]
        set(value) {
            savedStateHandle["draftTitle"] = value
        }

    private var draftBody: String?
        get() = savedStateHandle["draftBody"]
        set(value) {
            savedStateHandle["draftBody"] = value
        }

    init {
        retryLoad()
    }

    fun retryLoad() {
        viewModelScope.launch {
            runCatching { load() }.onFailure { error ->
                _ui.update { it.copy(error = error.message ?: "Could not open note") }
            }
        }
    }

    private suspend fun load() {
        val subjectName = subjectRepository.getSubject(subjectId)?.name.orEmpty()
        val existingId = initialNoteId ?: loadedNoteId
        val note = if (!existingId.isNullOrBlank()) {
            noteRepository.getNote(existingId)?.takeIf { it.subjectId == subjectId }
                ?: noteRepository.openOrCreateTodayNote(subjectId)
        } else {
            noteRepository.openOrCreateTodayNote(subjectId)
        }
        loadedNoteId = note.id
        lastKnownUpdatedAt = note.updatedAtEpochMs
        val title = draftTitle ?: note.title
        val body = draftBody ?: note.body
        _ui.value = NoteEditorUiState(
            noteId = note.id,
            subjectName = subjectName,
            localDate = note.localDate,
            title = title,
            body = body,
            ready = true,
            placeCursorAtEnd = initialNoteId == null && draftBody == null,
            saveStatus = SaveStatus.Idle,
        )
    }

    fun onTitleChange(title: String) {
        if (!_ui.value.ready || deleted) return
        revision++
        draftTitle = title
        _ui.update { it.copy(title = title, placeCursorAtEnd = false) }
        scheduleSave()
    }

    fun onBodyChange(body: String) {
        if (!_ui.value.ready || deleted) return
        revision++
        draftBody = body
        _ui.update { it.copy(body = body, placeCursorAtEnd = false) }
        scheduleSave()
    }

    fun consumeCursorRequest() {
        _ui.update { it.copy(placeCursorAtEnd = false) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _ui.update { it.copy(saveStatus = SaveStatus.Saving) }
            delay(400)
            persistNow()
        }
    }

    fun persistNow() {
        persistenceScope.launch { persistNowSuspend() }
    }

    suspend fun persistNowSuspend() = saveMutex.withLock {
        if (deleted) return@withLock
        val state = _ui.value
        val noteId = state.noteId ?: return@withLock
        val savingRevision = revision
        _ui.update { it.copy(saveStatus = SaveStatus.Saving) }
        noteRepository.saveNoteContent(
            noteId = noteId,
            title = state.title,
            body = state.body,
            expectedUpdatedAt = lastKnownUpdatedAt,
        ).onSuccess { saved ->
            lastKnownUpdatedAt = saved.updatedAtEpochMs
            val matches = saved.title == state.title.trim() && saved.body == state.body
            _ui.update { current ->
                current.copy(
                    saveStatus = when {
                        !matches -> SaveStatus.Failed
                        revision != savingRevision -> SaveStatus.Saving
                        else -> SaveStatus.Saved
                    },
                    error = if (matches) null else "The note changed elsewhere. Reopen it before editing.",
                )
            }
            subjectRepository.touchSubject(subjectId)
        }.onFailure { error ->
            _ui.update { current ->
                current.copy(saveStatus = SaveStatus.Failed, error = error.message ?: "Save failed")
            }
        }
    }

    fun onLeaveScreen() {
        saveJob?.cancel()
        // Application scope survives the navigation entry and its ViewModel being removed.
        persistenceScope.launch {
            persistNowSuspend()
            saveMutex.withLock {
                if (!deleted && _ui.value.title.isBlank() && _ui.value.body.isBlank()) {
                    _ui.value.noteId?.let { noteRepository.deleteEmptyPlaceholder(it) }
                }
            }
        }
    }

    fun requestDelete() {
        _ui.update { it.copy(deleteConfirm = true) }
    }

    fun dismissDelete() {
        _ui.update { it.copy(deleteConfirm = false) }
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        saveJob?.cancel()
        persistenceScope.launch {
            saveMutex.withLock {
                val noteId = _ui.value.noteId ?: return@withLock
                noteRepository.deleteNote(noteId).onSuccess {
                    deleted = true
                    _ui.update { it.copy(deleteConfirm = false) }
                    onDeleted()
                }.onFailure { error ->
                    _ui.update { it.copy(deleteConfirm = false, error = error.message ?: "Delete failed") }
                }
            }
        }
    }

    companion object {
        fun factory(
            subjectId: String,
            noteId: String?,
            noteRepository: NoteRepository,
            subjectRepository: SubjectRepository,
            persistenceScope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteEditorViewModel(
                    subjectId = subjectId,
                    initialNoteId = noteId,
                    noteRepository = noteRepository,
                    subjectRepository = subjectRepository,
                    savedStateHandle = createSavedStateHandle(),
                    persistenceScope = persistenceScope,
                )
            }
        }
    }
}
