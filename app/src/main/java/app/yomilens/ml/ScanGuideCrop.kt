package app.yomilens.ml

import kotlin.math.roundToInt

data class PixelCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** The OCR region matching the guide drawn over the camera preview. */
object ScanGuideCrop {
    const val HORIZONTAL_INSET = 0.08f
    const val TOP = 0.24f
    const val BOTTOM = 0.76f

    fun regionFor(width: Int, height: Int): PixelCrop {
        require(width > 0 && height > 0)
        return PixelCrop(
            left = (width * HORIZONTAL_INSET).roundToInt(),
            top = (height * TOP).roundToInt(),
            right = (width * (1f - HORIZONTAL_INSET)).roundToInt(),
            bottom = (height * BOTTOM).roundToInt(),
        )
    }
}
