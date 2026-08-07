package app.yomilens.ml

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomilens.model.TextOrientation
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
    fun reportsFuriganaAndEnglishForRequestedUserImage(): Unit = runBlocking {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue(testAssets.list("")?.contains(TEST_IMAGE) == true)
        val bitmap = testAssets.open(TEST_IMAGE).use(BitmapFactory::decodeStream)
        val ocrEngine = JapaneseOcrEngine()

        try {
            val recognition = ocrEngine.recognize(
                CapturedFrame(
                    bitmap = bitmap,
                    viewportCrop = Rect(0, 0, bitmap.width, bitmap.height),
                    rotationDegrees = 0,
                    closeSource = {},
                ),
            ).await()
            val detected = recognition.text
            assertEquals(
                listOf("橋", "花", "月", "友", "目", "色"),
                recognition.regions.map(JapaneseOcrRegion::text),
            )
            assertTrue(
                "Horizontal chart labels should stay horizontal",
                recognition.regions.all { region -> region.orientation == TextOrientation.HORIZONTAL },
            )
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
            EXPECTED_READINGS.forEach { (character, reading) ->
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

            val translationEngine = EnglishTranslationEngine()
            try {
                val english = translationEngine.translate(detected, annotated)
                assertEquals(
                    listOf("bridge", "flower", "moon", "friend", "eye", "color"),
                    english.split(Regex("\\s+")).map(String::lowercase),
                )
                val overlayEnglish = translationEngine.translateUnits(
                    recognition.regions.map { region ->
                        JapaneseTranslationUnit(
                            japanese = region.text,
                            readings = JapaneseReadingEngine().annotate(region.text),
                        )
                    },
                )
                assertEquals(
                    listOf("bridge", "flower", "moon", "friend", "eye", "color"),
                    overlayEnglish.map(String::lowercase),
                )
                Log.i(LOG_TAG, "ENGLISH=${english.replace('\n', '|')}")
                Log.i(LOG_TAG, "OVERLAY_ENGLISH=${overlayEnglish.joinToString("|")}")
            } finally {
                translationEngine.close()
            }
            recognition.frozenFrame.recycle()
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
        val EXPECTED_READINGS = mapOf(
            "橋" to "はし",
            "花" to "はな",
            "友" to "とも",
            "月" to "つき",
            "目" to "め",
            "色" to "いろ",
        )
    }
}
