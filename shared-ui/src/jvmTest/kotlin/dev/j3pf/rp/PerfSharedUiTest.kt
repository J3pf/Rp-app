package dev.j3pf.rp

import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshCommEvent
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import dev.j3pf.rp.protocol.MeshTodos
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.ui.diff.DiffRowKind
import dev.j3pf.rp.ui.diff.parseDiffRows
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.mesh.layout.NodeDisplayMode
import dev.j3pf.rp.ui.shared.SharedAppController
import dev.j3pf.rp.ui.theme.CuteDarkColors
import dev.j3pf.rp.ui.theme.CuteLightColors
import dev.j3pf.rp.ui.theme.computeRpSurfaceTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PerfSharedUiTest {
    private fun sampleSession() =
        Session(
            relayId = "0123456789abcdef",
            relayUrl = "https://relay.example.com",
            relayName = "Studio Mac",
            deviceId = "dev-1",
            deviceToken = "tok-1",
            pushKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        )

    private fun meshNode(
        id: String,
        role: String = "subordinate",
        parentId: String? = null,
        groupId: String? = "g1",
        status: String = "idle",
        activity: String? = null,
        todos: MeshTodos? = null,
        subagents: List<MeshSubagent> = emptyList(),
    ) = MeshNode(
        agentId = id,
        machineId = "m1",
        machineName = "Workstation",
        machineColor = "#7AA2F7",
        name = "Agent $id",
        status = status,
        online = true,
        role = role,
        parentAgentId = parentId,
        groupId = groupId,
        activity = activity,
        todos = todos,
        subagents = subagents,
    )

    @Test
    fun `MeshLayoutEngine linear pre-indexing preserves clustered leaf bus and multi-level row geometry`() {
        val engine = MeshLayoutEngine()
        val root = meshNode(id = "root", role = "orchestrator", status = "working")
        val manager = meshNode(id = "mgr", role = "subordinate", parentId = "root")
        // 5 leaf children under root trigger leaf clustering (> default leafThreshold = 4)
        val leaves = (1..5).map { i -> meshNode(id = "leaf-$i", role = "subordinate", parentId = "root") }
        val deepChild = meshNode(id = "deep", role = "subordinate", parentId = "mgr")
        val nodes = listOf(root, manager, deepChild) + leaves
        val edges =
            listOf(
                MeshEdge(edgeId = "e-root-mgr", a = "root", b = "mgr"),
                MeshEdge(edgeId = "e-mgr-deep", a = "mgr", b = "deep"),
                // Multi-level skip edge from root (level 0) directly to deep (level 2)
                MeshEdge(edgeId = "e-root-deep", a = "root", b = "deep"),
            ) + leaves.map { leaf -> MeshEdge(edgeId = "e-root-${leaf.agentId}", a = "root", b = leaf.agentId) }

        val result = engine.computeLayout(nodes = nodes, edges = edges, width = 800f, height = 800f)

        assertEquals(nodes.size, result.nodes.size)
        val leafPositioned = leaves.map { result.nodes.getValue(it.agentId) }
        assertTrue(leafPositioned.all { it.displayMode == NodeDisplayMode.LEAF_ITEM })
        val firstLeafTop = leafPositioned.minOf { it.top }
        val rootPos = result.nodes.getValue("root")
        val expectedBusY = (rootPos.bottom + firstLeafTop) / 2f

        for (leaf in leaves) {
            val edge = result.edges.first { it.edge.edgeId == "e-root-${leaf.agentId}" }
            assertTrue(edge.routePoints.size >= 4, "Leaf edge should route along vertical spine")
            assertEquals(expectedBusY, edge.routePoints[1].y, 0.01f)
        }

        val skipEdge = result.edges.first { it.edge.edgeId == "e-root-deep" }
        assertEquals(6, skipEdge.routePoints.size, "Multi-level edge should route through left channel")
    }

    @Test
    fun `MeshLayoutEngine cosmetic-only updates preserve exact coordinates while topology changes recompute`() {
        val engine = MeshLayoutEngine()
        val initialNodes =
            listOf(
                meshNode(id = "root", role = "orchestrator", status = "working", activity = "Step 1"),
                meshNode(id = "child", role = "subordinate", parentId = "root", status = "idle", activity = "Waiting"),
            )
        val initialEdges = listOf(MeshEdge(edgeId = "e1", a = "root", b = "child", pendingAsk = false))

        val (firstLayout, firstFp) =
            engine.computeOrUpdateLayout(
                nodes = initialNodes,
                edges = initialEdges,
                width = 411f,
                height = 800f,
            )

        // Cosmetic-only update: status, activity, pendingAsk changed, topology unchanged
        val cosmeticNodes =
            listOf(
                meshNode(id = "root", role = "orchestrator", status = "working", activity = "Step 2"),
                meshNode(id = "child", role = "subordinate", parentId = "root", status = "working", activity = "Running tests"),
            )
        val cosmeticEdges = listOf(MeshEdge(edgeId = "e1", a = "root", b = "child", pendingAsk = true))

        val (secondLayout, secondFp) =
            engine.computeOrUpdateLayout(
                nodes = cosmeticNodes,
                edges = cosmeticEdges,
                width = 411f,
                height = 800f,
                previousLayout = firstLayout,
                previousFingerprint = firstFp,
            )

        assertEquals(firstFp, secondFp)
        assertEquals(firstLayout.bounds, secondLayout.bounds)
        assertEquals(firstLayout.nodes.getValue("root").x, secondLayout.nodes.getValue("root").x)
        assertEquals(firstLayout.nodes.getValue("child").y, secondLayout.nodes.getValue("child").y)
        val updatedRoot = secondLayout.nodes.getValue("root")
        val updatedChild = secondLayout.nodes.getValue("child")
        val updatedEdge = secondLayout.edges.single()
        assertEquals("Step 2", updatedRoot.node.activity)
        assertEquals("Running tests", updatedChild.node.activity)
        assertTrue(updatedEdge.edge.pendingAsk)

        // Topology change: new node added -> fingerprint changes and layout recomputes
        val expandedNodes = cosmeticNodes + meshNode(id = "child-2", role = "subordinate", parentId = "root")
        val expandedEdges = cosmeticEdges + MeshEdge(edgeId = "e2", a = "root", b = "child-2")
        val (thirdLayout, thirdFp) =
            engine.computeOrUpdateLayout(
                nodes = expandedNodes,
                edges = expandedEdges,
                width = 411f,
                height = 800f,
                previousLayout = secondLayout,
                previousFingerprint = secondFp,
            )

        assertNotEquals(secondFp, thirdFp)
        assertEquals(3, thirdLayout.nodes.size)
        assertNotEquals(secondLayout.nodes.getValue("child").x, thirdLayout.nodes.getValue("child").x)
    }

    @Test
    fun `SharedAppController stores single reduced ChatState reference without duplicate reduction`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
            val controller =
                SharedAppController(
                    scope = scope,
                    secrets = secrets,
                    isSocketConnected = { true },
                    sendSocketText = { true },
                    connState = MutableStateFlow(ConnState.Connected),
                )

            val agentId = "m1:a-single"
            val session = controller.chatSessions.open(agentId)

            controller.wsClient.onText(
                Envelope
                    .of(
                        MsgType.AGENT_HISTORY,
                        AgentHistory(agentId = agentId, epoch = "ep-1", events = emptyList()),
                    ).encode(),
            )
            val cachedAfterHistory = assertNotNull(controller.cachedChatState(agentId))
            assertSame(session.state.value, cachedAfterHistory, "History cache must reuse session's already-reduced ChatState")

            val persistedEvent =
                AgentEvent(
                    agentId = agentId,
                    epoch = "ep-1",
                    seq = 1L,
                    e =
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("tool_execution_start"),
                                "toolCallId" to JsonPrimitive("tc-1"),
                                "toolName" to JsonPrimitive("read"),
                            ),
                        ),
                )
            controller.wsClient.onText(Envelope.of(MsgType.AGENT_EVENT, persistedEvent).encode())

            val cachedAfterEvent = assertNotNull(controller.cachedChatState(agentId))
            assertSame(session.state.value, cachedAfterEvent, "Event cache must reuse session's already-reduced ChatState")
            assertTrue("tc-1" in cachedAfterEvent.tools)

            controller.dispose()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `SharedAppController caps cache at 8 and onMemoryWarning evicts only inactive histories`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            assertEquals(8, SharedAppController.MAX_CACHED_CHAT_HISTORIES)
            val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
            val controller =
                SharedAppController(
                    scope = scope,
                    secrets = secrets,
                    isSocketConnected = { true },
                    sendSocketText = { true },
                    connState = MutableStateFlow(ConnState.Connected),
                )

            // Populate 5 inactive histories
            for (i in 1..5) {
                val id = "m1:inactive-$i"
                controller.chatSessions.open(id)
                controller.wsClient.onText(
                    Envelope
                        .of(
                            MsgType.AGENT_HISTORY,
                            AgentHistory(agentId = id, epoch = "1", events = emptyList()),
                        ).encode(),
                )
                controller.chatSessions.release(id)
            }

            // Keep 1 active open session
            val activeId = "m1:active"
            controller.chatSessions.open(activeId)
            controller.wsClient.onText(
                Envelope
                    .of(
                        MsgType.AGENT_HISTORY,
                        AgentHistory(agentId = activeId, epoch = "1", events = emptyList()),
                    ).encode(),
            )
            assertEquals(6, controller.cachedChatHistoryCount)

            controller.onMemoryWarning()

            assertEquals(1, controller.cachedChatHistoryCount)
            assertEquals(listOf(activeId), controller.cachedChatHistoryIds())

            // Releasing active session and receiving another memory warning clears all entries
            controller.chatSessions.release(activeId)
            controller.onMemoryWarning()
            assertEquals(0, controller.cachedChatHistoryCount)

            controller.dispose()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `SharedAppController coalesces mesh comm bubble cleanup into a single replaceable job`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
            val controller =
                SharedAppController(
                    scope = scope,
                    secrets = secrets,
                    isSocketConnected = { true },
                    sendSocketText = { true },
                    connState = MutableStateFlow(ConnState.Connected),
                )

            for (i in 1..10) {
                val comm =
                    MeshComm(
                        commKey = "c-$i",
                        fromAgentId = "a1",
                        toAgentId = "a2",
                        fromName = "A1",
                        toName = "A2",
                        text = "Ping $i",
                        at = i.toLong(),
                    )
                controller.wsClient.onText(Envelope.of(MsgType.MESH_COMM, MeshCommEvent(comm)).encode())
            }

            assertEquals(10, controller.meshState.value.activeBubbles.size)
            assertTrue(controller.hasPendingMeshBubbleCleanupJob)

            controller.dispose()
            assertFalse(controller.hasPendingMeshBubbleCleanupJob)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `parseDiffRows classifies headers hunks additions deletions and context with stable indices`() {
        val patch =
            listOf(
                "--- a/Foo.kt",
                "+++ b/Foo.kt",
                "@@ -1,3 +1,3 @@",
                " val unchanged = 1",
                "-val old = 2",
                "+val updated = 3",
            ).joinToString("\n")

        val rows = parseDiffRows(patch)

        assertEquals(6, rows.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), rows.map { it.index })
        assertEquals(
            listOf(
                DiffRowKind.CONTEXT,
                DiffRowKind.CONTEXT,
                DiffRowKind.HUNK,
                DiffRowKind.CONTEXT,
                DiffRowKind.DELETION,
                DiffRowKind.ADDITION,
            ),
            rows.map { it.kind },
        )
        assertEquals("+val updated = 3", rows[5].text)
    }

    @Test
    fun `computeRpSurfaceTokens produces distinct light and dark surface tokens`() {
        val darkTokens = computeRpSurfaceTokens(CuteDarkColors, isDark = true)
        val lightTokens = computeRpSurfaceTokens(CuteLightColors, isDark = false)

        assertNotEquals(darkTokens.elevatedDeck, lightTokens.elevatedDeck)
        assertNotEquals(darkTokens.insetBlock, lightTokens.insetBlock)
        assertNotEquals(darkTokens.hairline, lightTokens.hairline)
        assertEquals(computeRpSurfaceTokens(CuteDarkColors), darkTokens)
        assertEquals(computeRpSurfaceTokens(CuteLightColors), lightTokens)
    }
}
