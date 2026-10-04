package com.sina.uninotes.ui.backup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sina.uninotes.data.backup.BackupRepository
import com.sina.uninotes.ui.theme.UniAccent
import com.sina.uninotes.ui.theme.UniBackground
import com.sina.uninotes.ui.theme.UniPrimary
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    backupRepository: BackupRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<BackupRepository.Progress?>(null) }
    var confirmRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            message = null
            backupRepository.exportBackup(uri) { progress = it }
                .onSuccess { message = "Backup exported successfully." }
                .onFailure { error = it.message ?: "Export failed" }
            busy = false
            progress = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) confirmRestoreUri = uri
    }

    BackHandler(enabled = busy) { }

    Scaffold(
        containerColor = UniBackground,
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", color = UniText) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UniText)
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                "UniNotes stores everything only on this device. Uninstalling the app removes local data, so export a backup when you need a copy.",
                color = UniTextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Automatic cloud backup of app content is disabled. Use the buttons below to export or restore a local archive.",
                color = UniTextSecondary,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    exportLauncher.launch("uninotes-backup.zip")
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export backup") }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    importLauncher.launch(arrayOf("application/zip", "*/*"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore backup") }
            Spacer(Modifier.height(20.dp))
            progress?.let {
                Text(it.message, color = UniTextSecondary)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { it.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = UniPrimary,
                )
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = UniPrimary)
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = UniAccent)
            }
        }
    }

    confirmRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!busy) confirmRestoreUri = null },
            title = { Text("Replace current library?") },
            text = {
                Text("Restoring will replace all current subjects, notes, and photos after the backup is validated. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            message = null
                            backupRepository.importBackup(uri) { progress = it }
                                .onSuccess { message = "Restore completed." }
                                .onFailure { error = it.message ?: "Restore failed" }
                            busy = false
                            progress = null
                            confirmRestoreUri = null
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmRestoreUri = null }) { Text("Cancel") }
            },
        )
    }
}
