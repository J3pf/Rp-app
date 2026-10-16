package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshLayoutEngineTest {
    private val engine = MeshLayoutEngine()

    @Test
    fun `identical input produces stable positions and edges`() {
        val nodes =
            listOf(
                node("worker-b", parent = "root", role = "subordinate"),
                node("root", role = "orchestrator"),
                node("worker-a", parent = "root", role = "subordinate"),
            )
        val edges = listOf(edge("e2", "worker-a", "worker-b"), edge("e1", "root", "worker-a"))

        val first = engine.computeLayout(nodes, edges)
        repeat(5) {
            val next = engine.computeLayout(nodes.reversed(), edges.reversed())
            assertEquals(first.nodes.mapValues { it.value.x to it.value.y }, next.nodes.mapValues { it.value.x to it.value.y })
            assertEquals(first.edges, next.edges)
        }
    }

    @Test
    fun `hierarchy uses center and expanding concentric rings`() {
        val nodes =
            listOf(
                node("root", role = "orchestrator"),
                node("first", parent = "root", role = "subordinate"),
                node("second", parent = "first", role = "subordinate"),
                node("third", parent = "second", role = "subordinate"),
            )

        val result = engine.computeLayout(nodes, emptyList(), width = 800f, height = 600f)
        val center = result.nodes.getValue("root")
        val direct = result.nodes.getValue("first")
        val nested = result.nodes.getValue("second")
        val deeper = result.nodes.getValue("third")

        assertEquals("root", result.centerNodeId)
        assertEquals(0, center.level)
        assertEquals(1, direct.level)
        assertEquals(2, nested.level)
        assertEquals(3, deeper.level)
        assertEquals(400f, center.x)
        assertEquals(300f, center.y)
        assertEquals(engine.config.baseRingRadius, distance(center, direct), 0.01f)
        assertEquals(engine.config.baseRingRadius + engine.config.levelRingStep, distance(center, nested), 0.01f)
        assertEquals(engine.config.baseRingRadius + 2f * engine.config.levelRingStep, distance(center, deeper), 0.01f)
    }

    @Test
    fun `group members without parent links occupy the orchestrator direct ring`() {
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
        assertEquals(engine.config.baseRingRadius, distance(result.nodes.getValue("root"), result.nodes.getValue("member-a")), 0.01f)
        assertEquals(engine.config.baseRingRadius, distance(result.nodes.getValue("root"), result.nodes.getValue("member-b")), 0.01f)
    }

    @Test
    fun `satellites orbit parent at configured radius with even angular spacing`() {
        val subagents =
            listOf(
                subagent("task-1"),
                subagent("task-2"),
                subagent("task-3"),
                subagent("task-4"),
            )
        val result = engine.computeLayout(listOf(node("root", role = "orchestrator", subagents = subagents)), emptyList())
        val satellites = result.nodes.getValue("root").satellites

        assertEquals(4, satellites.size)
        satellites.forEachIndexed { index, satellite ->
            assertEquals("root", satellite.parentAgentId)
            assertEquals(engine.config.satelliteOrbitRadius, satellite.orbitRadius)
            assertEquals(engine.config.satelliteRadius, satellite.radius)
            assertEquals(index * (2f * PI.toFloat() / 4f), satellite.baseAngleRad, 0.0001f)
            val (x, y) = satellite.calculatePosition()
            assertEquals(engine.config.satelliteOrbitRadius, hypot(x.toDouble(), y.toDouble()).toFloat(), 0.001f)
            val (offsetX, offsetY) = satellite.calculatePosition(PI.toFloat() / 2f)
            assertEquals(engine.config.satelliteOrbitRadius, hypot(offsetX.toDouble(), offsetY.toDouble()).toFloat(), 0.001f)
        }
    }

    @Test
    fun `nodes on crowded rings have no collisions`() {
        val nodes =
            buildList {
                add(node("root", role = "orchestrator"))
                repeat(35) { add(node("worker-${it.toString().padStart(2, '0')}", parent = "root", role = "subordinate")) }
            }
        val result = engine.computeLayout(nodes, emptyList())
        val positioned = result.nodes.values.toList()

        positioned.forEachIndexed { index, left ->
            positioned.drop(index + 1).forEach { right ->
                assertTrue(
                    distance(left, right) >= left.radius + right.radius + 8f - 0.01f,
                    "${left.node.agentId} overlaps ${right.node.agentId}",
                )
            }
        }
    }

    @Test
    fun `edge control points are perpendicular and reverse edges curve apart`() {
        val nodes = listOf(node("a", role = "orchestrator"), node("b", parent = "a", role = "subordinate"))
        val forward = edge("forward", "a", "b")
        val backward = edge("backward", "b", "a")
        val result = engine.computeLayout(nodes, listOf(forward, backward))
        val positioned = result.edges.associateBy { it.edge.edgeId }

        val first = positioned.getValue("forward")
        val second = positioned.getValue("backward")
        val dx = first.toX - first.fromX
        val dy = first.toY - first.fromY
        val firstMidX = (first.fromX + first.toX) / 2f
        val firstMidY = (first.fromY + first.toY) / 2f
        val controlOffsetX = first.controlX - firstMidX
        val controlOffsetY = first.controlY - firstMidY

        assertEquals(0f, dx * controlOffsetX + dy * controlOffsetY, 0.01f)
        assertEquals(engine.config.edgeCurveOffset, hypot(controlOffsetX.toDouble(), controlOffsetY.toDouble()).toFloat(), 0.01f)
        val normalDot =
            (first.controlX - firstMidX) * (second.controlX - firstMidX) +
                (first.controlY - firstMidY) * (second.controlY - firstMidY)
        assertTrue(normalDot < 0f, "reverse edges should bend to opposite sides")
        assertTrue(first.curvature * second.curvature < 0f)
    }

    @Test
    fun `previous layout preserves angular ordering as nodes are added`() {
        val initialNodes =
            listOf(
                node("root", role = "orchestrator"),
                node("a", parent = "root", role = "subordinate"),
                node("b", parent = "root", role = "subordinate"),
                node("c", parent = "root", role = "subordinate"),
            )
        val previous = engine.computeLayout(initialNodes, emptyList())
        val expanded =
            engine.computeLayout(
                initialNodes + node("d", parent = "root", role = "subordinate"),
                emptyList(),
                previousLayout = previous,
            )
        val angles =
            listOf("a", "b", "c").map { id ->
                val point = expanded.nodes.getValue(id)
                atan2(point.y - 400f, point.x - 400f)
            }

        assertTrue(angles.zipWithNext().all { (left, right) -> positiveAngle(right - left) > 0f })
    }

    @Test
    fun `empty single disconnected and cyclic inputs are handled`() {
        val empty = engine.computeLayout(emptyList(), emptyList())
        assertTrue(empty.nodes.isEmpty())
        assertTrue(empty.edges.isEmpty())
        assertNull(empty.centerNodeId)
        assertEquals(0f, empty.bounds.width)
        assertEquals(0f, empty.bounds.height)

        val single = engine.computeLayout(listOf(node("only", role = "orchestrator")), emptyList())
        assertEquals(0, single.nodes.getValue("only").level)
        assertEquals(1, engine.computeLayout(listOf(node("solo", role = "solo")), emptyList()).nodes.size)

        val disconnected =
            engine.computeLayout(
                listOf(node("solo-b", role = "solo"), node("solo-a", role = "solo")),
                emptyList(),
            )
        assertNull(disconnected.centerNodeId)
        assertTrue(distance(disconnected.nodes.getValue("solo-a"), disconnected.nodes.getValue("solo-b")) > 0f)

        val cyclic =
            engine.computeLayout(
                listOf(
                    node("cycle-a", parent = "cycle-b", role = "subordinate"),
                    node("cycle-b", parent = "cycle-a", role = "subordinate"),
                ),
                emptyList(),
            )
        assertEquals(2, cyclic.nodes.size)
        assertEquals(1, cyclic.nodes.values.count { it.level == 0 })
        assertTrue(cyclic.nodes.values.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun `large layout stays deterministic and finishes within fifty milliseconds`() {
        val nodes =
            buildList {
                add(node("root", role = "orchestrator"))
                repeat(39) { index ->
                    val parent = if (index == 0) "root" else "node-${(index - 1) / 3}"
                    add(node("node-$index", parent = parent, role = "subordinate"))
                }
            }
        val start = System.nanoTime()
        val first = engine.computeLayout(nodes, emptyList())
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val second = engine.computeLayout(nodes, emptyList())

        assertEquals(first.nodes.mapValues { it.value.x to it.value.y }, second.nodes.mapValues { it.value.x to it.value.y })
        assertTrue(elapsedMs < 50.0, "layout took ${elapsedMs}ms")
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

    private fun distance(
        left: PositionedNode,
        right: PositionedNode,
    ): Float = hypot((left.x - right.x).toDouble(), (left.y - right.y).toDouble()).toFloat()

    private fun positiveAngle(angle: Float): Float = ((angle % (2f * PI.toFloat())) + 2f * PI.toFloat()) % (2f * PI.toFloat())
}
