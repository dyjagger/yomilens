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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomilens.model.LensOverlayItem
import app.yomilens.model.OverlayLabelSpec
import app.yomilens.model.LensOverlayPlacement
import app.yomilens.model.OutputMode
import app.yomilens.model.TextOrientation
import kotlinx.coroutines.delay
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
    val currentAutoCapture = rememberUpdatedState {
        val controller = cameraController
        if (
            controller != null &&
            viewModel.tryBeginCapture(controller.isStreaming())
        ) {
            try {
                imageCapture.takePicture(
                    mainExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            viewModel.onImageCaptured(image)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            viewModel.onCaptureFailed(
                                exception.message ?: "The photo could not be captured. Retrying automatically.",
                            )
                        }
                    },
                )
            } catch (error: Exception) {
                viewModel.onCaptureFailed(
                    error.message ?: "The photo could not be captured. Retrying automatically.",
                )
            }
        }
    }

    LaunchedEffect(hasCameraPermission, cameraController, state.isBusy) {
        if (hasCameraPermission && cameraController != null && !state.isBusy) {
            delay(AUTO_SCAN_INTERVAL_MILLIS)
            currentAutoCapture.value()
        }
    }

    YomiLensScreen(
        state = state,
        hasCameraPermission = hasCameraPermission,
        isCameraReady = cameraController != null,
        imageCapture = imageCapture,
        onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onModeSelected = viewModel::selectMode,
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
    val edgePadding = with(density) { 8.dp.toPx() }
    val collisionGap = with(density) { 6.dp.toPx() }
    val topReserved = topReservedPixels.takeIf { it > 0f }
        ?: with(density) { 68.dp.toPx() }
    val bottomReserved = bottomReservedPixels.takeIf { it > 0f }
        ?: with(density) { 204.dp.toPx() }
    Box(modifier.testTag("translation_overlay_layer")) {
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
        CollisionAwareOverlayLabels(
            items = items,
            mode = mode,
            outlineColor = outlineColor,
            edgePadding = edgePadding,
            collisionGap = collisionGap,
            topReserved = topReserved,
            bottomReserved = bottomReserved,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private data class OverlayLabelContent(
    val index: Int,
    val item: LensOverlayItem,
    val text: String,
)

@Composable
private fun CollisionAwareOverlayLabels(
    items: List<LensOverlayItem>,
    mode: OutputMode,
    outlineColor: Color,
    edgePadding: Float,
    collisionGap: Float,
    topReserved: Float,
    bottomReserved: Float,
    modifier: Modifier = Modifier,
) {
    val labels = items.mapIndexedNotNull { index, item ->
        item.labelFor(mode).takeIf(String::isNotBlank)?.let { label ->
            OverlayLabelContent(index, item, label)
        }
    }
    SubcomposeLayout(modifier) { constraints ->
        val maximumLabelWidth = (constraints.maxWidth - edgePadding * 2f)
            .roundToInt()
            .coerceAtLeast(1)
        val maximumLabelHeight = (
            constraints.maxHeight -
                maxOf(topReserved, edgePadding) -
                bottomReserved
            ).roundToInt().coerceAtLeast(1)
        val measured = labels.map { label ->
            val placeable = subcompose(label.index to label.text) {
                TranslationBubble(label, outlineColor)
            }.single().measure(
                Constraints(
                    maxWidth = maximumLabelWidth,
                    maxHeight = maximumLabelHeight,
                ),
            )
            label to placeable
        }
        val placements = LensOverlayPlacement.calculateAll(
            specs = measured.map { (label, placeable) ->
                OverlayLabelSpec(
                    id = label.index,
                    bounds = label.item.bounds,
                    width = placeable.width.toFloat(),
                    height = placeable.height.toFloat(),
                )
            },
            viewportWidth = constraints.maxWidth.toFloat(),
            viewportHeight = constraints.maxHeight.toFloat(),
            edgePadding = edgePadding,
            topReserved = topReserved,
            bottomReserved = bottomReserved,
            collisionGap = collisionGap,
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            measured.forEach { (label, placeable) ->
                placements[label.index]?.let { placement ->
                    placeable.place(
                        x = placement.x.roundToInt(),
                        y = placement.y.roundToInt(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationBubble(label: OverlayLabelContent, outlineColor: Color) {
    Surface(
        modifier = Modifier
            .testTag("overlay_${label.index}")
            .semantics {
                contentDescription = "${label.item.japanese}: ${label.text}"
            },
        color = Color.Black.copy(alpha = 0.82f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.95f)),
    ) {
        if (label.item.orientation == TextOrientation.VERTICAL) {
            VerticalOverlayText(label)
        } else {
            Text(
                text = label.text,
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

@Composable
private fun VerticalOverlayText(label: OverlayLabelContent) {
    val columns = label.text.filterNot { character -> character == '\r' }
        .chunked(MAX_VERTICAL_GLYPHS_PER_COLUMN)
        .asReversed()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .testTag("vertical_overlay_text_${label.index}"),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Top,
        ) {
            columns.forEach { column ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    column.forEach { character ->
                        if (character.isWhitespace()) {
                            Spacer(Modifier.height(7.dp))
                        } else {
                            Text(
                                text = character.toString(),
                                fontSize = 15.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
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
        phase == ScanPhase.CAPTURING -> "Capturing automatically…"
        phase == ScanPhase.RECOGNIZING -> "Reading kanji…"
        phase == ScanPhase.PREPARING_ENGLISH -> "Preparing English…"
        phase == ScanPhase.ERROR -> "Retrying automatically…"
        itemCount > 0 -> "$itemCount kanji ${if (itemCount == 1) "area" else "areas"} found"
        phase == ScanPhase.READY -> "No kanji in view • scanning automatically"
        else -> "Point at kanji • scanning automatically"
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
                Text(
                    text = if (hasCameraPermission && isCameraReady) {
                        "Automatic kanji scanning is on • tap the lens to focus"
                    } else {
                        "Automatic scanning starts when the camera is ready"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 5.dp)
                        .testTag("auto_scan_status")
                        .semantics {
                            contentDescription = "Completed automatic scans: ${state.completedScanCount}"
                        },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private const val AUTO_SCAN_INTERVAL_MILLIS = 1_500L
private const val MAX_VERTICAL_GLYPHS_PER_COLUMN = 10
