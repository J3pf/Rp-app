package dev.j3pf.rp.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desvanecimiento sutil derivado del tema en bordes horizontales para indicar scroll.
 */
fun Modifier.horizontalEdgeFade(
    scrollState: ScrollState? = null,
    edgeColor: Color = Color.Unspecified,
    length: Dp = 24.dp,
): Modifier =
    composed {
        val resolvedColor = if (edgeColor.isSpecified) edgeColor else MaterialTheme.colorScheme.background
        val density = LocalDensity.current
        val lengthPx = with(density) { length.toPx() }

        drawWithContent {
            drawContent()
            val canBackward = scrollState?.canScrollBackward ?: true
            val canForward = scrollState?.canScrollForward ?: true

            if (canBackward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor, resolvedColor.copy(alpha = 0f)),
                            startX = 0f,
                            endX = lengthPx,
                        ),
                    size = size.copy(width = lengthPx),
                )
            }
            if (canForward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor.copy(alpha = 0f), resolvedColor),
                            startX = size.width - lengthPx,
                            endX = size.width,
                        ),
                    topLeft = Offset(size.width - lengthPx, 0f),
                    size = size.copy(width = lengthPx),
                )
            }
        }
    }

fun Modifier.horizontalEdgeFade(
    lazyListState: LazyListState,
    edgeColor: Color = Color.Unspecified,
    length: Dp = 24.dp,
): Modifier =
    composed {
        val resolvedColor = if (edgeColor.isSpecified) edgeColor else MaterialTheme.colorScheme.background
        val density = LocalDensity.current
        val lengthPx = with(density) { length.toPx() }

        drawWithContent {
            drawContent()
            val canBackward = lazyListState.canScrollBackward
            val canForward = lazyListState.canScrollForward

            if (canBackward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor, resolvedColor.copy(alpha = 0f)),
                            startX = 0f,
                            endX = lengthPx,
                        ),
                    size = size.copy(width = lengthPx),
                )
            }
            if (canForward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor.copy(alpha = 0f), resolvedColor),
                            startX = size.width - lengthPx,
                            endX = size.width,
                        ),
                    topLeft = Offset(size.width - lengthPx, 0f),
                    size = size.copy(width = lengthPx),
                )
            }
        }
    }
