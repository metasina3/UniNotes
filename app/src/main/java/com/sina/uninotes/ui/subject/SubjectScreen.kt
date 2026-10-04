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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.material3.CircularProgressIndicator
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
import com.sina.uninotes.ui.components.currentLocalDay
import com.sina.uninotes.ui.theme.UniAccent
import com.sina.uninotes.ui.theme.UniBackground
import com.sina.uninotes.ui.theme.UniPrimary
import com.sina.uninotes.ui.theme.UniSurface
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary
import com.sina.uninotes.util.DateFormatting
import java.time.LocalDate

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
    val paging = viewModel.photosPaging.collectAsLazyPagingItems()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = currentLocalDay()

    Scaffold(
        containerColor = UniBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    ContentText(
                        text = subject?.name.orEmpty(),
                        modifier = Modifier.fillMaxWidth(),
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
                    .navigationBarsPadding()
                    .testTag("subjectActions")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        contentColor = UniBackground,
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
                .padding(padding)
                .consumeWindowInsets(padding),
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
                    photos = paging,
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
    photos: LazyPagingItems<GalleryRow>,
    photoRepository: PhotoRepository,
    today: LocalDate,
    onOpenPhoto: (String) -> Unit,
) {
    if (photos.itemCount == 0) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            when (photos.loadState.refresh) {
                is LoadState.Loading -> CircularProgressIndicator(color = UniPrimary)
                is LoadState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load photos", color = UniTextSecondary)
                    Button(onClick = photos::retry) { Text("Retry") }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(48.dp), tint = UniAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("No photos yet", color = UniText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap Camera to capture a whiteboard.", color = UniTextSecondary,
                        textAlign = TextAlign.Center)
                }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            count = photos.itemCount,
            key = photos.itemKey { row -> when (row) {
                is GalleryRow.Header -> "date-${row.dateKey}"
                is GalleryRow.Photo -> row.entity.id
            } },
            span = { index -> if (photos.peek(index) is GalleryRow.Header)
                GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { index ->
            when (val row = photos[index]) {
                is GalleryRow.Header -> Text(
                    text = DateFormatting.galleryHeader(LocalDate.parse(row.dateKey), today),
                    color = if (row.dateKey == today.toString()) UniAccent else UniTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
                is GalleryRow.Photo -> {
                    val path = row.entity.thumbnailRelativePath ?: row.entity.relativePath
                    AsyncImage(
                        model = photoRepository.resolveFile(path),
                        contentDescription = "Photo taken ${row.entity.localDate}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenPhoto(row.entity.id) },
                    )
                }
                null -> Spacer(Modifier.aspectRatio(1f))
            }
        }
        if (photos.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = UniPrimary)
                }
            }
        }
        if (photos.loadState.append is LoadState.Error) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(onClick = photos::retry) { Text("Retry loading photos") }
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
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EditNote, null, Modifier.size(48.dp), tint = UniPrimary)
                Spacer(Modifier.height(16.dp))
                Text("No notes yet", color = UniText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Tap Write note for today’s entry.", color = UniTextSecondary, textAlign = TextAlign.Center)
            }
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
