package com.sina.uninotes.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.local.db.SubjectEntity
import com.sina.uninotes.data.repository.NoteRepository
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.data.repository.SubjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SubjectViewModel(
    val subjectId: String,
    subjectRepository: SubjectRepository,
    noteRepository: NoteRepository,
    photoRepository: PhotoRepository,
) : ViewModel() {
    val subject: StateFlow<SubjectEntity?> =
        subjectRepository.observeSubject(subjectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notes: StateFlow<List<NoteEntity>> =
        noteRepository.observeNotes(subjectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photosPaging: Flow<PagingData<PhotoEntity>> =
        photoRepository.pagingPhotos(subjectId).cachedIn(viewModelScope)

    companion object {
        fun factory(
            subjectId: String,
            subjectRepository: SubjectRepository,
            noteRepository: NoteRepository,
            photoRepository: PhotoRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SubjectViewModel(subjectId, subjectRepository, noteRepository, photoRepository) as T
        }
    }
}
