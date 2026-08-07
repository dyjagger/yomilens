package app.yomilens.ml

import app.yomilens.text.JapaneseScript

/** Keeps OCR output focused on Japanese while preserving useful line boundaries. */
object JapaneseTextCleaner {
    fun clean(rawText: String): String = rawText
        .lineSequence()
        .map(::normalizeLine)
        .filter { line -> line.any(::isJapaneseCharacter) }
        .joinToString("\n")
        .trim()

    fun containsJapanese(text: String): Boolean = text.any(::isJapaneseCharacter)

    fun containsKanji(text: String): Boolean = JapaneseScript.containsKanji(text)

    /** Text sent to readings/translation contains Japanese script and allow-listed punctuation only. */
    fun cleanForOverlay(rawText: String): String = clean(
        buildString(rawText.length) {
            rawText.forEach { character ->
                if (
                    character.isWhitespace() ||
                    isJapaneseCharacter(character) ||
                    character in JAPANESE_PUNCTUATION
                ) {
                    append(character)
                }
            }
        },
    )

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
            JapaneseScript.isKanji(character) ||
            character in setOf('々', '〆', 'ヶ', 'ヵ', 'ー')

    private val JAPANESE_PUNCTUATION = setOf(
        '、',
        '。',
        '「',
        '」',
        '『',
        '』',
        '（',
        '）',
        '【',
        '】',
        '〔',
        '〕',
        '〈',
        '〉',
        '《',
        '》',
        '〜',
        '～',
        '…',
        '‥',
        '―',
    )
}
