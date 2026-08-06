package app.yomilens.ml

/** Keeps OCR output focused on Japanese while preserving useful line boundaries. */
object JapaneseTextCleaner {
    fun clean(rawText: String): String = rawText
        .lineSequence()
        .map(::normalizeLine)
        .filter { line -> line.any(::isJapaneseCharacter) }
        .joinToString("\n")
        .trim()

    private fun normalizeLine(line: String): String {
        val trimmed = line.trim()
        return buildString(trimmed.length) {
            var index = 0
            while (index < trimmed.length) {
                val character = trimmed[index]
                if (!character.isWhitespace()) {
                    append(character)
                    index += 1
                    continue
                }

                val previous = lastOrNull()
                var nextIndex = index + 1
                var hasWideGap = character == OCR_WIDE_GAP
                while (nextIndex < trimmed.length && trimmed[nextIndex].isWhitespace()) {
                    hasWideGap = hasWideGap || trimmed[nextIndex] == OCR_WIDE_GAP
                    nextIndex += 1
                }
                val next = trimmed.getOrNull(nextIndex)
                if (
                    previous != null &&
                    next != null &&
                    lastOrNull() != ' '
                ) {
                    append(if (hasWideGap) OCR_WIDE_GAP else ' ')
                }
                index = nextIndex
            }
        }
    }

    private fun isJapaneseCharacter(character: Char): Boolean =
        character.code in 0x3040..0x30FF ||
            character.code in 0x3400..0x4DBF ||
            character.code in 0x4E00..0x9FFF ||
            character.code in 0xF900..0xFAFF ||
            character in setOf('々', '〆', 'ヶ', 'ヵ', 'ー')
}
