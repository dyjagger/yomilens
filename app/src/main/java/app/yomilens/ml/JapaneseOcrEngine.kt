package app.yomilens.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import app.yomilens.model.NormalizedBounds
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

data class CapturedFrame(
    val bitmap: Bitmap,
    val viewportCrop: Rect,
    val rotationDegrees: Int,
    val closeSource: () -> Unit,
)

data class JapaneseOcrRegion(
    val text: String,
    val bounds: NormalizedBounds,
)

/** The upright captured viewport stays in memory so overlays cannot drift off the source text. */
data class JapaneseOcrResult(
    val text: String,
    val regions: List<JapaneseOcrRegion>,
    val frozenFrame: Bitmap,
)

class JapaneseOcrEngine(
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build(),
    ),
    private val textRecognition: (InputImage) -> Task<OcrRecognition> = { input ->
        recognizer.process(input).continueWith { task -> extractRecognition(task.result) }
    },
) : AutoCloseable {
    fun recognize(imageProxy: ImageProxy): Task<JapaneseOcrResult> {
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

    fun recognize(frame: CapturedFrame): Task<JapaneseOcrResult> {
        var fullBitmap = frame.bitmap
        var recognitionBitmap: Bitmap? = null
        var sourceClosed = false
        return try {
            fullBitmap = fullBitmap.replaceWith(fullBitmap.cropTo(frame.viewportCrop))
            fullBitmap = fullBitmap.replaceWith(fullBitmap.rotate(frame.rotationDegrees))
            val guideCrop = ScanGuideCrop.regionFor(fullBitmap.width, fullBitmap.height)
            recognitionBitmap = fullBitmap.cropCopy(guideCrop)

            sourceClosed = true
            frame.closeSource()
            val inputBitmap = recognitionBitmap
            val retainedFrame = fullBitmap
            textRecognition(InputImage.fromBitmap(inputBitmap, 0))
                .continueWith { task ->
                    val raw = task.result
                    val cleanedText = JapaneseTextCleaner.cleanForOverlay(raw.text)
                    val mappedRegions = raw.regions.mapNotNull { region ->
                        val cleanedRegion = JapaneseTextCleaner.cleanForOverlay(region.text)
                        cleanedRegion.takeIf(String::isNotBlank)?.let {
                            JapaneseOcrRegion(
                                text = it,
                                bounds = region.bounds.toNormalizedBounds(
                                    guideCrop = guideCrop,
                                    fullWidth = retainedFrame.width,
                                    fullHeight = retainedFrame.height,
                                ),
                            )
                        }
                    }.ifEmpty {
                        cleanedText.takeIf(String::isNotBlank)?.let {
                            listOf(
                                JapaneseOcrRegion(
                                    text = it,
                                    bounds = guideCrop.toNormalizedBounds(
                                        retainedFrame.width,
                                        retainedFrame.height,
                                    ),
                                ),
                            )
                        }.orEmpty()
                    }
                    JapaneseOcrResult(
                        text = cleanedText,
                        regions = mappedRegions,
                        frozenFrame = retainedFrame,
                    )
                }
                .addOnCompleteListener { task ->
                    inputBitmap.recycleSafely()
                    if (!task.isSuccessful) retainedFrame.recycleSafely()
                }
        } catch (error: Exception) {
            if (!sourceClosed) {
                sourceClosed = true
                frame.closeSource()
            }
            recognitionBitmap.recycleSafely()
            fullBitmap.recycleSafely()
            throw error
        }
    }

    override fun close() {
        recognizer.close()
    }
}

private fun extractRecognition(text: Text): OcrRecognition {
    val regions = OcrRegionLayout.regionsForDocument(
        text.textBlocks.map { block ->
            OcrPositionedBlock(
                lines = block.lines.map { line ->
                    OcrPositionedLine(
                        segments = line.elements.map { element ->
                            OcrPositionedSegment(element.text, element.boundingBox?.toPixelBounds())
                        }.ifEmpty {
                            listOf(OcrPositionedSegment(line.text, line.boundingBox?.toPixelBounds()))
                        },
                        bounds = line.boundingBox?.toPixelBounds(),
                        angleDegrees = line.angle,
                    )
                },
                bounds = block.boundingBox?.toPixelBounds(),
            )
        },
    )
    return OcrRecognition(
        text = OcrRegionLayout.textForRegions(regions),
        regions = regions,
    )
}

private fun Rect.toPixelBounds() = OcrPixelBounds(left, top, right, bottom)

private fun OcrPixelBounds?.toNormalizedBounds(
    guideCrop: PixelCrop,
    fullWidth: Int,
    fullHeight: Int,
): NormalizedBounds {
    if (this == null) return guideCrop.toNormalizedBounds(fullWidth, fullHeight)
    val absoluteLeft = (guideCrop.left + left).coerceIn(0, fullWidth)
    val absoluteTop = (guideCrop.top + top).coerceIn(0, fullHeight)
    val absoluteRight = (guideCrop.left + right).coerceIn(absoluteLeft, fullWidth)
    val absoluteBottom = (guideCrop.top + bottom).coerceIn(absoluteTop, fullHeight)
    return NormalizedBounds(
        left = absoluteLeft.toFloat() / fullWidth,
        top = absoluteTop.toFloat() / fullHeight,
        right = absoluteRight.toFloat() / fullWidth,
        bottom = absoluteBottom.toFloat() / fullHeight,
    )
}

private fun PixelCrop.toNormalizedBounds(fullWidth: Int, fullHeight: Int) = NormalizedBounds(
    left = left.toFloat() / fullWidth,
    top = top.toFloat() / fullHeight,
    right = right.toFloat() / fullWidth,
    bottom = bottom.toFloat() / fullHeight,
)

private fun Bitmap.cropTo(rect: Rect): Bitmap {
    val left = rect.left.coerceIn(0, width - 1)
    val top = rect.top.coerceIn(0, height - 1)
    val right = rect.right.coerceIn(left + 1, width)
    val bottom = rect.bottom.coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

private fun Bitmap.cropCopy(rect: PixelCrop): Bitmap {
    val cropped = Bitmap.createBitmap(this, rect.left, rect.top, rect.width, rect.height)
    return if (cropped === this) copy(config ?: Bitmap.Config.ARGB_8888, false) else cropped
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
