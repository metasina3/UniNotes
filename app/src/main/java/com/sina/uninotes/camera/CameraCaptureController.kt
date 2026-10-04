package com.sina.uninotes.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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

    var capabilities: CameraCapabilities = CameraCapabilities(false, 1f, 1f, true)
        private set

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        flashMode: FlashMode,
    ) {
        val provider = awaitProvider()
        cameraProvider = provider
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode.toImageCapture())
            .build()

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        capabilities = CameraCapabilities(
            hasFlash = cam.cameraInfo.hasFlashUnit(),
            minZoom = zoomState?.minZoomRatio ?: 1f,
            maxZoom = zoomState?.maxZoomRatio ?: 1f,
            supportsFrontCamera = hasFrontCamera(provider),
        )
        applyFlash(flashMode)
    }

    fun unbind() {
        cameraProvider?.unbindAll()
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
        imageCapture?.flashMode = mode.toImageCapture()
    }

    fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat(),
        )
        val point = factory.createPoint(x, y)
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
