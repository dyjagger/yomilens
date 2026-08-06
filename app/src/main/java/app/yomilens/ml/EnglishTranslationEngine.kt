package app.yomilens.ml

import app.yomilens.model.ReadingLine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

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
    ): String {
        val plan = EnglishTranslationPlanner.create(japanese, readings)
        if (plan.entries.any { entry -> entry.fixedEnglish == null }) {
            val downloadConditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(downloadConditions).await()
        }
        val translations = plan.entries.map { entry ->
            entry.fixedEnglish ?: translator.translate(entry.japanese).await().trim()
        }
        return plan.render(translations)
    }

    override fun close() {
        translator.close()
    }
}
