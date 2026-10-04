package com.sina.uninotes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sina.uninotes.ui.components.ContentText
import com.sina.uninotes.ui.components.ContentTextField
import com.sina.uninotes.ui.theme.UniAccent
import com.sina.uninotes.ui.theme.UniBackground
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary
import com.sina.uninotes.util.DateFormatting
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var titleValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var bodyValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    LaunchedEffect(ui.ready, ui.noteId) {
        if (!ui.ready) return@LaunchedEffect
        if (titleValue.text != ui.title) titleValue = TextFieldValue(ui.title, TextRange(ui.title.length))
        val selection = if (ui.placeCursorAtEnd) TextRange(ui.body.length) else TextRange(ui.body.length)
        if (bodyValue.text != ui.body) bodyValue = TextFieldValue(ui.body, selection)
        if (ui.placeCursorAtEnd) viewModel.consumeCursorRequest()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.persistNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLeaveScreen()
        }
    }

    BackHandler {
        viewModel.onLeaveScreen()
        onBack()
    }

    Scaffold(
        containerColor = UniBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ContentText(
                            text = ui.subjectName,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = UniText),
                        )
                        val dateLabel = ui.localDate.takeIf { it.isNotBlank() }?.let {
                            DateFormatting.noteListDate(LocalDate.parse(it))
                        }.orEmpty()
                        Text(dateLabel, color = UniTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.onLeaveScreen()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UniText)
                    }
                },
                actions = {
                    Text(
                        text = when (ui.saveStatus) {
                            SaveStatus.Saving -> "Saving…"
                            SaveStatus.Saved -> "Saved"
                            SaveStatus.Failed -> "Save failed"
                            SaveStatus.Idle -> ""
                        },
                        color = if (ui.saveStatus == SaveStatus.Failed) UniAccent else UniTextSecondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    IconButton(onClick = viewModel::requestDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = UniTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UniBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ui.error?.let { error ->
                Text(error, color = UniAccent)
                TextButton(onClick = { if (ui.ready) viewModel.persistNow() else viewModel.retryLoad() }) {
                    Text("Retry")
                }
            }
            ContentTextField(
                value = titleValue,
                onValueChange = {
                    titleValue = it
                    viewModel.onTitleChange(it.text)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = !ui.ready,
                hint = "Optional title",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(color = UniText),
            )
            Spacer(Modifier.height(16.dp))
            ContentTextField(
                value = bodyValue,
                onValueChange = {
                    bodyValue = it
                    viewModel.onBodyChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth(),
                minLines = 12,
                readOnly = !ui.ready,
                hint = "Write today’s note…",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = UniText),
            )
        }
    }

    if (ui.deleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete note?") },
            text = { Text("This note will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(onBack) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") }
            },
        )
    }
}
