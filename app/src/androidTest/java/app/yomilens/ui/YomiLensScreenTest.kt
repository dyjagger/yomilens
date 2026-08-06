package app.yomilens.ui

import androidx.camera.core.ImageCapture
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.yomilens.model.OutputMode
import app.yomilens.ui.theme.YomiLensTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class YomiLensScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outputSelectorExposesExactlyOneSelectedMode() {
        var selected = OutputMode.FURIGANA
        composeRule.setContent {
            YomiLensTheme {
                YomiLensScreen(
                    state = YomiLensUiState(selectedMode = selected),
                    hasCameraPermission = false,
                    imageCapture = ImageCapture.Builder().build(),
                    onRequestCamera = {},
                    onModeSelected = { selected = it },
                    onScan = {},
                    onRetryEnglish = {},
                    onCameraError = {},
                )
            }
        }

        composeRule.onNodeWithTag("mode_furigana").assertIsSelected()
        composeRule.onNodeWithTag("mode_english").performClick()
        assertEquals(OutputMode.ENGLISH, selected)
    }
}
