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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalImageFuriganaTest {
    @Test
    fun reportsFuriganaForRequestedUserImage(): Unit = runBlocking {
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
            val annotated = JapaneseReadingEngine().annotate(detected)
            val kanjiTokens = annotated
                .flatMap { it.tokens }
                .filter { token -> token.surface.any(::isKanji) }
            assertTrue("Expected the requested image to produce Japanese text", detected.isNotBlank())
            assertTrue("Expected the requested image to produce kanji", kanjiTokens.isNotEmpty())
            assertTrue(
                "Every detected kanji token should have furigana: $detected",
                kanjiTokens.all { token -> !token.furigana.isNullOrBlank() },
            )
            val detectedReadings = kanjiTokens.associate { token -> token.surface to token.furigana }
            EXPECTED_CROPPED_READINGS.forEach { (character, reading) ->
                assertEquals("Wrong furigana for $character in: $detected", reading, detectedReadings[character])
            }

            val furigana = annotated
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
        fun isKanji(character: Char): Boolean =
            character.code in 0x3400..0x4DBF ||
                character.code in 0x4E00..0x9FFF ||
                character.code in 0xF900..0xFAFF

        const val TEST_IMAGE = "requested_user_image.jpg"
        const val LOG_TAG = "YomiLensExternal"
        val EXPECTED_CROPPED_READINGS = mapOf(
            "友" to "とも",
            "月" to "つき",
            "目" to "め",
            "色" to "いろ",
        )
    }
}
