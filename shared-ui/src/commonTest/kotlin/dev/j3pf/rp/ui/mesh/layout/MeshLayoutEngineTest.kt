package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import dev.j3pf.rp.protocol.MeshTodos
import dev.j3pf.rp.ui.mesh.safeCommBubbleCenterY
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshLayoutEngineTest {
    private val engine = MeshLayoutEngine()

    @Test
    fun `tidy tree centers parent horizontally over its children`() {
        val nodes =
            listOf(
                node("root", role = "orchestrator"),
                node("child-1", parent = "root", role = "subordinate", subagents = listOf(subagent("s1"))),
                node("child-2", parent = "root", role = "subordinate", subagents = listOf(subagent("s2"))),
                node("child-3", parent = "root", role = "subordinate", subagents = listOf(subagent("s3"))),
            )
        val result = engine.computeLayout(nodes, emptyList(), width = 1200f, height = 800f)
        val root = result.nodes.getValue("root")
        val c1 = result.nodes.getValue("child-1")
        val c3 = result.nodes.getValue("child-3")

        val expectedCenterX = (c1.x + c3.x) / 2f
        assertEquals(expectedCenterX, root.x, 2f)
        assertEquals(c1.y, c3.y, 0.1f)
    }

    @Test
    fun `same depth level aligns on the exact same row`() {
        val nodes =
            listOf(
                node("ceo", role = "orchestrator"),
                node("dir-a", parent = "ceo", role = "subordinate", subagents = listOf(subagent("sa"))),
                node("dir-b", parent = "ceo", role = "subordinate", subagents = listOf(subagent("sb"))),
                node("team-a1", parent = "dir-a", role = "subordinate", subagents = listOf(subagent("s1"))),
                node("team-b1", parent = "dir-b", role = "subordinate", subagents = listOf(subagent("s2"))),
            )
        val result = engine.computeLayout(nodes, emptyList(), width = 1200f, height = 800f)
        val dirA = result.nodes.getValue("dir-a")
        val dirB = result.nodes.getValue("dir-b")
        val teamA1 = result.nodes.getValue("team-a1")
        val teamB1 = result.nodes.getValue("team-b1")

        assertEquals(dirA.y, dirB.y, 0.1f)
        assertEquals(teamA1.y, teamB1.y, 0.1f)
        assertTrue(dirA.y < teamA1.y)
    }

    @Test
    fun `multiple roots sit side by side on top row without orphan nodes`() {
        val nodes =
            listOf(
                node("root-1", role = "orchestrator"),
                node("worker-1", parent = "root-1", role = "subordinate"),
                node("root-2", role = "orchestrator"),
                node("worker-2", parent = "root-2", role = "subordinate"),
                node("solo-node", role = "solo"),
            )
        val result = engine.computeLayout(nodes, emptyList(), width = 1200f, height = 800f)
        val r1 = result.nodes.getValue("root-1")
        val r2 = result.nodes.getValue("root-2")
        val solo = result.nodes.getValue("solo-node")

        assertEquals(0, r1.level)
        assertEquals(0, r2.level)
        assertEquals(0, solo.level)
        assertEquals(r1.y, r2.y, 0.1f)
        assertEquals(r1.y, solo.y, 0.1f)
        assertFalse(r1.intersects(r2))
        assertFalse(r1.intersects(solo))
        assertFalse(r2.intersects(solo))
    }

    @Test
    fun `leaf children exceeding threshold render as compact vertical list hanging under parent`() {
        val root = node("manager", role = "orchestrator")
        val leaves = (1..5).map { node("leaf-$it", parent = "manager", role = "subordinate") }
        val result = engine.computeLayout(listOf(root) + leaves, emptyList(), width = 600f, height = 800f)

        val manager = result.nodes.getValue("manager")
        assertEquals(NodeDisplayMode.CARD, manager.displayMode)

        val positionedLeaves = leaves.map { result.nodes.getValue(it.agentId) }
        positionedLeaves.forEach { leaf ->
            assertEquals(NodeDisplayMode.LEAF_ITEM, leaf.displayMode)
            assertEquals("manager", leaf.parentId)
            assertTrue(leaf.top >= manager.bottom)
        }

        // Leaves must be stacked vertically (each leaf has a greater y than the previous)
        for (i in 0 until positionedLeaves.size - 1) {
            assertTrue(
                positionedLeaves[i].y < positionedLeaves[i + 1].y,
                "Leaf $i (y=${positionedLeaves[i].y}) must be above leaf ${i + 1} (y=${positionedLeaves[i + 1].y})",
            )
            // They share approximately the same horizontal alignment under the manager
            assertEquals(positionedLeaves[i].x, positionedLeaves[i + 1].x, 5f)
        }
    }

    @Test
    fun `orthogonal connectors route from parent bottom through horizontal bus to child top`() {
        val root = node("root", role = "orchestrator")
        val c1 = node("child-1", parent = "root", role = "subordinate", subagents = listOf(subagent("s1")))
        val c2 = node("child-2", parent = "root", role = "subordinate", subagents = listOf(subagent("s2")))
        val edge1 = edge("e1", "root", "child-1")
        val edge2 = edge("e2", "root", "child-2")

        val result = engine.computeLayout(listOf(root, c1, c2), listOf(edge1, edge2), width = 800f, height = 600f)
        val pRoot = result.nodes.getValue("root")
        val pC1 = result.nodes.getValue("child-1")
        val pC2 = result.nodes.getValue("child-2")

        val posEdge1 = result.edges.first { it.edge.edgeId == "e1" }
        val posEdge2 = result.edges.first { it.edge.edgeId == "e2" }

        assertTrue(posEdge1.routePoints.size >= 4, "Edge 1 must have at least 4 route points for orthogonal bus")
        assertTrue(posEdge2.routePoints.size >= 4, "Edge 2 must have at least 4 route points for orthogonal bus")

        // First point is parent bottom-center
        assertEquals(pRoot.x, posEdge1.routePoints.first().x, 1f)
        assertEquals(pRoot.bottom, posEdge1.routePoints.first().y, 1f)

        // Last point is child top-center
        assertEquals(pC1.x, posEdge1.routePoints.last().x, 1f)
        assertEquals(pC1.top, posEdge1.routePoints.last().y, 1f)

        // The horizontal bus Y is between parent bottom and child top, and identical for sibling edges
        val busY1 = posEdge1.routePoints[1].y
        val busY2 = posEdge2.routePoints[1].y
        assertEquals(busY1, busY2, 0.5f)
        assertTrue(busY1 > pRoot.bottom)
        assertTrue(busY1 < pC1.top)
    }

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
    fun `node bounds fit within viewport when scaled to min readable scale for one through twelve agents`() {
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
                val bounds = result.bounds
                val widthFitScale = if (bounds.width > 0f) width / bounds.width else 1f
                // At min readable scale (0.4x), wide trees fit horizontally
                val effectiveScale = minOf(1f, widthFitScale).coerceAtLeast(0.40f)
                assertTrue(
                    bounds.width * effectiveScale <= width + 50f,
                    "Tree width ${bounds.width} at scale $effectiveScale exceeds viewport $width for count=$count",
                )
            }
        }
    }

    @Test
    fun `many subordinate leaves render as compact vertical list hanging under manager`() {
        val result = engine.computeLayout(hierarchy(12), emptyList(), width = 360f, height = 640f)
        val orchestrator = result.nodes.getValue("root")
        val leaves = result.nodes.values.filter { it.displayMode == NodeDisplayMode.LEAF_ITEM }

        assertEquals(11, leaves.size)
        assertTrue(leaves.all { it.parentId == "root" })
        assertTrue(leaves.all { orchestrator.bottom <= it.top })
    }

    @Test
    fun `leaf threshold clusters only when row overflows at min readable scale or leaves exceed four`() {
        // 3 leaves fit as cards side by side on 360dp phone (Spec S4, D8)
        val threeLeaves = engine.computeLayout(hierarchy(4), emptyList(), width = 360f, height = 640f)
        val threeLeavesSubordinates = threeLeaves.nodes.values.filter { it.level == 1 }
        assertEquals(3, threeLeavesSubordinates.size)
        assertTrue(threeLeavesSubordinates.all { it.displayMode == NodeDisplayMode.CARD })
        assertEquals(3, threeLeavesSubordinates.map { it.x }.distinct().size)

        // 5 leaves exceed threshold (> 4), clustering into a compact vertical list (D8)
        val fiveLeaves = engine.computeLayout(hierarchy(6), emptyList(), width = 360f, height = 640f)
        val fiveLeavesSubordinates = fiveLeaves.nodes.values.filter { it.level == 1 }
        assertEquals(5, fiveLeavesSubordinates.size)
        assertTrue(fiveLeavesSubordinates.all { it.displayMode == NodeDisplayMode.LEAF_ITEM })
        assertEquals(1, fiveLeavesSubordinates.map { it.x }.distinct().size)

        val tablet = engine.computeLayout(hierarchy(4), emptyList(), width = 800f, height = 1280f)
        assertTrue(tablet.nodes.values.all { it.width <= 280f })
        assertTrue(tablet.nodes.getValue("root").width >= 200f)
    }

    @Test
    fun `card heights follow node content and row spacing uses the tallest card`() {
        val nodes =
            listOf(
                node("root", role = "orchestrator").copy(todos = MeshTodos(total = 2, done = 1), subagents = listOf(subagent("root-task"))),
                node("child-a", parent = "root", role = "subordinate").copy(todos = MeshTodos(total = 2, done = 0)),
                node("child-b", parent = "root", role = "subordinate").copy(subagents = listOf(subagent("child-task"))),
            )
        val result = engine.computeLayout(nodes, emptyList(), width = 411f, height = 750f)
        val root = result.nodes.getValue("root")
        val childA = result.nodes.getValue("child-a")
        val childB = result.nodes.getValue("child-b")

        assertEquals(156f, root.height)
        assertEquals(126f, childA.height)
        assertEquals(130f, childB.height)
        assertEquals(childA.y, childB.y)
        assertEquals(root.bottom + 56f + childB.height / 2f, childB.y)
    }

    @Test
    fun `measured card heights override estimates and determine each row height`() {
        val nodes =
            listOf(
                node("root", role = "orchestrator"),
                node("child-a", parent = "root", role = "subordinate"),
                node("child-b", parent = "root", role = "subordinate"),
            )
        val result =
            engine.computeLayout(
                nodes,
                emptyList(),
                width = 411f,
                height = 750f,
                cardHeights = mapOf("root" to 184f, "child-a" to 172f, "child-b" to 236f),
            )
        val root = result.nodes.getValue("root")
        val childA = result.nodes.getValue("child-a")
        val childB = result.nodes.getValue("child-b")

        assertEquals(184f, root.height)
        assertEquals(172f, childA.height)
        assertEquals(236f, childB.height)
        assertEquals(childA.y, childB.y)
        assertEquals(root.bottom + engine.config.cardSpacingY + childB.height / 2f, childB.y)
        assertFalse(root.intersects(childA))
        assertFalse(root.intersects(childB))
    }

    @Test
    fun `base card height never drops below one hundred and empty todos add no height`() {
        val lowHeightEngine = MeshLayoutEngine(LayoutConfig(cardHeight = 80f, minCardHeight = 76f))
        val nodes =
            listOf(
                node("plain", role = "orchestrator"),
                node("empty-todos", parent = "plain", role = "subordinate").copy(todos = MeshTodos(total = 0, done = 0)),
            )
        val result = lowHeightEngine.computeLayout(nodes, emptyList())

        assertEquals(100f, result.nodes.getValue("plain").height)
        assertEquals(100f, result.nodes.getValue("empty-todos").height)
    }

    @Test
    fun `hierarchy bus routes pass around intermediate cards`() {
        val root = node("root", role = "orchestrator")
        val middleA = node("middle-a", parent = "root", role = "subordinate")
        val middleB = node("middle-b", parent = "root", role = "subordinate")
        val target = node("target", parent = "middle-b", role = "subordinate")
        val result =
            engine.computeLayout(
                listOf(root, middleA, middleB, target),
                listOf(edge("long", "root", "target")),
                width = 411f,
                height = 750f,
            )
        val route = result.edges.single().routePoints
        val endpoints = setOf("root", "target")

        assertTrue(route.size >= 6, "Expected a bus route around intermediate rows, got $route")
        result.nodes.values.filter { it.node.agentId !in endpoints }.forEach { card ->
            route.zipWithNext().forEach { (from, to) ->
                assertFalse(
                    segmentCrossesCardInterior(from, to, card),
                    "Edge route $from -> $to crosses ${card.node.agentId}",
                )
            }
        }
    }

    @Test
    fun `tall layouts retain intrinsic card height and extend below the viewport for panning`() {
        val deepHierarchy =
            listOf(
                node("root", role = "orchestrator"),
                node("level-1", parent = "root", role = "subordinate", subagents = listOf(subagent("s1"))),
                node("level-2", parent = "level-1", role = "subordinate", subagents = listOf(subagent("s2"))),
                node("level-3", parent = "level-2", role = "subordinate"),
            )
        val result = engine.computeLayout(deepHierarchy, emptyList(), width = 360f, height = 300f)

        assertTrue(result.nodes.values.all { it.height >= 100f })
        assertTrue(result.nodes.values.maxOf { it.bottom } > 300f)
    }

    @Test
    fun `communication bubble clears cards beyond its sender and receiver`() {
        val blockingCards =
            listOf(
                LayoutBounds(80f, 90f, 120f, 150f),
                LayoutBounds(90f, 170f, 110f, 210f),
            )
        val centerY =
            safeCommBubbleCenterY(
                midpointY = 110f,
                senderBottom = 100f,
                receiverTop = 156f,
                otherCards = blockingCards,
                bubbleCenterX = 100f,
                bubbleHalfWidth = 20f,
            )

        assertEquals(234f, centerY)
        blockingCards.forEach { card ->
            assertTrue(centerY <= card.minY - 24f || centerY >= card.maxY + 24f)
        }
    }

    @Test
    fun `node bounds and intersection helper use card dimensions and margin`() {
        val left = PositionedNode(node("left"), x = 50f, y = 50f, level = 0)
        val adjacent = PositionedNode(node("right"), x = 251f, y = 50f, width = 200f, height = 90f, level = 0)

        assertEquals(200f, left.width)
        assertEquals(100f, left.height)
        assertEquals(LayoutBounds(-50f, 0f, 150f, 100f), left.bounds())
        assertFalse(left.intersects(adjacent))
        assertTrue(left.intersects(adjacent, margin = 2f))
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
        assertEquals(0, disconnected.nodes.getValue("solo-b").level)
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
    fun `density sweep across one through twelve agents with zero to four subagents preserves legible card width and prevents overlaps`() {
        val density = 2.625f
        val widthPx = 1080f
        val heightPx = 1800f
        val widthDp = widthPx / density
        val heightDp = heightPx / density

        for (count in 1..12) {
            for (subagentCount in 0..4) {
                val nodes =
                    hierarchy(count).map { node ->
                        val subs = (1..subagentCount).map { subagent("sub-$it") }
                        node.copy(subagents = subs)
                    }
                val result = engine.computeLayout(nodes, emptyList(), width = widthDp, height = heightDp)
                result.nodes.values.forEach { node ->
                    assertTrue(
                        node.width >= 180f - 0.01f,
                        "Node ${node.node.agentId} has width ${node.width} < 180dp at count=$count, subs=$subagentCount",
                    )
                }
                val positioned = result.nodes.values.toList()
                positioned.forEachIndexed { index, left ->
                    positioned.drop(index + 1).forEach { right ->
                        assertFalse(
                            left.intersects(right, margin = 2f),
                            "${left.node.agentId} overlaps ${right.node.agentId} at count=$count with $subagentCount subs",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `three siblings of uneven depth do not overlap deeper subtrees`() {
        val nodes =
            listOf(
                node("root", role = "orchestrator"),
                node("A", parent = "root", role = "subordinate", subagents = listOf(subagent("sa"))),
                node("A1", parent = "A", role = "subordinate", subagents = listOf(subagent("sa1"))),
                node("A2", parent = "A", role = "subordinate", subagents = listOf(subagent("sa2"))),
                node("A11", parent = "A1", role = "subordinate", subagents = listOf(subagent("sa11"))),
                node("A21", parent = "A2", role = "subordinate", subagents = listOf(subagent("sa21"))),
                node("B", parent = "root", role = "subordinate", subagents = listOf(subagent("sb"))),
                node("C", parent = "root", role = "subordinate", subagents = listOf(subagent("sc"))),
                node("C1", parent = "C", role = "subordinate", subagents = listOf(subagent("sc1"))),
                node("C11", parent = "C1", role = "subordinate", subagents = listOf(subagent("sc11"))),
            )
        val result = engine.computeLayout(nodes, emptyList(), width = 2000f, height = 1200f)
        val positioned = result.nodes.values.toList()
        positioned.forEachIndexed { i, left ->
            positioned.drop(i + 1).forEach { right ->
                assertFalse(
                    left.intersects(right, margin = 2f),
                    "${left.node.agentId} overlaps ${right.node.agentId}",
                )
            }
        }
    }

    @Test
    fun `leaf list taller than next row reserves sufficient vertical space without overlapping subsequent depth levels`() {
        val leaves = (1..8).map { node("leaf-$it", parent = "manager-a", role = "subordinate") }
        val nodes =
            listOf(
                node("root", role = "orchestrator"),
                node("manager-a", parent = "root", role = "subordinate", subagents = listOf(subagent("sma"))),
                node("manager-b", parent = "root", role = "subordinate", subagents = listOf(subagent("smb"))),
                node("sub-b1", parent = "manager-b", role = "subordinate", subagents = listOf(subagent("sb1"))),
                node("sub-b2", parent = "sub-b1", role = "subordinate", subagents = listOf(subagent("sb2"))),
            ) + leaves
        val result = engine.computeLayout(nodes, emptyList(), width = 1200f, height = 1200f)
        val positioned = result.nodes.values.toList()
        positioned.forEachIndexed { i, left ->
            positioned.drop(i + 1).forEach { right ->
                assertFalse(
                    left.intersects(right, margin = 2f),
                    "${left.node.agentId} overlaps ${right.node.agentId}",
                )
            }
        }
    }

    @Test
    fun `random forest layout ensures no two node rects intersect and no connector crosses a node`() {
        // Deterministic pseudo-random seed generator
        var seed = 42L

        fun nextRandom(bound: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            val v = ((seed ushr 33) % bound).toInt()
            return if (v < 0) -v else v
        }

        // Build a forest of 3 roots with various depths and branching
        val allNodes = mutableListOf<MeshNode>()
        val allEdges = mutableListOf<MeshEdge>()

        for (r in 1..3) {
            val rootId = "forest-root-$r"
            allNodes += node(rootId, role = "orchestrator")
            var currentLevelIds = listOf(rootId)

            for (depth in 1..3) {
                val nextLevelIds = mutableListOf<String>()
                for (parent in currentLevelIds) {
                    val childCount = nextRandom(3) + 1
                    for (c in 1..childCount) {
                        val childId = "node-$r-$depth-${parent.takeLast(4)}-$c"
                        val isLeaf = depth == 3 || nextRandom(2) == 0
                        allNodes +=
                            node(
                                childId,
                                parent = parent,
                                role = "subordinate",
                                subagents = if (!isLeaf) listOf(subagent("s-$childId")) else emptyList(),
                            )
                        allEdges += edge("e-$parent-$childId", parent, childId)
                        if (!isLeaf) {
                            nextLevelIds += childId
                        }
                    }
                }
                currentLevelIds = nextLevelIds
                if (currentLevelIds.isEmpty()) break
            }
        }

        val result = engine.computeLayout(allNodes, allEdges, width = 1400f, height = 1200f)
        val positioned = result.nodes.values.toList()

        // 1. No node intersects any other node
        positioned.forEachIndexed { i, left ->
            positioned.drop(i + 1).forEach { right ->
                assertFalse(
                    left.intersects(right, margin = 2f),
                    "${left.node.agentId} overlaps ${right.node.agentId} in random forest",
                )
            }
        }

        // 2. No connector segment crosses the interior of any node card or leaf
        result.edges.forEach { edge ->
            val route = edge.routePoints
            if (route.size >= 2) {
                for (idx in 0 until route.lastIndex) {
                    val p1 = route[idx]
                    val p2 = route[idx + 1]
                    positioned.forEach { card ->
                        assertFalse(
                            segmentCrossesCardInterior(p1, p2, card),
                            "Edge ${edge.edge.edgeId} segment ($p1 -> $p2) crosses interior of ${card.node.agentId}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `sibling roots have no connector drawn between them`() {
        val root1 = node("root-1", role = "orchestrator")
        val root2 = node("root-2", role = "orchestrator")
        val edges = listOf(edge("peer-edge", "root-1", "root-2"))
        val result = engine.computeLayout(listOf(root1, root2), edges, width = 800f, height = 600f)

        // Sibling roots must not have a connector drawn between them
        assertTrue(result.edges.isEmpty(), "Sibling roots should not have tree connector edges")
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

    private fun segmentCrossesCardInterior(
        from: EdgePoint,
        to: EdgePoint,
        card: PositionedNode,
    ): Boolean =
        if (from.x == to.x) {
            from.x > card.left &&
                from.x < card.right &&
                maxOf(minOf(from.y, to.y), card.top) < minOf(maxOf(from.y, to.y), card.bottom)
        } else if (from.y == to.y) {
            from.y > card.top &&
                from.y < card.bottom &&
                maxOf(minOf(from.x, to.x), card.left) < minOf(maxOf(from.x, to.x), card.right)
        } else {
            false
        }

    private fun edge(
        id: String,
        from: String,
        to: String,
    ) = MeshEdge(edgeId = id, a = from, b = to)

    private fun subagent(id: String) = MeshSubagent(taskId = id, agent = "agent-$id", label = id, status = "running")
}
