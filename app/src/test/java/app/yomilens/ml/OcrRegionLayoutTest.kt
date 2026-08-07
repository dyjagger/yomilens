package app.yomilens.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRegionLayoutTest {
    @Test
    fun keepsMultilineProseInOneContextualRegion() {
        val regions = OcrRegionLayout.regionsForBlock(
            lines = listOf(
                line("私は", left = 40, top = 20),
                line("月を", left = 40, top = 80),
                line("見ます", left = 40, top = 140),
            ),
            blockBounds = OcrPixelBounds(40, 20, 180, 180),
        )

        assertEquals(listOf("私は\n月を\n見ます"), regions.map(RawOcrRegion::text))
        assertEquals(OcrPixelBounds(40, 20, 180, 180), regions.single().bounds)
    }

    @Test
    fun splitsSpatialChartLabelsIntoIndependentRegions() {
        val regions = OcrRegionLayout.regionsForBlock(
            lines = listOf(
                chartLine(listOf("橋", "花", "月"), top = 20),
                chartLine(listOf("友", "目", "色"), top = 100),
            ),
            blockBounds = OcrPixelBounds(20, 20, 290, 150),
        )

        assertEquals(listOf("橋", "花", "月", "友", "目", "色"), regions.map(RawOcrRegion::text))
    }

    private fun line(text: String, left: Int, top: Int): OcrPositionedLine = OcrPositionedLine(
        segments = listOf(
            OcrPositionedSegment(
                text = text,
                bounds = OcrPixelBounds(left, top, left + 140, top + 40),
            ),
        ),
        bounds = OcrPixelBounds(left, top, left + 140, top + 40),
    )

    private fun chartLine(labels: List<String>, top: Int): OcrPositionedLine {
        val segments = labels.mapIndexed { index, label ->
            val left = 20 + index * 120
            OcrPositionedSegment(label, OcrPixelBounds(left, top, left + 30, top + 50))
        }
        return OcrPositionedLine(
            segments = segments,
            bounds = OcrPixelBounds(20, top, 290, top + 50),
        )
    }
}
