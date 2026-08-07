package app.yomilens.model

import app.yomilens.text.JapaneseScript

/** Bounds expressed as fractions of the full camera viewport. */
data class NormalizedBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f)
        require(top in 0f..1f)
        require(right in left..1f)
        require(bottom in top..1f)
    }
}

data class LensOverlayItem(
    val japanese: String,
    val bounds: NormalizedBounds,
    val readingLines: List<ReadingLine>,
    val romaji: String,
    val english: String? = null,
    val orientation: TextOrientation = TextOrientation.HORIZONTAL,
) {
    val kanjiReadingLines: List<ReadingLine>
        get() = readingLines.mapNotNull { line ->
            line.tokens.mapNotNull { token ->
                if (!JapaneseScript.containsKanji(token.surface)) return@mapNotNull null
                token.copy(
                    surface = JapaneseScript.onlyKanji(token.surface),
                    furigana = token.furigana?.let { reading ->
                        JapaneseScript.kanjiReading(token.surface, reading)
                    },
                )
            }
                .takeIf(List<ReadingToken>::isNotEmpty)
                ?.let(::ReadingLine)
        }

    val kanjiText: String
        get() = kanjiReadingLines.joinToString("\n") { line ->
            line.tokens.joinToString(" ", transform = ReadingToken::surface)
        }

    val furigana: String
        get() = kanjiReadingLines.joinToString("\n") { line ->
            line.tokens.joinToString(" ") { token -> token.furigana ?: token.surface }
        }
}

data class OverlayPlacement(
    val x: Float,
    val y: Float,
    val maxWidth: Float,
    val width: Float,
    val height: Float,
)

data class OverlayLabelSpec(
    val id: Int,
    val bounds: NormalizedBounds,
    val width: Float,
    val height: Float,
)

/** Places every measured label as one collision set, never as independent overlays. */
object LensOverlayPlacement {
    private const val MAX_SCAN_CANDIDATES = 512

    fun calculate(
        bounds: NormalizedBounds,
        viewportWidth: Float,
        viewportHeight: Float,
        labelWidth: Float,
        labelHeight: Float,
        edgePadding: Float,
        topReserved: Float,
        bottomReserved: Float,
    ): OverlayPlacement = calculateAll(
        specs = listOf(OverlayLabelSpec(0, bounds, labelWidth, labelHeight)),
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        edgePadding = edgePadding,
        topReserved = topReserved,
        bottomReserved = bottomReserved,
        collisionGap = edgePadding,
    ).getValue(0)

    fun calculateAll(
        specs: List<OverlayLabelSpec>,
        viewportWidth: Float,
        viewportHeight: Float,
        edgePadding: Float,
        topReserved: Float,
        bottomReserved: Float,
        collisionGap: Float,
    ): Map<Int, OverlayPlacement> {
        require(viewportWidth > 0f && viewportHeight > 0f)
        val minimumY = topReserved.coerceAtLeast(edgePadding)
        val availableLabelWidth = (viewportWidth - edgePadding * 2f).coerceAtLeast(edgePadding)
        val availableBottom = (viewportHeight - bottomReserved).coerceAtLeast(minimumY)
        val availableHeight = availableBottom - minimumY
        if (availableHeight <= 0f) return emptyMap()

        val sourceObstacles = specs.map { spec -> spec.bounds.toPixelRect(viewportWidth, viewportHeight) }
        val occupied = mutableListOf<PixelRect>()
        val placements = mutableMapOf<Int, OverlayPlacement>()

        specs.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }, OverlayLabelSpec::id))
            .forEach { spec ->
                val width = spec.width
                val height = spec.height
                if (
                    width <= 0f ||
                    height <= 0f ||
                    width > availableLabelWidth ||
                    height > availableHeight
                ) {
                    return@forEach
                }
                val source = spec.bounds.toPixelRect(viewportWidth, viewportHeight)
                val candidates = directCandidates(source, width, height, collisionGap)
                    .map { it.clamp(edgePadding, minimumY, viewportWidth - edgePadding, availableBottom) }
                    .distinct()

                val placed = candidates.firstOrNull { candidate ->
                    candidate.clears(occupied, collisionGap) &&
                        candidate.clears(sourceObstacles, collisionGap)
                } ?: scanForSpace(
                    width = width,
                    height = height,
                    left = edgePadding,
                    top = minimumY,
                    right = viewportWidth - edgePadding,
                    bottom = availableBottom,
                    gap = collisionGap,
                    occupied = occupied,
                    obstacles = sourceObstacles,
                    preferred = source,
                ) ?: candidates.firstOrNull { candidate ->
                    candidate.clears(occupied, collisionGap)
                } ?: scanForSpace(
                    width = width,
                    height = height,
                    left = edgePadding,
                    top = minimumY,
                    right = viewportWidth - edgePadding,
                    bottom = availableBottom,
                    gap = collisionGap,
                    occupied = occupied,
                    obstacles = emptyList(),
                    preferred = source,
                )

                if (placed != null) {
                    occupied += placed
                    placements[spec.id] = OverlayPlacement(
                        x = placed.left,
                        y = placed.top,
                        maxWidth = availableLabelWidth,
                        width = placed.width,
                        height = placed.height,
                    )
                }
            }
        return placements
    }

    private fun directCandidates(
        source: PixelRect,
        width: Float,
        height: Float,
        gap: Float,
    ): List<PixelRect> {
        val centeredX = source.centerX - width / 2f
        val centeredY = source.centerY - height / 2f
        return listOf(
            PixelRect(centeredX, source.top - gap - height, centeredX + width, source.top - gap),
            PixelRect(centeredX, source.bottom + gap, centeredX + width, source.bottom + gap + height),
            PixelRect(source.right + gap, centeredY, source.right + gap + width, centeredY + height),
            PixelRect(source.left - gap - width, centeredY, source.left - gap, centeredY + height),
            PixelRect(source.left, source.top - gap - height, source.left + width, source.top - gap),
            PixelRect(source.left, source.bottom + gap, source.left + width, source.bottom + gap + height),
        )
    }

    private fun scanForSpace(
        width: Float,
        height: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        gap: Float,
        occupied: List<PixelRect>,
        obstacles: List<PixelRect>,
        preferred: PixelRect,
    ): PixelRect? {
        val maximumX = right - width
        val maximumY = bottom - height
        if (maximumX < left || maximumY < top) return null

        val horizontalSpan = maximumX - left
        val verticalSpan = maximumY - top
        val aspectRatio = horizontalSpan / verticalSpan.coerceAtLeast(1f)
        val columns = kotlin.math.sqrt(MAX_SCAN_CANDIDATES * aspectRatio.toDouble())
            .toInt()
            .coerceIn(1, MAX_SCAN_CANDIDATES)
        val rows = (MAX_SCAN_CANDIDATES / columns).coerceAtLeast(1)
        var nearest: PixelRect? = null
        var nearestDistance = Float.POSITIVE_INFINITY

        axisPositions(top, verticalSpan, rows).forEach { y ->
            axisPositions(left, horizontalSpan, columns).forEach { x ->
                val candidate = PixelRect(x, y, x + width, y + height)
                if (candidate.clears(occupied, gap) && candidate.clears(obstacles, gap)) {
                    val horizontalDistance = candidate.centerX - preferred.centerX
                    val verticalDistance = candidate.centerY - preferred.centerY
                    val distance = horizontalDistance * horizontalDistance +
                        verticalDistance * verticalDistance
                    if (distance < nearestDistance) {
                        nearest = candidate
                        nearestDistance = distance
                    }
                }
            }
        }
        return nearest
    }

    private fun axisPositions(start: Float, span: Float, count: Int): List<Float> {
        if (span <= 0f || count <= 1) return listOf(start)
        return List(count) { index -> start + span * index / (count - 1f) }
    }
}

private data class PixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun clamp(minimumX: Float, minimumY: Float, maximumX: Float, maximumY: Float): PixelRect {
        val x = left.coerceIn(minimumX, (maximumX - width).coerceAtLeast(minimumX))
        val y = top.coerceIn(minimumY, (maximumY - height).coerceAtLeast(minimumY))
        return PixelRect(x, y, x + width, y + height)
    }

    fun clears(rectangles: List<PixelRect>, gap: Float): Boolean = rectangles.none { other ->
        left < other.right + gap &&
            right > other.left - gap &&
            top < other.bottom + gap &&
            bottom > other.top - gap
    }
}

private fun NormalizedBounds.toPixelRect(width: Float, height: Float) = PixelRect(
    left = left * width,
    top = top * height,
    right = right * width,
    bottom = bottom * height,
)
