package app.yomilens.ui

import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.yomilens.model.OutputMode
import app.yomilens.model.LensOverlayItem
import app.yomilens.model.NormalizedBounds
import app.yomilens.model.ReadingLine
import app.yomilens.model.ReadingToken
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
                    onScan = {},
                    onRetryEnglish = {},
                    onCameraError = {},
                    onCameraReady = {},
                )
            }
        }

        composeRule.onNodeWithTag("mode_furigana").assertIsSelected()
        composeRule.onNodeWithText("にほんごをべんきょうします。").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_romaji").performClick()
        composeRule.onNodeWithTag("mode_romaji").assertIsSelected()
        composeRule.onNodeWithText("nihongo o benkyou shimasu.").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_english").performClick()
        composeRule.onNodeWithTag("mode_english").assertIsSelected()
        composeRule.onNodeWithText("I study Japanese.").assertIsDisplayed()
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
                    onScan = {},
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
                    onScan = {},
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
