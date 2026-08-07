package app.yomilens.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensOverlayPlacementTest {
    @Test
    fun placesLabelAboveDetectedTextOnXperiaAspectRatio() {
        val placement = LensOverlayPlacement.calculate(
            bounds = NormalizedBounds(0.2f, 0.4f, 0.7f, 0.46f),
            viewportWidth = 1_080f,
            viewportHeight = 2_340f,
            labelWidth = 240f,
            labelHeight = 84f,
            edgePadding = 16f,
            topReserved = 136f,
            bottomReserved = 336f,
        )

        assertEquals(216f, placement.x)
        assertEquals(836f, placement.y)
        assertEquals(1_048f, placement.maxWidth)
    }

    @Test
    fun keepsTopAndBottomLabelsClearOfControls() {
        val top = placementFor(NormalizedBounds(0.1f, 0.01f, 0.4f, 0.05f))
        val bottom = placementFor(NormalizedBounds(0.1f, 0.96f, 0.4f, 0.99f))

        assertTrue(top.y >= 136f)
        assertTrue(bottom.y <= 1_920f)
    }

    @Test
    fun keepsMeasuredLabelInsideTheRightEdge() {
        val placement = LensOverlayPlacement.calculate(
            bounds = NormalizedBounds(0.98f, 0.4f, 1f, 0.45f),
            viewportWidth = 1_080f,
            viewportHeight = 2_340f,
            labelWidth = 260f,
            labelHeight = 84f,
            edgePadding = 16f,
            topReserved = 136f,
            bottomReserved = 420f,
        )

        assertTrue(placement.x + 260f <= 1_080f - 16f)
    }

    private fun placementFor(bounds: NormalizedBounds) = LensOverlayPlacement.calculate(
        bounds = bounds,
        viewportWidth = 1_080f,
        viewportHeight = 2_340f,
        labelWidth = 240f,
        labelHeight = 84f,
        edgePadding = 16f,
        topReserved = 136f,
        bottomReserved = 336f,
    )
}
