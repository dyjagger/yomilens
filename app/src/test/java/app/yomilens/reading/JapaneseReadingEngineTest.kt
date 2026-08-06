package app.yomilens.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseReadingEngineTest {
    private val engine = JapaneseReadingEngine()

    @Test
    fun annotatesKanjiButNotKanaOnlyTokens() {
        val tokens = engine.annotate("日本語を勉強します。").single().tokens

        assertEquals("にほんご", tokens.first { it.surface == "日本語" }.furigana)
        assertEquals("べんきょう", tokens.first { it.surface == "勉強" }.furigana)
        assertNull(tokens.first { it.surface == "を" }.furigana)
    }

    @Test
    fun romanizesParticlesByTheirSpokenReading() {
        val output = engine.romanize(engine.annotate("私は東京へ行く。"))

        assertTrue(output.startsWith("watashi wa"))
        assertTrue(output.contains("toukyou e"))
        assertTrue(output.endsWith("iku."))
    }

    @Test
    fun preservesDetectedLineBoundaries() {
        val lines = engine.annotate("日本語\n英語")

        assertEquals(2, lines.size)
        assertEquals("nihongo\neigo", engine.romanize(lines))
    }

    @Test
    fun commonScanSentencesGiveReadingsForEveryKanjiToken() {
        val sentences = listOf(
            "私は日本語を勉強しています。",
            "明日は東京駅へ行きます。",
            "カメラで本を読みます。",
        )

        sentences.forEach { sentence ->
            val kanjiTokens = engine.annotate(sentence)
                .flatMap { it.tokens }
                .filter { KanaScripts.containsKanji(it.surface) }

            assertTrue("Expected kanji tokens in: $sentence", kanjiTokens.isNotEmpty())
            assertTrue(
                "Every common kanji token should have furigana in: $sentence",
                kanjiTokens.all { !it.furigana.isNullOrBlank() },
            )
        }
    }
}
