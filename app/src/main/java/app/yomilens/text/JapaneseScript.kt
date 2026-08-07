package app.yomilens.text

/** Unicode script checks shared by OCR cleanup, readings, and overlay rendering. */
object JapaneseScript {
    fun containsKanji(text: String): Boolean = text.any(::isKanji)

    fun onlyKanji(text: String): String = text.filter(::isKanji)

    /** Removes visible kana runs from a token reading so overlays describe its kanji only. */
    fun kanjiReading(surface: String, hiraganaReading: String): String {
        var reading = hiraganaReading
        var searchFrom = 0
        surface.kanaRuns().forEach { visibleKana ->
            val matchAt = reading.indexOf(visibleKana, startIndex = searchFrom)
            if (matchAt >= 0) {
                reading = reading.removeRange(matchAt, matchAt + visibleKana.length)
                searchFrom = matchAt
            }
        }
        return reading.ifBlank { hiraganaReading }
    }

    fun isKanji(character: Char): Boolean =
        character.code in 0x3400..0x4DBF ||
            character.code in 0x4E00..0x9FFF ||
            character.code in 0xF900..0xFAFF ||
            character in setOf('々', '〆')

    private fun isKana(character: Char): Boolean = character.code in 0x3041..0x30F6

    private fun String.kanaRuns(): List<String> = buildList {
        val run = StringBuilder()
        this@kanaRuns.forEach { character ->
            if (isKana(character)) {
                run.append(character)
            } else if (run.isNotEmpty()) {
                add(run.toString().toHiragana())
                run.clear()
            }
        }
        if (run.isNotEmpty()) add(run.toString().toHiragana())
    }

    private fun String.toHiragana(): String = map { character ->
        if (character.code in 0x30A1..0x30F6) {
            (character.code - 0x60).toChar()
        } else {
            character
        }
    }.joinToString("")
}
