package app.yomilens.ui

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import app.yomilens.MainActivity
import org.junit.Rule
import org.junit.Test

class CameraScanSmokeTest {
    @get:Rule(order = 0)
    val cameraPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun streamingCameraCanCompleteOneCaptureCycle() {
        composeRule.waitUntil(timeoutMillis = 20_000) { scanButtonIsEnabled() }

        composeRule.onNodeWithTag("scan_button").performClick()
        composeRule.onNodeWithTag("scan_button").assertIsNotEnabled()

        composeRule.waitUntil(timeoutMillis = 30_000) { captureReachedOcrOutcome() }
        composeRule.waitUntil(timeoutMillis = 5_000) { scanButtonIsEnabled() }
    }

    private fun scanButtonIsEnabled(): Boolean {
        val nodes = composeRule.onAllNodesWithTag("scan_button").fetchSemanticsNodes()
        return nodes.size == 1 && !nodes.single().config.contains(SemanticsProperties.Disabled)
    }

    private fun captureReachedOcrOutcome(): Boolean {
        val detectedText = composeRule.onAllNodesWithTag("translation_overlay_layer").fetchSemanticsNodes()
        val noJapanese = composeRule.onAllNodesWithText(
            "No Japanese text was found. Move closer, hold steady, and try again.",
        ).fetchSemanticsNodes()
        return detectedText.isNotEmpty() || noJapanese.isNotEmpty()
    }
}
