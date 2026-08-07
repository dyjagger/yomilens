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

    @Test
    fun ignoresNonJapaneseElementsAndPunctuationDuringOverlayPlanning() {
        val regions = OcrRegionLayout.regionsForBlock(
            lines = listOf(
                OcrPositionedLine(
                    segments = listOf(
                        OcrPositionedSegment("日本語!?", OcrPixelBounds(20, 20, 150, 70)),
                        OcrPositionedSegment("!", OcrPixelBounds(170, 20, 180, 70)),
                        OcrPositionedSegment("OPEN", OcrPixelBounds(220, 20, 320, 70)),
                    ),
                    bounds = OcrPixelBounds(20, 20, 320, 70),
                ),
            ),
            blockBounds = OcrPixelBounds(20, 20, 320, 70),
        )

        assertEquals(listOf("日本語"), regions.map(RawOcrRegion::text))
        assertEquals(OcrPixelBounds(20, 20, 150, 70), regions.single().bounds)
    }

    @Test
    fun reconstructsVerticalMangaRowsAsRightToLeftColumns() {
        val regions = OcrRegionLayout.regionsForBlock(
            lines = listOf(
                mangaRow("物み鉄港", 685, 329, 803, 354),
                mangaRow("体たのに", 695, 350, 789, 372),
                mangaRow("といキ生", 686, 368, 796, 392),
                mangaRow("かなノえ", 686, 390, 800, 416),
                mangaRow("コて", 749, 416, 789, 432),
            ),
            blockBounds = OcrPixelBounds(684, 328, 803, 432),
        )

        assertEquals(listOf("港に生えて鉄のキノコみたいな物体とか"), regions.map(RawOcrRegion::text))
    }

    @Test
    fun mergesAdjacentMangaFragmentsBeforeReconstructingColumns() {
        val regions = OcrRegionLayout.regionsForDocument(
            listOf(
                OcrPositionedBlock(
                    lines = listOf(
                        mangaRow("じも名み", 884, 331, 972, 354),
                        mangaRow("やの前ん", 883, 352, 972, 376),
                        mangaRow("なっはな", 881, 376, 974, 395),
                        mangaRow("いて知知", 881, 395, 973, 416),
                        mangaRow("よらっ", 907, 418, 972, 437),
                        mangaRow("くな", 909, 435, 949, 458),
                    ),
                    bounds = OcrPixelBounds(881, 331, 974, 458),
                ),
                OcrPositionedBlock(
                    lines = listOf(
                        mangaRow("あいる", 906, 460, 972, 481),
                        OcrPositionedLine(
                            segments = listOf(
                                OcrPositionedSegment("る", OcrPixelBounds(906, 480, 922, 500)),
                                OcrPositionedSegment("け", OcrPixelBounds(957, 482, 972, 498)),
                            ),
                            bounds = OcrPixelBounds(906, 480, 972, 500),
                        ),
                    ),
                    bounds = OcrPixelBounds(906, 460, 972, 500),
                ),
            ),
        )

        assertEquals(
            listOf("みんな知っるけ名前は知らないものってよくあるじやない"),
            regions.map(RawOcrRegion::text),
        )
    }

    @Test
    fun mergesSingleGlyphBlocksIntoOneVerticalCompound() {
        val regions = OcrRegionLayout.regionsForDocument(
            listOf(
                glyphBlock("係", 133, 350, 173, 382),
                glyphBlock("船", 133, 385, 175, 418),
                glyphBlock("柱", 133, 421, 177, 456),
            ),
        )

        assertEquals(listOf("係船柱"), regions.map(RawOcrRegion::text))
        assertEquals(OcrPixelBounds(133, 350, 177, 456), regions.single().bounds)
    }

    @Test
    fun joinsASeparatedMangaGlyphBackIntoItsColumn() {
        val mainBlock = OcrPositionedBlock(
            lines = listOf(
                mangaRow("だも柱船あ", 823, 923, 975, 950),
                mangaRow("かのみをれ", 832, 951, 968, 975),
                mangaRow("らでた港つ", 835, 974, 968, 997),
                mangaRow("係しいにて", 831, 999, 967, 1021),
                mangaRow("船よな係", 831, 1022, 939, 1047),
                mangaRow("柱", 832, 1046, 855, 1068),
            ),
            bounds = OcrPixelBounds(823, 923, 975, 1068),
        )

        val regions = OcrRegionLayout.regionsForDocument(
            listOf(mainBlock, glyphBlock("留", 918, 1046, 939, 1068)),
        )

        assertEquals(
            listOf("あれつて船を港に係留柱みたいなものでしよだから係船柱"),
            regions.map(RawOcrRegion::text),
        )
    }

    @Test
    fun keepsAlreadyRecognizedVerticalLineInRecognizerOrder() {
        val regions = OcrRegionLayout.regionsForBlock(
            lines = listOf(
                OcrPositionedLine(
                    segments = listOf(
                        OcrPositionedSegment("ーあれ？", OcrPixelBounds(517, 1000, 547, 1084)),
                    ),
                    bounds = OcrPixelBounds(517, 1000, 547, 1084),
                    angleDegrees = 91.5f,
                ),
            ),
            blockBounds = OcrPixelBounds(517, 1000, 547, 1084),
        )

        assertEquals(listOf("ーあれ"), regions.map(RawOcrRegion::text))
    }

    @Test
    fun keepsCompactHorizontalProseInLineOrder() {
        val regions = OcrRegionLayout.regionsForBlock(
            lines = listOf(
                mangaRow("日本語", 40, 20, 130, 50),
                mangaRow("英語を", 40, 55, 130, 85),
                mangaRow("学ぶ。", 40, 90, 130, 120),
                mangaRow("毎日ね", 40, 125, 130, 155),
            ),
            blockBounds = OcrPixelBounds(40, 20, 130, 155),
        )

        assertEquals(listOf("日本語\n英語を\n学ぶ。\n毎日ね"), regions.map(RawOcrRegion::text))
    }

    @Test
    fun doesNotMergeNearbyIndependentVerticalRegions() {
        val regions = OcrRegionLayout.regionsForDocument(
            listOf(
                verticalBlock("右の文", left = 100, top = 20),
                verticalBlock("下の文", left = 100, top = 124),
            ),
        )

        assertEquals(listOf("右の文", "下の文"), regions.map(RawOcrRegion::text))
    }

    @Test
    fun overlappingBridgeBlocksDoNotTransitivelyMergeCaptions() {
        val regions = OcrRegionLayout.regionsForDocument(
            listOf(
                glyphBlock("右", 0, 20, 40, 60),
                glyphBlock("中", 15, 20, 55, 60),
                glyphBlock("左", 30, 20, 70, 60),
            ),
        )

        assertEquals(listOf("右", "中", "左"), regions.map(RawOcrRegion::text))
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

    private fun mangaRow(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): OcrPositionedLine = OcrPositionedLine(
        segments = listOf(OcrPositionedSegment(text, OcrPixelBounds(left, top, right, bottom))),
        bounds = OcrPixelBounds(left, top, right, bottom),
    )

    private fun glyphBlock(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): OcrPositionedBlock {
        val bounds = OcrPixelBounds(left, top, right, bottom)
        return OcrPositionedBlock(
            lines = listOf(
                OcrPositionedLine(
                    segments = listOf(OcrPositionedSegment(text, bounds)),
                    bounds = bounds,
                ),
            ),
            bounds = bounds,
        )
    }

    private fun verticalBlock(text: String, left: Int, top: Int): OcrPositionedBlock {
        val lines = text.mapIndexed { index, character ->
            mangaRow(
                text = character.toString(),
                left = left,
                top = top + index * 30,
                right = left + 28,
                bottom = top + index * 30 + 28,
            )
        }
        return OcrPositionedBlock(
            lines = lines,
            bounds = lines.mapNotNull(OcrPositionedLine::bounds).let { bounds ->
                OcrPixelBounds(
                    left = bounds.minOf(OcrPixelBounds::left),
                    top = bounds.minOf(OcrPixelBounds::top),
                    right = bounds.maxOf(OcrPixelBounds::right),
                    bottom = bounds.maxOf(OcrPixelBounds::bottom),
                )
            },
        )
    }
}
