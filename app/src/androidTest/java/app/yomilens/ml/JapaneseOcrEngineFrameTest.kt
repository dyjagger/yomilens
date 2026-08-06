package app.yomilens.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
                Tasks.forResult("SALE\n日 本 語")
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

            assertEquals("日本語", result)
            assertEquals(134, inputWidth)
            assertEquals(42, inputHeight)
            assertEquals(1, closeCount)
            assertTrue("The source bitmap should be released", source.isRecycled)
        } finally {
            engine.close()
        }
    }
}
