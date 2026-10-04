package com.sina.uninotes.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

enum class FlashMode { Off, Auto, On }

data class CameraCapabilities(
    val hasFlash: Boolean,
    val minZoom: Float,
    val maxZoom: Float,
    val supportsFrontCamera: Boolean,
)

class CameraCaptureController(
    private val context: Context,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val capturing = AtomicBoolean(false)

    private val _capabilities = MutableStateFlow(CameraCapabilities(false, 1f, 1f, false))
    val capabilityState = _capabilities.asStateFlow()
    val capabilities: CameraCapabilities get() = _capabilities.value
    private val _zoom = MutableStateFlow(1f)
    val zoom = _zoom.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        flashMode: FlashMode,
    ) {
        val provider = awaitProvider()
        cameraProvider = provider
        provider.unbindAll()

        _ready.value = false
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        imageCapture = ImageCapture.Builder()
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                .build())
            .setJpegQuality(98)
            .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode.toImageCapture())
            .build()

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val selected = if (provider.hasCamera(selector)) selector else {
            lensFacing = CameraSelector.LENS_FACING_FRONT
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        camera = provider.bindToLifecycle(lifecycleOwner, selected, preview, imageCapture)
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        _capabilities.value = CameraCapabilities(
            hasFlash = cam.cameraInfo.hasFlashUnit(),
            minZoom = zoomState?.minZoomRatio ?: 1f,
            maxZoom = zoomState?.maxZoomRatio ?: 1f,
            supportsFrontCamera = hasFrontCamera(provider) && provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA),
        )
        cam.cameraInfo.zoomState.observe(lifecycleOwner) { state -> _zoom.value = state.zoomRatio }
        _ready.value = true
        applyFlash(flashMode)
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        _ready.value = false
        camera = null
        imageCapture = null
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun currentZoom(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val clamped = min(max(ratio, state.minZoomRatio), state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun setLinearZoom(linear: Float) {
        camera?.cameraControl?.setLinearZoom(linear.coerceIn(0f, 1f))
    }

    fun applyFlash(mode: FlashMode) {
        imageCapture?.flashMode = if (capabilities.hasFlash) mode.toImageCapture() else ImageCapture.FLASH_MODE_OFF
    }

    fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun setExposure(normalized: Float) {
        val cam = camera ?: return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        if (range.lower >= range.upper) return
        val index = (range.lower + (range.upper - range.lower) * normalized.coerceIn(0f, 1f)).toInt()
        cam.cameraControl.setExposureCompensationIndex(index)
    }

    fun supportsExposure(): Boolean {
        val range = camera?.cameraInfo?.exposureState?.exposureCompensationRange ?: return false
        return range.lower < range.upper
    }

    suspend fun takePicture(outputFile: File): Result<File> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("Camera not ready"))
        if (!capturing.compareAndSet(false, true)) {
            return Result.failure(IllegalStateException("Capture already in progress"))
        }
        return try {
            outputFile.parentFile?.mkdirs()
            suspendCoroutine { cont ->
                val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            cont.resume(Result.success(outputFile))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            cont.resume(Result.failure(exception))
                        }
                    },
                )
            }
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            capturing.set(false)
        }
    }

    private fun FlashMode.toImageCapture(): Int = when (this) {
        FlashMode.Off -> ImageCapture.FLASH_MODE_OFF
        FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.On -> ImageCapture.FLASH_MODE_ON
    }

    private fun hasFrontCamera(provider: ProcessCameraProvider): Boolean =
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(context) as Executor,
        )
    }
}
