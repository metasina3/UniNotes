package com.sina.uninotes.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sina.uninotes.camera.CameraCaptureController
import com.sina.uninotes.camera.FlashMode
import com.sina.uninotes.data.repository.PhotoRepository
import com.sina.uninotes.ui.components.ContentText
import com.sina.uninotes.ui.theme.UniAccent
import com.sina.uninotes.ui.theme.UniBackground
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.UniTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    photoRepository: PhotoRepository,
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val latest by viewModel.latestPhoto.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val controller = remember { CameraCaptureController(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var rebindKey by remember { mutableStateOf(0) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permanentlyDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(permissionGranted, previewView, ui.flashMode, rebindKey) {
        val pv = previewView ?: return@LaunchedEffect
        if (!permissionGranted) return@LaunchedEffect
        runCatching { controller.bind(lifecycleOwner, pv, ui.flashMode) }
        viewModel.updateZoom(controller.currentZoom())
    }

    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
    }

    LaunchedEffect(ui.focusPoint) {
        if (ui.focusPoint != null) {
            delay(900)
            viewModel.clearFocusIndicator()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!permissionGranted) {
            PermissionPane(
                permanentlyDenied = permanentlyDenied,
                onRequest = {
                    permanentlyDenied = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
                onBack = onBack,
            )
            return@Box
        }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = pv
                    val detector = ScaleGestureDetector(
                        ctx,
                        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                controller.setZoomRatio(controller.currentZoom() * detector.scaleFactor)
                                viewModel.updateZoom(controller.currentZoom())
                                return true
                            }
                        },
                    )
                    pv.setOnTouchListener { _, event ->
                        detector.onTouchEvent(event)
                        if (event.action == MotionEvent.ACTION_UP && !detector.isInProgress) {
                            controller.tapToFocus(pv, event.x, event.y)
                            viewModel.showFocus(event.x, event.y)
                        }
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        ui.focusPoint?.let { (x, y) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = with(androidx.compose.ui.platform.LocalDensity.current) { x.toDp() } - 24.dp)
                        .padding(top = with(androidx.compose.ui.platform.LocalDensity.current) { y.toDp() } - 24.dp)
                        .size(48.dp)
                        .border(2.dp, UniAccent, RoundedCornerShape(8.dp)),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                ContentText(
                    text = ui.subjectName,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    modifier = Modifier.weight(1f),
                )
                if (controller.capabilities.hasFlash) {
                    IconButton(onClick = { viewModel.cycleFlash(true) }) {
                        Icon(
                            imageVector = when (ui.flashMode) {
                                FlashMode.Off -> Icons.Default.FlashOff
                                FlashMode.Auto -> Icons.Default.FlashAuto
                                FlashMode.On -> Icons.Default.FlashOn
                            },
                            contentDescription = "Flash",
                            tint = Color.White,
                        )
                    }
                }
                if (controller.capabilities.supportsFrontCamera) {
                    IconButton(onClick = {
                        controller.switchCamera()
                        rebindKey++
                    }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                    }
                }
            }
            Text(
                text = String.format("%.1fx", ui.zoomRatio),
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp),
            )
            if (controller.capabilities.maxZoom >= 2f) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    TextButton(onClick = {
                        controller.setZoomRatio(1f)
                        viewModel.updateZoom(1f)
                    }) { Text("1x", color = Color.White) }
                    TextButton(onClick = {
                        controller.setZoomRatio(minOf(2f, controller.capabilities.maxZoom))
                        viewModel.updateZoom(controller.currentZoom())
                    }) { Text("2x", color = Color.White) }
                }
            }
            if (controller.supportsExposure()) {
                var exposure by remember { mutableStateOf(0.5f) }
                Slider(
                    value = exposure,
                    onValueChange = {
                        exposure = it
                        controller.setExposure(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .padding(start = 12.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ui.statusMessage?.let { Text(it, color = Color.White) }
            ui.errorMessage?.let { Text(it, color = UniAccent) }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val thumb = latest
                if (thumb != null) {
                    val path = thumb.thumbnailRelativePath ?: thumb.relativePath
                    AsyncImage(
                        model = photoRepository.resolveFile(path),
                        contentDescription = "Last photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenPhoto(thumb.id) },
                    )
                } else {
                    Spacer(Modifier.size(56.dp))
                }

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !ui.capturing) {
                            scope.launch {
                                val temp = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
                                controller.takePicture(temp).fold(
                                    onSuccess = { viewModel.onCaptureFileReady(it) },
                                    onFailure = { error ->
                                        if (error.message != "Capture already in progress") {
                                            viewModel.reportError(error.message ?: "Capture failed")
                                        }
                                    },
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.capturing) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = UniBackground)
                    } else {
                        Box(
                            Modifier
                                .size(68.dp)
                                .border(3.dp, UniBackground, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun PermissionPane(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UniBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera permission needed", color = UniText)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) {
                "Camera access is denied. Open Settings to allow camera permission for UniNotes."
            } else {
                "UniNotes needs the camera to capture whiteboard photos inside the app."
            },
            color = UniTextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = if (permanentlyDenied) onOpenSettings else onRequest) {
            Text(if (permanentlyDenied) "Open Settings" else "Grant permission")
        }
        TextButton(onClick = onBack) { Text("Back", color = UniTextSecondary) }
    }
}
