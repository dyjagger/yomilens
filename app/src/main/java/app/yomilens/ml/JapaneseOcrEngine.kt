package app.yomilens.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

data class CapturedFrame(
    val bitmap: Bitmap,
    val viewportCrop: Rect,
    val rotationDegrees: Int,
    val closeSource: () -> Unit,
)

class JapaneseOcrEngine(
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build(),
    ),
    private val textRecognition: (InputImage) -> Task<String> = { input ->
        recognizer.process(input).continueWith { result -> result.result.text }
    },
) : AutoCloseable {
    fun recognize(imageProxy: ImageProxy): Task<String> {
        val sourceBitmap = try {
            imageProxy.toBitmap()
        } catch (error: Exception) {
            imageProxy.close()
            throw error
        }

        val frame = try {
            CapturedFrame(
                bitmap = sourceBitmap,
                viewportCrop = Rect(imageProxy.cropRect),
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                closeSource = imageProxy::close,
            )
        } catch (error: Exception) {
            sourceBitmap.recycleSafely()
            imageProxy.close()
            throw error
        }
        return recognize(frame)
    }

    fun recognize(frame: CapturedFrame): Task<String> {
        var workingBitmap = frame.bitmap
        var sourceClosed = false
        return try {
            workingBitmap = workingBitmap.replaceWith(
                workingBitmap.cropTo(frame.viewportCrop),
            )
            workingBitmap = workingBitmap.replaceWith(
                workingBitmap.rotate(frame.rotationDegrees),
            )
            val guideCrop = ScanGuideCrop.regionFor(
                width = workingBitmap.width,
                height = workingBitmap.height,
            )
            workingBitmap = workingBitmap.replaceWith(
                Bitmap.createBitmap(
                    workingBitmap,
                    guideCrop.left,
                    guideCrop.top,
                    guideCrop.width,
                    guideCrop.height,
                ),
            )

            sourceClosed = true
            frame.closeSource()
            val inputBitmap = workingBitmap
            val input = InputImage.fromBitmap(inputBitmap, 0)
            textRecognition(input)
                .continueWith { result -> JapaneseTextCleaner.clean(result.result) }
                .addOnCompleteListener { inputBitmap.recycleSafely() }
        } catch (error: Exception) {
            if (!sourceClosed) {
                sourceClosed = true
                frame.closeSource()
            }
            workingBitmap.recycleSafely()
            throw error
        }
    }

    override fun close() {
        recognizer.close()
    }
}

private fun Bitmap.cropTo(rect: Rect): Bitmap {
    val left = rect.left.coerceIn(0, width - 1)
    val top = rect.top.coerceIn(0, height - 1)
    val right = rect.right.coerceIn(left + 1, width)
    val bottom = rect.bottom.coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap?.replaceWith(replacement: Bitmap): Bitmap {
    if (this != null && this !== replacement) recycleSafely()
    return replacement
}

private fun Bitmap?.recycleSafely() {
    if (this != null && !isRecycled) recycle()
}
