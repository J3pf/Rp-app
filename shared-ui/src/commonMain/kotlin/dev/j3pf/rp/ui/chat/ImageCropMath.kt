package dev.j3pf.rp.ui.chat

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Normalized crop region in `[0f, 1f]` image coordinates. */
data class NormalizedCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun isFullImage(epsilon: Float = 0.002f): Boolean =
        left <= epsilon &&
            top <= epsilon &&
            right >= 1f - epsilon &&
            bottom >= 1f - epsilon

    fun toPixelRect(
        imageWidth: Int,
        imageHeight: Int,
    ): PixelCropRect {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive" }
        val pxLeft = (left.coerceIn(0f, 1f) * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        val pxTop = (top.coerceIn(0f, 1f) * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        val pxRight = (right.coerceIn(0f, 1f) * imageWidth).roundToInt().coerceIn(pxLeft + 1, imageWidth)
        val pxBottom = (bottom.coerceIn(0f, 1f) * imageHeight).roundToInt().coerceIn(pxTop + 1, imageHeight)
        return PixelCropRect(
            left = pxLeft,
            top = pxTop,
            width = pxRight - pxLeft,
            height = pxBottom - pxTop,
        )
    }

    companion object {
        val FULL = NormalizedCropRect(0f, 0f, 1f, 1f)
    }
}

/** Concrete pixel bounds for bitmap cropping. */
data class PixelCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** Pixel-space rectangle inside a viewport canvas. */
data class DisplayRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/** Active drag handle on the crop overlay. */
enum class CropHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    MOVE,
}

/** Compute the centered `ContentScale.Fit` rectangle of an image inside a viewport. */
fun computeFitRect(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
): DisplayRect {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return DisplayRect(0f, 0f, 0f, 0f)
    }
    val scale = min(containerWidth / imageWidth.toFloat(), containerHeight / imageHeight.toFloat())
    val fittedW = imageWidth * scale
    val fittedH = imageHeight * scale
    return DisplayRect(
        left = (containerWidth - fittedW) / 2f,
        top = (containerHeight - fittedH) / 2f,
        width = fittedW,
        height = fittedH,
    )
}

/** Hit-test touch coordinates against corners (highest priority), edges, or interior move area. */
fun hitTestCropHandle(
    touchX: Float,
    touchY: Float,
    imageRect: DisplayRect,
    cropRect: NormalizedCropRect,
    handleRadiusPx: Float,
): CropHandle? {
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    val cropLeft = imageRect.left + cropRect.left * imageRect.width
    val cropTop = imageRect.top + cropRect.top * imageRect.height
    val cropRight = imageRect.left + cropRect.right * imageRect.width
    val cropBottom = imageRect.top + cropRect.bottom * imageRect.height

    val corners =
        listOf(
            CropHandle.TOP_LEFT to hypot((touchX - cropLeft).toDouble(), (touchY - cropTop).toDouble()),
            CropHandle.TOP_RIGHT to hypot((touchX - cropRight).toDouble(), (touchY - cropTop).toDouble()),
            CropHandle.BOTTOM_LEFT to hypot((touchX - cropLeft).toDouble(), (touchY - cropBottom).toDouble()),
            CropHandle.BOTTOM_RIGHT to hypot((touchX - cropRight).toDouble(), (touchY - cropBottom).toDouble()),
        )
    val nearestCorner = corners.minByOrNull { it.second }
    if (nearestCorner != null && nearestCorner.second <= handleRadiusPx.toDouble()) {
        return nearestCorner.first
    }

    val withinHorizontal = touchX >= cropLeft - handleRadiusPx && touchX <= cropRight + handleRadiusPx
    val withinVertical = touchY >= cropTop - handleRadiusPx && touchY <= cropBottom + handleRadiusPx

    val edgeCandidates = mutableListOf<Pair<CropHandle, Float>>()
    if (withinHorizontal && abs(touchY - cropTop) <= handleRadiusPx) {
        edgeCandidates += CropHandle.TOP to abs(touchY - cropTop)
    }
    if (withinHorizontal && abs(touchY - cropBottom) <= handleRadiusPx) {
        edgeCandidates += CropHandle.BOTTOM to abs(touchY - cropBottom)
    }
    if (withinVertical && abs(touchX - cropLeft) <= handleRadiusPx) {
        edgeCandidates += CropHandle.LEFT to abs(touchX - cropLeft)
    }
    if (withinVertical && abs(touchX - cropRight) <= handleRadiusPx) {
        edgeCandidates += CropHandle.RIGHT to abs(touchX - cropRight)
    }
    val nearestEdge = edgeCandidates.minByOrNull { it.second }
    if (nearestEdge != null) {
        return nearestEdge.first
    }

    if (touchX in cropLeft..cropRight && touchY in cropTop..cropBottom) {
        return CropHandle.MOVE
    }
    return null
}

/** Apply a normalized delta to [rect] for [handle], enforcing [minSize] and `[0f, 1f]` bounds. */
fun applyCropDrag(
    rect: NormalizedCropRect,
    handle: CropHandle,
    dxNorm: Float,
    dyNorm: Float,
    minSize: Float = 0.08f,
): NormalizedCropRect {
    val safeMin = minSize.coerceIn(0.02f, 0.5f)
    return when (handle) {
        CropHandle.MOVE -> {
            val w = rect.width.coerceIn(safeMin, 1f)
            val h = rect.height.coerceIn(safeMin, 1f)
            val newLeft = (rect.left + dxNorm).coerceIn(0f, max(0f, 1f - w))
            val newTop = (rect.top + dyNorm).coerceIn(0f, max(0f, 1f - h))
            NormalizedCropRect(newLeft, newTop, newLeft + w, newTop + h)
        }

        CropHandle.TOP_LEFT -> {
            val newLeft = (rect.left + dxNorm).coerceIn(0f, rect.right - safeMin)
            val newTop = (rect.top + dyNorm).coerceIn(0f, rect.bottom - safeMin)
            rect.copy(left = newLeft, top = newTop)
        }

        CropHandle.TOP_RIGHT -> {
            val newRight = (rect.right + dxNorm).coerceIn(rect.left + safeMin, 1f)
            val newTop = (rect.top + dyNorm).coerceIn(0f, rect.bottom - safeMin)
            rect.copy(right = newRight, top = newTop)
        }

        CropHandle.BOTTOM_LEFT -> {
            val newLeft = (rect.left + dxNorm).coerceIn(0f, rect.right - safeMin)
            val newBottom = (rect.bottom + dyNorm).coerceIn(rect.top + safeMin, 1f)
            rect.copy(left = newLeft, bottom = newBottom)
        }

        CropHandle.BOTTOM_RIGHT -> {
            val newRight = (rect.right + dxNorm).coerceIn(rect.left + safeMin, 1f)
            val newBottom = (rect.bottom + dyNorm).coerceIn(rect.top + safeMin, 1f)
            rect.copy(right = newRight, bottom = newBottom)
        }

        CropHandle.TOP -> {
            val newTop = (rect.top + dyNorm).coerceIn(0f, rect.bottom - safeMin)
            rect.copy(top = newTop)
        }

        CropHandle.BOTTOM -> {
            val newBottom = (rect.bottom + dyNorm).coerceIn(rect.top + safeMin, 1f)
            rect.copy(bottom = newBottom)
        }

        CropHandle.LEFT -> {
            val newLeft = (rect.left + dxNorm).coerceIn(0f, rect.right - safeMin)
            rect.copy(left = newLeft)
        }

        CropHandle.RIGHT -> {
            val newRight = (rect.right + dxNorm).coerceIn(rect.left + safeMin, 1f)
            rect.copy(right = newRight)
        }
    }
}
