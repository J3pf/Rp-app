package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Pure Kotlin corporate org-chart tidy-tree layout for agent mesh snapshots. */
class MeshLayoutEngine(
    val config: LayoutConfig = LayoutConfig(),
) {
    fun computeLayout(
        nodes: List<MeshNode>,
        edges: List<MeshEdge>,
        width: Float = 800f,
        height: Float = 800f,
        previousLayout: MeshLayoutResult? = null,
        cardHeights: Map<String, Float> = emptyMap(),
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

        // Defensive cycle detection: establish valid parent links
        val validParentId = mutableMapOf<String, String>()
        for (node in orderedNodes) {
            val pid = node.parentAgentId
            if (pid != null && pid in nodesById && pid != node.agentId) {
                var cur: String? = pid
                var hasCycle = false
                val visited = mutableSetOf(node.agentId)
                while (cur != null) {
                    if (cur in visited) {
                        hasCycle = true
                        break
                    }
                    visited.add(cur)
                    cur = nodesById[cur]?.parentAgentId
                }
                if (!hasCycle) {
                    validParentId[node.agentId] = pid
                }
            }
        }

        // Group membership fallback: if node has no parent, but shares groupId with orchestrator
        val orchestrators = orderedNodes.filter { it.role == "orchestrator" }
        for (orch in orchestrators) {
            val gId = orch.groupId
            if (gId != null) {
                for (node in orderedNodes) {
                    if (node.agentId !in validParentId &&
                        node.agentId != orch.agentId &&
                        node.groupId == gId &&
                        node.role != "orchestrator"
                    ) {
                        validParentId[node.agentId] = orch.agentId
                    }
                }
            }
        }

        val childrenByParent =
            orderedNodes
                .filter { it.agentId in validParentId }
                .groupBy { validParentId.getValue(it.agentId) }
                .mapValues { (_, children) -> children.sortedBy { it.agentId } }

        // Roots are nodes without a parent
        val roots =
            orderedNodes
                .filter { it.agentId !in validParentId }
                .sortedWith(
                    compareBy(
                        { if (it.role == "orchestrator") 0 else 1 },
                        { it.groupId.orEmpty() },
                        { it.name },
                        { it.agentId },
                    ),
                )

        val previousNodes = previousLayout?.nodes.orEmpty()
        val horizontalPadding = config.viewportPaddingX.coerceAtLeast(0f)
        val verticalPadding = config.viewportPaddingY.coerceAtLeast(0f)
        val availableWidth = (width - 2f * horizontalPadding).coerceAtLeast(100f)

        // Card sizing
        val baseCardHeight = max(100f, max(config.cardHeight, config.minCardHeight))

        val nodeHeights =
            orderedNodes.associate { node ->
                val estimatedHeight =
                    baseCardHeight +
                        (if (node.todos?.total?.let { it > 0 } == true) 26f else 0f) +
                        (if (node.subagents.isNotEmpty()) 30f else 0f)
                val measuredHeight = cardHeights[node.agentId]?.takeIf { it.isFinite() && it > 0f }
                node.agentId to (measuredHeight ?: estimatedHeight)
            }

        // Leaf clustering per parent: S4 rules
        val leafGroupChildren = mutableMapOf<String, List<MeshNode>>()
        val regularChildren = mutableMapOf<String, List<MeshNode>>()

        for ((parentId, children) in childrenByParent) {
            val candidateLeaves =
                children
                    .filter { child ->
                        child.subagents.isEmpty() &&
                            childrenByParent[child.agentId].isNullOrEmpty() &&
                            child.role != "orchestrator"
                    }.sortedWith(
                        compareBy(
                            { previousNodes[it.agentId]?.x ?: Float.POSITIVE_INFINITY },
                            { previousNodes[it.agentId]?.y ?: Float.POSITIVE_INFINITY },
                            { it.agentId },
                        ),
                    )
            val nonLeaves = children.filter { it !in candidateLeaves }

            val totalChildrenCount = nonLeaves.size + candidateLeaves.size
            val minRowSpan = totalChildrenCount * config.minCardWidth + (totalChildrenCount - 1) * config.cardSpacingX
            val shouldCluster =
                candidateLeaves.size > config.leafThreshold ||
                    (candidateLeaves.size > 1 && minRowSpan > availableWidth)

            if (shouldCluster && candidateLeaves.isNotEmpty()) {
                leafGroupChildren[parentId] = candidateLeaves
                regularChildren[parentId] = nonLeaves
            } else {
                leafGroupChildren[parentId] = emptyList()
                regularChildren[parentId] = children
            }
        }

        // Depth assignment
        val levels = mutableMapOf<String, Int>()

        fun assignLevels(
            nodeId: String,
            currentLevel: Int,
        ) {
            levels[nodeId] = currentLevel
            regularChildren[nodeId].orEmpty().forEach { child ->
                assignLevels(child.agentId, currentLevel + 1)
            }
            leafGroupChildren[nodeId].orEmpty().forEach { leaf ->
                levels[leaf.agentId] = currentLevel + 1
            }
        }
        roots.forEach { assignLevels(it.agentId, 0) }

        val maxDepth = levels.values.maxOrNull() ?: 0

        // Determine dynamic card width per level to fit available width when possible
        val cardWidthByLevel = mutableMapOf<Int, Float>()
        for (d in 0..maxDepth) {
            val cardsAtLevel =
                orderedNodes.filter {
                    levels[it.agentId] == d &&
                        leafGroupChildren.values.none { leaves -> leaves.any { l -> l.agentId == it.agentId } }
                }
            val cardCount = cardsAtLevel.size
            val dynamicWidth =
                if (cardCount > 1) {
                    ((availableWidth - (cardCount - 1) * config.cardSpacingX) / cardCount)
                        .coerceIn(config.minCardWidth, config.cardWidth)
                } else {
                    config.cardWidth.coerceIn(config.minCardWidth, 320f)
                }
            cardWidthByLevel[d] = dynamicWidth
        }

        val leafWidth = min(config.leafItemWidth, config.cardWidth)
        val leafHeight = config.leafItemHeight

        val rowHeights = mutableMapOf<Int, Float>()
        for (d in 0..maxDepth) {
            val cardsAtLevel =
                orderedNodes.filter {
                    levels[it.agentId] == d &&
                        leafGroupChildren.values.none { leaves -> leaves.any { l -> l.agentId == it.agentId } }
                }
            val cardMaxHeight = cardsAtLevel.maxOfOrNull { nodeHeights.getValue(it.agentId) } ?: baseCardHeight
            rowHeights[d] = max(baseCardHeight, cardMaxHeight)
        }

        val rowTops = mutableMapOf<Int, Float>()
        val rowCenters = mutableMapOf<Int, Float>()
        var currentY = verticalPadding
        for (d in 0..maxDepth) {
            rowTops[d] = currentY
            val rHeight = rowHeights.getValue(d)
            rowCenters[d] = currentY + rHeight / 2f
            currentY += rHeight + config.cardSpacingY
        }

        // Build LayoutTree for Reingold-Tilford tidy-tree positioning
        data class TreeNode(
            val nodeId: String?,
            val isLeafGroup: Boolean,
            val leafNodes: List<MeshNode>,
            val width: Float,
            val height: Float,
            val depth: Int,
            val children: List<TreeNode>,
        ) {
            var x: Float = 0f
            val leftContour = mutableMapOf<Int, Float>()
            val rightContour = mutableMapOf<Int, Float>()
        }

        fun buildTree(
            node: MeshNode,
            depth: Int,
        ): TreeNode {
            val childNodes = regularChildren[node.agentId].orEmpty()
            val sortedChildren =
                childNodes.sortedWith(
                    compareBy(
                        { previousNodes[it.agentId]?.x ?: Float.POSITIVE_INFINITY },
                        { it.agentId },
                    ),
                )
            val subTrees = sortedChildren.map { buildTree(it, depth + 1) }.toMutableList()

            val leaves = leafGroupChildren[node.agentId].orEmpty()
            if (leaves.isNotEmpty()) {
                val groupHeight = leaves.size * (leafHeight + 4f)
                val leafGroupTree =
                    TreeNode(
                        nodeId = null,
                        isLeafGroup = true,
                        leafNodes = leaves,
                        width = leafWidth,
                        height = groupHeight,
                        depth = depth + 1,
                        children = emptyList(),
                    )
                subTrees.add(leafGroupTree)
            }

            val nodeWidth = cardWidthByLevel[depth] ?: config.cardWidth
            return TreeNode(
                nodeId = node.agentId,
                isLeafGroup = false,
                leafNodes = emptyList(),
                width = nodeWidth,
                height = nodeHeights.getValue(node.agentId),
                depth = depth,
                children = subTrees,
            )
        }

        // Reingold-Tilford tidy-tree layout on a single tree
        fun layoutTree(tree: TreeNode) {
            if (tree.children.isEmpty()) {
                tree.x = 0f
                tree.leftContour[0] = -tree.width / 2f
                tree.rightContour[0] = tree.width / 2f
                return
            }

            tree.children.forEach { layoutTree(it) }

            // Place children side by side with contour separation
            tree.children[0].x = 0f
            for (i in 1 until tree.children.size) {
                val left = tree.children[i - 1]
                val right = tree.children[i]
                var minDistance = config.cardSpacingX

                // Check contours across all shared depths
                val sharedDepths = left.rightContour.keys.intersect(right.leftContour.keys)
                for (d in sharedDepths) {
                    val leftEdge = left.x + left.rightContour.getValue(d)
                    val rightEdge = right.x + right.leftContour.getValue(d)
                    val gap = rightEdge - leftEdge
                    if (gap < config.cardSpacingX) {
                        minDistance = max(minDistance, config.cardSpacingX - gap)
                    }
                }
                right.x += minDistance
            }

            // Parent is centered over its children
            val childrenMidpoint = (tree.children.first().x + tree.children.last().x) / 2f
            tree.x = childrenMidpoint

            // Shift children so parent is at origin
            for (child in tree.children) {
                child.x -= childrenMidpoint
            }

            // Merge contours
            tree.leftContour[0] = -tree.width / 2f
            tree.rightContour[0] = tree.width / 2f
            val maxChildDepth = tree.children.maxOf { child -> child.leftContour.keys.maxOrNull() ?: 0 }
            for (d in 0..maxChildDepth) {
                val lefts = tree.children.mapNotNull { child -> child.leftContour[d]?.let { child.x + it } }
                val rights = tree.children.mapNotNull { child -> child.rightContour[d]?.let { child.x + it } }
                if (lefts.isNotEmpty()) {
                    tree.leftContour[d + 1] = lefts.min()
                }
                if (rights.isNotEmpty()) {
                    tree.rightContour[d + 1] = rights.max()
                }
            }
        }

        val rootTrees = roots.map { buildTree(it, 0) }
        rootTrees.forEach { layoutTree(it) }

        // Place multiple roots side by side on top row
        rootTrees[0].x = 0f
        for (i in 1 until rootTrees.size) {
            val left = rootTrees[i - 1]
            val right = rootTrees[i]
            var minDistance = config.cardSpacingX * 1.5f
            val sharedDepths = left.rightContour.keys.intersect(right.leftContour.keys)
            for (d in sharedDepths) {
                val leftEdge = left.x + left.rightContour.getValue(d)
                val rightEdge = right.x + right.leftContour.getValue(d)
                val gap = rightEdge - leftEdge
                if (gap < config.cardSpacingX * 1.5f) {
                    minDistance = max(minDistance, config.cardSpacingX * 1.5f - gap)
                }
            }
            right.x += minDistance
        }

        // Shift root trees to center inside available viewport
        var allMinX = Float.POSITIVE_INFINITY
        var allMaxX = Float.NEGATIVE_INFINITY

        fun computeTreeBounds(
            node: TreeNode,
            parentX: Float,
        ) {
            val curX = parentX + node.x
            allMinX = min(allMinX, curX - node.width / 2f)
            allMaxX = max(allMaxX, curX + node.width / 2f)
            node.children.forEach { computeTreeBounds(it, curX) }
        }
        rootTrees.forEach { computeTreeBounds(it, it.x) }

        val totalTreeWidth = if (allMaxX > allMinX) allMaxX - allMinX else config.cardWidth
        val globalOffsetX =
            if (totalTreeWidth < availableWidth) {
                horizontalPadding + (availableWidth - totalTreeWidth) / 2f - allMinX
            } else {
                horizontalPadding - allMinX
            }

        // Flatten positioned nodes
        val positionedNodes = linkedMapOf<String, PositionedNode>()

        fun emitNodes(
            treeNode: TreeNode,
            absoluteX: Float,
        ) {
            val curX = absoluteX + treeNode.x
            if (treeNode.isLeafGroup) {
                val leaves = treeNode.leafNodes
                val parentTopY = rowTops.getValue(treeNode.depth)
                var currentLeafTop = parentTopY
                leaves.forEachIndexed { index, leaf ->
                    val thisLeafHeight = cardHeights[leaf.agentId]?.takeIf { it.isFinite() && it > 0f } ?: leafHeight
                    val leafY = currentLeafTop + thisLeafHeight / 2f
                    positionedNodes[leaf.agentId] =
                        PositionedNode(
                            node = leaf,
                            x = curX,
                            y = leafY,
                            width = leafWidth,
                            height = thisLeafHeight,
                            level = treeNode.depth,
                            displayMode = NodeDisplayMode.LEAF_ITEM,
                            parentId = validParentId[leaf.agentId],
                            leafIndex = index,
                            totalLeaves = leaves.size,
                        )
                    currentLeafTop += thisLeafHeight + 8f
                }
            } else {
                val id = treeNode.nodeId!!
                val node = nodesById.getValue(id)
                val rowCenterY = rowCenters.getValue(treeNode.depth)
                positionedNodes[id] =
                    PositionedNode(
                        node = node,
                        x = curX,
                        y = rowCenterY,
                        width = treeNode.width,
                        height = treeNode.height,
                        level = treeNode.depth,
                        displayMode = NodeDisplayMode.CARD,
                        parentId = validParentId[id],
                    )
                for (child in treeNode.children) {
                    emitNodes(child, curX)
                }
            }
        }

        rootTrees.forEach { rootTree ->
            emitNodes(rootTree, globalOffsetX)
        }

        // Position orthogonal bus edges
        val positionedEdges =
            edges
                .sortedWith(compareBy<MeshEdge>({ it.edgeId }, { it.a }, { it.b }))
                .mapNotNull { edge -> positionEdge(edge, positionedNodes) }

        val primaryRoot = roots.firstOrNull { it.role == "orchestrator" } ?: roots.first()
        return MeshLayoutResult(
            nodes = positionedNodes,
            edges = positionedEdges,
            bounds = calculateBounds(positionedNodes, positionedEdges),
            centerNodeId = primaryRoot.agentId,
        )
    }

    private fun positionEdge(
        edge: MeshEdge,
        nodes: Map<String, PositionedNode>,
    ): PositionedEdge? {
        val a = nodes[edge.a] ?: return null
        val b = nodes[edge.b] ?: return null

        val aIsParent = a.level < b.level
        val bIsParent = b.level < a.level

        val parentAndChild =
            when {
                aIsParent -> a to b
                bIsParent -> b to a
                else -> null
            }

        val routePoints =
            parentAndChild?.let { (parent, child) ->
                orthogonalBusRoute(parent, child, nodes)
            } ?: emptyList()

        val endpoints =
            when {
                aIsParent -> EdgeEndpoints(a.x, a.bottom, b.x, b.top)
                bIsParent -> EdgeEndpoints(b.x, b.bottom, a.x, a.top)
                a.y == b.y && a.x <= b.x -> EdgeEndpoints(a.right, a.y, b.left, b.y)
                a.y == b.y -> EdgeEndpoints(a.left, a.y, b.right, b.y)
                a.y < b.y -> EdgeEndpoints(a.x, a.bottom, b.x, b.top)
                else -> EdgeEndpoints(a.x, a.top, b.x, b.bottom)
            }

        val fromX = routePoints.firstOrNull()?.x ?: endpoints.fromX
        val fromY = routePoints.firstOrNull()?.y ?: endpoints.fromY
        val toX = routePoints.lastOrNull()?.x ?: endpoints.toX
        val toY = routePoints.lastOrNull()?.y ?: endpoints.toY

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

    private data class EdgeEndpoints(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
    )

    private fun orthogonalBusRoute(
        parent: PositionedNode,
        child: PositionedNode,
        nodes: Map<String, PositionedNode>,
    ): List<EdgePoint> {
        if (child.displayMode == NodeDisplayMode.LEAF_ITEM) {
            val rows = nodes.values.groupBy { it.y }.toSortedMap()
            val childRow = rows[child.y].orEmpty()
            val minChildTop = childRow.minOfOrNull { it.top } ?: child.top
            val busY = (parent.bottom + minOf(minChildTop, child.top)) / 2f
            val spineX = child.left + 16f
            val points = mutableListOf<EdgePoint>()
            points += EdgePoint(parent.x, parent.bottom)
            if (parent.x != spineX) {
                points += EdgePoint(parent.x, busY)
                points += EdgePoint(spineX, busY)
            }
            points += EdgePoint(spineX, child.y)
            return points
        }

        // Multi-level hierarchy edges (skipping intermediate rows)
        if (child.level > parent.level + 1) {
            val rows = nodes.values.groupBy { it.y }.toSortedMap()
            val rowCenters = rows.keys.toList()
            val parentRowIndex = rowCenters.indexOf(parent.y)
            val childRowIndex = rowCenters.indexOf(child.y)
            if (parentRowIndex >= 0 && childRowIndex > parentRowIndex + 1) {
                val parentRow = rows.getValue(parent.y)
                val childRow = rows.getValue(child.y)
                val nextRow = rows.getValue(rowCenters[parentRowIndex + 1])
                val previousRow = rows.getValue(rowCenters[childRowIndex - 1])
                val sourceChannelY = (parentRow.maxOf { it.bottom } + nextRow.minOf { it.top }) / 2f
                val targetChannelY = (previousRow.maxOf { it.bottom } + childRow.minOf { it.top }) / 2f
                val channelX = nodes.values.minOf { it.left } - config.edgeRoutingMargin.coerceAtLeast(4f)
                val points = mutableListOf<EdgePoint>()
                points += EdgePoint(parent.x, parent.bottom)
                points += EdgePoint(parent.x, sourceChannelY)
                points += EdgePoint(channelX, sourceChannelY)
                points += EdgePoint(channelX, targetChannelY)
                points += EdgePoint(child.x, targetChannelY)
                points += EdgePoint(child.x, child.top)
                return points
            }
        }

        // Direct parent-child orthogonal bus
        val busY = (parent.bottom + child.top) / 2f
        val points = mutableListOf<EdgePoint>()
        points += EdgePoint(parent.x, parent.bottom)
        if (parent.x != child.x) {
            points += EdgePoint(parent.x, busY)
            points += EdgePoint(child.x, busY)
        }
        points += EdgePoint(child.x, child.top)
        return points
    }

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
