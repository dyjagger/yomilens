package app.yomilens.ml

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomilens.reading.JapaneseReadingEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalImageFuriganaTest {
    @Test
    fun reportsFuriganaForRequestedUserImage() = runBlocking {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue(testAssets.list("")?.contains(TEST_IMAGE) == true)
        val bitmap = testAssets.open(TEST_IMAGE).use(BitmapFactory::decodeStream)
        val ocrEngine = JapaneseOcrEngine()

        try {
            val detected = ocrEngine.recognize(
                CapturedFrame(
                    bitmap = bitmap,
                    viewportCrop = Rect(0, 0, bitmap.width, bitmap.height),
                    rotationDegrees = 0,
                    closeSource = {},
                ),
            ).await()
            val furigana = JapaneseReadingEngine().annotate(detected)
                .joinToString(" | ") { line ->
                    line.tokens.joinToString(" ") { token ->
                        token.furigana?.let { reading -> "${token.surface}[$reading]" }
                            ?: token.surface
                    }
                }

            Log.i(LOG_TAG, "DETECTED=${detected.replace('\n', '|')}")
            Log.i(LOG_TAG, "FURIGANA=$furigana")
        } finally {
            ocrEngine.close()
        }
    }

    private companion object {
        const val TEST_IMAGE = "requested_user_image.jpg"
        const val LOG_TAG = "YomiLensExternal"
    }
}
