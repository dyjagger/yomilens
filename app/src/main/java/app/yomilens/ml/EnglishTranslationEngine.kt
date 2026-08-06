package app.yomilens.ml

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
    suspend fun translate(japanese: String): String {
        val downloadConditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(downloadConditions).await()
        return translator.translate(japanese).await().trim()
    }

    override fun close() {
        translator.close()
    }
}
