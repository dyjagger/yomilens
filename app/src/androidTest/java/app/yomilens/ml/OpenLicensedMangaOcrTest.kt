package app.yomilens.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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

/**
 * Kasuga's CC BY-SA 3.0 `いけいけ！百科事典娘` page 1 is downloaded by CI from
 * Wikimedia Commons. The source image is intentionally not bundled in production.
 */
@RunWith(AndroidJUnit4::class)
class OpenLicensedMangaOcrTest {
    @Test
    fun restoresTopToBottomRightToLeftReadingOrder(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue(assets.list("")?.contains(TEST_IMAGE) == true)
        val source = assets.open(TEST_IMAGE).use(BitmapFactory::decodeStream)
        val bitmap = Bitmap.createScaledBitmap(source, 1_080, 1_440, true)
        if (bitmap !== source) source.recycle()
        val engine = JapaneseOcrEngine()

        val recognition = try {
            engine.recognize(
                CapturedFrame(
                    bitmap = bitmap,
                    viewportCrop = Rect(0, 0, bitmap.width, bitmap.height),
                    rotationDegrees = 0,
                    closeSource = {},
                ),
            ).await()
        } finally {
            engine.close()
        }

        try {
            val regions = recognition.regions.map(JapaneseOcrRegion::text)
            assertTrue("Manga should produce multiple kanji overlays: $regions", regions.size >= 4)
            assertTrue("Split title answer should be one compound: $regions", "係船柱" in regions)
            assertTrue("First balloon has wrong vertical order: $regions", EXPECTED_HARBOR_TEXT in regions)
            assertTrue("Second balloon has wrong vertical order: $regions", EXPECTED_NAME_TEXT in regions)
            assertTrue("Third balloon has wrong vertical order: $regions", EXPECTED_MOORING_TEXT in regions)
            listOf("係船柱", EXPECTED_HARBOR_TEXT, EXPECTED_NAME_TEXT, EXPECTED_MOORING_TEXT).forEach { text ->
                assertEquals(
                    "Manga overlay should retain vertical direction for: $text",
                    TextOrientation.VERTICAL,
                    recognition.regions.single { region -> region.text == text }.orientation,
                )
            }

            val readingEngine = JapaneseReadingEngine()
            val readings = readingEngine.annotate("係船柱")
            assertEquals("けいせんちゅう", readings.single().tokens.single().furigana)
            assertEquals("keisenchuu", readingEngine.romanize(readings))
            assertEquals(
                "Mooring post",
                EnglishTranslationPlanner.create("係船柱", readings).entries.single().fixedEnglish,
            )
        } finally {
            recognition.frozenFrame.recycle()
        }
    }

    private companion object {
        const val TEST_IMAGE = "open_licensed_manga_page.jpg"
        const val EXPECTED_HARBOR_TEXT = "港に生えて鉄のキノコみたいな物体とか"
        const val EXPECTED_NAME_TEXT = "みんな知っるけ名前は知らないものってよくあるじやない"
        const val EXPECTED_MOORING_TEXT = "あれつて船を港に係留柱みたいなものでしよだから係船柱"
    }
}
