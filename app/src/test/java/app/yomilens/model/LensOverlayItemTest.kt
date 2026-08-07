package app.yomilens.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LensOverlayItemTest {
    @Test
    fun visibleReadingsAndTranslationInputExcludeKanaOnlyTokens() {
        val item = LensOverlayItem(
            japanese = "日本語を勉強します。カメラ",
            bounds = NormalizedBounds(0f, 0f, 1f, 1f),
            readingLines = listOf(
                ReadingLine(
                    listOf(
                        ReadingToken("日本語", "にほんご", "ニホンゴ", false),
                        ReadingToken("を", null, "ヲ", true),
                        ReadingToken("勉強", "べんきょう", "ベンキョウ", false),
                        ReadingToken("します", null, "シマス", false),
                        ReadingToken("。", null, "。", false),
                        ReadingToken("カメラ", null, "カメラ", false),
                        ReadingToken("食べる", "たべる", "タベル", false),
                        ReadingToken("取り扱い", "とりあつかい", "トリアツカイ", false),
                    ),
                ),
            ),
            romaji = "nihongo benkyou ta toatsuka",
        )

        assertEquals("にほんご べんきょう た とあつか", item.furigana)
        assertEquals("日本語 勉強 食 取扱", item.kanjiText)
        assertEquals(
            listOf("日本語", "勉強", "食", "取扱"),
            item.kanjiReadingLines.single().tokens.map { it.surface },
        )
    }
}
