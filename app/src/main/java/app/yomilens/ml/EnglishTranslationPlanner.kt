package app.yomilens.ml

import app.yomilens.model.ReadingLine

data class EnglishTranslationEntry(
    val japanese: String,
    val fixedEnglish: String? = null,
)

data class EnglishTranslationPlan(
    val rows: List<List<EnglishTranslationEntry>>,
    val preserveLayout: Boolean,
) {
    val entries: List<EnglishTranslationEntry> = rows.flatten()

    fun render(translations: List<String>): String {
        require(translations.size == entries.size)
        if (!preserveLayout) return translations.single().trim()

        val iterator = translations.iterator()
        return rows.joinToString("\n") { row ->
            row.joinToString(CHART_COLUMN_SEPARATOR) { iterator.next().trim() }
        }
    }

    private companion object {
        const val CHART_COLUMN_SEPARATOR = "   "
    }
}

/** Keeps sentence context for prose but translates spatially separated labels independently. */
object EnglishTranslationPlanner {
    fun create(japanese: String, readings: List<ReadingLine>): EnglishTranslationPlan {
        if (OCR_WIDE_GAP !in japanese) {
            return EnglishTranslationPlan(
                rows = listOf(
                    listOf(
                        EnglishTranslationEntry(
                            japanese = japanese,
                            fixedEnglish = readingAwareEnglish(japanese, readings),
                        ),
                    ),
                ),
                preserveLayout = false,
            )
        }

        val chartRows = japanese.lineSequence()
            .map { line -> line.trim().split(OCR_WIDE_GAP).filter(String::isNotBlank) }
            .filter { row -> row.isNotEmpty() }
            .toList()

        if (chartRows.flatten().size < 2) {
            return EnglishTranslationPlan(
                rows = listOf(listOf(EnglishTranslationEntry(japanese))),
                preserveLayout = false,
            )
        }

        val readingBySurface = readingBySurface(readings)
        return EnglishTranslationPlan(
            rows = chartRows.map { row ->
                row.map { entry ->
                    EnglishTranslationEntry(
                        japanese = entry,
                        fixedEnglish = READING_AWARE_LABELS[entry to readingBySurface[entry]],
                    )
                }
            },
            preserveLayout = true,
        )
    }

    private fun readingAwareEnglish(japanese: String, readings: List<ReadingLine>): String? =
        READING_AWARE_LABELS[japanese to readingBySurface(readings)[japanese]]

    private fun readingBySurface(readings: List<ReadingLine>): Map<String, String> = readings
        .flatMap { line -> line.tokens }
        .mapNotNull { token -> token.furigana?.let { token.surface to it } }
        .toMap()

    private val READING_AWARE_LABELS = mapOf(
        ("月" to "つき") to "Moon",
    )
}
