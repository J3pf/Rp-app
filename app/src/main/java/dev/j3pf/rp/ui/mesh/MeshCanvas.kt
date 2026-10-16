package dev.j3pf.rp.ui.mesh

import android.graphics.DashPathEffect
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult
import dev.j3pf.rp.ui.mesh.layout.PositionedEdge
import dev.j3pf.rp.ui.mesh.layout.PositionedNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

/** Draws the complete topology in one Canvas; semantics overlays represent nodes, never particles. */
@Composable
fun MeshCanvas(
    layout: MeshLayoutResult?,
    activeBubbles: List<LiveCommBubble> = emptyList(),
    onNodeClick: (String) -> Unit,
    onEdgeClick: (String) -> Unit,
    onViewportSizeChanged: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val reducedMotion =
        remember(context) {
            runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
                .getOrDefault(false)
        }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val frameTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
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
        !reducedMotion && (
            layout?.nodes?.values?.any { it.node.status == "working" || it.satellites.isNotEmpty() } == true ||
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

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { onViewportSizeChanged(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(widthPx, heightPx) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val oldZoom = zoom
                        val newZoom = (oldZoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val zoomRatio = newZoom / oldZoom
                        panX = centroid.x - widthPx / 2f - (centroid.x - widthPx / 2f - panX) * zoomRatio + pan.x
                        panY = centroid.y - heightPx / 2f - (centroid.y - heightPx / 2f - panY) * zoomRatio + pan.y
                        zoom = newZoom
                    }
                }.pointerInput(layout, widthPx, heightPx) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        },
                        onTap = { tap ->
                            val currentLayout = layout ?: return@detectTapGestures
                            val localTap =
                                Offset(
                                    x = (tap.x - widthPx / 2f - panX) / zoom + widthPx / 2f,
                                    y = (tap.y - heightPx / 2f - panY) / zoom + heightPx / 2f,
                                )
                            val hitNode =
                                currentLayout.nodes.values
                                    .sortedByDescending { it.radius }
                                    .firstOrNull { node ->
                                        hypot(localTap.x - node.x, localTap.y - node.y) <= node.radius + 14f
                                    }
                            if (hitNode != null) {
                                onNodeClick(hitNode.node.agentId)
                                return@detectTapGestures
                            }
                            val hitEdge =
                                currentLayout.edges.firstOrNull { edge ->
                                    distanceToCurve(localTap, edge) <= 16f
                                }
                            hitEdge?.let { onEdgeClick(it.edge.edgeId) }
                        },
                    )
                },
        ) {
            val currentLayout = layout ?: return@Canvas
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val currentTimeMs = frameTime.longValue
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            nativeCanvas.save()
            nativeCanvas.translate(centerX + panX, centerY + panY)
            nativeCanvas.scale(zoom, zoom)

            currentLayout.edges.forEach { positioned ->
                val edge = positioned.edge
                path.reset()
                path.moveTo(positioned.fromX - centerX, positioned.fromY - centerY)
                path.quadTo(
                    positioned.controlX - centerX,
                    positioned.controlY - centerY,
                    positioned.toX - centerX,
                    positioned.toY - centerY,
                )
                val age = edge.lastAt?.let { currentTimeMs - it }
                val active = age?.let { it in 0..ACTIVE_EDGE_MS } ?: edge.active
                val edgeTint = if (edge.pendingAsk) scheme.tertiary else scheme.outlineVariant
                if (active) {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = AndroidPaint.Style.STROKE
                    paint.strokeWidth = 9f
                    paint.strokeCap = AndroidPaint.Cap.ROUND
                    paint.color = scheme.primary.copy(alpha = 0.24f).toArgb()
                    nativeCanvas.drawPath(path, paint)
                }
                paint.reset()
                paint.isAntiAlias = true
                paint.style = AndroidPaint.Style.STROKE
                paint.strokeWidth = if (active) 3.5f else 2f
                paint.strokeCap = AndroidPaint.Cap.ROUND
                val edgePulse = if (reducedMotion) 0f else pulse(currentTimeMs)
                paint.color = edgeTint.copy(alpha = if (edge.pendingAsk) 0.55f + 0.4f * edgePulse else 0.8f).toArgb()
                if (edge.pendingAsk) {
                    val phase = if (reducedMotion) 0f else (currentTimeMs / 55f % 30f)
                    paint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), phase)
                }
                nativeCanvas.drawPath(path, paint)
                paint.pathEffect = null
            }

            val bubblesByEdge =
                activeBubbles
                    .asSequence()
                    .filter { it.expiresAtMs > currentTimeMs }
                    .filter { bubble ->
                        val from = bubble.comm.fromAgentId
                        val to = bubble.comm.toAgentId
                        from != null && to != null && from in currentLayout.nodes && to in currentLayout.nodes
                    }.groupBy { bubble ->
                        val a = bubble.comm.fromAgentId ?: ""
                        val b = bubble.comm.toAgentId ?: ""
                        val edgeKey = if (a < b) "$a|$b" else "$b|$a"
                        edgeKey
                    }

            bubblesByEdge.values.forEach { edgeBubbles ->
                val visibleBubbles = edgeBubbles.sortedByDescending { it.expiresAtMs }.take(2)
                val firstComm = visibleBubbles.first().comm
                val firstSender = firstComm.fromAgentId?.let(currentLayout.nodes::get) ?: return@forEach
                val firstReceiver = firstComm.toAgentId?.let(currentLayout.nodes::get) ?: return@forEach
                val firstEdge =
                    currentLayout.edges.firstOrNull {
                        (it.edge.a == firstComm.fromAgentId && it.edge.b == firstComm.toAgentId) ||
                            (it.edge.a == firstComm.toAgentId && it.edge.b == firstComm.fromAgentId)
                    }
                val controlX = firstEdge?.controlX ?: (firstSender.x + firstReceiver.x) / 2f
                val controlY = firstEdge?.controlY ?: (firstSender.y + firstReceiver.y) / 2f
                val midpointX = 0.25f * firstSender.x + 0.5f * controlX + 0.25f * firstReceiver.x
                val midpointY = 0.25f * firstSender.y + 0.5f * controlY + 0.25f * firstReceiver.y

                visibleBubbles.forEachIndexed { index, bubble ->
                    val comm = bubble.comm
                    val sender = comm.fromAgentId?.let(currentLayout.nodes::get) ?: return@forEachIndexed
                    val receiver = comm.toAgentId?.let(currentLayout.nodes::get) ?: return@forEachIndexed
                    val startAt = bubble.expiresAtMs - BUBBLE_LIFETIME_MS
                    val progress = ((currentTimeMs - startAt).toFloat() / BUBBLE_LIFETIME_MS).coerceIn(0f, 1f)
                    val edge =
                        currentLayout.edges.firstOrNull {
                            (it.edge.a == comm.fromAgentId && it.edge.b == comm.toAgentId) ||
                                (it.edge.a == comm.toAgentId && it.edge.b == comm.fromAgentId)
                        }
                    val particleControlX = edge?.controlX ?: (sender.x + receiver.x) / 2f
                    val particleControlY = edge?.controlY ?: (sender.y + receiver.y) / 2f
                    if (!reducedMotion) {
                        val inverse = 1f - progress
                        val x = inverse * inverse * sender.x + 2f * inverse * progress * particleControlX + progress * progress * receiver.x
                        val y = inverse * inverse * sender.y + 2f * inverse * progress * particleControlY + progress * progress * receiver.y
                        paint.reset()
                        paint.isAntiAlias = true
                        paint.color = scheme.primary.copy(alpha = 0.22f).toArgb()
                        nativeCanvas.drawCircle(x - centerX, y - centerY, 12f, paint)
                        paint.color = scheme.primary.toArgb()
                        nativeCanvas.drawCircle(x - centerX, y - centerY, 5f, paint)
                    }
                    val bubbleOffsetY = midpointY - centerY - 17f - (index * 28f)
                    drawSpeechBubble(
                        nativeCanvas,
                        midpointX - centerX,
                        bubbleOffsetY,
                        formatMeshCommText(comm.text),
                        paint,
                        textPaint,
                        scheme.surfaceVariant,
                        scheme.onSurfaceVariant,
                    )
                }

                val hiddenCount = edgeBubbles.size - visibleBubbles.size
                if (hiddenCount > 0) {
                    val bubbleOffsetY = midpointY - centerY - 17f - (visibleBubbles.size * 28f)
                    drawSpeechBubble(
                        nativeCanvas,
                        midpointX - centerX,
                        bubbleOffsetY,
                        "+$hiddenCount más",
                        paint,
                        textPaint,
                        scheme.surfaceVariant,
                        scheme.onSurfaceVariant,
                    )
                }
            }

            currentLayout.nodes.values.forEach { node ->
                drawNode(nativeCanvas, node, centerX, centerY, currentTimeMs, reducedMotion, paint, textPaint, scheme)
            }
            nativeCanvas.restore()
        }

        // Canvas has a single gesture surface; these 48dp semantic targets expose each node to assistive technology.
        layout?.nodes?.values?.forEach { positioned ->
            val node = positioned.node
            val label = formatMeshNodeContentDescription(node)
            Box(
                Modifier
                    .offset {
                        val transformedX = widthPx / 2f + panX + (positioned.x - widthPx / 2f) * zoom
                        val transformedY = heightPx / 2f + panY + (positioned.y - heightPx / 2f) * zoom
                        IntOffset(
                            (transformedX - 24.dp.toPx()).roundToInt(),
                            (transformedY - 24.dp.toPx()).roundToInt(),
                        )
                    }.size(48.dp)
                    .clickable { onNodeClick(node.agentId) }
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                    },
            )
        }
    }
}

private fun drawNode(
    canvas: android.graphics.Canvas,
    positioned: PositionedNode,
    centerX: Float,
    centerY: Float,
    nowMs: Long,
    reducedMotion: Boolean,
    paint: AndroidPaint,
    textPaint: AndroidPaint,
    scheme: androidx.compose.material3.ColorScheme,
) {
    val node = positioned.node
    val x = positioned.x - centerX
    val y = positioned.y - centerY
    val pulseAmount = if (node.status == "working" && !reducedMotion) pulse(nowMs) else 0f
    val radius = positioned.radius * (1f + pulseAmount * 0.045f)
    val statusColor =
        when (node.status.lowercase()) {
            "working" -> scheme.primary
            "waiting_input" -> scheme.tertiary
            "error" -> scheme.error
            else -> scheme.outline
        }.let { if (node.online) it else lerp(it, scheme.surfaceVariant, 0.68f) }
    val machineColor = parseMachineColor(node.machineColor, scheme.primary)

    paint.reset()
    paint.isAntiAlias = true
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = machineColor.toArgb()
    canvas.drawCircle(x, y, radius + 4f, paint)

    paint.style = AndroidPaint.Style.FILL
    paint.color = statusColor.copy(alpha = 0.18f + pulseAmount * 0.12f).toArgb()
    canvas.drawCircle(x, y, radius, paint)
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = 2f
    paint.color = statusColor.toArgb()
    canvas.drawCircle(x, y, radius, paint)

    val badge =
        when (node.role) {
            "orchestrator" -> formatMeshRole(node.role).uppercase()
            "subordinate" -> formatMeshRole(node.role).uppercase()
            else -> null
        }
    if (badge != null) {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.textAlign = AndroidPaint.Align.CENTER
        textPaint.textSize = 9f
        textPaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        val badgeWidth = textPaint.measureText(badge) + 14f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = scheme.secondaryContainer.toArgb()
        canvas.drawRoundRect(x - badgeWidth / 2f, y - radius - 19f, x + badgeWidth / 2f, y - radius - 3f, 8f, 8f, paint)
        textPaint.color = scheme.onSecondaryContainer.toArgb()
        canvas.drawText(badge, x, y - radius - 7f, textPaint)
    }

    node.todos?.takeIf { it.total > 0 }?.let { todos ->
        paint.reset()
        paint.isAntiAlias = true
        paint.style = AndroidPaint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = scheme.outlineVariant.toArgb()
        canvas.drawArc(x - radius - 8f, y - radius - 8f, x + radius + 8f, y + radius + 8f, -90f, 360f, false, paint)
        paint.color = scheme.tertiary.toArgb()
        val progress = (todos.done.toFloat() / todos.total).coerceIn(0f, 1f)
        canvas.drawArc(x - radius - 8f, y - radius - 8f, x + radius + 8f, y + radius + 8f, -90f, progress * 360f, false, paint)
    }

    textPaint.reset()
    textPaint.isAntiAlias = true
    textPaint.textAlign = AndroidPaint.Align.CENTER
    textPaint.color = scheme.onSurface.toArgb()
    textPaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    textPaint.textSize = 12f
    canvas.drawText(ellipsize(textPaint, node.name, radius * 1.8f), x, y + radius + 16f, textPaint)
    node.todos?.takeIf { it.total > 0 }?.let { todos ->
        textPaint.textSize = 10f
        textPaint.color = scheme.onSurfaceVariant.toArgb()
        canvas.drawText("${todos.done}/${todos.total}", x, y + radius + 29f, textPaint)
    }
    node.activity?.takeIf { it.isNotBlank() }?.let { activity ->
        textPaint.textSize = 10f
        textPaint.color = scheme.onSurfaceVariant.toArgb()
        canvas.drawText(
            ellipsize(textPaint, activity, radius * 2.8f),
            x,
            y + radius +
                if (node.todos?.total ?: 0 >
                    0
                ) {
                    42f
                } else {
                    29f
                },
            textPaint,
        )
    }

    positioned.satellites.forEach { satellite ->
        val orbitAngle = if (reducedMotion) 0f else nowMs / SATELLITE_PERIOD_MS.toFloat() * (2f * PI.toFloat())
        val (offsetX, offsetY) = satellite.calculatePosition(orbitAngle)
        val sx = x + offsetX
        val sy = y + offsetY
        paint.reset()
        paint.isAntiAlias = true
        paint.color = scheme.secondary.toArgb()
        canvas.drawCircle(sx, sy, satellite.radius, paint)
        paint.color = scheme.onSecondary.toArgb()
        canvas.drawCircle(sx, sy, satellite.radius * 0.32f, paint)
    }
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
    textPaint.textSize = 11f
    textPaint.textAlign = AndroidPaint.Align.CENTER
    textPaint.color = foreground.toArgb()
    val display = ellipsize(textPaint, text, 168f)
    val width = textPaint.measureText(display) + 20f
    paint.reset()
    paint.isAntiAlias = true
    paint.color = background.toArgb()
    canvas.drawRoundRect(centerX - width / 2f, centerY - 12f, centerX + width / 2f, centerY + 12f, 12f, 12f, paint)
    canvas.drawText(display, centerX, centerY + 4f, textPaint)
}

private fun ellipsize(
    paint: AndroidPaint,
    value: String,
    maxWidth: Float,
): String {
    if (paint.measureText(value) <= maxWidth) return value
    val suffix = "…"
    val available = (maxWidth - paint.measureText(suffix)).coerceAtLeast(0f)
    val count = paint.breakText(value, true, available, null)
    return value.take(count).trimEnd() + suffix
}

private fun parseMachineColor(
    raw: String,
    fallback: Color,
): Color = runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrDefault(fallback)

private fun pulse(nowMs: Long): Float = (0.5f + 0.5f * sin(nowMs / 700f * (2f * PI.toFloat()))).toFloat()

private fun distanceToCurve(
    point: Offset,
    edge: PositionedEdge,
): Float {
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

private const val MIN_ZOOM = 0.3f
private const val MAX_ZOOM = 3f
private const val ACTIVE_EDGE_MS = 20_000L
private const val BUBBLE_LIFETIME_MS = 6_000L
private const val SATELLITE_PERIOD_MS = 8_000L
