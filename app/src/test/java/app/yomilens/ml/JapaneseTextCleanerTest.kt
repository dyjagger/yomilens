package app.yomilens.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class JapaneseTextCleanerTest {
    @Test
    fun removesBackgroundLinesWithoutJapanese() {
        val result = JapaneseTextCleaner.clean("SALE 50%\n日本語を勉強します。\nOPEN 24 HOURS")

        assertEquals("日本語を勉強します。", result)
    }

    @Test
    fun rejoinsJapaneseGlyphsSplitByOcrSpacing() {
        val result = JapaneseTextCleaner.clean("日 本 語 を 勉 強 し ま す")

        assertEquals("日本語を勉強します", result)
    }

    @Test
    fun preservesWordsAndMultipleJapaneseLines() {
        val result = JapaneseTextCleaner.clean("東京 Station\nカメラ TEST")

        assertEquals("東京 Station\nカメラ TEST", result)
    }
}
