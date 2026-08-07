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

    @Test
    fun readsSeparatedCharactersFromTheRequestedChartIndividually() {
        val readings = engine.annotate("橋 花 月\n友 目 色")
            .flatMap { it.tokens }
            .filter { KanaScripts.containsKanji(it.surface) }
            .associate { it.surface to it.furigana }

        assertEquals("はし", readings["橋"])
        assertEquals("はな", readings["花"])
        assertEquals("つき", readings["月"])
        assertEquals("とも", readings["友"])
        assertEquals("め", readings["目"])
        assertEquals("いろ", readings["色"])
    }

    @Test
    fun keepsTheEstablishedReadingOfMooringPostTogether() {
        val token = engine.annotate("係船柱").single().tokens.single()

        assertEquals("係船柱", token.surface)
        assertEquals("けいせんちゅう", token.furigana)
        assertEquals("keisenchuu", engine.romanize(engine.annotate("係船柱")))
    }

    @Test
    fun keepsTheMooringPostReadingInsideASentence() {
        val tokens = engine.annotate("だから係船柱です").single().tokens

        assertEquals("けいせんちゅう", tokens.single { it.surface == "係船柱" }.furigana)
    }
}
