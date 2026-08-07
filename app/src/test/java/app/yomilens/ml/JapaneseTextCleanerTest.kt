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
    fun preservesSpacingReportedByOcr() {
        val result = JapaneseTextCleaner.clean("日 本 語 を 勉 強 し ま す")

        assertEquals("日 本 語 を 勉 強 し ま す", result)
    }

    @Test
    fun preservesMeaningfulSpacingBetweenChartCharacters() {
        val result = JapaneseTextCleaner.clean("橋　花　月\n友　目　色")

        assertEquals("橋　花　月\n友　目　色", result)
    }

    @Test
    fun preservesWordsAndMultipleJapaneseLines() {
        val result = JapaneseTextCleaner.clean("東京 Station\nカメラ TEST")

        assertEquals("東京 Station\nカメラ TEST", result)
    }

    @Test
    fun overlayTextRemovesNonJapanesePunctuationAndLatinText() {
        val result = JapaneseTextCleaner.cleanForOverlay("日本語!?！？ ABC\n《「月」―…》。OPEN!")

        assertEquals("日本語\n《「月」―…》。", result)
    }
}
