package app.yomilens.ml

data class OcrPixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class RawOcrRegion(
    val text: String,
    val bounds: OcrPixelBounds?,
)

data class OcrRecognition(
    val text: String,
    val regions: List<RawOcrRegion>,
)

data class OcrPositionedSegment(
    val text: String,
    val bounds: OcrPixelBounds?,
) {
    val horizontalSegment = OcrSegment(text, bounds?.left, bounds?.right)
}

data class OcrPositionedLine(
    val segments: List<OcrPositionedSegment>,
    val bounds: OcrPixelBounds?,
)

/** Preserves paragraph context while separating genuinely spaced chart labels. */
object OcrRegionLayout {
    fun regionsForBlock(
        lines: List<OcrPositionedLine>,
        blockBounds: OcrPixelBounds?,
    ): List<RawOcrRegion> {
        val japaneseLines = lines.map { line ->
            line.copy(
                segments = line.segments.mapNotNull { segment ->
                    val japanese = JapaneseTextCleaner.cleanForOverlay(segment.text)
                    segment.copy(text = japanese).takeIf { japanese.isNotBlank() }
                },
            )
        }
        val groupsByLine = japaneseLines.map { line -> splitLine(line.segments) }
        val containsSeparatedLabels = groupsByLine.any { groups -> groups.size > 1 }

        if (!containsSeparatedLabels) {
            val text = OcrLayoutReconstructor.reconstruct(
                japaneseLines.map { line -> line.segments.map(OcrPositionedSegment::horizontalSegment) },
            )
            if (!JapaneseTextCleaner.containsJapanese(text)) return emptyList()
            return listOf(
                RawOcrRegion(
                    text = text,
                    bounds = japaneseLines.flatMap(OcrPositionedLine::segments)
                        .mapNotNull(OcrPositionedSegment::bounds)
                        .unionBounds()
                        ?: blockBounds,
                ),
            )
        }

        return japaneseLines.flatMapIndexed { lineIndex, line ->
            groupsByLine[lineIndex].mapNotNull { group ->
                val text = OcrLayoutReconstructor.reconstructLine(
                    group.map(OcrPositionedSegment::horizontalSegment),
                )
                if (!JapaneseTextCleaner.containsJapanese(text)) return@mapNotNull null
                RawOcrRegion(
                    text = text,
                    bounds = group.mapNotNull(OcrPositionedSegment::bounds).unionBounds()
                        ?: line.bounds,
                )
            }
        }
    }

    private fun splitLine(segments: List<OcrPositionedSegment>): List<List<OcrPositionedSegment>> {
        val usable = segments.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return emptyList()
        return buildList {
            var current = mutableListOf(usable.first())
            usable.drop(1).forEach { segment ->
                if (
                    OcrLayoutReconstructor.isVisuallySeparate(
                        current.last().horizontalSegment,
                        segment.horizontalSegment,
                    )
                ) {
                    add(current)
                    current = mutableListOf()
                }
                current += segment
            }
            add(current)
        }
    }
}

private fun List<OcrPixelBounds>.unionBounds(): OcrPixelBounds? {
    val first = firstOrNull() ?: return null
    return drop(1).fold(first) { union, bounds ->
        OcrPixelBounds(
            left = minOf(union.left, bounds.left),
            top = minOf(union.top, bounds.top),
            right = maxOf(union.right, bounds.right),
            bottom = maxOf(union.bottom, bounds.bottom),
        )
    }
}
