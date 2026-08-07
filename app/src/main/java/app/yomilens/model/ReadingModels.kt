package app.yomilens.model

enum class OutputMode(val label: String) {
    FURIGANA("Furigana"),
    ROMAJI("Romaji"),
    ENGLISH("English"),
}

enum class TextOrientation {
    HORIZONTAL,
    VERTICAL,
}

data class ReadingToken(
    val surface: String,
    val furigana: String?,
    val readingKatakana: String,
    val isParticle: Boolean,
)

data class ReadingLine(
    val tokens: List<ReadingToken>,
)
