package app.yomilens.ml

import app.yomilens.reading.JapaneseReadingEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLayoutReconstructorTest {
    @Test
    fun joinsCloseJapaneseSegmentsSoTheTokenizerKeepsCompoundContext() {
        val detected = OcrLayoutReconstructor.reconstruct(
            listOf(
                closeSegments("日本語を勉強します"),
            ),
        )

        assertEquals("日本語を勉強します", detected)
        val readings = JapaneseReadingEngine().annotate(detected)
            .flatMap { it.tokens }
            .associate { it.surface to it.furigana }
        assertEquals("にほんご", readings["日本語"])
        assertEquals("べんきょう", readings["勉強"])
    }

    @Test
    fun preservesWideGapsBetweenRequestedChartEntries() {
        val detected = OcrLayoutReconstructor.reconstruct(
            listOf(
                chartSegments("橋花月"),
                chartSegments("友目色"),
            ),
        )

        assertEquals("橋　花　月\n友　目　色", detected)
        val readings = JapaneseReadingEngine().annotate(detected)
            .flatMap { it.tokens }
            .associate { it.surface to it.furigana }
        assertEquals("はし", readings["橋"])
        assertEquals("はな", readings["花"])
        assertEquals("つき", readings["月"])
        assertEquals("とも", readings["友"])
        assertEquals("め", readings["目"])
        assertEquals("いろ", readings["色"])
    }

    private fun closeSegments(text: String): List<OcrSegment> = text.mapIndexed { index, character ->
        val left = index * 22
        OcrSegment(character.toString(), left = left, right = left + 20)
    }

    private fun chartSegments(text: String): List<OcrSegment> = text.mapIndexed { index, character ->
        val left = index * 120
        OcrSegment(character.toString(), left = left, right = left + 30)
    }
}
