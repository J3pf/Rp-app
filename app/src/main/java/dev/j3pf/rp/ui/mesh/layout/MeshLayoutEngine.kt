package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Pure Kotlin hierarchical top-down layout for agent mesh snapshots. */
class MeshLayoutEngine(
    val config: LayoutConfig = LayoutConfig(),
) {
    fun computeLayout(
        nodes: List<MeshNode>,
        edges: List<MeshEdge>,
        width: Float = 800f,
        height: Float = 800f,
        previousLayout: MeshLayoutResult? = null,
    ): MeshLayoutResult {
        val orderedNodes =
            nodes
                .sortedWith(
                    compareBy<MeshNode>(
                        { it.agentId },
                        { it.role },
                        { it.parentAgentId.orEmpty() },
                        { it.groupId.orEmpty() },
                        { it.name },
                        { it.machineId },
                    ),
                ).distinctBy { it.agentId }
        if (orderedNodes.isEmpty()) {
            return MeshLayoutResult(
                nodes = emptyMap(),
                edges = emptyList(),
                bounds = LayoutBounds(0f, 0f, 0f, 0f),
                centerNodeId = null,
            )
        }

        val nodesById = orderedNodes.associateBy { it.agentId }
        val root =
            orderedNodes.firstOrNull { it.role == "orchestrator" }
                ?: orderedNodes.firstOrNull {
                    it.role != "solo" && (it.parentAgentId == null || it.parentAgentId !in nodesById)
                }
                ?: orderedNodes.first()
        val childrenByParent =
            orderedNodes
                .filter { it.parentAgentId != null && it.parentAgentId in nodesById }
                .groupBy { it.parentAgentId!! }
                .mapValues { (_, children) -> children.sortedBy { it.agentId } }
        val levels = mutableMapOf(root.agentId to 0)

        fun attachDescendants(
            parentId: String,
            parentLevel: Int,
        ) {
            childrenByParent[parentId].orEmpty().forEach { child ->
                if (child.agentId !in levels) {
                    val childLevel = parentLevel + 1
                    levels[child.agentId] = childLevel
                    attachDescendants(child.agentId, childLevel)
                }
            }
        }

        attachDescendants(root.agentId, 0)
        root.groupId?.let { groupId ->
            orderedNodes
                .filter { it.groupId == groupId && it.agentId !in levels }
                .forEach { member ->
                    levels[member.agentId] = 1
                    attachDescendants(member.agentId, 1)
                }
        }
        orderedNodes.forEach { node ->
            if (node.agentId !in levels) {
                levels[node.agentId] = 1
                attachDescendants(node.agentId, 1)
            }
        }

        val nodesByLevel = orderedNodes.groupBy { levels.getValue(it.agentId) }.toSortedMap()
        val largestLevel = nodesByLevel.values.maxOf { it.size }
        val horizontalPadding = config.viewportPaddingX.coerceAtLeast(0f)
        val verticalPadding = config.viewportPaddingY.coerceAtLeast(0f)
        val availableWidth = (width - 2f * horizontalPadding).coerceAtLeast(0f)
        val availableHeight = (height - 2f * verticalPadding).coerceAtLeast(0f)
        val horizontalSpacing = config.cardSpacingX.coerceAtLeast(2f)
        val minimumWidth = config.minCardWidth.coerceAtLeast(1f)
        val preferredWidth = config.cardWidth.coerceAtLeast(minimumWidth)
        val widthCapacity =
            floor((availableWidth + horizontalSpacing) / (minimumWidth + horizontalSpacing))
                .toInt()
                .coerceAtLeast(1)
        val preferredColumns =
            floor((availableWidth + horizontalSpacing) / (preferredWidth + horizontalSpacing))
                .toInt()
                .coerceAtLeast(1)
        val aspectColumns =
            if (availableHeight > 0f && availableWidth > 0f) {
                ceil(sqrt(largestLevel * availableWidth / availableHeight)).toInt().coerceAtLeast(1)
            } else {
                largestLevel
            }
        val columnCount =
            max(preferredColumns, aspectColumns)
                .coerceAtMost(widthCapacity)
                .coerceAtMost(largestLevel)
                .coerceAtLeast(1)
        val cardWidth =
            min(
                preferredWidth,
                ((availableWidth - (columnCount - 1) * horizontalSpacing) / columnCount).coerceAtLeast(0f),
            )

        val previousNodes = previousLayout?.nodes.orEmpty()
        val rows =
            nodesByLevel.values.flatMap { levelNodes ->
                levelNodes
                    .sortedWith(
                        compareBy<MeshNode>(
                            { previousNodes[it.agentId]?.y ?: Float.POSITIVE_INFINITY },
                            { previousNodes[it.agentId]?.x ?: Float.POSITIVE_INFINITY },
                            { it.agentId },
                        ),
                    ).chunked(columnCount)
            }
        val rowCount = rows.size
        val minimumHeight = config.minCardHeight.coerceAtLeast(0f)
        val preferredHeight = config.cardHeight.coerceAtLeast(minimumHeight)
        val requestedVerticalSpacing = config.cardSpacingY.coerceAtLeast(2f)
        val verticalSpacing =
            if (rowCount > 1) {
                val spacingThatPreservesMinimumCards =
                    ((availableHeight - rowCount * minimumHeight) / (rowCount - 1)).coerceAtLeast(2f)
                min(requestedVerticalSpacing, spacingThatPreservesMinimumCards)
            } else {
                0f
            }
        val cardHeight =
            min(
                preferredHeight,
                ((availableHeight - verticalSpacing * (rowCount - 1)) / rowCount).coerceAtLeast(0f),
            )
        val positions = mutableMapOf<String, Pair<Float, Float>>()
        rows.forEachIndexed { rowIndex, rowNodes ->
            val rowWidth = rowNodes.size * cardWidth + (rowNodes.size - 1) * horizontalSpacing
            val firstCenterX = width / 2f - rowWidth / 2f + cardWidth / 2f
            val centerY = verticalPadding + cardHeight / 2f + rowIndex * (cardHeight + verticalSpacing)
            rowNodes.forEachIndexed { columnIndex, node ->
                positions[node.agentId] = firstCenterX + columnIndex * (cardWidth + horizontalSpacing) to centerY
            }
        }

        val positionedNodes = linkedMapOf<String, PositionedNode>()
        orderedNodes.forEach { node ->
            val (x, y) = positions.getValue(node.agentId)
            val satellites = positionSatellites(node, cardWidth, cardHeight)
            positionedNodes[node.agentId] =
                PositionedNode(
                    node = node,
                    x = x,
                    y = y,
                    width = cardWidth,
                    height = cardHeight,
                    level = levels.getValue(node.agentId),
                    satellites = satellites,
                )
        }

        val positionedEdges =
            edges
                .sortedWith(compareBy<MeshEdge>({ it.edgeId }, { it.a }, { it.b }))
                .mapNotNull { edge -> positionEdge(edge, positionedNodes) }
        return MeshLayoutResult(
            nodes = positionedNodes,
            edges = positionedEdges,
            bounds = calculateBounds(positionedNodes, positionedEdges),
            centerNodeId = root.agentId,
        )
    }

    private fun positionSatellites(
        node: MeshNode,
        cardWidth: Float,
        cardHeight: Float,
    ): List<PositionedSatellite> {
        val subagents = node.subagents.sortedBy { it.taskId }
        if (subagents.isEmpty()) return emptyList()
        val satelliteWidth = min(88f, cardWidth / 2f)
        val satelliteHeight = min(22f, cardHeight / 2f)
        val horizontalOffset = cardWidth / 4f
        val verticalOffset = cardHeight / 4f
        return subagents.mapIndexed { index, subagent ->
            val column = index % 2
            val row = index / 2
            val offsetX =
                when {
                    subagents.size == 1 -> 0f
                    column == 0 -> -horizontalOffset
                    else -> horizontalOffset
                }
            val offsetY =
                when {
                    subagents.size <= 2 -> 0f
                    row == 0 -> -verticalOffset
                    else -> verticalOffset
                }
            PositionedSatellite(
                subagent = subagent,
                parentAgentId = node.agentId,
                orbitRadius = 0f,
                baseAngleRad = 0f,
                radius = config.satelliteRadius.coerceAtLeast(0f),
                offsetX = offsetX,
                offsetY = offsetY,
                width = satelliteWidth,
                height = satelliteHeight,
            )
        }
    }

    private fun positionEdge(
        edge: MeshEdge,
        nodes: Map<String, PositionedNode>,
    ): PositionedEdge? {
        val a = nodes[edge.a] ?: return null
        val b = nodes[edge.b] ?: return null
        val aIsParent = b.node.parentAgentId == a.node.agentId || a.level < b.level
        val bIsParent = a.node.parentAgentId == b.node.agentId || b.level < a.level
        val endpoints =
            when {
                aIsParent -> EdgeEndpoints(a.x, a.bottom, b.x, b.top)
                bIsParent -> EdgeEndpoints(b.x, b.bottom, a.x, a.top)
                a.y == b.y && a.x <= b.x -> EdgeEndpoints(a.right, a.y, b.left, b.y)
                a.y == b.y -> EdgeEndpoints(a.left, a.y, b.right, b.y)
                a.y < b.y -> EdgeEndpoints(a.x, a.bottom, b.x, b.top)
                else -> EdgeEndpoints(b.x, b.bottom, a.x, a.top)
            }
        val fromX = endpoints.fromX
        val fromY = endpoints.fromY
        val toX = endpoints.toX
        val toY = endpoints.toY
        val low = if (edge.a <= edge.b) a else b
        val high = if (edge.a <= edge.b) b else a
        val dx = high.x - low.x
        val dy = high.y - low.y
        val length = sqrt(dx * dx + dy * dy)
        val normalX = if (length > 0f) -dy / length else 0f
        val normalY = if (length > 0f) dx / length else 1f
        val curvature = config.edgeCurveOffset.coerceAtLeast(0f) * if (edge.a <= edge.b) 1f else -1f
        val midpointX = (fromX + toX) / 2f
        val midpointY = (fromY + toY) / 2f
        return PositionedEdge(
            edge = edge,
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            controlX = midpointX + normalX * curvature,
            controlY = midpointY + normalY * curvature,
            curvature = curvature,
        )
    }

    private data class EdgeEndpoints(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
    )

    private fun calculateBounds(
        nodes: Map<String, PositionedNode>,
        edges: List<PositionedEdge>,
    ): LayoutBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        fun include(bounds: LayoutBounds) {
            minX = min(minX, bounds.minX)
            minY = min(minY, bounds.minY)
            maxX = max(maxX, bounds.maxX)
            maxY = max(maxY, bounds.maxY)
        }

        for (node in nodes.values) {
            include(node.bounds())
            node.satellites.forEach { satellite ->
                val (offsetX, offsetY) = satellite.calculatePosition()
                include(
                    LayoutBounds(
                        node.x + offsetX - satellite.width / 2f,
                        node.y + offsetY - satellite.height / 2f,
                        node.x + offsetX + satellite.width / 2f,
                        node.y + offsetY + satellite.height / 2f,
                    ),
                )
            }
        }
        edges.forEach { edge ->
            minX = min(minX, edge.controlX)
            minY = min(minY, edge.controlY)
            maxX = max(maxX, edge.controlX)
            maxY = max(maxY, edge.controlY)
        }
        if (minX == Float.POSITIVE_INFINITY) return LayoutBounds(0f, 0f, 0f, 0f)
        return LayoutBounds(minX, minY, maxX, maxY)
    }
}
