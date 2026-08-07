package app.yomilens.ui

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
    fun streamingCameraAutomaticallyCompletesTwoSerializedCaptureCycles() {
        composeRule.waitUntil(timeoutMillis = 20_000) { automaticScanningIsReady() }
        composeRule.waitUntil(timeoutMillis = 40_000) { completedScanCountIs(1) }
        composeRule.waitUntil(timeoutMillis = 15_000) { automaticCaptureIsBusy() }
        composeRule.waitUntil(timeoutMillis = 40_000) { completedScanCountIs(2) }
    }

    private fun automaticScanningIsReady(): Boolean = composeRule.onAllNodesWithText(
        "Automatic kanji scanning is on • tap the lens to focus",
    ).fetchSemanticsNodes().isNotEmpty()

    private fun completedScanCountIs(count: Int): Boolean = composeRule.onAllNodesWithContentDescription(
        "Completed automatic scans: $count",
    ).fetchSemanticsNodes().isNotEmpty()

    private fun automaticCaptureIsBusy(): Boolean {
        val capturing = composeRule.onAllNodesWithText("Capturing automatically…").fetchSemanticsNodes()
        val recognizing = composeRule.onAllNodesWithText("Reading kanji…").fetchSemanticsNodes()
        return capturing.isNotEmpty() || recognizing.isNotEmpty()
    }
}
