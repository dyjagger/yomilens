package app.yomilens.ui

import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import app.yomilens.model.OutputMode
import app.yomilens.model.LensOverlayItem
import app.yomilens.model.NormalizedBounds
import app.yomilens.model.ReadingLine
import app.yomilens.model.ReadingToken
import app.yomilens.model.TextOrientation
import app.yomilens.ui.theme.YomiLensTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

class YomiLensScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eachSelectionShowsTheExpectedOutputFromTheSameScan() {
        var state by mutableStateOf(sampleState())
        composeRule.setContent {
            YomiLensTheme {
                YomiLensScreen(
                    state = state,
                    hasCameraPermission = false,
                    isCameraReady = false,
                    imageCapture = ImageCapture.Builder().build(),
                    onRequestCamera = {},
                    onModeSelected = { state = state.copy(selectedMode = it) },
                    onRetryEnglish = {},
                    onCameraError = {},
                    onCameraReady = {},
                )
            }
        }

        composeRule.onNodeWithTag("mode_furigana").assertIsSelected()
        composeRule.onNodeWithText("にほんご べんきょう").assertIsDisplayed()
        composeRule.onAllNodesWithTag("vertical_overlay_text_0").assertCountEquals(0)

        composeRule.onNodeWithTag("mode_romaji").performClick()
        composeRule.onNodeWithTag("mode_romaji").assertIsSelected()
        composeRule.onNodeWithText("nihongo benkyou").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_english").performClick()
        composeRule.onNodeWithTag("mode_english").assertIsSelected()
        composeRule.onNodeWithText("I study Japanese.").assertIsDisplayed()
    }

    @Test
    fun cameraControlsDescribeAutomaticScanningWithoutAScanButton() {
        composeRule.setContent {
            YomiLensTheme {
                YomiLensScreen(
                    state = YomiLensUiState(),
                    hasCameraPermission = false,
                    isCameraReady = false,
                    imageCapture = ImageCapture.Builder().build(),
                    onRequestCamera = {},
                    onModeSelected = {},
                    onRetryEnglish = {},
                    onCameraError = {},
                    onCameraReady = {},
                )
            }
        }

        composeRule.onNodeWithTag("auto_scan_status").assertIsDisplayed()
        composeRule.onAllNodesWithTag("scan_button").assertCountEquals(0)
    }

    @Test
    fun verticallyDetectedMangaUsesAVerticalTranslationLabel() {
        val state = sampleState().copy(
            selectedMode = OutputMode.ENGLISH,
            overlayItems = sampleState().overlayItems.map { item ->
                item.copy(orientation = TextOrientation.VERTICAL, english = "ABCDEFGHIJK")
            },
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                YomiLensTheme {
                    YomiLensScreen(
                        state = state,
                        hasCameraPermission = false,
                        isCameraReady = false,
                        imageCapture = ImageCapture.Builder().build(),
                        onRequestCamera = {},
                        onModeSelected = {},
                        onRetryEnglish = {},
                        onCameraError = {},
                        onCameraReady = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("vertical_overlay_text_0").assertIsDisplayed()
        val first = composeRule.onNodeWithText("A").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithText("B").getUnclippedBoundsInRoot()
        val wrapped = composeRule.onNodeWithText("K").getUnclippedBoundsInRoot()
        assertTrue("Vertical text should progress downward: $first then $second", first.top < second.top)
        assertTrue("Later columns should wrap to the left: $first then $wrapped", first.left > wrapped.left)
    }

    @Test
    fun frozenFrameLivesUntilTheCompositionReleasesIt() {
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val frame = RetainedCameraFrame(bitmap)
        var state by mutableStateOf(sampleState().copy(frozenFrame = frame))
        composeRule.setContent {
            YomiLensTheme {
                YomiLensScreen(
                    state = state,
                    hasCameraPermission = false,
                    isCameraReady = false,
                    imageCapture = ImageCapture.Builder().build(),
                    onRequestCamera = {},
                    onModeSelected = {},
                    onRetryEnglish = {},
                    onCameraError = {},
                    onCameraReady = {},
                )
            }
        }

        composeRule.onNodeWithTag("frozen_camera_frame").assertIsDisplayed()
        composeRule.runOnIdle {
            state = state.copy(frozenFrame = null)
            frame.release()
        }
        composeRule.waitForIdle()

        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun bottomEnglishLabelStaysAboveTheMeasuredControlTray() {
        val state = sampleState().copy(
            selectedMode = OutputMode.ENGLISH,
            overlayItems = sampleState().overlayItems.map { item ->
                item.copy(bounds = NormalizedBounds(0.9f, 0.96f, 1f, 0.99f))
            },
        )
        composeRule.setContent {
            YomiLensTheme {
                YomiLensScreen(
                    state = state,
                    hasCameraPermission = false,
                    isCameraReady = false,
                    imageCapture = ImageCapture.Builder().build(),
                    onRequestCamera = {},
                    onModeSelected = {},
                    onRetryEnglish = {},
                    onCameraError = {},
                    onCameraReady = {},
                )
            }
        }
        composeRule.waitForIdle()

        val labelBounds = composeRule.onNodeWithTag("overlay_0").getUnclippedBoundsInRoot()
        val controlBounds = composeRule.onNodeWithTag("lens_controls").getUnclippedBoundsInRoot()
        assertTrue("Label $labelBounds overlaps controls $controlBounds", labelBounds.bottom <= controlBounds.top)
    }

    @Test
    fun nearbyEnglishLabelsDoNotOverlapEachOther() {
        val base = sampleState().overlayItems.single()
        val state = sampleState().copy(
            selectedMode = OutputMode.ENGLISH,
            overlayItems = listOf(
                base.copy(
                    japanese = "橋",
                    bounds = NormalizedBounds(0.42f, 0.40f, 0.50f, 0.46f),
                    english = "Bridge",
                ),
                base.copy(
                    japanese = "花",
                    bounds = NormalizedBounds(0.48f, 0.42f, 0.56f, 0.48f),
                    english = "Flower",
                ),
                base.copy(
                    japanese = "月",
                    bounds = NormalizedBounds(0.52f, 0.44f, 0.60f, 0.50f),
                    english = "Moon",
                ),
            ),
        )
        composeRule.setContent {
            YomiLensTheme {
                YomiLensScreen(
                    state = state,
                    hasCameraPermission = false,
                    isCameraReady = false,
                    imageCapture = ImageCapture.Builder().build(),
                    onRequestCamera = {},
                    onModeSelected = {},
                    onRetryEnglish = {},
                    onCameraError = {},
                    onCameraReady = {},
                )
            }
        }
        composeRule.waitForIdle()

        val bounds = (0..2).map { index ->
            composeRule.onNodeWithTag("overlay_$index").getUnclippedBoundsInRoot()
        }
        bounds.forEachIndexed { index, label ->
            bounds.drop(index + 1).forEach { other ->
                val overlaps = label.left < other.right &&
                    label.right > other.left &&
                    label.top < other.bottom &&
                    label.bottom > other.top
                assertTrue("Labels overlap: $label and $other", !overlaps)
            }
        }
    }

    @Test
    fun rtlLayoutKeepsLabelInAbsoluteCameraCoordinates() {
        val state = sampleState().copy(
            selectedMode = OutputMode.ENGLISH,
            overlayItems = sampleState().overlayItems.map { item ->
                item.copy(bounds = NormalizedBounds(0.05f, 0.4f, 0.2f, 0.46f))
            },
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                YomiLensTheme {
                    YomiLensScreen(
                        state = state,
                        hasCameraPermission = false,
                        isCameraReady = false,
                        imageCapture = ImageCapture.Builder().build(),
                        onRequestCamera = {},
                        onModeSelected = {},
                        onRetryEnglish = {},
                        onCameraError = {},
                        onCameraReady = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val label = composeRule.onNodeWithTag("overlay_0").getUnclippedBoundsInRoot()
        val lens = composeRule.onNodeWithTag("translation_overlay_layer").getUnclippedBoundsInRoot()
        assertTrue("RTL mirrored camera-space label $label in $lens", label.left < lens.right / 2)
    }

    private fun sampleState() = YomiLensUiState(
        selectedMode = OutputMode.FURIGANA,
        phase = ScanPhase.READY,
        recognizedJapanese = "日本語を勉強します。",
        readingLines = listOf(
            ReadingLine(
                listOf(
                    ReadingToken("日本語", "にほんご", "ニホンゴ", false),
                    ReadingToken("を", null, "ヲ", true),
                    ReadingToken("勉強", "べんきょう", "ベンキョウ", false),
                    ReadingToken("し", null, "シ", false),
                    ReadingToken("ます", null, "マス", false),
                    ReadingToken("。", null, "。", false),
                ),
            ),
        ),
        romaji = "nihongo o benkyou shimasu.",
        english = "I study Japanese.",
        overlayItems = listOf(
            LensOverlayItem(
                japanese = "日本語を勉強します。",
                bounds = NormalizedBounds(0.12f, 0.35f, 0.88f, 0.43f),
                readingLines = listOf(
                    ReadingLine(
                        listOf(
                            ReadingToken("日本語", "にほんご", "ニホンゴ", false),
                            ReadingToken("を", null, "ヲ", true),
                            ReadingToken("勉強", "べんきょう", "ベンキョウ", false),
                            ReadingToken("し", null, "シ", false),
                            ReadingToken("ます", null, "マス", false),
                            ReadingToken("。", null, "。", false),
                        ),
                    ),
                ),
                romaji = "nihongo o benkyou shimasu.",
                english = "I study Japanese.",
            ),
        ),
    )
}
