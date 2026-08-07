package app.yomilens.ml

import kotlin.math.max

const val OCR_WIDE_GAP: Char = '\u3000'

/** A piece of OCR text and its horizontal bounds within one detected line. */
data class OcrSegment(
    val text: String,
    val left: Int?,
    val right: Int?,
)

/**
 * Rebuilds OCR lines without turning separately positioned chart entries into
 * accidental Japanese compounds.
 */
object OcrLayoutReconstructor {
    fun reconstruct(lines: List<List<OcrSegment>>): String = lines
        .map(::reconstructLine)
        .filter(String::isNotBlank)
        .joinToString("\n")

    fun reconstructLine(segments: List<OcrSegment>): String = buildString {
        val usableSegments = segments
            .map { segment -> segment.copy(text = segment.text.filterNot(Char::isWhitespace)) }
            .filter { it.text.isNotEmpty() }

        usableSegments.forEachIndexed { index, segment ->
            if (index > 0 && isVisuallySeparate(usableSegments[index - 1], segment)) {
                append(OCR_WIDE_GAP)
            }
            append(segment.text)
        }
    }

    /** Splits one OCR line into independently positioned labels or prose runs. */
    fun splitVisuallySeparate(segments: List<OcrSegment>): List<List<OcrSegment>> {
        val usableSegments = segments.filter { it.text.isNotBlank() }
        if (usableSegments.isEmpty()) return emptyList()

        return buildList {
            var current = mutableListOf(usableSegments.first())
            usableSegments.drop(1).forEach { segment ->
                if (isVisuallySeparate(current.last(), segment)) {
                    add(current)
                    current = mutableListOf()
                }
                current += segment
            }
            add(current)
        }
    }

    internal fun isVisuallySeparate(previous: OcrSegment, current: OcrSegment): Boolean {
        val previousLeft = previous.left ?: return false
        val previousRight = previous.right ?: return false
        val currentLeft = current.left ?: return false
        val currentRight = current.right ?: return false
        val gap = currentLeft - previousRight
        if (gap <= 0) return false

        val previousGlyphWidth = (previousRight - previousLeft).toFloat() /
            previous.text.codePointCount().coerceAtLeast(1)
        val currentGlyphWidth = (currentRight - currentLeft).toFloat() /
            current.text.codePointCount().coerceAtLeast(1)
        val typicalGlyphWidth = max(previousGlyphWidth, currentGlyphWidth)

        return gap > typicalGlyphWidth * SEPARATE_ENTRY_GAP_RATIO
    }

    private fun String.codePointCount(): Int = codePointCount(0, length)

    private const val SEPARATE_ENTRY_GAP_RATIO = 0.75f
}
