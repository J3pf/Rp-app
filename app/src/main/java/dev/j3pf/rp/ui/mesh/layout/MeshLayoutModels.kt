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
    val radius: Float,
    val level: Int,
    val satellites: List<PositionedSatellite>,
)

/** A subagent whose position is relative to its parent node. */
data class PositionedSatellite(
    val subagent: MeshSubagent,
    val parentAgentId: String,
    val orbitRadius: Float,
    val baseAngleRad: Float,
    val radius: Float,
) {
    fun calculatePosition(currentAngleOffsetRad: Float = 0f): Pair<Float, Float> {
        val angle = baseAngleRad + currentAngleOffsetRad
        return (cos(angle) * orbitRadius) to (sin(angle) * orbitRadius)
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
)
