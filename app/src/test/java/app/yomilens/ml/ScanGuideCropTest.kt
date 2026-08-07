package app.yomilens.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanGuideCropTest {
    @Test
    fun scansTheWholeVisibleLens() {
        assertEquals(
            PixelCrop(left = 0, top = 0, right = 1_000, bottom = 2_000),
            ScanGuideCrop.regionFor(width = 1_000, height = 2_000),
        )
    }

    @Test
    fun alwaysProducesPositiveDimensions() {
        val crop = ScanGuideCrop.regionFor(width = 1, height = 1)

        assertEquals(1, crop.width)
        assertEquals(1, crop.height)
    }
}
