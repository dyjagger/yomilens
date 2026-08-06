package app.yomilens.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanGuideCropTest {
    @Test
    fun cropsToTheVisibleCenterGuide() {
        assertEquals(
            PixelCrop(left = 80, top = 480, right = 920, bottom = 1520),
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
