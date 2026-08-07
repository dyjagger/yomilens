package app.yomilens.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaConvertersTest {
    @Test
    fun katakanaReadingBecomesHiragana() {
        assertEquals("にほんご", KanaScripts.katakanaToHiragana("ニホンゴ"))
    }

    @Test
    fun kanjiDetectionDoesNotMistakeKanaForKanji() {
        assertTrue(KanaScripts.containsKanji("日本語"))
        assertFalse(KanaScripts.containsKanji("にほんご"))
        assertFalse(KanaScripts.containsKanji("カメラ"))
        assertFalse(KanaScripts.containsKanji("ヶ"))
        assertFalse(KanaScripts.containsKanji("ヵ"))
    }

    @Test
    fun romanizerHandlesCommonJapaneseSounds() {
        assertEquals("nihongo", HepburnRomanizer.romanize("ニホンゴ"))
        assertEquals("gakkou", HepburnRomanizer.romanize("ガッコウ"))
        assertEquals("matcha", HepburnRomanizer.romanize("マッチャ"))
        assertEquals("shin'you", HepburnRomanizer.romanize("シンヨウ"))
    }

    @Test
    fun romanizerHandlesLoanWordsAndLongVowels() {
        assertEquals("koohii", HepburnRomanizer.romanize("コーヒー"))
        assertEquals("fairu", HepburnRomanizer.romanize("ファイル"))
    }

    @Test
    fun romanizerConvertsJapanesePunctuation() {
        assertEquals("nihongo,kamera.", HepburnRomanizer.romanize("ニホンゴ、カメラ。"))
    }
}
