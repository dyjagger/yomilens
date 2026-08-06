package app.yomilens.ui

import android.view.MotionEvent
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.yomilens.ml.ScanGuideCrop
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CameraScanController internal constructor(
    private val camera: Camera,
    private val previewView: PreviewView,
    private val mainExecutor: Executor,
) {
    fun focusGuide(onFocused: () -> Unit) {
        val delivered = AtomicBoolean(false)
        val finish = {
            if (delivered.compareAndSet(false, true)) onFocused()
        }
        try {
            val future = startFocus(previewView.width / 2f, previewView.height / 2f, 0.35f)
            future?.addListener(finish, mainExecutor) ?: finish()
            previewView.postDelayed(finish, FOCUS_TIMEOUT_MILLIS)
        } catch (_: Exception) {
            finish()
        }
    }

    fun focusAt(x: Float, y: Float, size: Float = 0.18f) {
        try {
            startFocus(x, y, size)
        } catch (_: Exception) {
            // Continuous autofocus remains available when a metering point is unsupported.
        }
    }

    private fun startFocus(x: Float, y: Float, size: Float): com.google.common.util.concurrent.ListenableFuture<*>? {
        if (previewView.width <= 0 || previewView.height <= 0) return null
        val point = previewView.meteringPointFactory.createPoint(x, y, size)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        return camera.cameraControl.startFocusAndMetering(action)
    }

    private companion object {
        const val FOCUS_TIMEOUT_MILLIS = 1_500L
    }
}

@Composable
fun CameraViewport(
    imageCapture: ImageCapture,
    onCameraError: (String) -> Unit,
    onCameraReady: (CameraScanController?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val currentOnCameraError = rememberUpdatedState(onCameraError)
    val currentOnCameraReady = rememberUpdatedState(onCameraReady)

    DisposableEffect(lifecycleOwner, previewView, imageCapture) {
        val disposed = AtomicBoolean(false)
        var cameraProvider: ProcessCameraProvider? = null
        var boundController: CameraScanController? = null
        var hasBoundCamera = false
        val streamObserver = Observer<PreviewView.StreamState> { streamState ->
            currentOnCameraReady.value(
                boundController.takeIf { streamState == PreviewView.StreamState.STREAMING },
            )
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)

        val bindCamera = bindCamera@{
            if (disposed.get() || hasBoundCamera) return@bindCamera
            val provider = cameraProvider ?: return@bindCamera
            val viewPort = previewView.viewPort ?: return@bindCamera
            try {
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val useCases = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                    .setViewPort(viewPort)
                    .build()

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCases,
                )
                val scanController = CameraScanController(camera, previewView, mainExecutor)
                boundController = scanController
                previewView.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_UP -> {
                            scanController.focusAt(event.x, event.y)
                            previewView.performClick()
                            true
                        }
                        MotionEvent.ACTION_DOWN -> true
                        else -> false
                    }
                }
                hasBoundCamera = true
                if (previewView.previewStreamState.value == PreviewView.StreamState.STREAMING) {
                    currentOnCameraReady.value(scanController)
                }
            } catch (_: Exception) {
                boundController = null
                currentOnCameraReady.value(null)
                currentOnCameraError.value("The camera could not start on this device.")
            }
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (disposed.get()) return@addListener
                try {
                    cameraProvider = providerFuture.get()
                    previewView.doOnLayout { bindCamera() }
                    bindCamera()
                } catch (_: Exception) {
                    currentOnCameraReady.value(null)
                    currentOnCameraError.value("The camera could not start on this device.")
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed.set(true)
            previewView.previewStreamState.removeObserver(streamObserver)
            previewView.setOnTouchListener(null)
            boundController = null
            cameraProvider?.unbindAll()
            currentOnCameraReady.value(null)
        }
    }

    Box(
        modifier = modifier.semantics {
            contentDescription = "Live camera preview. Center Japanese text inside the guide."
        },
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        FocusGuide(Modifier.fillMaxSize())
    }
}

@Composable
private fun FocusGuide(modifier: Modifier = Modifier) {
    val guideColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier) {
        val left = size.width * ScanGuideCrop.HORIZONTAL_INSET
        val right = size.width * (1f - ScanGuideCrop.HORIZONTAL_INSET)
        val top = size.height * ScanGuideCrop.TOP
        val bottom = size.height * ScanGuideCrop.BOTTOM
        val lineLength = size.minDimension * 0.10f
        val strokeWidth = size.minDimension * 0.009f
        val shade = Color.Black.copy(alpha = 0.28f)
        drawRect(shade, topLeft = Offset.Zero, size = size.copy(height = top))
        drawRect(shade, topLeft = Offset(0f, bottom), size = size.copy(height = size.height - bottom))
        drawRect(shade, topLeft = Offset(0f, top), size = size.copy(width = left, height = bottom - top))
        drawRect(
            shade,
            topLeft = Offset(right, top),
            size = size.copy(width = size.width - right, height = bottom - top),
        )

        fun corner(horizontalStart: Offset, horizontalEnd: Offset, verticalEnd: Offset) {
            drawLine(guideColor, horizontalStart, horizontalEnd, strokeWidth, StrokeCap.Round)
            drawLine(guideColor, horizontalStart, verticalEnd, strokeWidth, StrokeCap.Round)
        }

        corner(Offset(left, top), Offset(left + lineLength, top), Offset(left, top + lineLength))
        corner(Offset(right, top), Offset(right - lineLength, top), Offset(right, top + lineLength))
        corner(Offset(left, bottom), Offset(left + lineLength, bottom), Offset(left, bottom - lineLength))
        corner(Offset(right, bottom), Offset(right - lineLength, bottom), Offset(right, bottom - lineLength))
    }
}
