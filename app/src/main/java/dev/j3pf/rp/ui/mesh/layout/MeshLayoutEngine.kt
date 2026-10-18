package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
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
        val horizontalPadding = config.viewportPaddingX.coerceAtLeast(0f)
        val verticalPadding = config.viewportPaddingY.coerceAtLeast(0f)
        val availableWidth = (width - 2f * horizontalPadding).coerceAtLeast(0f)
        val horizontalSpacing = config.cardSpacingX.coerceAtLeast(4f)
        val minimumCardWidth = config.minCardWidth.coerceAtLeast(1f)
        val maxColumns =
            ((availableWidth + horizontalSpacing) / (minimumCardWidth + horizontalSpacing))
                .toInt()
                .coerceIn(1, 4)

        val previousNodes = previousLayout?.nodes.orEmpty()
        // Group each level into balanced rows that fill available width
        val rows =
            nodesByLevel.values.flatMap { levelNodes ->
                val sorted =
                    levelNodes.sortedWith(
                        compareBy<MeshNode>(
                            { previousNodes[it.agentId]?.y ?: Float.POSITIVE_INFINITY },
                            { previousNodes[it.agentId]?.x ?: Float.POSITIVE_INFINITY },
                            { it.agentId },
                        ),
                    )
                // If single node (e.g. orchestrator level), keep on its own row
                if (sorted.size == 1) {
                    listOf(sorted)
                } else {
                    sorted.chunked(maxColumns)
                }
            }
        val verticalSpacing = if (rows.size > 1) config.cardSpacingY.coerceAtLeast(48f) else 0f
        val baseCardHeight = max(config.cardHeight, config.minCardHeight)
        val nodeHeights =
            orderedNodes.associate { node ->
                node.agentId to (baseCardHeight + (if (node.todos != null) 24f else 0f) + (if (node.subagents.isNotEmpty()) 28f else 0f))
            }
        val rowHeights = rows.map { row -> row.maxOf { nodeHeights.getValue(it.agentId) } }

        val positions = mutableMapOf<String, Pair<Float, Float>>()
        val nodeWidths = mutableMapOf<String, Float>()
        var rowTop = verticalPadding

        rows.forEachIndexed { rowIndex, rowNodes ->
            val cols = rowNodes.size
            val rowCardWidth =
                min(
                    config.cardWidth.coerceAtLeast(minimumCardWidth),
                    (availableWidth - (cols - 1) * horizontalSpacing) / cols,
                )
            val rowWidth = cols * rowCardWidth + (cols - 1) * horizontalSpacing
            val firstCenterX = width / 2f - rowWidth / 2f + rowCardWidth / 2f
            val rowHeight = rowHeights[rowIndex]
            val rowCenterY = rowTop + rowHeight / 2f

            rowNodes.forEachIndexed { columnIndex, node ->
                positions[node.agentId] = firstCenterX + columnIndex * (rowCardWidth + horizontalSpacing) to rowCenterY
                nodeWidths[node.agentId] = rowCardWidth
            }
            rowTop += rowHeight + verticalSpacing
        }

        val positionedNodes = linkedMapOf<String, PositionedNode>()
        orderedNodes.forEach { node ->
            val (x, y) = positions.getValue(node.agentId)
            val nodeWidth = nodeWidths.getValue(node.agentId)
            val nodeHeight = nodeHeights.getValue(node.agentId)
            val satellites = positionSatellites(node, nodeWidth, nodeHeight)
            positionedNodes[node.agentId] =
                PositionedNode(
                    node = node,
                    x = x,
                    y = y,
                    width = nodeWidth,
                    height = nodeHeight,
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
        val parentAndChild =
            when {
                aIsParent -> a to b
                bIsParent -> b to a
                else -> null
            }
        val routePoints =
            parentAndChild
                ?.let { (parent, child) ->
                    orthogonalRoute(parent, child, nodes)
                }.orEmpty()
        val routedEndpoints =
            parentAndChild?.let { (parent, child) ->
                EdgeEndpoints(parent.x, parent.bottom, child.x, child.top)
            } ?: endpoints
        val fromX = routedEndpoints.fromX
        val fromY = routedEndpoints.fromY
        val toX = routedEndpoints.toX
        val toY = routedEndpoints.toY
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
            routePoints = routePoints,
        )
    }

    private fun orthogonalRoute(
        parent: PositionedNode,
        child: PositionedNode,
        nodes: Map<String, PositionedNode>,
    ): List<EdgePoint> {
        val rows = nodes.values.groupBy { it.y }.toSortedMap()
        val rowCenters = rows.keys.toList()
        val parentRowIndex = rowCenters.indexOf(parent.y)
        val childRowIndex = rowCenters.indexOf(child.y)
        if (parentRowIndex < 0 || childRowIndex <= parentRowIndex) return emptyList()

        val parentRow = rows.getValue(parent.y)
        val childRow = rows.getValue(child.y)
        val nextRow = rows.getValue(rowCenters[parentRowIndex + 1])
        val previousRow = rows.getValue(rowCenters[childRowIndex - 1])
        val sourceChannelY = (parentRow.maxOf { it.bottom } + nextRow.minOf { it.top }) / 2f
        val targetChannelY = (previousRow.maxOf { it.bottom } + childRow.minOf { it.top }) / 2f
        val points = mutableListOf(EdgePoint(parent.x, parent.bottom))

        fun append(
            x: Float,
            y: Float,
        ) {
            val point = EdgePoint(x, y)
            if (points.last() != point) points += point
        }

        append(parent.x, sourceChannelY)
        if (childRowIndex == parentRowIndex + 1) {
            append(child.x, sourceChannelY)
        } else {
            val channelX = nodes.values.minOf { it.left } - config.edgeRoutingMargin.coerceAtLeast(4f)
            append(channelX, sourceChannelY)
            append(channelX, targetChannelY)
            append(child.x, targetChannelY)
        }
        append(child.x, child.top)
        return points
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
            val points = edge.routePoints.ifEmpty { listOf(EdgePoint(edge.controlX, edge.controlY)) }
            points.forEach { point ->
                minX = min(minX, point.x)
                minY = min(minY, point.y)
                maxX = max(maxX, point.x)
                maxY = max(maxY, point.y)
            }
        }
        if (minX == Float.POSITIVE_INFINITY) return LayoutBounds(0f, 0f, 0f, 0f)
        return LayoutBounds(minX, minY, maxX, maxY)
    }
}
