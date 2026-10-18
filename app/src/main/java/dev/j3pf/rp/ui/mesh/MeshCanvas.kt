package dev.j3pf.rp.ui.mesh

import android.graphics.DashPathEffect
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withTranslation
import dev.j3pf.rp.ui.mesh.layout.LayoutBounds
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult
import dev.j3pf.rp.ui.mesh.layout.PositionedEdge
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

/**
 * Renders the agent mesh topology strictly within its viewport (Specs S2, S3).
 * Below the header/filters, clipped to bounds, with auto-fit zoom, pan/zoom gestures,
 * interactive Agent Cards, Bezier edge connectors, and real-time comm particles.
 */
@Composable
fun MeshCanvas(
    layout: MeshLayoutResult?,
    modifier: Modifier = Modifier,
    activeBubbles: List<LiveCommBubble> = emptyList(),
    onNodeClick: (String) -> Unit,
    onEdgeClick: (String) -> Unit,
    onViewportSizeChanged: (Float, Float) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val localReducedMotion = LocalReducedMotion.current
    val systemReducedMotion =
        remember(context) {
            runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
                .getOrDefault(false)
        }
    val reducedMotion = localReducedMotion || systemReducedMotion
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val frameTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cardHeights by remember(layout?.nodes?.keys?.toList()) { mutableStateOf(emptyMap<String, Float>()) }
    val paint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
    val textPaint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG) }
    val path = remember { AndroidPath() }

    val nowMs = System.currentTimeMillis()
    val activeEdgeExpiresAt =
        layout
            ?.edges
            .orEmpty()
            .mapNotNull { edge ->
                edge.edge.lastAt
                    ?.plus(ACTIVE_EDGE_MS + 1)
                    ?.takeIf { it > nowMs }
            }.minOrNull()
    val hasMovingContent =
        !reducedMotion &&
            (
                layout?.edges?.any { it.edge.pendingAsk } == true ||
                    activeBubbles.any { it.expiresAtMs > nowMs }
            )

    LaunchedEffect(hasMovingContent, activeEdgeExpiresAt) {
        frameTime.longValue = System.currentTimeMillis()
        if (hasMovingContent) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos {
                    frameTime.longValue = System.currentTimeMillis()
                }
            }
        } else if (activeEdgeExpiresAt != null) {
            delay((activeEdgeExpiresAt - System.currentTimeMillis()).coerceAtLeast(1L))
            frameTime.longValue = System.currentTimeMillis()
        }
    }

    val dashIntervals = remember { floatArrayOf(6f, 6f) }
    val bubblesByEdge =
        remember(activeBubbles, layout) {
            val nodes = layout?.nodes.orEmpty()
            activeBubbles
                .filter { bubble ->
                    val from = bubble.comm.fromAgentId
                    val to = bubble.comm.toAgentId
                    from != null && to != null && from in nodes && to in nodes
                }.groupBy { bubble ->
                    val a = bubble.comm.fromAgentId ?: ""
                    val b = bubble.comm.toAgentId ?: ""
                    if (a < b) "$a|$b" else "$b|$a"
                }
        }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val widthDp = widthPx / density.density
        val heightDp = heightPx / density.density
        val currentLayout =
            remember(layout, widthDp, heightDp, cardHeights) {
                layout?.let { inputLayout ->
                    MeshLayoutEngine().computeLayout(
                        nodes = inputLayout.nodes.values.map { it.node },
                        edges = inputLayout.edges.map { it.edge },
                        width = widthDp,
                        height = heightDp,
                        previousLayout = inputLayout,
                        cardHeights = cardHeights,
                    )
                }
            }

        LaunchedEffect(widthDp, heightDp) {
            onViewportSizeChanged(widthDp, heightDp)
        }

        val bounds = currentLayout?.bounds ?: LayoutBounds(0f, 0f, 0f, 0f)

        // Fit-to-content scaling in DP space
        val padding = 20f
        val contentWidth = maxOf(bounds.width + 2f * padding, 100f)
        val contentHeight = maxOf(bounds.height + 2f * padding, 100f)
        val widthFitScale = if (bounds.width > 0f) widthDp / contentWidth else 1f
        val heightFitScale = if (bounds.height > 0f) heightDp / contentHeight else 1f
        val naturalFitScale = minOf(widthFitScale, heightFitScale)
        val isLandscape = widthDp > heightDp && widthDp >= 600f
        val autoFitScale = if (isLandscape) 1f else minOf(1f, naturalFitScale).coerceAtLeast(0.65f)
        val effectiveZoom = zoom * autoFitScale
        val topPaddingDp = 16f
        val contentOffsetX = meshContentOffsetX(bounds, widthDp, effectiveZoom)
        val contentOffsetY = meshContentOffsetY(bounds, heightDp, effectiveZoom, topPaddingDp)

        // Canvas for connectors, active glow, dashed lines, and comm particles/bubbles
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(widthDp, heightDp, bounds, autoFitScale) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val oldZoom = zoom
                        val newZoom = (oldZoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val oldEffectiveZoom = oldZoom * autoFitScale
                        val newEffectiveZoom = newZoom * autoFitScale
                        val zoomRatio = newEffectiveZoom / oldEffectiveZoom
                        val panDpX = pan.x / density.density
                        val panDpY = pan.y / density.density
                        val centroidDpX = centroid.x / density.density
                        val centroidDpY = centroid.y / density.density
                        val oldOffsetX = meshContentOffsetX(bounds, widthDp, oldEffectiveZoom)
                        val oldOffsetY = meshContentOffsetY(bounds, heightDp, oldEffectiveZoom, topPaddingDp)
                        val newOffsetX = meshContentOffsetX(bounds, widthDp, newEffectiveZoom)
                        val newOffsetY = meshContentOffsetY(bounds, heightDp, newEffectiveZoom, topPaddingDp)
                        panX = centroidDpX + panDpX - newOffsetX - (centroidDpX - oldOffsetX - panX) * zoomRatio
                        panY = centroidDpY + panDpY - newOffsetY - (centroidDpY - oldOffsetY - panY) * zoomRatio
                        zoom = newZoom
                    }
                }.pointerInput(layout, widthDp, heightDp, bounds, autoFitScale) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        },
                        onTap = { tap ->
                            val activeLayout = layout ?: return@detectTapGestures
                            val tapDpX = tap.x / density.density
                            val tapDpY = tap.y / density.density
                            val localTap =
                                Offset(
                                    x = (tapDpX - contentOffsetX - panX) / effectiveZoom,
                                    y = (tapDpY - contentOffsetY - panY) / effectiveZoom,
                                )
                            // Check if tap hit any node card
                            val hitNode =
                                activeLayout.nodes.values.firstOrNull { node ->
                                    localTap.x >= node.left &&
                                        localTap.x <= node.right &&
                                        localTap.y >= node.top &&
                                        localTap.y <= node.bottom
                                }
                            if (hitNode != null) {
                                onNodeClick(hitNode.node.agentId)
                                return@detectTapGestures
                            }
                            // Check edge tap
                            val hitEdge =
                                activeLayout.edges.firstOrNull { edge ->
                                    distanceToCurve(localTap, edge) <= 20f
                                }
                            hitEdge?.let { onEdgeClick(it.edge.edgeId) }
                        },
                    )
                },
        ) {
            val activeLayout = currentLayout ?: return@Canvas
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val currentTimeMs = frameTime.longValue

            nativeCanvas.withTranslation(
                (contentOffsetX + panX) * density.density,
                (contentOffsetY + panY) * density.density,
            ) {
                nativeCanvas.scale(
                    effectiveZoom * density.density,
                    effectiveZoom * density.density,
                )

                // Draw edge curves in DP space
                activeLayout.edges.forEach { positioned ->
                    val edge = positioned.edge
                    path.reset()
                    if (positioned.routePoints.size >= 2) {
                        val firstPoint = positioned.routePoints.first()
                        path.moveTo(firstPoint.x, firstPoint.y)
                        for (index in 1 until positioned.routePoints.size) {
                            val point = positioned.routePoints[index]
                            path.lineTo(point.x, point.y)
                        }
                    } else {
                        path.moveTo(positioned.fromX, positioned.fromY)
                        path.quadTo(
                            positioned.controlX,
                            positioned.controlY,
                            positioned.toX,
                            positioned.toY,
                        )
                    }
                    val age = edge.lastAt?.let { currentTimeMs - it }
                    val active = age?.let { it in 0..ACTIVE_EDGE_MS } ?: edge.active
                    val edgeTint = if (edge.pendingAsk) scheme.tertiary else scheme.outlineVariant

                    if (active) {
                        paint.reset()
                        paint.isAntiAlias = true
                        paint.style = AndroidPaint.Style.STROKE
                        paint.strokeWidth = 3.5f
                        paint.strokeCap = AndroidPaint.Cap.ROUND
                        paint.color = scheme.primary.copy(alpha = 0.28f).toArgb()
                        nativeCanvas.drawPath(path, paint)
                    }

                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = AndroidPaint.Style.STROKE
                    paint.strokeWidth = if (active) 2f else 1.2f
                    paint.strokeCap = AndroidPaint.Cap.ROUND
                    val edgePulse = if (reducedMotion) 0f else pulse(currentTimeMs)
                    paint.color = edgeTint.copy(alpha = if (edge.pendingAsk) 0.55f + 0.4f * edgePulse else 0.75f).toArgb()
                    if (edge.pendingAsk) {
                        val phase = if (reducedMotion) 0f else (currentTimeMs / 55f % 30f)
                        paint.pathEffect = DashPathEffect(dashIntervals, phase)
                    }
                    nativeCanvas.drawPath(path, paint)
                    paint.pathEffect = null
                }

                // In-flight comms and speech bubbles
                bubblesByEdge.values.forEach { edgeBubbles ->
                    val visibleBubbles = edgeBubbles.sortedByDescending { it.expiresAtMs }.take(1)
                    val firstComm = visibleBubbles.first().comm
                    val firstSender = firstComm.fromAgentId?.let(activeLayout.nodes::get) ?: return@forEach
                    val firstReceiver = firstComm.toAgentId?.let(activeLayout.nodes::get) ?: return@forEach
                    val firstEdge =
                        activeLayout.edges.firstOrNull {
                            (it.edge.a == firstComm.fromAgentId && it.edge.b == firstComm.toAgentId) ||
                                (it.edge.a == firstComm.toAgentId && it.edge.b == firstComm.fromAgentId)
                        }

                    val edgeFromX = firstEdge?.fromX ?: firstSender.x
                    val edgeFromY = firstEdge?.fromY ?: firstSender.bottom
                    val edgeToX = firstEdge?.toX ?: firstReceiver.x
                    val edgeToY = firstEdge?.toY ?: firstReceiver.top
                    val midpoint =
                        firstEdge?.let { pointAlongEdge(it, 0.5f) }
                            ?: Offset((edgeFromX + edgeToX) / 2f, (edgeFromY + edgeToY) / 2f)
                    val midpointX = midpoint.x
                    val safeMidpointY = safeCommBubbleCenterY(midpoint.y, firstSender.bottom, firstReceiver.top)

                    visibleBubbles.forEach bubbleLoop@{ bubble ->
                        val comm = bubble.comm
                        val sender = comm.fromAgentId?.let(activeLayout.nodes::get) ?: return@bubbleLoop
                        val receiver = comm.toAgentId?.let(activeLayout.nodes::get) ?: return@bubbleLoop
                        val startAt = bubble.expiresAtMs - BUBBLE_LIFETIME_MS
                        val progress = ((currentTimeMs - startAt).toFloat() / BUBBLE_LIFETIME_MS).coerceIn(0f, 1f)

                        // Travelling comm particle
                        if (!reducedMotion) {
                            val point =
                                firstEdge?.let { pointAlongEdge(it, progress) }
                                    ?: Offset(
                                        edgeFromX + (edgeToX - edgeFromX) * progress,
                                        edgeFromY + (edgeToY - edgeFromY) * progress,
                                    )
                            paint.reset()
                            paint.isAntiAlias = true
                            paint.color = scheme.primary.copy(alpha = 0.25f).toArgb()
                            nativeCanvas.drawCircle(point.x, point.y, 5f, paint)
                            paint.color = scheme.primary.toArgb()
                            nativeCanvas.drawCircle(point.x, point.y, 2.5f, paint)
                        }

                        // Speech bubble with message preview positioned in the clear connector gap
                        drawSpeechBubble(
                            nativeCanvas,
                            midpointX,
                            safeMidpointY,
                            formatMeshCommText(comm.text),
                            paint,
                            textPaint,
                            scheme.surfaceVariant,
                            scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Render interactive Agent Cards in DP space with full legibility
        currentLayout?.nodes?.values?.forEach { positioned ->
            val node = positioned.node
            val cardLeftDp = contentOffsetX + panX + positioned.left * effectiveZoom
            val cardTopDp = contentOffsetY + panY + positioned.top * effectiveZoom
            MeshAgentCard(
                node = node,
                modifier =
                    Modifier
                        .offset(cardLeftDp.dp, cardTopDp.dp)
                        .width(positioned.width.dp)
                        .wrapContentHeight()
                        .onSizeChanged { size ->
                            val measuredHeight = size.height / density.density
                            if (cardHeights[node.agentId] != measuredHeight) {
                                cardHeights = cardHeights + (node.agentId to measuredHeight)
                            }
                        }.graphicsLayer {
                            scaleX = effectiveZoom
                            scaleY = effectiveZoom
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                allNodes = currentLayout.nodes.values.map { it.node },
                onClick = { onNodeClick(node.agentId) },
                reducedMotion = reducedMotion,
            )
        }
    }
}

private fun meshContentOffsetX(
    bounds: LayoutBounds,
    widthDp: Float,
    zoom: Float,
): Float = (widthDp - bounds.width * zoom) / 2f - bounds.minX * zoom

private fun meshContentOffsetY(
    bounds: LayoutBounds,
    heightDp: Float,
    zoom: Float,
    topPaddingDp: Float,
): Float =
    if (bounds.height * zoom > heightDp - topPaddingDp) {
        topPaddingDp - bounds.minY * zoom
    } else {
        (heightDp - bounds.height * zoom) / 2f - bounds.minY * zoom
    }

internal fun safeCommBubbleCenterY(
    midpointY: Float,
    senderBottom: Float,
    receiverTop: Float,
): Float {
    val minimumY = senderBottom + 24f
    val maximumY = receiverTop - 24f
    return if (minimumY <= maximumY) midpointY.coerceIn(minimumY, maximumY) else maxOf(midpointY, minimumY)
}

private fun pointAlongEdge(
    edge: PositionedEdge,
    progress: Float,
): Offset {
    val route = edge.routePoints
    if (route.size < 2) {
        val inverse = 1f - progress
        return Offset(
            inverse * inverse * edge.fromX + 2f * inverse * progress * edge.controlX + progress * progress * edge.toX,
            inverse * inverse * edge.fromY + 2f * inverse * progress * edge.controlY + progress * progress * edge.toY,
        )
    }

    var totalLength = 0f
    for (index in 0 until route.lastIndex) {
        val from = route[index]
        val to = route[index + 1]
        totalLength += hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()).toFloat()
    }
    var remaining = progress.coerceIn(0f, 1f) * totalLength
    for (index in 0 until route.lastIndex) {
        val from = route[index]
        val to = route[index + 1]
        val segmentLength = hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()).toFloat()
        if (remaining <= segmentLength || segmentLength == 0f) {
            val fraction = if (segmentLength == 0f) 0f else remaining / segmentLength
            return Offset(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction)
        }
        remaining -= segmentLength
    }
    val last = route.last()
    return Offset(last.x, last.y)
}

private fun drawSpeechBubble(
    canvas: android.graphics.Canvas,
    centerX: Float,
    centerY: Float,
    text: String,
    paint: AndroidPaint,
    textPaint: AndroidPaint,
    background: Color,
    foreground: Color,
) {
    textPaint.reset()
    textPaint.isAntiAlias = true
    textPaint.textSize = 10f
    textPaint.textAlign = AndroidPaint.Align.CENTER
    textPaint.color = foreground.toArgb()
    val display = if (text.length > 25) text.take(24) + "…" else text
    val width = textPaint.measureText(display) + 16f
    paint.reset()
    paint.isAntiAlias = true
    paint.color = background.toArgb()
    canvas.drawRoundRect(centerX - width / 2f, centerY - 9f, centerX + width / 2f, centerY + 9f, 9f, 9f, paint)
    canvas.drawText(display, centerX, centerY + 3.5f, textPaint)
}

private fun pulse(nowMs: Long): Float = 0.5f + 0.5f * sin(nowMs / 700f * (2f * PI.toFloat()))

private fun distanceToCurve(
    point: Offset,
    edge: PositionedEdge,
): Float {
    if (edge.routePoints.size >= 2) {
        var best = Float.POSITIVE_INFINITY
        for (index in 0 until edge.routePoints.lastIndex) {
            val from = edge.routePoints[index]
            val to = edge.routePoints[index + 1]
            best = minOf(best, distanceToSegment(point.x, point.y, from.x, from.y, to.x, to.y))
        }
        return best
    }
    var best = Float.POSITIVE_INFINITY
    var previousX = edge.fromX
    var previousY = edge.fromY
    for (step in 1..24) {
        val t = step / 24f
        val inverse = 1f - t
        val x = inverse * inverse * edge.fromX + 2f * inverse * t * edge.controlX + t * t * edge.toX
        val y = inverse * inverse * edge.fromY + 2f * inverse * t * edge.controlY + t * t * edge.toY
        best = minOf(best, distanceToSegment(point.x, point.y, previousX, previousY, x, y))
        previousX = x
        previousY = y
    }
    return best
}

private fun distanceToSegment(
    px: Float,
    py: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((px - x1) * dx + (py - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3f
private const val ACTIVE_EDGE_MS = 20_000L
private const val BUBBLE_LIFETIME_MS = 6_000L
