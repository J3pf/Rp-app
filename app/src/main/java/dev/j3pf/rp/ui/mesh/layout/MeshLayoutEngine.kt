package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure Kotlin radial-hierarchical layout for agent mesh snapshots. */
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
        val orchestrator = orderedNodes.firstOrNull { it.role == "orchestrator" }
        val hierarchyRoot =
            orderedNodes.firstOrNull {
                it.role != "solo" && (it.parentAgentId == null || it.parentAgentId !in nodesById)
            }
        val cyclicRoot = orderedNodes.firstOrNull { it.role != "solo" }
        val centerNode = orchestrator ?: hierarchyRoot ?: cyclicRoot
        val centerNodeId = centerNode?.agentId
        val centerX = width / 2f
        val centerY = height / 2f
        val levels = mutableMapOf<String, Int>()
        val childrenByParent =
            orderedNodes
                .filter { it.parentAgentId != null && it.parentAgentId in nodesById }
                .groupBy { it.parentAgentId!! }
                .mapValues { (_, children) -> children.sortedBy { it.agentId } }

        fun attachDescendants(
            parentId: String,
            parentLevel: Int,
        ) {
            for (child in childrenByParent[parentId].orEmpty()) {
                if (child.agentId !in levels) {
                    val childLevel = parentLevel + 1
                    levels[child.agentId] = childLevel
                    attachDescendants(child.agentId, childLevel)
                }
            }
        }

        if (centerNode != null) {
            levels[centerNode.agentId] = 0
            attachDescendants(centerNode.agentId, 0)

            // Group membership supplies a direct-ring relationship where the snapshot has no parent link.
            val centerGroupId = centerNode.groupId
            if (centerGroupId != null) {
                for (member in orderedNodes) {
                    if (member.groupId == centerGroupId && member.agentId !in levels) {
                        levels[member.agentId] = 1
                        attachDescendants(member.agentId, 1)
                    }
                }
            }
        }

        // Keep disconnected components visible, and seed malformed/cyclic components exactly once.
        for (node in orderedNodes) {
            if (node.agentId !in levels) {
                levels[node.agentId] = 1
                attachDescendants(node.agentId, 1)
            }
        }

        val nodeRadii =
            orderedNodes.associate { node ->
                node.agentId to
                    when (node.role) {
                        "orchestrator" -> config.orchestratorRadius
                        "subordinate" -> config.subordinateRadius
                        else -> config.soloRadius
                    }.coerceAtLeast(0f)
            }
        val levelGroups = orderedNodes.groupBy { levels.getValue(it.agentId) }
        val positions = mutableMapOf<String, Pair<Float, Float>>()
        if (centerNode != null) positions[centerNode.agentId] = centerX to centerY

        val priorRings = mutableListOf<Pair<Float, Float>>() // ring radius, largest node radius on that ring
        val collisionMargin = COLLISION_MARGIN
        for ((level, ringNodes) in levelGroups.toSortedMap()) {
            if (level == 0) continue
            val maxNodeRadius = ringNodes.maxOf { nodeRadii.getValue(it.agentId) }
            val baseRadius = (config.baseRingRadius + (level - 1) * config.levelRingStep).coerceAtLeast(0f)
            val count = ringNodes.size
            val minimumSpacing = max(config.minNodeSpacing.coerceAtLeast(0f), maxNodeRadius * 2f + collisionMargin)
            val circumferenceRadius = count * config.minNodeSpacing.coerceAtLeast(0f) / (2f * PI.toFloat())
            val chordRadius =
                if (count > 1) {
                    minimumSpacing / (2f * sin(PI.toFloat() / count))
                } else {
                    0f
                }
            var ringRadius = max(baseRadius, max(circumferenceRadius, chordRadius))

            if (level == 1 && centerNode != null) {
                ringRadius = max(ringRadius, nodeRadii.getValue(centerNode.agentId) + maxNodeRadius + collisionMargin)
            }
            for ((previousRadius, previousMaxNodeRadius) in priorRings) {
                ringRadius = max(ringRadius, previousRadius + previousMaxNodeRadius + maxNodeRadius + collisionMargin)
            }

            val ringOrder = orderRing(ringNodes, previousLayout, centerX, centerY)
            val angleStep = 2f * PI.toFloat() / ringOrder.size
            val startAngle = ringStartAngle(ringOrder, previousLayout, centerX, centerY, angleStep)
            ringOrder.forEachIndexed { index, node ->
                val angle = startAngle + index * angleStep
                positions[node.agentId] =
                    (centerX + cos(angle) * ringRadius) to (centerY + sin(angle) * ringRadius)
            }
            priorRings += ringRadius to maxNodeRadius
        }

        val positionedNodes = linkedMapOf<String, PositionedNode>()
        for (node in orderedNodes) {
            val (x, y) = positions.getValue(node.agentId)
            val satellites =
                node.subagents
                    .sortedBy { it.taskId }
                    .mapIndexed { index, subagent ->
                        val angle = if (node.subagents.isEmpty()) 0f else 2f * PI.toFloat() * index / node.subagents.size
                        PositionedSatellite(
                            subagent = subagent,
                            parentAgentId = node.agentId,
                            orbitRadius = config.satelliteOrbitRadius.coerceAtLeast(0f),
                            baseAngleRad = angle,
                            radius = config.satelliteRadius.coerceAtLeast(0f),
                        )
                    }
            positionedNodes[node.agentId] =
                PositionedNode(
                    node = node,
                    x = x,
                    y = y,
                    radius = nodeRadii.getValue(node.agentId),
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
            centerNodeId = centerNodeId,
        )
    }

    private fun orderRing(
        ringNodes: List<MeshNode>,
        previousLayout: MeshLayoutResult?,
        centerX: Float,
        centerY: Float,
    ): List<MeshNode> {
        val previousNodes = previousLayout?.nodes.orEmpty()
        val existing =
            ringNodes
                .filter { it.agentId in previousNodes }
                .sortedWith(
                    compareBy<MeshNode>(
                        { angleOf(previousNodes.getValue(it.agentId), centerX, centerY) },
                        { it.agentId },
                    ),
                )
        val newNodes = ringNodes.filter { it.agentId !in previousNodes }.sortedBy { it.agentId }
        return existing + newNodes
    }

    private fun ringStartAngle(
        ringNodes: List<MeshNode>,
        previousLayout: MeshLayoutResult?,
        centerX: Float,
        centerY: Float,
        angleStep: Float,
    ): Float {
        if (previousLayout == null || ringNodes.isEmpty()) return DEFAULT_START_ANGLE
        val priorNodes = previousLayout.nodes
        val deltas =
            ringNodes.mapIndexedNotNull { index, node ->
                priorNodes[node.agentId]?.let { angleOf(it, centerX, centerY) - index * angleStep }
            }
        if (deltas.isEmpty()) return DEFAULT_START_ANGLE
        val sinSum = deltas.sumOf { sin(it.toDouble()) }
        val cosSum = deltas.sumOf { cos(it.toDouble()) }
        if (sinSum * sinSum + cosSum * cosSum < ANGLE_EPSILON) return DEFAULT_START_ANGLE
        return atan2(sinSum, cosSum).toFloat()
    }

    private fun angleOf(
        node: PositionedNode,
        centerX: Float,
        centerY: Float,
    ): Float = atan2(node.y - centerY, node.x - centerX)

    private fun positionEdge(
        edge: MeshEdge,
        nodes: Map<String, PositionedNode>,
    ): PositionedEdge? {
        val from = nodes[edge.a] ?: return null
        val to = nodes[edge.b] ?: return null
        val lowId = minOf(edge.a, edge.b)
        val highId = maxOf(edge.a, edge.b)
        val low = nodes.getValue(lowId)
        val high = nodes.getValue(highId)
        val dx = high.x - low.x
        val dy = high.y - low.y
        val length = sqrt(dx * dx + dy * dy)
        val normalX = if (length > 0f) -dy / length else 0f
        val normalY = if (length > 0f) dx / length else 1f
        val direction = if (edge.a <= edge.b) 1f else -1f
        val curvature = config.edgeCurveOffset.coerceAtLeast(0f) * direction
        val midpointX = (from.x + to.x) / 2f
        val midpointY = (from.y + to.y) / 2f
        return PositionedEdge(
            edge = edge,
            fromX = from.x,
            fromY = from.y,
            toX = to.x,
            toY = to.y,
            controlX = midpointX + normalX * curvature,
            controlY = midpointY + normalY * curvature,
            curvature = curvature,
        )
    }

    private fun calculateBounds(
        nodes: Map<String, PositionedNode>,
        edges: List<PositionedEdge>,
    ): LayoutBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        fun include(
            x: Float,
            y: Float,
            radius: Float = 0f,
        ) {
            minX = min(minX, x - radius)
            minY = min(minY, y - radius)
            maxX = max(maxX, x + radius)
            maxY = max(maxY, y + radius)
        }

        for (node in nodes.values) {
            include(node.x, node.y, node.radius)
            for (satellite in node.satellites) {
                val (offsetX, offsetY) = satellite.calculatePosition()
                include(node.x + offsetX, node.y + offsetY, satellite.radius)
            }
        }
        for (edge in edges) include(edge.controlX, edge.controlY)
        return LayoutBounds(minX, minY, maxX, maxY)
    }

    private companion object {
        const val COLLISION_MARGIN = 8f
        const val ANGLE_EPSILON = 1e-12
        val DEFAULT_START_ANGLE = -PI.toFloat() / 2f
    }
}
