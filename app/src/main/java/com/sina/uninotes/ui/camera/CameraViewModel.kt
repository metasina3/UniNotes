package com.sina.uninotes.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sina.uninotes.camera.FlashMode
import com.sina.uninotes.camera.CameraCaptureController
import kotlinx.coroutines.CoroutineScope
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.data.repository.SubjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CameraUiState(
    val subjectName: String = "",
    val flashMode: FlashMode = FlashMode.Off,
    val zoomRatio: Float = 1f,
    val capturing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val focusPoint: Pair<Float, Float>? = null,
)

class CameraViewModel(
    val subjectId: String,
    val folderId: String,
    private val photoRepository: PhotoRepository,
    private val subjectRepository: SubjectRepository,
    private val persistenceScope: CoroutineScope,
) : ViewModel() {
    private val _ui = MutableStateFlow(CameraUiState())
    val ui: StateFlow<CameraUiState> = _ui.asStateFlow()

    val latestPhoto: StateFlow<PhotoEntity?> =
        photoRepository.observeLatest(subjectId, folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val name = subjectRepository.getSubject(subjectId)?.name.orEmpty()
            _ui.update { it.copy(subjectName = name) }
        }
    }

    fun cycleFlash(hasFlash: Boolean) {
        if (!hasFlash) return
        val next = when (_ui.value.flashMode) {
            FlashMode.Off -> FlashMode.Auto
            FlashMode.Auto -> FlashMode.On
            FlashMode.On -> FlashMode.Off
        }
        _ui.update { it.copy(flashMode = next) }
    }

    fun updateZoom(ratio: Float) {
        _ui.update { it.copy(zoomRatio = ratio) }
    }

    fun showFocus(x: Float, y: Float) {
        _ui.update { it.copy(focusPoint = x to y) }
    }

    fun clearFocusIndicator() {
        _ui.update { it.copy(focusPoint = null) }
    }

    fun capture(controller: CameraCaptureController) {
        if (_ui.value.capturing) return
        _ui.update { it.copy(capturing = true, statusMessage = "Capturing…", errorMessage = null) }
        val capturedAt = System.currentTimeMillis()
        persistenceScope.launch {
            var pending: PhotoEntity? = null
            try {
                val photo = photoRepository.beginCapture(subjectId, folderId, capturedAt)
                pending = photo
                controller.takePicture(photoRepository.captureFile(photo)).getOrThrow()
                _ui.update { it.copy(statusMessage = "Saving…") }
                photoRepository.completeCapture(photo).getOrThrow()
                pending = null
                subjectRepository.touchSubject(subjectId)
                _ui.update { it.copy(statusMessage = "Saved", errorMessage = null) }
            } catch (error: Exception) {
                pending?.let { runCatching { photoRepository.discardCapture(it) } }
                reportError(error.message ?: "Could not save photo")
            } finally {
                _ui.update { it.copy(capturing = false) }
            }
        }
    }

    fun clearStatus() {
        _ui.update { it.copy(statusMessage = null) }
    }

    fun reportError(message: String) {
        _ui.update { it.copy(capturing = false, errorMessage = message, statusMessage = null) }
    }

    companion object {
        fun factory(
            subjectId: String,
            folderId: String,
            photoRepository: PhotoRepository,
            subjectRepository: SubjectRepository,
            persistenceScope: CoroutineScope,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CameraViewModel(
                    subjectId,
                    folderId,
                    photoRepository,
                    subjectRepository,
                    persistenceScope,
                ) as T
        }
    }
}
