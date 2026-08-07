package app.yomilens.ui

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.yomilens.ml.EnglishTranslationEngine
import app.yomilens.ml.JapaneseOcrEngine
import app.yomilens.ml.JapaneseOcrResult
import app.yomilens.ml.JapaneseTranslationUnit
import app.yomilens.model.LensOverlayItem
import app.yomilens.model.OutputMode
import app.yomilens.model.ReadingLine
import app.yomilens.reading.JapaneseReadingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.Task

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
    val overlayItems: List<LensOverlayItem> = emptyList(),
    val frozenFrame: RetainedCameraFrame? = null,
    val errorMessage: String? = null,
    val completedScanCount: Int = 0,
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
    private val captureAdmission = CaptureAdmissionGate()

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
            requestEnglish(scanGeneration, current.overlayItems)
        }
    }

    fun tryBeginCapture(isCameraStreaming: Boolean): Boolean {
        val current = mutableState.value
        if (!captureAdmission.tryAcquire(current.isBusy, isCameraStreaming)) return false
        scanGeneration += 1
        translationJob?.cancel()
        mutableState.value = current.copy(
            phase = ScanPhase.CAPTURING,
            errorMessage = null,
        )
        return true
    }

    fun onImageCaptured(imageProxy: ImageProxy) {
        val generation = scanGeneration
        mutableState.value = mutableState.value.copy(phase = ScanPhase.RECOGNIZING)
        val recognitionTask = try {
            ocrEngine.recognize(imageProxy)
        } catch (error: Exception) {
            captureAdmission.complete()
            mutableState.value = mutableState.value.copy(
                phase = ScanPhase.ERROR,
                errorMessage = error.message ?: "Japanese text recognition failed. Please try again.",
            )
            return
        }

        viewModelScope.launch {
            var pendingFrame: Bitmap? = null
            try {
                val recognition = recognitionTask.awaitOwnedResult()
                pendingFrame = recognition.frozenFrame
                if (generation != scanGeneration) {
                    recognition.frozenFrame.recycleSafely()
                    pendingFrame = null
                    captureAdmission.complete()
                    return@launch
                }
                val japanese = recognition.text
                if (japanese.isBlank()) {
                    recognition.frozenFrame.recycleSafely()
                    pendingFrame = null
                    val previousFrame = mutableState.value.frozenFrame
                    mutableState.value = mutableState.value.copy(
                        phase = ScanPhase.READY,
                        recognizedJapanese = "",
                        readingLines = emptyList(),
                        romaji = "",
                        english = null,
                        overlayItems = emptyList(),
                        frozenFrame = null,
                        errorMessage = null,
                        completedScanCount = mutableState.value.completedScanCount + 1,
                    )
                    captureAdmission.complete()
                    previousFrame?.release()
                    return@launch
                }

                val lines = withContext(processingDispatcher) {
                    readingEngine.annotate(japanese)
                }
                val romaji = withContext(processingDispatcher) {
                    readingEngine.romanizeKanji(lines)
                }
                val overlays = withContext(processingDispatcher) {
                    recognition.regions.map { region ->
                        val regionReadings = readingEngine.annotate(region.text)
                        LensOverlayItem(
                            japanese = region.text,
                            bounds = region.bounds,
                            readingLines = regionReadings,
                            romaji = readingEngine.romanizeKanji(regionReadings),
                            orientation = region.orientation,
                        )
                    }
                }
                if (generation != scanGeneration) {
                    recognition.frozenFrame.recycleSafely()
                    pendingFrame = null
                    captureAdmission.complete()
                    return@launch
                }

                val previousFrame = mutableState.value.frozenFrame
                mutableState.value = mutableState.value.copy(
                    recognizedJapanese = japanese,
                    readingLines = lines,
                    romaji = romaji,
                    english = null,
                    overlayItems = overlays,
                    frozenFrame = RetainedCameraFrame(recognition.frozenFrame),
                    phase = if (mutableState.value.selectedMode == OutputMode.ENGLISH) {
                        ScanPhase.PREPARING_ENGLISH
                    } else {
                        ScanPhase.READY
                    },
                    errorMessage = null,
                    completedScanCount = mutableState.value.completedScanCount + 1,
                )
                captureAdmission.complete()
                previousFrame?.release()
                pendingFrame = null

                if (mutableState.value.selectedMode == OutputMode.ENGLISH) {
                    requestEnglish(generation, overlays)
                }
            } catch (cancellation: CancellationException) {
                pendingFrame.recycleSafely()
                throw cancellation
            } catch (error: Exception) {
                pendingFrame.recycleSafely()
                captureAdmission.complete()
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
        captureAdmission.complete()
        mutableState.value = mutableState.value.copy(
            phase = ScanPhase.ERROR,
            errorMessage = message,
        )
    }

    fun retryEnglish() {
        val current = mutableState.value
        if (current.recognizedJapanese.isNotBlank()) {
            requestEnglish(scanGeneration, current.overlayItems)
        }
    }

    private fun requestEnglish(
        generation: Int,
        overlays: List<LensOverlayItem>,
    ) {
        translationJob?.cancel()
        mutableState.value = mutableState.value.copy(
            phase = ScanPhase.PREPARING_ENGLISH,
            errorMessage = null,
        )
        translationJob = viewModelScope.launch {
            try {
                val translations = translator.translateUnits(
                    overlays.map { overlay ->
                        JapaneseTranslationUnit(overlay.kanjiText, overlay.kanjiReadingLines)
                    },
                )
                if (generation != scanGeneration) return@launch
                mutableState.value = mutableState.value.copy(
                    english = translations.joinToString("\n"),
                    overlayItems = overlays.zip(translations) { overlay, english ->
                        overlay.copy(english = english)
                    },
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
        mutableState.value.frozenFrame?.release()
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

private fun Bitmap?.recycleSafely() {
    if (this != null && !isRecycled) recycle()
}

/** Recycles a successful task result if cancellation happens before the ViewModel can own it. */
internal suspend fun Task<JapaneseOcrResult>.awaitOwnedResult(): JapaneseOcrResult =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val result = task.result
                continuation.resume(result) { _, cancelledResult, _ ->
                    cancelledResult.frozenFrame.recycleSafely()
                }
            } else {
                val error = task.exception ?: CancellationException("Japanese OCR was cancelled")
                continuation.resumeWith(Result.failure(error))
            }
        }
    }
