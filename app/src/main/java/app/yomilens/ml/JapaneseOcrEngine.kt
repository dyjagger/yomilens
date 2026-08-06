package app.yomilens.ml

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class JapaneseOcrEngine(
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build(),
    ),
) : AutoCloseable {
    @OptIn(markerClass = [ExperimentalGetImage::class])
    fun recognize(imageProxy: ImageProxy): Task<String> {
        return try {
            val mediaImage = imageProxy.image ?: error("The camera returned an empty image.")
            val input = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees,
            )
            recognizer.process(input)
                .continueWith { result -> result.result.text.trim() }
                .addOnCompleteListener { imageProxy.close() }
        } catch (error: Exception) {
            imageProxy.close()
            throw error
        }
    }

    override fun close() {
        recognizer.close()
    }
}
