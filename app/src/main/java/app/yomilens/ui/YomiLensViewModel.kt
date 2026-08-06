package app.yomilens.ui

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.yomilens.ml.EnglishTranslationEngine
import app.yomilens.ml.JapaneseOcrEngine
import app.yomilens.model.OutputMode
import app.yomilens.model.ReadingLine
import app.yomilens.reading.JapaneseReadingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class ScanPhase {
    IDLE,
    CAPTURING,
    RECOGNIZING,
    PREPARING_ENGLISH,
    READY,
    ERROR,
}

data class YomiLensUiState(
    val selectedMode: OutputMode = OutputMode.FURIGANA,
    val phase: ScanPhase = ScanPhase.IDLE,
    val recognizedJapanese: String = "",
    val readingLines: List<ReadingLine> = emptyList(),
    val romaji: String = "",
    val english: String? = null,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() = phase in setOf(
            ScanPhase.CAPTURING,
            ScanPhase.RECOGNIZING,
            ScanPhase.PREPARING_ENGLISH,
        )
}

class YomiLensViewModel(
    private val ocrEngine: JapaneseOcrEngine = JapaneseOcrEngine(),
    private val readingEngine: JapaneseReadingEngine = JapaneseReadingEngine(),
    private val translator: EnglishTranslationEngine = EnglishTranslationEngine(),
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(YomiLensUiState())
    val state = mutableState.asStateFlow()

    private var scanGeneration = 0
    private var translationJob: Job? = null

    fun selectMode(mode: OutputMode) {
        val current = mutableState.value
        if (
            current.selectedMode == mode ||
            current.phase == ScanPhase.CAPTURING ||
            current.phase == ScanPhase.RECOGNIZING
        ) {
            return
        }

        mutableState.value = current.copy(
            selectedMode = mode,
            phase = when {
                current.recognizedJapanese.isBlank() -> ScanPhase.IDLE
                mode == OutputMode.ENGLISH && current.english == null -> ScanPhase.PREPARING_ENGLISH
                else -> ScanPhase.READY
            },
            errorMessage = null,
        )

        if (mode == OutputMode.ENGLISH && current.recognizedJapanese.isNotBlank() && current.english == null) {
            requestEnglish(scanGeneration, current.recognizedJapanese, current.readingLines)
        }
    }

    fun beginCapture() {
        scanGeneration += 1
        translationJob?.cancel()
        mutableState.value = mutableState.value.copy(
            phase = ScanPhase.CAPTURING,
            errorMessage = null,
            english = null,
        )
    }

    fun onImageCaptured(imageProxy: ImageProxy) {
        val generation = scanGeneration
        mutableState.value = mutableState.value.copy(phase = ScanPhase.RECOGNIZING)
        val recognitionTask = try {
            ocrEngine.recognize(imageProxy)
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                phase = ScanPhase.ERROR,
                errorMessage = error.message ?: "Japanese text recognition failed. Please try again.",
            )
            return
        }

        viewModelScope.launch {
            try {
                val japanese = recognitionTask.await()
                if (generation != scanGeneration) return@launch
                if (japanese.isBlank()) {
                    mutableState.value = mutableState.value.copy(
                        phase = ScanPhase.ERROR,
                        errorMessage = "No Japanese text was found. Move closer, hold steady, and try again.",
                    )
                    return@launch
                }

                val lines = withContext(processingDispatcher) {
                    readingEngine.annotate(japanese)
                }
                val romaji = withContext(processingDispatcher) {
                    readingEngine.romanize(lines)
                }
                if (generation != scanGeneration) return@launch

                mutableState.value = mutableState.value.copy(
                    recognizedJapanese = japanese,
                    readingLines = lines,
                    romaji = romaji,
                    english = null,
                    phase = if (mutableState.value.selectedMode == OutputMode.ENGLISH) {
                        ScanPhase.PREPARING_ENGLISH
                    } else {
                        ScanPhase.READY
                    },
                    errorMessage = null,
                )

                if (mutableState.value.selectedMode == OutputMode.ENGLISH) {
                    requestEnglish(generation, japanese, lines)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generation == scanGeneration) {
                    mutableState.value = mutableState.value.copy(
                        phase = ScanPhase.ERROR,
                        errorMessage = error.message ?: "Japanese text recognition failed. Please try again.",
                    )
                }
            }
        }
    }

    fun onCaptureFailed(message: String) {
        mutableState.value = mutableState.value.copy(
            phase = ScanPhase.ERROR,
            errorMessage = message,
        )
    }

    fun retryEnglish() {
        val current = mutableState.value
        if (current.recognizedJapanese.isNotBlank()) {
            requestEnglish(scanGeneration, current.recognizedJapanese, current.readingLines)
        }
    }

    private fun requestEnglish(
        generation: Int,
        japanese: String,
        readings: List<ReadingLine>,
    ) {
        translationJob?.cancel()
        mutableState.value = mutableState.value.copy(
            phase = ScanPhase.PREPARING_ENGLISH,
            errorMessage = null,
        )
        translationJob = viewModelScope.launch {
            try {
                val english = translator.translate(japanese, readings)
                if (generation != scanGeneration) return@launch
                mutableState.value = mutableState.value.copy(
                    english = english,
                    phase = ScanPhase.READY,
                    errorMessage = null,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == scanGeneration && mutableState.value.selectedMode == OutputMode.ENGLISH) {
                    mutableState.value = mutableState.value.copy(
                        phase = ScanPhase.ERROR,
                        errorMessage = "English needs a one-time translation model download. Check your connection and retry.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        ocrEngine.close()
        translator.close()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(YomiLensViewModel::class.java))
                return YomiLensViewModel() as T
            }
        }
    }
}
