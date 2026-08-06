package app.yomilens.ui

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraViewport(
    imageCapture: ImageCapture,
    onCameraError: (String) -> Unit,
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

    DisposableEffect(lifecycleOwner, previewView, imageCapture) {
        val disposed = AtomicBoolean(false)
        var cameraProvider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (disposed.get()) return@addListener
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } catch (_: Exception) {
                    onCameraError("The camera could not start on this device.")
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed.set(true)
            cameraProvider?.unbindAll()
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
    val density = LocalDensity.current
    val lineLength = with(density) { 34.dp.toPx() }
    val inset = with(density) { 30.dp.toPx() }
    val strokeWidth = with(density) { 3.dp.toPx() }

    Canvas(modifier) {
        val left = inset
        val right = size.width - inset
        val top = size.height * 0.27f
        val bottom = size.height * 0.73f
        val shade = Color.Black.copy(alpha = 0.10f)
        drawRect(shade)

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
