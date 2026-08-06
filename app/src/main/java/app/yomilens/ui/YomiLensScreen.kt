@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.yomilens.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomilens.model.OutputMode
import app.yomilens.model.ReadingLine

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
            .build()
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    YomiLensScreen(
        state = state,
        hasCameraPermission = hasCameraPermission,
        imageCapture = imageCapture,
        onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onModeSelected = viewModel::selectMode,
        onScan = {
            viewModel.beginCapture()
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
        },
        onRetryEnglish = viewModel::retryEnglish,
        onCameraError = viewModel::onCaptureFailed,
    )
}

@Composable
fun YomiLensScreen(
    state: YomiLensUiState,
    hasCameraPermission: Boolean,
    imageCapture: ImageCapture,
    onRequestCamera: () -> Unit,
    onModeSelected: (OutputMode) -> Unit,
    onScan: () -> Unit,
    onRetryEnglish: () -> Unit,
    onCameraError: (String) -> Unit,
) {
    Scaffold { safePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(safePadding),
        ) {
            AppHeader()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.43f)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 3.dp,
            ) {
                if (hasCameraPermission) {
                    CameraViewport(
                        imageCapture = imageCapture,
                        onCameraError = onCameraError,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CameraPermissionPanel(onRequestCamera)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.57f)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 4.dp,
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        text = "Show me",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutputModeSelector(
                        selected = state.selectedMode,
                        enabled = state.phase != ScanPhase.CAPTURING &&
                            state.phase != ScanPhase.RECOGNIZING,
                        onSelected = onModeSelected,
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    ResultPanel(
                        state = state,
                        onRetryEnglish = onRetryEnglish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 10.dp),
                    )
                    Button(
                        onClick = onScan,
                        enabled = hasCameraPermission && !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("scan_button"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (state.selectedMode == OutputMode.ENGLISH) {
                                "Scan & translate with Google"
                            } else {
                                "Scan Japanese"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("読", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("YomiLens", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Point. Scan. Read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CameraPermissionPanel(onRequestCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "YomiLens uses the camera only to read the text you scan. Images are not saved.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRequestCamera) { Text("Allow camera") }
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
private fun ResultPanel(
    state: YomiLensUiState,
    onRetryEnglish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            state.phase == ScanPhase.CAPTURING || state.phase == ScanPhase.RECOGNIZING -> {
                ProgressMessage("Reading Japanese…")
            }

            state.phase == ScanPhase.PREPARING_ENGLISH -> {
                ProgressMessage("Preparing the on-device English model…")
            }

            state.phase == ScanPhase.ERROR -> {
                ErrorMessage(
                    message = state.errorMessage.orEmpty(),
                    canRetryEnglish = state.selectedMode == OutputMode.ENGLISH &&
                        state.recognizedJapanese.isNotBlank(),
                    onRetryEnglish = onRetryEnglish,
                )
            }

            state.recognizedJapanese.isBlank() -> {
                Text(
                    "Center a clear line of Japanese in the guide, choose an output, then scan.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            state.selectedMode == OutputMode.FURIGANA -> {
                FuriganaOutput(state.readingLines)
            }

            state.selectedMode == OutputMode.ROMAJI -> {
                PlainOutput(state.romaji)
            }

            else -> {
                EnglishOutput(state.english.orEmpty())
            }
        }
    }
}

@Composable
private fun ProgressMessage(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    canRetryEnglish: Boolean,
    onRetryEnglish: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (canRetryEnglish) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onRetryEnglish) { Text("Retry model download") }
        }
    }
}

@Composable
private fun FuriganaOutput(lines: List<ReadingLine>) {
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("output"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lines.forEach { line ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    line.tokens.forEach { token ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = token.furigana ?: " ",
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = token.surface,
                                fontSize = 22.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainOutput(text: String) {
    SelectionContainer {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("output"),
            style = MaterialTheme.typography.headlineSmall,
            lineHeight = 32.sp,
        )
    }
}

@Composable
private fun EnglishOutput(text: String) {
    var showDisclaimer by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("output"),
    ) {
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.headlineSmall, lineHeight = 32.sp)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = { uriHandler.openUri("https://translate.google.com") },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Powered by Google Translate")
        }
        TextButton(
            onClick = { showDisclaimer = true },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Automatic translation disclaimer")
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
