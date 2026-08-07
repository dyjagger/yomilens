package app.yomilens.model

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
) {
    val furigana: String
        get() = readingLines.joinToString("\n") { line ->
            line.tokens.joinToString("") { token -> token.furigana ?: token.surface }
        }
}

data class OverlayPlacement(
    val x: Float,
    val y: Float,
    val maxWidth: Float,
)

/** Keeps labels near their text while avoiding the top status and bottom controls. */
object LensOverlayPlacement {
    fun calculate(
        bounds: NormalizedBounds,
        viewportWidth: Float,
        viewportHeight: Float,
        labelWidth: Float,
        labelHeight: Float,
        edgePadding: Float,
        topReserved: Float,
        bottomReserved: Float,
    ): OverlayPlacement {
        require(viewportWidth > 0f && viewportHeight > 0f)
        val minimumY = topReserved.coerceAtLeast(edgePadding)
        val maximumY = (viewportHeight - bottomReserved - labelHeight)
            .coerceAtLeast(minimumY)
        val availableLabelWidth = (viewportWidth - edgePadding * 2f).coerceAtLeast(edgePadding)
        val boundedLabelWidth = labelWidth.coerceIn(edgePadding, availableLabelWidth)
        val regionTop = bounds.top * viewportHeight
        val regionBottom = bounds.bottom * viewportHeight
        val above = regionTop - labelHeight - edgePadding
        val proposedY = if (above >= minimumY) above else regionBottom + edgePadding
        val x = (bounds.left * viewportWidth).coerceIn(
            edgePadding,
            (viewportWidth - edgePadding - boundedLabelWidth).coerceAtLeast(edgePadding),
        )
        return OverlayPlacement(
            x = x,
            y = proposedY.coerceIn(minimumY, maximumY),
            maxWidth = availableLabelWidth,
        )
    }
}
