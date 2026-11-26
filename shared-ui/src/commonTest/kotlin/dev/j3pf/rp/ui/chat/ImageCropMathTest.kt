package dev.j3pf.rp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageCropMathTest {
    @Test
    fun `computeFitRect centers landscape and portrait images proportionally`() {
        val landscape = computeFitRect(containerWidth = 400f, containerHeight = 800f, imageWidth = 1000, imageHeight = 500)
        assertEquals(0f, landscape.left, 0.01f)
        assertEquals(300f, landscape.top, 0.01f)
        assertEquals(400f, landscape.width, 0.01f)
        assertEquals(200f, landscape.height, 0.01f)

        val portrait = computeFitRect(containerWidth = 800f, containerHeight = 400f, imageWidth = 500, imageHeight = 1000)
        assertEquals(300f, portrait.left, 0.01f)
        assertEquals(0f, portrait.top, 0.01f)
        assertEquals(200f, portrait.width, 0.01f)
        assertEquals(400f, portrait.height, 0.01f)

        val zero = computeFitRect(0f, 400f, 100, 100)
        assertEquals(0f, zero.width)
    }

    @Test
    fun `toPixelRect converts normalized bounds and clamps safely`() {
        assertTrue(NormalizedCropRect.FULL.isFullImage())
        val half = NormalizedCropRect(0.25f, 0.1f, 0.75f, 0.9f)
        assertFalse(half.isFullImage())
        assertEquals(
            PixelCropRect(left = 250, top = 100, width = 500, height = 800),
            half.toPixelRect(1000, 1000),
        )

        // Degenerate zero-width normalized rect still produces at least 1x1 pixel inside bounds
        val tiny = NormalizedCropRect(1f, 1f, 1f, 1f).toPixelRect(100, 100)
        assertEquals(PixelCropRect(left = 99, top = 99, width = 1, height = 1), tiny)
    }

    @Test
    fun `hitTestCropHandle prioritizes corners over edges over move`() {
        val imgRect = DisplayRect(left = 100f, top = 200f, width = 400f, height = 400f)
        val crop = NormalizedCropRect(0.25f, 0.25f, 0.75f, 0.75f)
        // Crop box in canvas coords: left=200, top=300, right=400, bottom=500

        assertEquals(CropHandle.TOP_LEFT, hitTestCropHandle(205f, 305f, imgRect, crop, 24f))
        assertEquals(CropHandle.BOTTOM_RIGHT, hitTestCropHandle(398f, 496f, imgRect, crop, 24f))
        assertEquals(CropHandle.TOP, hitTestCropHandle(300f, 304f, imgRect, crop, 24f))
        assertEquals(CropHandle.LEFT, hitTestCropHandle(202f, 400f, imgRect, crop, 24f))
        assertEquals(CropHandle.MOVE, hitTestCropHandle(300f, 400f, imgRect, crop, 24f))
        assertNull(hitTestCropHandle(50f, 50f, imgRect, crop, 24f))
    }

    @Test
    fun `applyCropDrag enforces minSize and unit bounds for corners edges and move`() {
        val start = NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.8f)

        // Dragging top-left past bottom-right stops at minSize
        val clampedTl = applyCropDrag(start, CropHandle.TOP_LEFT, dxNorm = 0.9f, dyNorm = 0.9f, minSize = 0.1f)
        assertEquals(0.7f, clampedTl.left, 0.001f)
        assertEquals(0.7f, clampedTl.top, 0.001f)
        assertEquals(0.8f, clampedTl.right, 0.001f)
        assertEquals(0.8f, clampedTl.bottom, 0.001f)

        // Dragging move keeps size intact and clamps at [0, 1]
        val moved = applyCropDrag(start, CropHandle.MOVE, dxNorm = 0.5f, dyNorm = -0.5f, minSize = 0.1f)
        assertEquals(0.4f, moved.left, 0.001f)
        assertEquals(0f, moved.top, 0.001f)
        assertEquals(1f, moved.right, 0.001f)
        assertEquals(0.6f, moved.bottom, 0.001f)

        // Single-edge drags
        val shrunkBottom = applyCropDrag(start, CropHandle.BOTTOM, dxNorm = 0.2f, dyNorm = -0.2f, minSize = 0.1f)
        assertEquals(0.6f, shrunkBottom.bottom, 0.001f)
        assertEquals(0.2f, shrunkBottom.left, 0.001f)
    }

    @Test
    fun `normalizeRotationDegrees and ImageEditSpec handle quarter turns`() {
        assertEquals(0, normalizeRotationDegrees(0))
        assertEquals(90, normalizeRotationDegrees(90))
        assertEquals(270, normalizeRotationDegrees(-90))
        assertEquals(0, normalizeRotationDegrees(360))
        assertEquals(180, normalizeRotationDegrees(540))

        assertTrue(ImageEditSpec().isNoOp)
        assertTrue(ImageEditSpec(cropRect = NormalizedCropRect.FULL, rotationDegrees = 360).isNoOp)
        assertFalse(ImageEditSpec(rotationDegrees = 90).isNoOp)
        assertFalse(ImageEditSpec(cropRect = NormalizedCropRect(0.1f, 0.1f, 0.9f, 0.9f)).isNoOp)
    }
}
