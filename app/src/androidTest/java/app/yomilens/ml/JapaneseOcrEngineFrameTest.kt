package app.yomilens.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomilens.model.TextOrientation
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JapaneseOcrEngineFrameTest {
    @Test
    fun closesRotatesCropsCleansAndRecyclesCapturedFrame() = runBlocking {
        var closeCount = 0
        var inputWidth = 0
        var inputHeight = 0
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val engine = JapaneseOcrEngine(
            textRecognition = { input ->
                inputWidth = input.width
                inputHeight = input.height
                Tasks.forResult(
                    OcrRecognition(
                        text = "SALE!?\n日本語！？\nカメラ",
                        regions = listOf(
                            RawOcrRegion(
                                "日本語!? OPEN",
                                OcrPixelBounds(20, 15, 120, 55),
                                TextOrientation.VERTICAL,
                            ),
                            RawOcrRegion("カメラ", OcrPixelBounds(20, 60, 120, 90)),
                        ),
                    ),
                )
            },
        )

        try {
            val result = engine.recognize(
                CapturedFrame(
                    bitmap = source,
                    viewportCrop = Rect(10, 20, 90, 180),
                    rotationDegrees = 90,
                    closeSource = { closeCount += 1 },
                ),
            ).await()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals("日本語", result.text)
            assertEquals(160, inputWidth)
            assertEquals(80, inputHeight)
            assertEquals("日本語", result.regions.single().text)
            assertEquals(TextOrientation.VERTICAL, result.regions.single().orientation)
            assertEquals(0.125f, result.regions.single().bounds.left, 0.0001f)
            assertEquals(0.1875f, result.regions.single().bounds.top, 0.0001f)
            assertEquals(0.75f, result.regions.single().bounds.right, 0.0001f)
            assertEquals(0.6875f, result.regions.single().bounds.bottom, 0.0001f)
            assertEquals(160, result.frozenFrame.width)
            assertEquals(80, result.frozenFrame.height)
            assertEquals(1, closeCount)
            assertTrue("The source bitmap should be released", source.isRecycled)
            result.frozenFrame.recycle()
        } finally {
            engine.close()
        }
    }
}
