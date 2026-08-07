package app.yomilens.ml

import app.yomilens.model.ReadingLine
import app.yomilens.model.ReadingToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishTranslationPlannerTest {
    @Test
    fun keepsOrdinaryJapaneseTogetherForSentenceContext() {
        val plan = EnglishTranslationPlanner.create(
            japanese = "日本語を勉強します。",
            readings = emptyList(),
        )

        assertFalse(plan.preserveLayout)
        assertEquals(listOf("日本語を勉強します。"), plan.entries.map { it.japanese })
        assertEquals("I study Japanese.", plan.render(listOf("I study Japanese.")))
    }

    @Test
    fun separatesRequestedChartAndUsesTheStandaloneMoonReading() {
        val plan = EnglishTranslationPlanner.create(
            japanese = "友\n月\n目　色",
            readings = chartReadings(),
        )

        assertTrue(plan.preserveLayout)
        assertEquals(listOf("友", "月", "目", "色"), plan.entries.map { it.japanese })
        assertEquals("Moon", plan.entries.first { it.japanese == "月" }.fixedEnglish)
        assertEquals(
            "Friend\nMoon\nEye   Color",
            plan.render(listOf("Friend", "Moon", "Eye", "Color")),
        )
    }

    @Test
    fun keepsNaturallySpacedProseTogether() {
        val japanese = "日本語を 勉強します。"

        val plan = EnglishTranslationPlanner.create(japanese, readings = emptyList())

        assertFalse(plan.preserveLayout)
        assertEquals(listOf(japanese), plan.entries.map { it.japanese })
    }

    @Test
    fun keepsShortMultilineProseTogether() {
        val japanese = "私は\n月を\n見ます"

        val plan = EnglishTranslationPlanner.create(japanese, readings = emptyList())

        assertFalse(plan.preserveLayout)
        assertEquals(listOf(japanese), plan.entries.map { it.japanese })
    }

    @Test
    fun doesNotForceMoonWhenTheReadingDoesNotMeanTsuki() {
        val plan = EnglishTranslationPlanner.create(
            japanese = "月　日　年",
            readings = listOf(
                ReadingLine(
                    listOf(ReadingToken("月", "げつ", "ゲツ", false)),
                ),
            ),
        )

        assertNull(plan.entries.first { it.japanese == "月" }.fixedEnglish)
    }

    @Test
    fun standaloneMoonOverlayUsesItsFuriganaToResolveMeaning() {
        val plan = EnglishTranslationPlanner.create(
            japanese = "月",
            readings = listOf(reading("月", "つき", "ツキ")),
        )

        assertEquals("Moon", plan.entries.single().fixedEnglish)
    }

    @Test
    fun establishedMooringPostReadingGetsTheCorrectEnglishLabel() {
        val plan = EnglishTranslationPlanner.create(
            japanese = "係船柱",
            readings = listOf(reading("係船柱", "けいせんちゅう", "ケイセンチュウ")),
        )

        assertEquals("Mooring post", plan.entries.single().fixedEnglish)
    }

    @Test
    fun removesLeadingMangaElongationMarkBeforeTranslation() {
        val plan = EnglishTranslationPlanner.create("ーあれ", readings = emptyList())

        assertEquals("あれ", plan.entries.single().japanese)
    }

    private fun chartReadings() = listOf(
        reading("友", "とも", "トモ"),
        reading("月", "つき", "ツキ"),
        ReadingLine(
            listOf(
                ReadingToken("目", "め", "メ", false),
                ReadingToken(" ", null, " ", false),
                ReadingToken("色", "いろ", "イロ", false),
            ),
        ),
    )

    private fun reading(surface: String, furigana: String, katakana: String) = ReadingLine(
        listOf(ReadingToken(surface, furigana, katakana, false)),
    )
}
