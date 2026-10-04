package com.sina.uninotes.ui.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary
import com.sina.uninotes.util.DateFormatting
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    subjectId: String,
    initialPhotoId: String,
    photoRepository: PhotoRepository,
    onBack: () -> Unit,
) {
    val photos by photoRepository.observePhotos(subjectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val startIndex = photos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { photos.size.coerceAtLeast(1) })
    var deleteTarget by remember { mutableStateOf<PhotoEntity?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    var openedInitial by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(photos, initialPhotoId) {
        val idx = photos.indexOfFirst { it.id == initialPhotoId }
        if (!openedInitial && idx >= 0) {
            pagerState.scrollToPage(idx)
            openedInitial = true
        }
        zoomed = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (photos.isEmpty()) {
            Text("Photo unavailable", color = UniText, modifier = Modifier.align(Alignment.Center))
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
            ) { page ->
                val photo = photos.getOrNull(page) ?: return@HorizontalPager
                ZoomablePhoto(
                    model = photoRepository.resolveFile(photo.relativePath),
                    onZoomingChanged = { if (page == pagerState.currentPage) zoomed = it },
                )
            }
        }

        error?.let {
            Text(it, color = com.sina.uninotes.ui.theme.UniAccent,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        photos.getOrNull(pagerState.currentPage)?.let { deleteTarget = it }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete photo", tint = Color.White)
                }
            }
            val current = photos.getOrNull(pagerState.currentPage)
            if (current != null) {
                Text(
                    DateFormatting.photoDateTime(current.capturedAtEpochMs),
                    color = UniTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }

    deleteTarget?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete photo?") },
            text = { Text("This photo will be permanently removed from UniNotes.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            photoRepository.deletePhoto(photo.id)
                                .onSuccess {
                                    deleteTarget = null
                                    if (photos.size <= 1) onBack()
                                }.onFailure { error = it.message ?: "Could not delete photo"; deleteTarget = null }
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ZoomablePhoto(
    model: Any,
    onZoomingChanged: (Boolean) -> Unit,
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(value: Offset, zoom: Float): Offset {
        val x = size.width * (zoom - 1f) / 2f
        val y = size.height * (zoom - 1f) / 2f
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomingChanged(false)
                        } else {
                            scale = 2.5f
                            onZoomingChanged(true)
                        }
                    },
                )
            }
            .pointerInput(model) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val centroid = event.calculateCentroid(useCurrent = false) - Offset(size.width / 2f, size.height / 2f)
                            offset = if (newScale <= 1.01f) Offset.Zero else
                                clamp((offset - centroid) * (newScale / scale) + centroid + event.calculatePan(), newScale)
                            scale = newScale
                            event.changes.forEach { it.consume() }
                            onZoomingChanged(newScale > 1.01f)
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
