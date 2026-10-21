package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode

enum class NodeDisplayMode {
    CARD,
    LEAF_ITEM,
}

/** A mesh node positioned in layout coordinates. */
data class PositionedNode(
    val node: MeshNode,
    val x: Float,
    val y: Float,
    val width: Float = 200f,
    val height: Float = 100f,
    val level: Int,
    val displayMode: NodeDisplayMode = NodeDisplayMode.CARD,
    val parentId: String? = null,
    val leafIndex: Int = -1,
    val totalLeaves: Int = 0,
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

/** A point in the layout coordinate space used by orthogonal edge routes. */
data class EdgePoint(
    val x: Float,
    val y: Float,
)

/** An edge represented by a curve or an orthogonal bus route. */
data class PositionedEdge(
    val edge: MeshEdge,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val controlX: Float,
    val controlY: Float,
    val curvature: Float,
    val routePoints: List<EdgePoint> = emptyList(),
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
    val edgeCurveOffset: Float = 24f,
    val cardWidth: Float = 280f,
    val cardHeight: Float = 100f,
    val minCardWidth: Float = 180f,
    val minCardHeight: Float = 100f,
    val leafItemWidth: Float = 220f,
    val leafItemHeight: Float = 36f,
    val leafThreshold: Int = 3,
    val cardSpacingX: Float = 12f,
    val cardSpacingY: Float = 56f,
    val viewportPaddingX: Float = 12f,
    val viewportPaddingY: Float = 16f,
    val edgeRoutingMargin: Float = 16f,
)
