package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import kotlin.math.cos
import kotlin.math.sin

/** A mesh node positioned in layout coordinates. */
data class PositionedNode(
    val node: MeshNode,
    val x: Float,
    val y: Float,
    val width: Float = 200f,
    val height: Float = 90f,
    val radius: Float = maxOf(width, height) / 2f,
    val level: Int,
    val satellites: List<PositionedSatellite>,
) {
    val left: Float get() = x - width / 2f
    val top: Float get() = y - height / 2f
    val right: Float get() = x + width / 2f
    val bottom: Float get() = y + height / 2f

    fun bounds(): LayoutBounds = LayoutBounds(left, top, right, bottom)

    fun intersects(
        other: PositionedNode,
        margin: Float = 0f,
    ): Boolean =
        left < other.right + margin &&
            right > other.left - margin &&
            top < other.bottom + margin &&
            bottom > other.top - margin
}

/** A subagent positioned relative to its parent node. */
data class PositionedSatellite(
    val subagent: MeshSubagent,
    val parentAgentId: String,
    val orbitRadius: Float,
    val baseAngleRad: Float,
    val radius: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val width: Float = 88f,
    val height: Float = 22f,
) {
    fun calculatePosition(currentAngleOffsetRad: Float = 0f): Pair<Float, Float> =
        if (orbitRadius > 0f) {
            val angle = baseAngleRad + currentAngleOffsetRad
            (cos(angle) * orbitRadius) to (sin(angle) * orbitRadius)
        } else {
            offsetX to offsetY
        }
}

/** An edge represented by a quadratic Bezier curve. */
data class PositionedEdge(
    val edge: MeshEdge,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val controlX: Float,
    val controlY: Float,
    val curvature: Float,
)

data class MeshLayoutResult(
    val nodes: Map<String, PositionedNode>,
    val edges: List<PositionedEdge>,
    val bounds: LayoutBounds,
    val centerNodeId: String?,
)

data class LayoutBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

data class LayoutConfig(
    val orchestratorRadius: Float = 44f,
    val subordinateRadius: Float = 32f,
    val soloRadius: Float = 28f,
    val baseRingRadius: Float = 160f,
    val levelRingStep: Float = 140f,
    val minNodeSpacing: Float = 80f,
    val satelliteOrbitRadius: Float = 48f,
    val satelliteRadius: Float = 10f,
    val edgeCurveOffset: Float = 24f,
    val cardWidth: Float = 180f,
    val cardHeight: Float = 90f,
    val minCardWidth: Float = 84f,
    val minCardHeight: Float = 56f,
    val cardSpacingX: Float = 12f,
    val cardSpacingY: Float = 20f,
    val viewportPaddingX: Float = 12f,
    val viewportPaddingY: Float = 16f,
)
