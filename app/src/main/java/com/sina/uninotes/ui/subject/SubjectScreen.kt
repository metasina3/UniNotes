package com.sina.uninotes.ui.subject

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sina.uninotes.data.local.db.FolderWithCounts
import com.sina.uninotes.data.local.db.NoteEntity
import com.sina.uninotes.data.local.db.PhotoEntity
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.data.repository.SubjectRepository
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubjectScreen(
    viewModel: SubjectViewModel,
    photoRepository: PhotoRepository,
    subjectRepository: SubjectRepository,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onWriteNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    val subject by viewModel.subject.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = currentLocalDay()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }

    BackHandler(enabled = ui.selectionMode) { viewModel.exitSelection() }

    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeMessage()
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = photoRepository.importFromUris(
                resolver = context.contentResolver,
                subjectId = viewModel.subjectId,
                uris = uris,
                folderId = viewModel.folderId,
            )
            importing = false
            result.onSuccess { count ->
                subjectRepository.touchSubject(viewModel.subjectId)
                snackbar.showSnackbar(
                    if (count == 1) "Imported 1 photo" else "Imported $count photos",
                )
            }.onFailure {
                snackbar.showSnackbar(it.message ?: "Could not import photos")
            }
        }
    }

    val title = if (viewModel.isRoot) {
        subject?.name.orEmpty()
    } else {
        folder?.name ?: subject?.name.orEmpty()
    }

    Scaffold(
        containerColor = UniBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 88.dp),
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    ContentText(
                        text = title,
                        modifier = Modifier.fillMaxWidth(),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(color = UniText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (ui.selectionMode) viewModel.exitSelection() else onBack()
                    }) {
                        Icon(
                            if (ui.selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (ui.selectionMode) "Cancel selection" else "Back",
                            tint = UniText,
                        )
                    }
                },
                actions = {
                    if (ui.selectionMode) {
                        IconButton(onClick = viewModel::selectAllPhotos) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = UniText)
                        }
                        IconButton(
                            onClick = { viewModel.moveSelected(-1) },
                            enabled = ui.selectedPhotoIds.size == 1,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Move earlier",
                                tint = if (ui.selectedPhotoIds.size == 1) UniText else UniTextSecondary,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveSelected(1) },
                            enabled = ui.selectedPhotoIds.size == 1,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Move later",
                                tint = if (ui.selectedPhotoIds.size == 1) UniText else UniTextSecondary,
                            )
                        }
                        IconButton(
                            onClick = viewModel::requestDeleteSelected,
                            enabled = ui.selectedPhotoIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = if (ui.selectedPhotoIds.isNotEmpty()) Color(0xFFFF6B6B) else UniTextSecondary,
                            )
                        }
                    } else if (tab == 0 && photos.isNotEmpty()) {
                        TextButton(onClick = { viewModel.enterSelection() }) {
                            Text("Select", color = UniPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UniBackground),
            )
        },
        bottomBar = {
            if (!ui.selectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UniBackground)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 12.dp)
                        .testTag("subjectActions")
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (importing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = UniPrimary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Importing photos…", color = UniTextSecondary)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (viewModel.isRoot) {
                            ActionButton(
                                label = "Folder",
                                icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    createFolderName = ""
                                    viewModel.showCreateFolder()
                                },
                                enabled = !importing,
                                container = UniSurface,
                                content = UniText,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ActionButton(
                            label = "Gallery",
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !importing,
                            container = UniSurface,
                            content = UniText,
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton(
                            label = "Camera",
                            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = onOpenCamera,
                            enabled = !importing,
                            container = UniAccent,
                            content = UniBackground,
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton(
                            label = "Note",
                            icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                            onClick = onWriteNote,
                            enabled = !importing,
                            container = UniPrimary,
                            content = UniBackground,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
            if (viewModel.isRoot && folders.isNotEmpty()) {
                Text(
                    "Subfolders",
                    color = UniTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(folders, key = { it.id }) { item ->
                        FolderChip(
                            folder = item,
                            onOpen = { onOpenFolder(item.id) },
                            onDelete = { viewModel.requestDeleteFolder(item) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

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
                    photos = photos,
                    photoRepository = photoRepository,
                    selectionMode = ui.selectionMode,
                    selectedIds = ui.selectedPhotoIds,
                    onOpenPhoto = onOpenPhoto,
                    onToggleSelect = viewModel::togglePhotoSelection,
                    onLongPress = { viewModel.enterSelection(it) },
                )
                else -> NotesList(
                    notes = notes,
                    today = today,
                    onOpenNote = onOpenNote,
                )
            }
        }
    }

    if (ui.createFolderDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateFolder,
            title = { Text("New subfolder") },
            text = {
                OutlinedTextField(
                    value = createFolderName,
                    onValueChange = { createFolderName = it },
                    singleLine = true,
                    label = { Text("Name (e.g. جلسه 1)") },
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.ContentOrLtr,
                        textAlign = TextAlign.Start,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.createFolder(createFolderName) }) {
                    Text("Create", color = UniPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateFolder) { Text("Cancel") }
            },
        )
    }

    ui.deleteFolderConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteFolder,
            title = { Text("Delete subfolder?") },
            text = {
                Text("Delete “${target.name}” and all photos/notes inside it?")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteFolder) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteFolder) { Text("Cancel") }
            },
        )
    }

    if (ui.deletePhotosConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeletePhotos,
            title = { Text("Delete photos?") },
            text = {
                val count = ui.selectedPhotoIds.size
                Text(
                    if (count == 1) "Delete the selected photo?"
                    else "Delete $count selected photos?",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteSelected) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeletePhotos) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        icon()
        Spacer(Modifier.size(4.dp))
        Text(label, fontWeight = FontWeight.Bold, maxLines = 1, color = content)
    }
}

@Composable
private fun FolderChip(
    folder: FolderWithCounts,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(UniSurface)
            .border(1.dp, UniTextSecondary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = UniAccent)
        Column {
            ContentText(
                text = folder.name,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(color = UniText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${folder.photoCount} photos · ${folder.noteCount} notes",
                color = UniTextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosGallery(
    photos: List<PhotoEntity>,
    photoRepository: PhotoRepository,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onOpenPhoto: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(48.dp), tint = UniAccent)
                Spacer(Modifier.height(16.dp))
                Text("No photos yet", color = UniText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Use Gallery or Camera. Long-press a photo to select, delete, or reorder.",
                    color = UniTextSecondary,
                    textAlign = TextAlign.Center,
                )
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
        items(photos, key = { it.id }) { photo ->
            val path = photo.thumbnailRelativePath ?: photo.relativePath
            val selected = photo.id in selectedIds
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) onToggleSelect(photo.id) else onOpenPhoto(photo.id)
                        },
                        onLongClick = { onLongPress(photo.id) },
                    ),
            ) {
                AsyncImage(
                    model = photoRepository.resolveFile(path),
                    contentDescription = "Photo taken ${photo.localDate}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (selected) UniPrimary.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.12f),
                            ),
                    )
                    Icon(
                        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) UniAccent else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(2.dp),
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
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EditNote, null, Modifier.size(48.dp), tint = UniPrimary)
                Spacer(Modifier.height(16.dp))
                Text("No notes yet", color = UniText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Tap Note for today’s entry.", color = UniTextSecondary, textAlign = TextAlign.Center)
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
