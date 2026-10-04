package com.sina.uninotes.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sina.uninotes.R
import com.sina.uninotes.data.local.db.SubjectWithCounts
import com.sina.uninotes.ui.components.ContentText
import com.sina.uninotes.ui.theme.UniAccent
import com.sina.uninotes.ui.theme.UniBackground
import com.sina.uninotes.ui.theme.UniPrimary
import com.sina.uninotes.ui.theme.UniSurface
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSubject: (String) -> Unit,
    onOpenBackup: () -> Unit,
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = UniBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_dino_logo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, UniAccent.copy(alpha = 0.35f), CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("DinoNotes", color = UniText, fontWeight = FontWeight.Bold)
                            Text(
                                "My Subjects",
                                color = UniTextSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = UniText)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Backup & Restore") },
                            onClick = {
                                menuExpanded = false
                                onOpenBackup()
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UniBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showCreate,
                modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                containerColor = UniPrimary,
                contentColor = UniBackground,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create subject")
            }
        },
    ) { padding ->
        if (subjects.isEmpty()) {
            EmptySubjects(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onCreate = viewModel::showCreate,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(subjects, key = { it.id }) { subject ->
                    SubjectCard(
                        subject = subject,
                        onOpen = { onOpenSubject(subject.id) },
                        onRename = { viewModel.showRename(subject.id, subject.name) },
                        onDelete = { viewModel.showDelete(subject.id, subject.name) },
                    )
                }
            }
        }
    }

    when (val dialog = ui.dialog) {
        HomeDialog.Create -> SubjectNameDialog(
            title = "New subject",
            confirmLabel = "Create",
            initial = "",
            error = ui.errorMessage,
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::createSubject,
        )
        is HomeDialog.Rename -> SubjectNameDialog(
            title = "Rename subject",
            confirmLabel = "Save",
            initial = dialog.currentName,
            error = ui.errorMessage,
            onDismiss = viewModel::dismissDialog,
            onConfirm = { viewModel.renameSubject(dialog.subjectId, it) },
        )
        is HomeDialog.DeleteConfirm -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Delete subject?") },
            text = {
                Text("Delete “${dialog.name}”? All photos and notes in this subject will also be removed.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSubject(dialog.subjectId) }) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
            },
        )
        null -> Unit
    }
}

@Composable
private fun EmptySubjects(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No subjects yet", color = UniText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a subject for each course. Photos and daily notes stay organized per subject.",
            color = UniTextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onCreate) {
            Text("Create subject", color = UniPrimary)
        }
    }
}

@Composable
private fun SubjectCard(
    subject: SubjectWithCounts,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UniSurface)
            .border(1.dp, UniTextSecondary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(subject.colorArgb).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.MenuBook, null, tint = Color(subject.colorArgb), modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            ContentText(
                text = subject.name,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = UniText),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${subject.photoCount} photos · ${subject.noteCount} notes",
                color = UniTextSecondary,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Subject options", tint = UniTextSecondary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun SubjectNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.ContentOrLtr, textAlign = TextAlign.Start,
                    ),
                    label = { Text("Subject name") },
                    isError = error != null,
                    supportingText = {
                        if (error != null) Text(error, color = Color(0xFFFF6B6B))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel, color = UniPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
