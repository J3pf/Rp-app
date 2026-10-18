package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshLayoutEngineTest {
    private val engine = MeshLayoutEngine()

    @Test
    fun `orchestrator is centered at the top and subordinates are below it`() {
        val width = 360f
        val result = engine.computeLayout(hierarchy(4), emptyList(), width = width, height = 640f)
        val orchestrator = result.nodes.getValue("root")

        assertEquals("root", result.centerNodeId)
        assertEquals(width / 2f, orchestrator.x)
        assertEquals(0, orchestrator.level)
        result.nodes.values.filter { it !== orchestrator }.forEach { subordinate ->
            assertTrue(orchestrator.y < subordinate.y)
        }
    }

    @Test
    fun `hierarchy levels are laid out strictly top down`() {
        val nodes =
            listOf(
                node("root", role = "orchestrator"),
                node("first", parent = "root", role = "subordinate"),
                node("second", parent = "first", role = "subordinate"),
                node("third", parent = "second", role = "subordinate"),
            )
        val result = engine.computeLayout(nodes, emptyList(), width = 800f, height = 1200f)

        assertEquals(listOf(0, 1, 2, 3), listOf("root", "first", "second", "third").map { result.nodes.getValue(it).level })
        nodes.filter { it.parentAgentId != null }.forEach { child ->
            assertTrue(result.nodes.getValue(child.parentAgentId!!).y < result.nodes.getValue(child.agentId).y)
        }
    }

    @Test
    fun `one through twelve node layouts have no bounding box collisions in all viewports`() {
        val viewports =
            listOf(
                360f to 640f,
                411f to 750f,
                800f to 480f,
                800f to 1200f,
            )
        for ((width, height) in viewports) {
            for (count in 1..12) {
                val result = engine.computeLayout(hierarchy(count), emptyList(), width = width, height = height)
                val positioned = result.nodes.values.toList()
                positioned.forEachIndexed { index, left ->
                    positioned.drop(index + 1).forEach { right ->
                        assertFalse(
                            left.intersects(right, margin = 2f),
                            "${left.node.agentId} overlaps ${right.node.agentId} at ${width}x$height",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `node bounds remain inside all supported viewports for one through twelve agents`() {
        val viewports =
            listOf(
                360f to 640f,
                411f to 750f,
                800f to 480f,
                800f to 1200f,
            )
        for ((width, height) in viewports) {
            for (count in 1..12) {
                val result = engine.computeLayout(hierarchy(count), emptyList(), width = width, height = height)
                result.nodes.values.forEach { positioned ->
                    assertTrue(positioned.left >= 0f, "${positioned.node.agentId} left=${positioned.left} at ${width}x$height")
                    assertTrue(positioned.top >= 0f, "${positioned.node.agentId} top=${positioned.top} at ${width}x$height")
                    assertTrue(positioned.right <= width, "${positioned.node.agentId} right=${positioned.right} at ${width}x$height")
                    assertTrue(positioned.bottom <= height, "${positioned.node.agentId} bottom=${positioned.bottom} at ${width}x$height")
                    assertEquals(maxOf(positioned.width, positioned.height) / 2f, positioned.radius)
                }
            }
        }
    }

    @Test
    fun `many subordinates wrap into rows below the orchestrator`() {
        val result = engine.computeLayout(hierarchy(12), emptyList(), width = 360f, height = 640f)
        val orchestrator = result.nodes.getValue("root")
        val subordinateRows =
            result.nodes.values
                .filter { it.level == 1 }
                .map { it.y }
                .distinct()

        assertTrue(subordinateRows.size > 1)
        assertTrue(subordinateRows.all { orchestrator.y < it })
    }

    @Test
    fun `node bounds and intersection helper use card dimensions and margin`() {
        val left = PositionedNode(node("left"), x = 50f, y = 50f, level = 0, satellites = emptyList())
        val adjacent = PositionedNode(node("right"), x = 251f, y = 50f, width = 200f, height = 90f, level = 0, satellites = emptyList())

        assertEquals(200f, left.width)
        assertEquals(90f, left.height)
        assertEquals(100f, left.radius)
        assertEquals(LayoutBounds(-50f, 5f, 150f, 95f), left.bounds())
        assertFalse(left.intersects(adjacent))
        assertTrue(left.intersects(adjacent, margin = 2f))
    }

    @Test
    fun `subagents have static offsets attached within the parent card`() {
        val subagents = (1..4).map { subagent("task-$it") }
        val parent =
            engine
                .computeLayout(
                    listOf(node("root", role = "orchestrator", subagents = subagents)),
                    emptyList(),
                ).nodes
                .getValue("root")

        assertEquals(4, parent.satellites.size)
        parent.satellites.forEach { satellite ->
            assertEquals("root", satellite.parentAgentId)
            assertEquals(0f, satellite.orbitRadius)
            val (offsetX, offsetY) = satellite.calculatePosition()
            assertEquals(satellite.offsetX, offsetX)
            assertEquals(satellite.offsetY, offsetY)
            assertTrue(offsetX - satellite.width / 2f >= -parent.width / 2f)
            assertTrue(offsetX + satellite.width / 2f <= parent.width / 2f)
            assertTrue(offsetY - satellite.height / 2f >= -parent.height / 2f)
            assertTrue(offsetY + satellite.height / 2f <= parent.height / 2f)
        }
    }

    @Test
    fun `identical input in any order produces identical positions and edges`() {
        val nodes =
            listOf(
                node("worker-b", parent = "root", role = "subordinate"),
                node("root", role = "orchestrator"),
                node("worker-a", parent = "root", role = "subordinate"),
            )
        val edges = listOf(edge("e2", "worker-a", "worker-b"), edge("e1", "root", "worker-a"))
        val first = engine.computeLayout(nodes, edges, width = 411f, height = 750f)

        repeat(5) {
            val next = engine.computeLayout(nodes.reversed(), edges.reversed(), width = 411f, height = 750f)
            assertEquals(first.nodes.mapValues { it.value.x to it.value.y }, next.nodes.mapValues { it.value.x to it.value.y })
            assertEquals(first.edges, next.edges)
        }
    }

    @Test
    fun `previous layout preserves horizontal order for existing nodes`() {
        val initialNodes =
            listOf(
                node("root", role = "orchestrator"),
                node("a", parent = "root", role = "subordinate"),
                node("b", parent = "root", role = "subordinate"),
                node("c", parent = "root", role = "subordinate"),
            )
        val baseline = engine.computeLayout(initialNodes, emptyList(), width = 800f, height = 800f)
        val previous =
            baseline.copy(
                nodes =
                    baseline.nodes.mapValues { (id, positioned) ->
                        when (id) {
                            "a" -> positioned.copy(x = 700f, y = 300f)
                            "b" -> positioned.copy(x = 500f, y = 300f)
                            "c" -> positioned.copy(x = 300f, y = 300f)
                            else -> positioned
                        }
                    },
            )
        val expanded =
            engine.computeLayout(
                initialNodes + node("d", parent = "root", role = "subordinate"),
                emptyList(),
                width = 800f,
                height = 800f,
                previousLayout = previous,
            )
        val existingOrder =
            expanded.nodes.values
                .filter { it.level == 1 }
                .sortedWith(compareBy({ it.y }, { it.x }))
                .map { it.node.agentId }

        assertEquals(listOf("c", "b", "a", "d"), existingOrder)
    }

    @Test
    fun `group members without parent links are direct orchestrator children`() {
        val result =
            engine.computeLayout(
                listOf(
                    node("root", role = "orchestrator", groupId = "g"),
                    node("member-b", role = "subordinate", groupId = "g"),
                    node("member-a", role = "subordinate", groupId = "g"),
                ),
                emptyList(),
            )

        assertEquals(0, result.nodes.getValue("root").level)
        assertEquals(1, result.nodes.getValue("member-a").level)
        assertEquals(1, result.nodes.getValue("member-b").level)
        assertTrue(result.nodes.getValue("root").y < result.nodes.getValue("member-a").y)
        assertTrue(result.nodes.getValue("root").y < result.nodes.getValue("member-b").y)
    }

    @Test
    fun `hierarchy edges start at parent bottom and end at child top`() {
        val result =
            engine.computeLayout(
                listOf(node("root", role = "orchestrator"), node("child", parent = "root", role = "subordinate")),
                listOf(edge("tree", "root", "child")),
            )
        val parent = result.nodes.getValue("root")
        val child = result.nodes.getValue("child")
        val positioned = result.edges.single()

        assertEquals(parent.x, positioned.fromX)
        assertEquals(parent.bottom, positioned.fromY)
        assertEquals(child.x, positioned.toX)
        assertEquals(child.top, positioned.toY)
    }

    @Test
    fun `peer edges retain opposite bezier curves when direction is reversed`() {
        val nodes =
            listOf(
                node("a", role = "orchestrator"),
                node("b", parent = "a", role = "subordinate"),
                node("c", parent = "a", role = "subordinate"),
            )
        val forward = edge("forward", "b", "c")
        val backward = edge("backward", "c", "b")
        val result = engine.computeLayout(nodes, listOf(forward, backward))
        val positioned = result.edges.associateBy { it.edge.edgeId }
        val first = positioned.getValue("forward")
        val second = positioned.getValue("backward")

        val dx = first.toX - first.fromX
        val dy = first.toY - first.fromY
        val midpointX = (first.fromX + first.toX) / 2f
        val midpointY = (first.fromY + first.toY) / 2f
        val controlOffsetX = first.controlX - midpointX
        val controlOffsetY = first.controlY - midpointY
        assertEquals(0f, dx * controlOffsetX + dy * controlOffsetY, 0.01f)
        assertEquals(engine.config.edgeCurveOffset, hypot(controlOffsetX.toDouble(), controlOffsetY.toDouble()).toFloat(), 0.01f)
        assertTrue(first.curvature * second.curvature < 0f)
    }

    @Test
    fun `empty disconnected and cyclic inputs are handled`() {
        val empty = engine.computeLayout(emptyList(), emptyList())
        assertTrue(empty.nodes.isEmpty())
        assertTrue(empty.edges.isEmpty())
        assertNull(empty.centerNodeId)
        assertEquals(0f, empty.bounds.width)
        assertEquals(0f, empty.bounds.height)

        val solo = engine.computeLayout(listOf(node("solo", role = "solo")), emptyList())
        assertEquals(1, solo.nodes.size)
        assertEquals(0, solo.nodes.getValue("solo").level)

        val disconnected =
            engine.computeLayout(
                listOf(node("solo-b", role = "solo"), node("solo-a", role = "solo")),
                emptyList(),
            )
        assertEquals("solo-a", disconnected.centerNodeId)
        assertEquals(0, disconnected.nodes.getValue("solo-a").level)
        assertEquals(1, disconnected.nodes.getValue("solo-b").level)
        assertFalse(disconnected.nodes.getValue("solo-a").intersects(disconnected.nodes.getValue("solo-b")))

        val cyclic =
            engine.computeLayout(
                listOf(
                    node("cycle-a", parent = "cycle-b", role = "subordinate"),
                    node("cycle-b", parent = "cycle-a", role = "subordinate"),
                ),
                emptyList(),
            )
        assertEquals(2, cyclic.nodes.size)
        assertTrue(cyclic.nodes.values.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun `satellites with positive orbit radius retain animated orbit calculation`() {
        val satellite =
            PositionedSatellite(
                subagent = subagent("task"),
                parentAgentId = "root",
                orbitRadius = 48f,
                baseAngleRad = 0f,
                radius = 10f,
            )
        val (x, y) = satellite.calculatePosition()
        assertEquals(48f, hypot(x.toDouble(), y.toDouble()).toFloat(), 0.001f)
        val (rotatedX, rotatedY) = satellite.calculatePosition(PI.toFloat() / 2f)
        assertEquals(48f, hypot(rotatedX.toDouble(), rotatedY.toDouble()).toFloat(), 0.001f)
        assertEquals(PI.toFloat() / 2f, atan2(rotatedY, rotatedX), 0.001f)
    }

    private fun hierarchy(count: Int): List<MeshNode> =
        buildList {
            add(node("root", role = "orchestrator"))
            repeat(count - 1) { index ->
                add(node("worker-${index.toString().padStart(2, '0')}", parent = "root", role = "subordinate"))
            }
        }

    private fun node(
        id: String,
        parent: String? = null,
        role: String = "solo",
        groupId: String? = null,
        subagents: List<MeshSubagent> = emptyList(),
    ) = MeshNode(
        agentId = id,
        machineId = "machine",
        machineName = "Machine",
        name = id,
        status = "idle",
        role = role,
        parentAgentId = parent,
        groupId = groupId,
        subagents = subagents,
    )

    private fun edge(
        id: String,
        from: String,
        to: String,
    ) = MeshEdge(edgeId = id, a = from, b = to)

    private fun subagent(id: String) = MeshSubagent(taskId = id, agent = "agent-$id", label = id, status = "running")
}
