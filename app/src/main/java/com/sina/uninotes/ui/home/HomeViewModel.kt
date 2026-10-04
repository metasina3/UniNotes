package com.sina.uninotes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sina.uninotes.data.local.db.SubjectWithCounts
import com.sina.uninotes.data.repository.SubjectRepository
import com.sina.uninotes.ui.theme.SubjectPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val dialog: HomeDialog? = null,
    val errorMessage: String? = null,
)

sealed interface HomeDialog {
    data object Create : HomeDialog
    data class Rename(val subjectId: String, val currentName: String) : HomeDialog
    data class DeleteConfirm(val subjectId: String, val name: String) : HomeDialog
}

class HomeViewModel(
    private val subjectRepository: SubjectRepository,
) : ViewModel() {
    val subjects: StateFlow<List<SubjectWithCounts>> =
        subjectRepository.observeSubjects()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    fun showCreate() {
        _ui.value = _ui.value.copy(dialog = HomeDialog.Create, errorMessage = null)
    }

    fun showRename(subjectId: String, name: String) {
        _ui.value = _ui.value.copy(dialog = HomeDialog.Rename(subjectId, name), errorMessage = null)
    }

    fun showDelete(subjectId: String, name: String) {
        _ui.value = _ui.value.copy(dialog = HomeDialog.DeleteConfirm(subjectId, name))
    }

    fun dismissDialog() {
        _ui.value = _ui.value.copy(dialog = null, errorMessage = null)
    }

    fun createSubject(name: String) {
        viewModelScope.launch {
            val color = SubjectPalette[subjects.value.size % SubjectPalette.size]
            subjectRepository.createSubject(name, color)
                .onSuccess { dismissDialog() }
                .onFailure {
                    _ui.value = _ui.value.copy(errorMessage = it.message ?: "Could not create subject")
                }
        }
    }

    fun renameSubject(subjectId: String, name: String) {
        viewModelScope.launch {
            subjectRepository.renameSubject(subjectId, name)
                .onSuccess { dismissDialog() }
                .onFailure {
                    _ui.value = _ui.value.copy(errorMessage = it.message ?: "Could not rename subject")
                }
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            subjectRepository.deleteSubject(subjectId)
                .onSuccess { dismissDialog() }
                .onFailure {
                    _ui.value = _ui.value.copy(errorMessage = it.message ?: "Could not delete subject")
                }
        }
    }

    companion object {
        fun factory(subjectRepository: SubjectRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(subjectRepository) as T
        }
    }
}
