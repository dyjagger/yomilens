package app.yomilens.ml

data class PixelCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** The OCR region covers the entire camera viewport. */
object ScanGuideCrop {
    fun regionFor(width: Int, height: Int): PixelCrop {
        require(width > 0 && height > 0)
        return PixelCrop(
            left = 0,
            top = 0,
            right = width,
            bottom = height,
        )
    }
}
