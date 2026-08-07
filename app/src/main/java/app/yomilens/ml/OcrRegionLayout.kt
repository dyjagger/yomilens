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
    val angleDegrees: Float = 0f,
)

data class OcrPositionedBlock(
    val lines: List<OcrPositionedLine>,
    val bounds: OcrPixelBounds?,
)

/** Preserves prose context, reconstructs vertical manga, and separates spaced labels. */
object OcrRegionLayout {
    fun regionsForDocument(blocks: List<OcrPositionedBlock>): List<RawOcrRegion> =
        mergeRelatedBlocks(blocks.mapNotNull(::cleanBlock)).flatMap { group ->
            regionsForBlock(
                lines = group.flatMap(OcrPositionedBlock::lines),
                blockBounds = group.mapNotNull(OcrPositionedBlock::bounds).unionBounds(),
            )
        }

    fun regionsForBlock(
        lines: List<OcrPositionedLine>,
        blockBounds: OcrPixelBounds?,
    ): List<RawOcrRegion> {
        val japaneseLines = lines.map(::cleanLine).filter { it.segments.isNotEmpty() }
        if (japaneseLines.isEmpty()) return emptyList()

        if (VerticalJapaneseLayout.isVertical(japaneseLines)) {
            val text = VerticalJapaneseLayout.reconstruct(japaneseLines)
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

    private fun cleanBlock(block: OcrPositionedBlock): OcrPositionedBlock? {
        val lines = block.lines.map(::cleanLine).filter { it.segments.isNotEmpty() }
        return block.copy(lines = lines).takeIf { lines.isNotEmpty() }
    }

    private fun cleanLine(line: OcrPositionedLine): OcrPositionedLine = line.copy(
        segments = line.segments.mapNotNull { segment ->
            val japanese = JapaneseTextCleaner.cleanForOverlay(segment.text)
            segment.copy(text = japanese).takeIf { japanese.isNotBlank() }
        },
    )

    private fun mergeRelatedBlocks(
        blocks: List<OcrPositionedBlock>,
    ): List<List<OcrPositionedBlock>> {
        if (blocks.size < 2) return blocks.map(::listOf)

        val parent = IntArray(blocks.size) { it }

        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        fun union(first: Int, second: Int) {
            val firstRoot = root(first)
            val secondRoot = root(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }

        blocks.indices.forEach { first ->
            ((first + 1) until blocks.size).forEach { second ->
                if (blocks[first].isRelatedTo(blocks[second])) union(first, second)
            }
        }

        val groups = linkedMapOf<Int, MutableList<OcrPositionedBlock>>()
        blocks.forEachIndexed { index, block ->
            groups.getOrPut(root(index)) { mutableListOf() }.add(block)
        }
        return groups.values.toList()
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

private fun OcrPositionedBlock.isRelatedTo(other: OcrPositionedBlock): Boolean {
    val first = bounds ?: return false
    val second = other.bounds ?: return false
    val horizontalOverlap = first.overlapWidth(second)
    val verticalOverlap = first.overlapHeight(second)
    if (
        horizontalOverlap >= minOf(first.width, second.width) * OVERLAPPING_FRAGMENT_RATIO &&
        verticalOverlap >= minOf(first.height, second.height) * OVERLAPPING_FRAGMENT_RATIO
    ) {
        return true
    }
    if (verticalOverlap > 0f) return false

    val verticalGap = first.separationY(second)
    val glyphWidth = maxOf(typicalGlyphWidth(), other.typicalGlyphWidth())
    val glyphHeight = maxOf(typicalLineHeight(), other.typicalLineHeight())
    val alignedCenters = kotlin.math.abs(first.centerX - second.centerX) <=
        glyphWidth * VERTICAL_CONTINUATION_ALIGNMENT

    return verticalGap <= glyphHeight * VERTICAL_CONTINUATION_GAP && alignedCenters
}

private fun OcrPositionedBlock.typicalGlyphWidth(): Float = lines.flatMap { line ->
    line.segments.mapNotNull { segment ->
        val bounds = segment.bounds ?: return@mapNotNull null
        val characters = segment.text.count { !it.isWhitespace() }.coerceAtLeast(1)
        bounds.width.toFloat() / characters
    }
}.medianOr(DEFAULT_GLYPH_SIZE)

private fun OcrPositionedBlock.typicalLineHeight(): Float = lines
    .mapNotNull(OcrPositionedLine::bounds)
    .map { it.height.toFloat() }
    .medianOr(DEFAULT_GLYPH_SIZE)

private object VerticalJapaneseLayout {
    fun isVertical(lines: List<OcrPositionedLine>): Boolean {
        val boundedLines = lines.filter { it.bounds != null }
        if (boundedLines.size < MIN_VERTICAL_ROWS) return false
        if (boundedLines.count { it.angleDegrees.isVerticalAngle() } > boundedLines.size / 2) {
            // ML Kit already returns a correctly ordered string for a line it marks as vertical.
            return false
        }

        val bounds = boundedLines.mapNotNull(OcrPositionedLine::bounds).unionBounds() ?: return false
        if (bounds.width > bounds.height * MAX_VERTICAL_ASPECT_RATIO) return false
        if (!rowsAreDense(boundedLines.mapNotNull(OcrPositionedLine::bounds))) return false

        val characterCounts = boundedLines.map { line ->
            line.segments.sumOf { segment -> segment.text.count { !it.isWhitespace() } }
        }
        val typicalCharactersPerRow = characterCounts.map(Int::toFloat).medianOr(0f)
        val singletonColumn = boundedLines.size >= MIN_SINGLE_COLUMN_ROWS &&
            characterCounts.all { it == 1 } &&
            centersAreAligned(boundedLines)
        val compactGrid = boundedLines.size >= MIN_GRID_ROWS &&
            typicalCharactersPerRow >= MIN_GRID_COLUMNS
        if (!singletonColumn && !compactGrid) return false

        val glyphs = glyphsFor(boundedLines)
        if (glyphs.size < MIN_VERTICAL_GLYPHS) return false
        val columns = clusterColumns(glyphs)
        val verticallyConnectedGlyphs = columns.sumOf { column ->
            if (column.glyphs.size >= 2) column.glyphs.size else 0
        }
        return verticallyConnectedGlyphs.toFloat() / glyphs.size >= MIN_COLUMN_COVERAGE
    }

    fun reconstruct(lines: List<OcrPositionedLine>): String = clusterColumns(glyphsFor(lines))
        .sortedByDescending(VerticalColumn::centerX)
        .joinToString("") { column ->
            column.glyphs.sortedWith(
                compareBy<PositionedGlyph> { it.bounds.top }.thenBy { it.bounds.left },
            ).joinToString("") { it.character.toString() }
        }

    private fun centersAreAligned(lines: List<OcrPositionedLine>): Boolean {
        val bounds = lines.mapNotNull(OcrPositionedLine::bounds)
        val typicalWidth = bounds.map { it.width.toFloat() }.medianOr(DEFAULT_GLYPH_SIZE)
        val centers = bounds.map(OcrPixelBounds::centerX)
        return (centers.maxOrNull()!! - centers.minOrNull()!!) <= typicalWidth * SINGLE_COLUMN_ALIGNMENT
    }

    private fun rowsAreDense(bounds: List<OcrPixelBounds>): Boolean {
        val rows = bounds.sortedWith(compareBy(OcrPixelBounds::top).thenBy(OcrPixelBounds::left))
        if (rows.size < 2) return false
        val typicalHeight = rows.map { it.height.toFloat() }.medianOr(DEFAULT_GLYPH_SIZE)
        val densePairs = rows.zipWithNext().count { (current, next) ->
            next.top - current.bottom <= typicalHeight * MAX_VERTICAL_ROW_GAP
        }
        return densePairs.toFloat() / (rows.size - 1) >= MIN_DENSE_ROW_COVERAGE
    }

    private fun glyphsFor(lines: List<OcrPositionedLine>): List<PositionedGlyph> = lines.flatMap { line ->
        line.segments.flatMap { segment ->
            val bounds = segment.bounds ?: return@flatMap emptyList()
            val characters = segment.text.filterNot(Char::isWhitespace).toList()
            characters.mapIndexed { index, character ->
                val left = bounds.left + bounds.width * index / characters.size
                val right = bounds.left + bounds.width * (index + 1) / characters.size
                PositionedGlyph(character, OcrPixelBounds(left, bounds.top, right, bounds.bottom))
            }
        }
    }

    private fun clusterColumns(glyphs: List<PositionedGlyph>): List<VerticalColumn> {
        if (glyphs.isEmpty()) return emptyList()
        val tolerance = glyphs.map { it.bounds.width.toFloat() }
            .medianOr(DEFAULT_GLYPH_SIZE) * COLUMN_ALIGNMENT_TOLERANCE
        val columns = mutableListOf<VerticalColumn>()
        glyphs.sortedByDescending { it.bounds.centerX }.forEach { glyph ->
            val column = columns.minByOrNull { kotlin.math.abs(it.centerX - glyph.bounds.centerX) }
                ?.takeIf { kotlin.math.abs(it.centerX - glyph.bounds.centerX) <= tolerance }
            if (column == null) {
                columns += VerticalColumn(glyph.bounds.centerX, mutableListOf(glyph))
            } else {
                column.glyphs += glyph
                column.centerX = column.glyphs.map { it.bounds.centerX }.average().toFloat()
            }
        }
        return columns
    }
}

private data class PositionedGlyph(
    val character: Char,
    val bounds: OcrPixelBounds,
)

private data class VerticalColumn(
    var centerX: Float,
    val glyphs: MutableList<PositionedGlyph>,
)

private val OcrPixelBounds.width: Int
    get() = (right - left).coerceAtLeast(1)

private val OcrPixelBounds.height: Int
    get() = (bottom - top).coerceAtLeast(1)

private val OcrPixelBounds.centerX: Float
    get() = (left + right) / 2f

private fun OcrPixelBounds.overlapWidth(other: OcrPixelBounds): Float =
    (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0).toFloat()

private fun OcrPixelBounds.overlapHeight(other: OcrPixelBounds): Float =
    (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0).toFloat()

private fun OcrPixelBounds.separationY(other: OcrPixelBounds): Float = when {
    bottom < other.top -> (other.top - bottom).toFloat()
    other.bottom < top -> (top - other.bottom).toFloat()
    else -> 0f
}

private fun Float.isVerticalAngle(): Boolean {
    val normalized = ((this % 180f) + 180f) % 180f
    return normalized in 45f..135f
}

private fun List<Float>.medianOr(default: Float): Float {
    if (isEmpty()) return default
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2f
    } else {
        sorted[middle]
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

private const val OVERLAPPING_FRAGMENT_RATIO = 0.80f
private const val VERTICAL_CONTINUATION_ALIGNMENT = 0.65f
private const val VERTICAL_CONTINUATION_GAP = 0.30f
private const val DEFAULT_GLYPH_SIZE = 24f
private const val MIN_VERTICAL_ROWS = 2
private const val MIN_SINGLE_COLUMN_ROWS = 3
private const val MIN_GRID_ROWS = 4
private const val MIN_GRID_COLUMNS = 3f
private const val MIN_VERTICAL_GLYPHS = 3
private const val MAX_VERTICAL_ASPECT_RATIO = 1.60f
private const val SINGLE_COLUMN_ALIGNMENT = 0.60f
private const val COLUMN_ALIGNMENT_TOLERANCE = 0.58f
private const val MIN_COLUMN_COVERAGE = 0.60f
private const val MAX_VERTICAL_ROW_GAP = 0.10f
private const val MIN_DENSE_ROW_COVERAGE = 0.75f
