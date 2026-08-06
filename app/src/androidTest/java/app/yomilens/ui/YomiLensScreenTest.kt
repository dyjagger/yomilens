package app.yomilens.ui

import androidx.camera.core.ImageCapture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.yomilens.model.OutputMode
import app.yomilens.model.ReadingLine
import app.yomilens.model.ReadingToken
import app.yomilens.ui.theme.YomiLensTheme
import org.junit.Rule
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
        composeRule.onNodeWithTag("detected_text").assertTextEquals("日本語を勉強します。")
        composeRule.onNodeWithText("にほんご").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_romaji").performClick()
        composeRule.onNodeWithTag("mode_romaji").assertIsSelected()
        composeRule.onNodeWithText("nihongo o benkyou shimasu.").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_english").performClick()
        composeRule.onNodeWithTag("mode_english").assertIsSelected()
        composeRule.onNodeWithText("I study Japanese.").assertIsDisplayed()
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
    )
}
