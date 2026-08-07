package app.yomilens.ml

import app.yomilens.model.ReadingLine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

data class JapaneseTranslationUnit(
    val japanese: String,
    val readings: List<ReadingLine>,
)

class EnglishTranslationEngine(
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    ),
) : AutoCloseable {
    suspend fun translate(
        japanese: String,
        readings: List<ReadingLine> = emptyList(),
    ): String = translateUnits(listOf(JapaneseTranslationUnit(japanese, readings))).single()

    suspend fun translateUnits(units: List<JapaneseTranslationUnit>): List<String> {
        if (units.isEmpty()) return emptyList()
        val plans = units.map { unit ->
            EnglishTranslationPlanner.create(unit.japanese, unit.readings)
        }
        if (plans.any { plan -> plan.entries.any { entry -> entry.fixedEnglish == null } }) {
            val downloadConditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(downloadConditions).await()
        }
        return plans.map { plan ->
            val translations = plan.entries.map { entry ->
                entry.fixedEnglish ?: translator.translate(entry.japanese).await().trim()
            }
            plan.render(translations)
        }
    }

    override fun close() {
        translator.close()
    }
}
