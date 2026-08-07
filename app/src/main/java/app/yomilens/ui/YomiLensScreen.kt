package app.yomilens.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomilens.model.LensOverlayItem
import app.yomilens.model.LensOverlayPlacement
import app.yomilens.model.OutputMode
import kotlin.math.roundToInt

@Composable
fun YomiLensRoute(viewModel: YomiLensViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .build()
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var cameraController by remember { mutableStateOf<CameraScanController?>(null) }

    YomiLensScreen(
        state = state,
        hasCameraPermission = hasCameraPermission,
        isCameraReady = cameraController != null,
        imageCapture = imageCapture,
        onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onModeSelected = viewModel::selectMode,
        onScan = {
            val controller = cameraController
            if (controller == null) {
                viewModel.onCaptureFailed("The camera is still starting. Please wait a moment and try again.")
                return@YomiLensScreen
            }
            viewModel.beginCapture()
            controller.focusCenter {
                try {
                    imageCapture.takePicture(
                        mainExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                viewModel.onImageCaptured(image)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                viewModel.onCaptureFailed(
                                    exception.message ?: "The photo could not be captured. Please try again.",
                                )
                            }
                        },
                    )
                } catch (error: Exception) {
                    viewModel.onCaptureFailed(
                        error.message ?: "The photo could not be captured. Please try again.",
                    )
                }
            }
        },
        onRetryEnglish = viewModel::retryEnglish,
        onCameraError = viewModel::onCaptureFailed,
        onCameraReady = { cameraController = it },
    )
}

@Composable
fun YomiLensScreen(
    state: YomiLensUiState,
    hasCameraPermission: Boolean,
    isCameraReady: Boolean,
    imageCapture: ImageCapture,
    onRequestCamera: () -> Unit,
    onModeSelected: (OutputMode) -> Unit,
    onScan: () -> Unit,
    onRetryEnglish: () -> Unit,
    onCameraError: (String) -> Unit,
    onCameraReady: (CameraScanController?) -> Unit,
) {
    var topBarHeightPixels by remember { mutableStateOf(0) }
    var controlsHeightPixels by remember { mutableStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            CameraViewport(
                imageCapture = imageCapture,
                onCameraError = onCameraError,
                onCameraReady = onCameraReady,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CameraPermissionPanel(onRequestCamera)
        }

        val retainedFrame = state.frozenFrame
        DisposableEffect(retainedFrame) {
            val retainedByComposition = retainedFrame?.retain() == true
            onDispose {
                if (retainedByComposition) retainedFrame.release()
            }
        }
        retainedFrame
            ?.bitmap
            ?.takeUnless { it.isRecycled }
            ?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Scanned camera frame",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("frozen_camera_frame"),
                    contentScale = ContentScale.FillBounds,
                )
            }

        if (state.overlayItems.isNotEmpty()) {
            TranslationOverlayLayer(
                items = state.overlayItems,
                mode = state.selectedMode,
                topReservedPixels = topBarHeightPixels.toFloat(),
                bottomReservedPixels = controlsHeightPixels.toFloat(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        LensTopBar(
            phase = state.phase,
            isCameraReady = isCameraReady,
            itemCount = state.overlayItems.size,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeightPixels = it.height },
        )

        LensControls(
            state = state,
            hasCameraPermission = hasCameraPermission,
            isCameraReady = isCameraReady,
            onModeSelected = onModeSelected,
            onScan = onScan,
            onRetryEnglish = onRetryEnglish,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { controlsHeightPixels = it.height },
        )
    }
}

@Composable
private fun TranslationOverlayLayer(
    items: List<LensOverlayItem>,
    mode: OutputMode,
    topReservedPixels: Float,
    bottomReservedPixels: Float,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    BoxWithConstraints(modifier.testTag("translation_overlay_layer")) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val edgePadding = with(density) { 8.dp.toPx() }
        val fallbackLabelWidth = with(density) { 160.dp.toPx() }
        val fallbackLabelHeight = with(density) { 42.dp.toPx() }
        val topReserved = topReservedPixels.takeIf { it > 0f }
            ?: with(density) { 68.dp.toPx() }
        val bottomReserved = bottomReservedPixels.takeIf { it > 0f }
            ?: with(density) { 204.dp.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            items.forEach { item ->
                val left = item.bounds.left * size.width
                val top = item.bounds.top * size.height
                val width = (item.bounds.right - item.bounds.left) * size.width
                val height = (item.bounds.bottom - item.bounds.top) * size.height
                drawRoundRect(
                    color = outlineColor.copy(alpha = 0.9f),
                    topLeft = Offset(left, top),
                    size = ComposeSize(width, height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }

        items.forEachIndexed { index, item ->
            val label = item.labelFor(mode)
            if (label.isBlank()) return@forEachIndexed
            var measuredLabelSize by remember(item, mode, label) { mutableStateOf(IntSize.Zero) }
            val labelWidth = measuredLabelSize.width.takeIf { it > 0 }?.toFloat()
                ?: fallbackLabelWidth.coerceAtMost(viewportWidth - edgePadding * 2f)
            val labelHeight = measuredLabelSize.height.takeIf { it > 0 }?.toFloat()
                ?: fallbackLabelHeight
            val placement = LensOverlayPlacement.calculate(
                bounds = item.bounds,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                labelWidth = labelWidth,
                labelHeight = labelHeight,
                edgePadding = edgePadding,
                topReserved = topReserved,
                bottomReserved = bottomReserved,
            )
            val maximumWidth = with(density) { placement.maxWidth.toDp() }
            Surface(
                modifier = Modifier
                    .offset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }
                    .widthIn(max = maximumWidth)
                    .onSizeChanged { measuredLabelSize = it }
                    .zIndex(2f)
                    .testTag("overlay_$index")
                    .semantics {
                        contentDescription = "${item.japanese}: $label"
                    },
                color = Color.Black.copy(alpha = 0.78f),
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.9f)),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun LensOverlayItem.labelFor(mode: OutputMode): String = when (mode) {
    OutputMode.FURIGANA -> furigana
    OutputMode.ROMAJI -> romaji
    OutputMode.ENGLISH -> english.orEmpty()
}

@Composable
private fun LensTopBar(
    phase: ScanPhase,
    isCameraReady: Boolean,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val message = when {
        !isCameraReady -> "Starting camera…"
        phase == ScanPhase.CAPTURING -> "Focusing and capturing…"
        phase == ScanPhase.RECOGNIZING -> "Reading Japanese…"
        phase == ScanPhase.PREPARING_ENGLISH -> "Preparing English…"
        phase == ScanPhase.ERROR -> "Check the message below"
        itemCount > 0 -> "$itemCount text ${if (itemCount == 1) "area" else "areas"} found"
        else -> "Point at Japanese • tap the image to focus"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "読  YomiLens",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (phase in setOf(ScanPhase.CAPTURING, ScanPhase.RECOGNIZING, ScanPhase.PREPARING_ENGLISH)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(message, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LensControls(
    state: YomiLensUiState,
    hasCameraPermission: Boolean,
    isCameraReady: Boolean,
    onModeSelected: (OutputMode) -> Unit,
    onScan: () -> Unit,
    onRetryEnglish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lens_controls")
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.phase == ScanPhase.ERROR) {
            LensErrorMessage(
                message = state.errorMessage.orEmpty(),
                canRetryEnglish = state.selectedMode == OutputMode.ENGLISH &&
                    state.recognizedJapanese.isNotBlank(),
                onRetryEnglish = onRetryEnglish,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 5.dp,
        ) {
            Column(Modifier.padding(10.dp)) {
                OutputModeSelector(
                    selected = state.selectedMode,
                    enabled = state.phase != ScanPhase.CAPTURING && state.phase != ScanPhase.RECOGNIZING,
                    onSelected = onModeSelected,
                )
                if (state.selectedMode == OutputMode.ENGLISH) {
                    GoogleTranslationLinks()
                } else {
                    Spacer(Modifier.height(7.dp))
                }
                Button(
                    onClick = onScan,
                    enabled = hasCameraPermission && isCameraReady && !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("scan_button"),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(
                        when {
                            state.frozenFrame != null -> "Scan again"
                            state.selectedMode == OutputMode.ENGLISH -> "Scan & translate with Google"
                            else -> "Scan Japanese"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputModeSelector(
    selected: OutputMode,
    enabled: Boolean,
    onSelected: (OutputMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutputMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                enabled = enabled,
                onClick = { onSelected(mode) },
                label = {
                    Text(
                        text = mode.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_${mode.name.lowercase()}")
                    .semantics { role = Role.RadioButton },
            )
        }
    }
}

@Composable
private fun CameraPermissionPanel(onRequestCamera: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(28.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 5.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "YomiLens reads the camera in memory. Images are never saved or uploaded.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRequestCamera) { Text("Allow camera") }
            }
        }
    }
}

@Composable
private fun LensErrorMessage(
    message: String,
    canRetryEnglish: Boolean,
    onRetryEnglish: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (canRetryEnglish) {
                TextButton(onClick = onRetryEnglish) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun GoogleTranslationLinks() {
    var showDisclaimer by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { uriHandler.openUri("https://translate.google.com") }) {
            Text("Powered by Google Translate", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { showDisclaimer = true }) {
            Text("Disclaimer", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) { Text("Close") }
            },
            title = { Text("Translation disclaimer") },
            text = {
                Text(
                    "This service may contain translations powered by Google. Google disclaims all warranties related to the translations, express or implied, including warranties of accuracy, reliability, merchantability, fitness for a particular purpose, and noninfringement.",
                )
            },
        )
    }
}
