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

        assertEquals(366f, placement.x)
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

    @Test
    fun resolvesCollisionsAcrossTheWholeLabelSet() {
        val placements = LensOverlayPlacement.calculateAll(
            specs = (0 until 5).map { index ->
                OverlayLabelSpec(
                    id = index,
                    bounds = NormalizedBounds(0.42f, 0.42f, 0.58f, 0.48f),
                    width = 260f,
                    height = 84f,
                )
            },
            viewportWidth = 1_080f,
            viewportHeight = 2_340f,
            edgePadding = 16f,
            topReserved = 136f,
            bottomReserved = 420f,
            collisionGap = 12f,
        )

        assertEquals(5, placements.size)
        placements.values.toList().forEachIndexed { index, placement ->
            placements.values.drop(index + 1).forEach { other ->
                assertTrue(
                    "Placements overlap or violate their gap: $placement and $other",
                    !placement.overlapsWithGap(other, gap = 12f),
                )
            }
        }
    }

    @Test
    fun omitsOversizeLabelInsteadOfUsingSmallerCollisionBounds() {
        val placements = LensOverlayPlacement.calculateAll(
            specs = listOf(
                OverlayLabelSpec(
                    id = 0,
                    bounds = NormalizedBounds(0.2f, 0.4f, 0.7f, 0.46f),
                    width = 240f,
                    height = 1_800f,
                ),
            ),
            viewportWidth = 1_080f,
            viewportHeight = 2_340f,
            edgePadding = 16f,
            topReserved = 136f,
            bottomReserved = 420f,
            collisionGap = 12f,
        )

        assertTrue(placements.isEmpty())
    }

    @Test(timeout = 2_000)
    fun denseFullLensInputUsesBoundedFallbackWithoutOverlap() {
        val placements = LensOverlayPlacement.calculateAll(
            specs = (0 until 40).map { index ->
                OverlayLabelSpec(
                    id = index,
                    bounds = NormalizedBounds(0f, 0f, 1f, 1f),
                    width = 8f,
                    height = 8f,
                )
            },
            viewportWidth = 1_080f,
            viewportHeight = 2_340f,
            edgePadding = 4f,
            topReserved = 136f,
            bottomReserved = 420f,
            collisionGap = 4f,
        )

        assertEquals(40, placements.size)
        placements.values.toList().forEachIndexed { index, placement ->
            placements.values.drop(index + 1).forEach { other ->
                assertTrue(!placement.overlapsWithGap(other, gap = 4f))
            }
        }
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

    private fun OverlayPlacement.overlapsWithGap(other: OverlayPlacement, gap: Float): Boolean =
        x < other.x + other.width + gap &&
            x + width > other.x - gap &&
            y < other.y + other.height + gap &&
            y + height > other.y - gap
}
