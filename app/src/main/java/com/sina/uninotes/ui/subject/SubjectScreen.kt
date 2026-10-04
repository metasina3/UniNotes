package com.sina.uninotes.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.ui.components.ContentText
import com.sina.uninotes.ui.theme.UniAccent
import com.sina.uninotes.ui.theme.UniBackground
import com.sina.uninotes.ui.theme.UniPrimary
import com.sina.uninotes.ui.theme.UniSurface
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary
import com.sina.uninotes.util.DateFormatting
import java.time.LocalDate

private sealed interface GalleryRow {
    data class Header(val dateKey: String, val label: String) : GalleryRow
    data class Photo(val entity: PhotoEntity) : GalleryRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    viewModel: SubjectViewModel,
    photoRepository: PhotoRepository,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onWriteNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    val subject by viewModel.subject.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    // Keep paging warm for large libraries; also observe list for stable date headers.
    val paging = viewModel.photosPaging.collectAsLazyPagingItems()
    val photos by photoRepository.observePhotos(viewModel.subjectId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    val today = remember { DateFormatting.todayLocalDate() }

    Scaffold(
        containerColor = UniBackground,
        topBar = {
            TopAppBar(
                title = {
                    ContentText(
                        text = subject?.name.orEmpty(),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(color = UniText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UniText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UniBackground),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UniBackground)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenCamera,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UniAccent,
                        contentColor = UniBackground,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Camera", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onWriteNote,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UniPrimary,
                        contentColor = UniText,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Write note", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = UniBackground,
                contentColor = UniPrimary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[tab]),
                        color = UniPrimary,
                    )
                },
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Photos", color = if (tab == 0) UniPrimary else UniTextSecondary) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Notes", color = if (tab == 1) UniPrimary else UniTextSecondary) },
                )
            }

            when (tab) {
                0 -> PhotosGallery(
                    photos = if (photos.isNotEmpty()) photos else paging.itemSnapshotList.items.filterNotNull(),
                    photoRepository = photoRepository,
                    today = today,
                    onOpenPhoto = onOpenPhoto,
                )
                else -> NotesList(
                    notes = notes,
                    today = today,
                    onOpenNote = onOpenNote,
                )
            }
        }
    }
}

@Composable
private fun PhotosGallery(
    photos: List<PhotoEntity>,
    photoRepository: PhotoRepository,
    today: LocalDate,
    onOpenPhoto: (String) -> Unit,
) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos yet. Tap Camera to capture a whiteboard.", color = UniTextSecondary)
        }
        return
    }

    val rows = remember(photos, today) {
        buildList {
            var last: String? = null
            photos.forEach { photo ->
                if (photo.localDate != last) {
                    last = photo.localDate
                    val label = DateFormatting.galleryHeader(LocalDate.parse(photo.localDate), today)
                    add(GalleryRow.Header(photo.localDate, label))
                }
                add(GalleryRow.Photo(photo))
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = rows,
            key = {
                when (it) {
                    is GalleryRow.Header -> "h-${it.dateKey}"
                    is GalleryRow.Photo -> it.entity.id
                }
            },
            span = {
                when (it) {
                    is GalleryRow.Header -> GridItemSpan(3)
                    is GalleryRow.Photo -> GridItemSpan(1)
                }
            },
        ) { row ->
            when (row) {
                is GalleryRow.Header -> {
                    Text(
                        text = row.label,
                        color = if (row.label == "Today") UniAccent else UniTextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                is GalleryRow.Photo -> {
                    val path = row.entity.thumbnailRelativePath ?: row.entity.relativePath
                    AsyncImage(
                        model = photoRepository.resolveFile(path),
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenPhoto(row.entity.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesList(
    notes: List<NoteEntity>,
    today: LocalDate,
    onOpenNote: (String) -> Unit,
) {
    if (notes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes yet. Tap Write note for today’s entry.", color = UniTextSecondary)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(notes, key = { it.id }) { note ->
            val date = LocalDate.parse(note.localDate)
            val isToday = date == today
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(UniSurface)
                    .then(
                        if (isToday) {
                            Modifier.border(1.dp, UniAccent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onOpenNote(note.id) }
                    .padding(14.dp),
            ) {
                Text(
                    DateFormatting.noteListDate(date, today),
                    color = if (isToday) UniAccent else UniTextSecondary,
                    fontWeight = FontWeight.Medium,
                )
                if (note.title.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    ContentText(
                        text = note.title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = UniText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    ContentText(
                        text = note.body,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = UniTextSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
