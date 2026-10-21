package dev.j3pf.rp.ui.mesh

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.CommAction
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MeshSubagent
import dev.j3pf.rp.protocol.MeshTodos
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MeshScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `formatters localize mesh roles statuses actions and hidden content`() {
        assertEquals("Orquestador", formatMeshRole("orchestrator"))
        assertEquals("Subordinado", formatMeshRole("subordinate"))
        assertEquals("Individual", formatMeshRole("solo"))
        assertEquals("Trabajando", formatMeshStatus("working"))
        assertEquals("Esperando respuesta", formatMeshStatus("waiting_input"))
        assertEquals("Inactivo", formatMeshStatus("idle"))
        assertEquals("Desconectado", formatMeshStatus("offline"))
        assertEquals("Error", formatMeshStatus("error"))
        assertEquals("Compactando", formatMeshStatus("compacting"))
        assertEquals("En ejecución", formatMeshSubagentStatus("running"))
        assertEquals("Terminado", formatMeshSubagentStatus("done"))
        assertEquals("Completado", formatMeshSubagentStatus("completed"))
        assertEquals("Trabajando", formatMeshSubagentStatus("working"))
        assertEquals("Error", formatMeshSubagentStatus("error"))
        assertEquals("Detenido", formatMeshSubagentStatus("stopped"))
        assertEquals("Pregunta", formatMeshCommAction("ask"))
        assertEquals("Respuesta", formatMeshCommAction("reply"))
        assertEquals("Pase", formatMeshCommAction("handover"))
        assertEquals("Envío", formatMeshCommAction("send"))
        assertEquals("Mensaje", formatMeshCommAction("message"))
        assertEquals("Mensaje", formatMeshCommAction("unknown"))
        assertEquals("Contenido oculto", formatMeshCommText(null))
        assertEquals("3 de 5 tareas", formatMeshTodos(3, 5))
    }

    @Test
    fun `loading state names the agent mesh`() {
        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = MeshUiState(isLoading = true),
                    onOpenChat = {},
                )
            }
        }

        rule.onNodeWithText("Cargando red de agentes…").assertIsDisplayed()
    }

    @Test
    fun `empty state explains how agents appear`() {
        val empty = MeshSnapshot()
        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState =
                        MeshUiState(
                            isLoading = false,
                            snapshot = empty,
                            machineChips =
                                listOf(
                                    MeshMachineChip(machineId = null, name = "", color = "", agentCount = 0),
                                ),
                            groupChips =
                                listOf(
                                    MeshGroupChip(groupId = MeshViewModel.SOLO_GROUP_ID, label = "", isSolo = true, memberCount = 0),
                                ),
                        ),
                    onOpenChat = {},
                )
            }
        }

        rule.onNodeWithText("Red de agentes").assertIsDisplayed()
        rule.onNodeWithText("Máquinas").assertIsDisplayed()
        rule.onNodeWithText("Grupos").assertIsDisplayed()
        rule.onNodeWithText("Todas").assertIsDisplayed()
        rule.onNodeWithText("Sin grupo · 0").assertIsDisplayed()
        rule.onNodeWithText("Vista de lista").assertIsDisplayed()
        rule.onNodeWithText("No hay agentes").assertIsDisplayed()
        rule.onNodeWithText("Abre Pi con el plugin rp en tus PCs para ver la red de agentes aquí.").assertIsDisplayed()
    }

    @Test
    fun `toggle to list view displays nodes accessibly`() {
        val nodes = listOf(node("orchestrator", "Orquestador Principal", "orchestrator"), node("worker", "Worker Protocol", "subordinate"))
        val snapshot = MeshSnapshot(nodes = nodes)
        val layout = MeshLayoutEngine().computeLayout(nodes, emptyList())
        val state = MeshUiState(isLoading = false, snapshot = snapshot, layoutResult = layout)
        val isList = mutableStateOf(false)

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = if (isList.value) state.copy(isListView = true) else state,
                    onOpenChat = {},
                    onToggleListView = { isList.value = !isList.value },
                )
            }
        }

        // In diagram view, full node label texts are displayed in accessibility targets
        rule.onNodeWithContentDescription("Orquestador Orquestador Principal, Inactivo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Subordinado Worker Protocol, Inactivo").assertIsDisplayed()

        rule.onNodeWithText("Vista de lista").performClick()
        rule.onNodeWithText("Vista de diagrama").assertIsDisplayed()
        rule.onNodeWithText("Orquestador Principal").assertIsDisplayed()
        rule.onNodeWithText("Worker Protocol").assertIsDisplayed()
        rule.onNodeWithContentDescription("Orquestador Orquestador Principal, Inactivo").assertIsDisplayed()
    }

    @Test
    fun `tapping leaf item opens node detail sheet`() {
        val manager = node("manager-1", "Jefe de Equipo", "orchestrator")
        val leaves = (1..5).map { node("leaf-$it", "Especialista $it", "subordinate", parent = "manager-1") }
        val allNodes = listOf(manager) + leaves
        val snapshot = MeshSnapshot(nodes = allNodes)
        val layout = MeshLayoutEngine().computeLayout(allNodes, emptyList(), width = 600f, height = 800f)
        val clickedNodeId = mutableStateOf<String?>(null)
        val state =
            mutableStateOf(
                MeshUiState(
                    isLoading = false,
                    snapshot = snapshot,
                    layoutResult = layout,
                ),
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = state.value,
                    onOpenChat = {},
                    onNodeClick = { id ->
                        clickedNodeId.value = id
                        val found = allNodes.first { it.agentId == id }
                        state.value = state.value.copy(nodeDetail = MeshNodeDetail(found))
                    },
                )
            }
        }

        // Leaf items should be displayed with their names
        rule.onNodeWithText("Especialista 1").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals("leaf-1", clickedNodeId.value)
        rule.onNodeWithText("Detalles").assertExists()
    }

    @Test
    fun `recenter action button is displayed and clickable`() {
        val root = node("root", "Orquestador", "orchestrator")
        val snapshot = MeshSnapshot(nodes = listOf(root))
        val layout = MeshLayoutEngine().computeLayout(listOf(root), emptyList())
        val state = MeshUiState(isLoading = false, snapshot = snapshot, layoutResult = layout)

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = state, onOpenChat = {})
            }
        }

        rule.onNodeWithContentDescription("Recentrar organigrama").assertIsDisplayed().performClick()
        rule.onNodeWithText("Recentrar").assertIsDisplayed()
    }

    @Test
    fun `TalkBack hierarchy semantics include reporta a on subordinate leaf items`() {
        val manager = node("orch", "Maestro", "orchestrator")
        val worker = node("w1", "Colaborador", "subordinate", parent = "orch")
        val snapshot = MeshSnapshot(nodes = listOf(manager, worker))
        val layout = MeshLayoutEngine().computeLayout(listOf(manager, worker), emptyList())
        val state = MeshUiState(isLoading = false, snapshot = snapshot, layoutResult = layout)

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = state, onOpenChat = {})
            }
        }

        val expectedDesc = "Subordinado Colaborador, Inactivo, reporta a Maestro"
        rule.onNodeWithContentDescription(expectedDesc).assertExists()
    }

    @Test
    fun `tapping node opens node detail sheet`() {
        val agent = node("agent-1", "Review Agent", "solo").copy(todos = MeshTodos(total = 3, done = 1))
        val snapshot = MeshSnapshot(nodes = listOf(agent))
        val layout = MeshLayoutEngine().computeLayout(snapshot.nodes, snapshot.edges)
        val state = mutableStateOf(MeshUiState(isLoading = false, snapshot = snapshot, layoutResult = layout))

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = state.value,
                    onOpenChat = {},
                    onNodeClick = { state.value = state.value.copy(nodeDetail = MeshNodeDetail(agent)) },
                )
            }
        }

        rule.onNodeWithContentDescription("Individual Review Agent, Inactivo, 1 de 3 tareas").performClick()
        rule.waitForIdle()
        rule
            .onAllNodes(
                androidx.compose.ui.test
                    .hasText("Review Agent"),
            ).fetchSemanticsNodes()
            .size
            .let {
                kotlin.test.assertTrue(it >= 1)
            }
        rule.onNodeWithText("Detalles").assertExists()
        rule.onNodeWithText("TODOs").assertExists()
        rule.onNodeWithText("Máquina").assertExists()
        rule.onNodeWithText("Progreso").assertExists()
        rule.onNodeWithText("No hay subagentes").assertExists()
        rule.onNodeWithText("Sin conversaciones recientes").assertExists()
        rule.onNodeWithText("Mostrar detalles técnicos").assertExists().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Directorio (cwd)").assertExists()
        rule.onNodeWithText("ID de agente").assertExists()
        rule.onNodeWithText("Abrir chat").assertExists()
        rule.onNodeWithText("Cerrar").assertExists()
    }

    @Test
    fun `conversation sheet localizes title count hidden content and action`() {
        val message =
            MeshComm(
                commKey = "comm-1",
                fromAgentId = "a",
                toAgentId = "b",
                fromName = "Ana",
                toName = "Beto",
                action = CommAction.ASK,
                text = null,
                at = 1L,
            )
        val conversation =
            MeshConversationDetail(
                agentA = "a",
                agentB = "b",
                agentAName = "Ana",
                agentBName = "Beto",
                items = listOf(message),
                nextBefore = 0L,
            )
        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = MeshUiState(isLoading = false, snapshot = MeshSnapshot(), conversationDetail = conversation),
                    onOpenChat = {},
                )
            }
        }

        rule.onNodeWithText("Conversación entre Ana y Beto").assertExists()
        rule.onNodeWithText("1 mensajes").assertExists()
        rule.onNodeWithText("Cargar mensajes anteriores").assertExists()
        rule.onNodeWithText("Contenido oculto").assertExists()
        rule.onNodeWithText("Pregunta").assertExists()
    }

    @Test
    fun `disconnected banner shows stale data and retry action`() {
        val state = MeshUiState(isLoading = false, isConnected = false, snapshot = MeshSnapshot())
        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = state, onOpenChat = {}, onRetry = {})
            }
        }

        rule.onNodeWithText("Desconectado — mostrando datos locales").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test
    fun `list view renders all 3 agent rows when snapshot has agents`() {
        val nodes =
            listOf(
                MeshNode(
                    agentId = "node-1",
                    machineId = "m1",
                    machineName = "Machine 1",
                    name = "Orchestrator Agent",
                    role = "orchestrator",
                    status = "working",
                ),
                MeshNode(
                    agentId = "node-2",
                    machineId = "m1",
                    machineName = "Machine 1",
                    name = "Subordinate Worker 1",
                    role = "subordinate",
                    status = "working",
                ),
                MeshNode(
                    agentId = "node-3",
                    machineId = "m1",
                    machineName = "Machine 1",
                    name = "Subordinate Worker 2",
                    role = "subordinate",
                    status = "idle",
                ),
            )
        val snapshot =
            MeshSnapshot(
                groups =
                    listOf(
                        dev.j3pf.rp.protocol.MeshGroup(
                            groupId = "g1",
                            label = "Orchestrator",
                            memberAgentIds = listOf("node-1", "node-2", "node-3"),
                        ),
                    ),
                nodes = nodes,
                edges = emptyList(),
            )
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                isListView = true,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = uiState,
                    onOpenChat = {},
                )
            }
        }

        rule.onNodeWithText("Orchestrator Agent", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Subordinate Worker 1", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Subordinate Worker 2", substring = true).assertIsDisplayed()
    }

    @Test
    fun `404 error shows unsupported relay message and retry button without skeleton`() {
        var retried = false
        val uiState =
            MeshUiState(
                isLoading = false,
                errorMessage = "Tu relay no soporta la Red de agentes. Actualiza Rp-relay a la versión 0.5.0 o superior.",
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = uiState,
                    onOpenChat = {},
                    onRetry = { retried = true },
                )
            }
        }

        rule.onNodeWithText("Tu relay no soporta la Red de agentes. Actualiza Rp-relay a la versión 0.5.0 o superior.").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").assertIsDisplayed().performClick()
        rule
            .onAllNodes(
                androidx.compose.ui.test
                    .hasText("Cargando red de agentes…"),
            ).fetchSemanticsNodes()
            .isEmpty()
        kotlin.test.assertTrue(retried)
    }

    @Test
    fun `network error shows generic message and retry button without skeleton`() {
        val uiState =
            MeshUiState(
                isLoading = false,
                errorMessage = "Error al cargar la red de agentes",
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = uiState,
                    onOpenChat = {},
                )
            }
        }

        rule.onNodeWithText("Error al cargar la red de agentes").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").assertIsDisplayed()
        rule
            .onAllNodes(
                androidx.compose.ui.test
                    .hasText("Cargando red de agentes…"),
            ).fetchSemanticsNodes()
            .isEmpty()
    }

    @Test
    fun `retry recovery transitions from error to loaded nodes`() {
        val nodes = listOf(node("agent-1", "Agente Principal", "solo"))
        val snapshot = MeshSnapshot(nodes = nodes)
        val layout = MeshLayoutEngine().computeLayout(nodes, emptyList())

        var state by
            androidx.compose.runtime.mutableStateOf(
                MeshUiState(
                    isLoading = false,
                    errorMessage = "Tu relay no soporta la Red de agentes. Actualiza Rp-relay a la versión 0.5.0 o superior.",
                ),
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = state,
                    onOpenChat = {},
                    onRetry = {
                        state =
                            MeshUiState(
                                isLoading = false,
                                errorMessage = null,
                                snapshot = snapshot,
                                layoutResult = layout,
                                isListView = true,
                            )
                    },
                )
            }
        }

        rule.onNodeWithText("Reintentar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Agente Principal").assertIsDisplayed()
    }

    @Test
    fun `subordinate card gives its header width to the agent name instead of a role badge`() {
        val subordinate = node("worker", "Long Subordinate Agent Name", "subordinate")

        rule.setContent {
            RpTheme {
                MeshAgentCard(node = subordinate, allNodes = listOf(subordinate), onClick = {})
            }
        }

        rule.onNodeWithText("Long Subordinate Agent Name").assertIsDisplayed()
        kotlin.test.assertTrue(
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Subordinado"),
                ).fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `communication bubble center stays 24dp clear of sender and receiver cards`() {
        val centerY = safeCommBubbleCenterY(midpointY = 110f, senderBottom = 100f, receiverTop = 156f)

        assertEquals(124f, centerY)
        kotlin.test.assertTrue(centerY >= 100f + 24f)
        kotlin.test.assertTrue(centerY <= 156f - 24f)
    }

    @Test
    fun `agent card displays display name, role, activity, todos, subagents, and responds to click`() {
        var clicked = false
        val subagents =
            listOf(
                dev.j3pf.rp.protocol.MeshSubagent(
                    taskId = "task-1",
                    agent = "Worker UI",
                    label = "Pruebas UI",
                    status = "working",
                ),
            )
        val cardNode =
            MeshNode(
                agentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                machineId = "m1",
                machineName = "Omarchy",
                name = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                cwd = "/home/drift/Workspace/RP/Rp-app",
                gitBranch = "main",
                status = "working",
                role = "orchestrator",
                activity = "Refactorizando UI",
                online = true,
                todos = MeshTodos(total = 5, done = 3, inProgress = 1, blocked = 0, pending = 1),
                subagents = subagents,
            )

        rule.setContent {
            RpTheme {
                MeshAgentCard(
                    node = cardNode,
                    allNodes = listOf(cardNode),
                    onClick = { clicked = true },
                )
            }
        }

        // Display name must be "Rp-app", not the raw UUID!
        rule.onNodeWithText("Rp-app").assertIsDisplayed()
        rule.onNodeWithText("Omarchy · main").assertIsDisplayed()
        rule.onNodeWithText("Orquestador").assertIsDisplayed()
        rule.onNodeWithText("Trabajando").assertIsDisplayed()
        rule.onNodeWithText("· Refactorizando UI", substring = true).assertIsDisplayed()
        rule.onNodeWithText("3/5").assertIsDisplayed()
        rule.onNodeWithText("Pruebas UI").assertIsDisplayed()

        rule.onNodeWithText("Rp-app").performClick()
        kotlin.test.assertTrue(clicked)
    }

    @Test
    fun `four-agent rendered diagram at 411dp displays readable cards with width at least 140dp strictly below filters`() {
        val orch =
            MeshNode(
                agentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
                gitBranch = "main",
                activity = "Coordinando red",
            )
        val sub1 =
            MeshNode(
                agentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                name = "01a14fbb-44e3-765f-99e1-…",
                role = "subordinate",
                parentAgentId = orch.agentId,
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-relay",
                gitBranch = "main",
            )
        val sub2 =
            MeshNode(
                agentId = "subagent-chat-01a14fbb-2812-7332",
                machineId = "m-croma",
                machineName = "Croma",
                name = "subagent-chat-01a14fbb-2812-7332",
                role = "subordinate",
                parentAgentId = orch.agentId,
                status = "idle",
                online = true,
                cwd = "/home/drift/Workspace/RP",
                gitBranch = "feat/mesh",
            )
        val sub3 =
            MeshNode(
                agentId = "node-croma-plugin",
                machineId = "m-croma",
                machineName = "Croma",
                name = "Worker Plugin",
                role = "subordinate",
                parentAgentId = orch.agentId,
                status = "offline",
                online = false,
                cwd = "/home/drift/Workspace/RP/Rp-plugin",
            )
        val allNodes = listOf(orch, sub1, sub2, sub3)
        val layout = MeshLayoutEngine().computeLayout(allNodes, emptyList(), width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes),
                layoutResult = layout,
                isListView = false,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 4),
                        MeshMachineChip(machineId = "m-omarchy", name = "Omarchy", color = "#6750A4", agentCount = 2),
                        MeshMachineChip(machineId = "m-croma", name = "Croma", color = "#7AA2F7", agentCount = 2),
                    ),
                groupChips =
                    listOf(
                        MeshGroupChip(groupId = "grp-1", label = "Rp-app", memberCount = 4),
                    ),
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = uiState,
                    onOpenChat = {},
                )
            }
        }

        // Verify human display name is visible and no raw UUIDs
        rule.onNodeWithText("Rp-app").assertIsDisplayed()
        rule.onNodeWithText("Rp-relay").assertIsDisplayed()
        rule.onNodeWithText("RP").assertIsDisplayed()
        rule.onNodeWithText("Worker Plugin").assertIsDisplayed()

        // Filter chips bounding box
        val machinesHeaderBounds = rule.onNodeWithText("Máquinas").fetchSemanticsNode().boundsInRoot
        val groupsHeaderBounds = rule.onNodeWithText("Grupos").fetchSemanticsNode().boundsInRoot
        val highestFilterBottom = maxOf(machinesHeaderBounds.bottom, groupsHeaderBounds.bottom)

        // Verify all 4 cards have width >= 140dp in pixels and are strictly below filter chips
        val minCardWidthPx = with(rule.density) { 140.dp.toPx() }
        val orchBounds = rule.onNodeWithText("Rp-app").fetchSemanticsNode().boundsInRoot
        val sub1Bounds = rule.onNodeWithText("Rp-relay").fetchSemanticsNode().boundsInRoot
        val sub2Bounds = rule.onNodeWithText("RP").fetchSemanticsNode().boundsInRoot
        val sub3Bounds = rule.onNodeWithText("Worker Plugin").fetchSemanticsNode().boundsInRoot

        listOf(orchBounds, sub1Bounds, sub2Bounds, sub3Bounds).forEach { bounds ->
            kotlin.test.assertTrue(bounds.width >= minCardWidthPx - 1f, "Card width ${bounds.width} < 140dp ($minCardWidthPx px)")
            kotlin.test.assertTrue(
                bounds.top >= highestFilterBottom,
                "Card top ${bounds.top} intersects filter bottom $highestFilterBottom",
            )
        }
        val machineChipBottom =
            rule
                .onNodeWithText("Omarchy · 2")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val groupChipBottom =
            rule
                .onNodeWithText("Rp-app · 4")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        assertOrchestratorCardInsideViewport("Rp-app", maxOf(machineChipBottom, groupChipBottom))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `diagram card legibility 1 agent`() {
        val orch =
            MeshNode(
                agentId = "node-omarchy-app",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Orquestador Maestro",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
            )
        val allNodes = listOf(orch)
        val layout = MeshLayoutEngine().computeLayout(allNodes, emptyList(), width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        assertCardMeetsLegibility("Orquestador Maestro")
        assertOrchestratorCardInsideViewport("Orquestador Maestro")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `diagram card legibility 4 agents 360dp`() {
        val (allNodes, edges) = build4AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 360f, height = 500f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        listOf("Rp-app", "Rp-relay", "RP", "Worker Plugin").forEach { name ->
            assertCardMeetsLegibility(name)
        }
        assertOrchestratorCardInsideViewport("Rp-app")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `diagram card legibility 4 agents 411dp`() {
        val (allNodes, edges) = build4AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        listOf("Rp-app", "Rp-relay", "RP", "Worker Plugin").forEach { name ->
            assertCardMeetsLegibility(name)
        }
        assertOrchestratorCardInsideViewport("Rp-app")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `diagram card legibility 12 agents`() {
        val (allNodes, edges) = build12AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        assertCardMeetsLegibility("Orquestador Maestro")
        (1..11).forEach { i ->
            assertCardMeetsLegibility("Worker-$i")
        }
        assertOrchestratorCardInsideViewport("Orquestador Maestro")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `diagram card legibility 12 agents in short phone viewport anchors orchestrator at top`() {
        val (allNodes, edges) = build12AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 360f, height = 520f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        assertCardMeetsLegibility("Orquestador Maestro")
        assertOrchestratorCardInsideViewport("Orquestador Maestro", expectedTopOffset = with(rule.density) { 16.dp.toPx() })
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-xhdpi")
    fun `diagram card legibility landscape`() {
        val (allNodes, edges) = build4AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 800f, height = 480f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        listOf("Rp-app", "Rp-relay", "RP", "Worker Plugin").forEach { name ->
            assertCardMeetsLegibility(name)
        }
        assertOrchestratorCardInsideViewport("Rp-app")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun `compact landscape filters share a row while orchestrator and first subordinate row remain visible`() {
        val (nodes, edges) = build4AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(nodes, edges, width = 891f, height = 411f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = nodes, edges = edges),
                layoutResult = layout,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 4),
                        MeshMachineChip(machineId = "m-omarchy", name = "Omarchy", color = "#6750A4", agentCount = 2),
                        MeshMachineChip(machineId = "m-croma", name = "Croma", color = "#7AA2F7", agentCount = 2),
                    ),
                groupChips = listOf(MeshGroupChip(groupId = "grp-1", label = "Rp-app", memberCount = 4)),
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        val machineLabel =
            rule
                .onNodeWithText("Máquinas")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val groupLabel =
            rule
                .onNodeWithText("Grupos")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals((machineLabel.top + machineLabel.bottom) / 2f, (groupLabel.top + groupLabel.bottom) / 2f, 1f)

        val headerBottom =
            rule
                .onNodeWithText("Red de agentes")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val machineChip = rule.onNodeWithText("Todas · 4").fetchSemanticsNode().boundsInRoot
        val groupChip = rule.onNodeWithText("Rp-app · 4").fetchSemanticsNode().boundsInRoot
        val filtersBottom = maxOf(machineChip.bottom, groupChip.bottom)
        kotlin.test.assertTrue(
            filtersBottom - headerBottom <= with(rule.density) { 80.dp.toPx() },
            "Compact filter area is taller than 80dp",
        )

        val cardBounds =
            nodes.map { node ->
                val parent = node.parentAgentId?.let { pid -> nodes.firstOrNull { it.agentId == pid } }
                val description = formatMeshNodeContentDescription(node, nodes, parent)
                rule
                    .onNodeWithContentDescription(description)
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
        val orchestrator =
            rule
                .onNodeWithContentDescription(
                    formatMeshNodeContentDescription(nodes.first(), nodes),
                ).fetchSemanticsNode()
                .boundsInRoot
        val firstSubordinate =
            rule
                .onNodeWithContentDescription(
                    formatMeshNodeContentDescription(nodes[1], nodes, nodes.first()),
                ).fetchSemanticsNode()
                .boundsInRoot
        kotlin.test.assertTrue(orchestrator.bottom <= firstSubordinate.top + 1f, "First subordinate row overlaps the orchestrator")
        kotlin.test.assertTrue(cardBounds.all { it.top >= filtersBottom }, "A card intersects the compact filter row")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `four-agent cards contain all text without overlap at font scale 1 point 5`() {
        assertFontScaledCardContainment(1.5f, build4AgentsScenarioNodes().first)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `four-agent cards contain all text without overlap at font scale 2`() {
        assertFontScaledCardContainment(2f, build4AgentsScenarioNodes().first)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `twelve-agent cards contain all text without overlap at font scale 1 point 5`() {
        assertFontScaledCardContainment(1.5f, build12AgentsScenarioNodes().first)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `twelve-agent cards contain all text without overlap at font scale 2`() {
        assertFontScaledCardContainment(2f, build12AgentsScenarioNodes().first)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `diagram card legibility tablet`() {
        val (allNodes, edges) = build4AgentsScenarioNodes()
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 800f, height = 1280f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = allNodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = uiState, onOpenChat = {})
            }
        }

        listOf("Rp-app", "Rp-relay", "RP", "Worker Plugin").forEach { name ->
            assertCardMeetsLegibility(name)
        }
        assertOrchestratorCardInsideViewport("Rp-app")
    }

    private fun assertFontScaledCardContainment(
        fontScale: Float,
        inputNodes: List<MeshNode>,
    ) {
        val nodes =
            inputNodes.mapIndexed { index, node ->
                node.copy(
                    activity = "Implementando contenido accesible del agente ${index + 1}",
                    todos = MeshTodos(total = 4, done = index % 4, inProgress = 1, pending = 3 - index % 4),
                    subagents =
                        if (index == 0) {
                            listOf(
                                dev.j3pf.rp.protocol.MeshSubagent(
                                    taskId = "font-scale-task",
                                    agent = "Font scale worker",
                                    label = "Pruebas de escala",
                                    status = "working",
                                ),
                            )
                        } else {
                            emptyList()
                        },
                )
            }
        val layout = MeshLayoutEngine().computeLayout(nodes, emptyList(), width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = nodes),
                layoutResult = layout,
            )

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                RpTheme {
                    MeshScreenContent(uiState = uiState, onOpenChat = {})
                }
            }
        }
        rule.waitForIdle()

        val cards =
            nodes.map { node ->
                val parent = node.parentAgentId?.let { pid -> nodes.firstOrNull { it.agentId == pid } }
                rule
                    .onNodeWithContentDescription(formatMeshNodeContentDescription(node, nodes, parent))
                    .assertExists()
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
        cards.forEachIndexed { index, card ->
            cards.drop(index + 1).forEach { other ->
                val separated =
                    card.right <= other.left + 1f ||
                        other.right <= card.left + 1f ||
                        card.bottom <= other.top + 1f ||
                        other.bottom <= card.top + 1f
                kotlin.test.assertTrue(separated, "Cards at indices $index and ${cards.indexOf(other)} overlap at fontScale=$fontScale")
            }
        }

        val textNodes =
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("", substring = true),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
        cards.forEachIndexed { index, card ->
            val textsInsideCard =
                textNodes.filter { textNode ->
                    val bounds = textNode.boundsInRoot
                    val centerX = (bounds.left + bounds.right) / 2f
                    val centerY = (bounds.top + bounds.bottom) / 2f
                    centerX >= card.left && centerX <= card.right && centerY >= card.top && centerY <= card.bottom
                }
            kotlin.test.assertTrue(textsInsideCard.isNotEmpty(), "No Text nodes found inside card $index at fontScale=$fontScale")
            textsInsideCard.forEach { textNode ->
                val text =
                    textNode.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .joinToString { it.text }
                val results = mutableListOf<TextLayoutResult>()
                textNode.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(results)
                val textLayout = results.firstOrNull() ?: error("No TextLayoutResult for '$text' inside card $index")
                kotlin.test.assertFalse(textLayout.didOverflowHeight, "Text '$text' overflows vertically at fontScale=$fontScale")
                val textBounds = textNode.boundsInRoot
                kotlin.test.assertTrue(textBounds.left >= card.left - 1f, "Text '$text' extends past card $index's left edge")
                kotlin.test.assertTrue(textBounds.top >= card.top - 1f, "Text '$text' extends above card $index")
                kotlin.test.assertTrue(textBounds.right <= card.right + 1f, "Text '$text' extends past card $index's right edge")
                kotlin.test.assertTrue(textBounds.bottom <= card.bottom + 1f, "Text '$text' extends below card $index")
            }
        }
    }

    private fun assertCardMeetsLegibility(
        agentName: String,
        requireViewportContainment: Boolean = true,
    ) {
        val nodeInteraction = rule.onNodeWithText(agentName)
        if (requireViewportContainment) nodeInteraction.assertIsDisplayed()

        val textLayoutResults = mutableListOf<TextLayoutResult>()
        val action = nodeInteraction.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
        action?.action?.invoke(textLayoutResults)
        val textLayout = textLayoutResults.firstOrNull() ?: error("No TextLayoutResult for $agentName")

        kotlin.test.assertFalse(textLayout.didOverflowHeight, "Name '$agentName' has height overflow")

        val cardBounds = nodeInteraction.fetchSemanticsNode().boundsInRoot
        val textNodes =
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("", substring = true),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
        val cardTextNodes =
            textNodes.filter { textNode ->
                val bounds = textNode.boundsInRoot
                bounds.left < cardBounds.right &&
                    bounds.right > cardBounds.left &&
                    bounds.top < cardBounds.bottom &&
                    bounds.bottom > cardBounds.top
            }
        kotlin.test.assertTrue(cardTextNodes.isNotEmpty(), "No Text nodes found inside card '$agentName'")
        cardTextNodes.forEach { textNode ->
            val text =
                textNode.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .joinToString { it.text }
            val results = mutableListOf<TextLayoutResult>()
            textNode.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action
                ?.invoke(results)
            val result = results.firstOrNull() ?: error("No TextLayoutResult for '$text' in card '$agentName'")
            kotlin.test.assertFalse(result.didOverflowHeight, "Text '$text' in card '$agentName' has height overflow")
            val textBounds = textNode.boundsInRoot
            kotlin.test.assertTrue(textBounds.left >= cardBounds.left - 1f, "Text '$text' extends past the left of card '$agentName'")
            kotlin.test.assertTrue(textBounds.top >= cardBounds.top - 1f, "Text '$text' extends above card '$agentName'")
            kotlin.test.assertTrue(textBounds.right <= cardBounds.right + 1f, "Text '$text' extends past the right of card '$agentName'")
            kotlin.test.assertTrue(textBounds.bottom <= cardBounds.bottom + 1f, "Text '$text' extends below card '$agentName'")
        }

        val visibleLength =
            if (textLayout.isLineEllipsized(0)) {
                textLayout.getLineEnd(0, visibleEnd = true)
            } else {
                agentName.length
            }
        val minChars = minOf(6, agentName.length)
        kotlin.test.assertTrue(
            visibleLength >= minChars,
            "Name '$agentName' displays only $visibleLength chars (expected at least $minChars)",
        )

        if (requireViewportContainment) {
            val machinesHeader = rule.onNodeWithText("Red de agentes").fetchSemanticsNode().boundsInRoot
            kotlin.test.assertTrue(
                cardBounds.top >= machinesHeader.bottom,
                "Card '$agentName' top ${cardBounds.top} intersects header bottom ${machinesHeader.bottom}",
            )
            val liveStripBounds = rule.onNodeWithText("En vivo").fetchSemanticsNode().boundsInRoot
            kotlin.test.assertTrue(
                cardBounds.bottom <= liveStripBounds.top + 1f,
                "Card '$agentName' bottom ${cardBounds.bottom} intersects LiveStrip top ${liveStripBounds.top}",
            )
        }
    }

    private fun assertOrchestratorCardInsideViewport(
        agentName: String,
        viewportTop: Float? = null,
        expectedTopOffset: Float? = null,
    ) {
        val cardBounds = rule.onNodeWithText(agentName).fetchSemanticsNode().boundsInRoot
        val titleBottom =
            rule
                .onNodeWithText("Red de agentes")
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        val top = viewportTop ?: titleBottom + with(rule.density) { 16.dp.toPx() }
        val liveStripTop =
            rule
                .onNodeWithText("En vivo")
                .fetchSemanticsNode()
                .boundsInRoot.top
        kotlin.test.assertTrue(cardBounds.top >= top - 1f, "Orchestrator '$agentName' begins above viewport top $top: ${cardBounds.top}")
        kotlin.test.assertTrue(
            cardBounds.bottom <= liveStripTop + 1f,
            "Orchestrator '$agentName' ends below viewport bottom $liveStripTop: ${cardBounds.bottom}",
        )
        expectedTopOffset?.let { expected ->
            assertEquals(expected, cardBounds.top - top, 1f, "Orchestrator '$agentName' should start at the top padding")
        }
    }

    private fun build4AgentsScenarioNodes(): Pair<List<MeshNode>, List<MeshEdge>> {
        val orch =
            MeshNode(
                agentId = "node-omarchy-app",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Rp-app",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
            )
        val sub1 =
            MeshNode(
                agentId = "node-omarchy-relay",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Rp-relay",
                role = "subordinate",
                parentAgentId = orch.agentId,
                status = "idle",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-relay",
            )
        val sub2 =
            MeshNode(
                agentId = "node-croma-rp",
                machineId = "m-croma",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "RP",
                role = "subordinate",
                parentAgentId = orch.agentId,
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/RP",
            )
        val sub3 =
            MeshNode(
                agentId = "node-croma-plugin",
                machineId = "m-croma",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Worker Plugin",
                role = "subordinate",
                parentAgentId = orch.agentId,
                status = "offline",
                online = false,
                cwd = "/home/drift/Workspace/RP/Rp-plugin",
            )
        val all = listOf(orch, sub1, sub2, sub3)
        val edges =
            listOf(
                MeshEdge(edgeId = "e1", a = orch.agentId, b = sub1.agentId, count = 3, active = true, lastAction = "ask"),
                MeshEdge(edgeId = "e2", a = orch.agentId, b = sub2.agentId, count = 1, active = false, lastAction = "reply"),
                MeshEdge(edgeId = "e3", a = orch.agentId, b = sub3.agentId, count = 0, active = false, lastAction = "message"),
            )
        return all to edges
    }

    private fun build12AgentsScenarioNodes(): Pair<List<MeshNode>, List<MeshEdge>> {
        val root =
            MeshNode(
                agentId = "orch-root",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Orquestador Maestro",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP",
            )
        val subordinates =
            (1..11).map { i ->
                MeshNode(
                    agentId = "sub-$i",
                    machineId = if (i % 2 == 0) "m-omarchy" else "m-croma",
                    machineName = if (i % 2 == 0) "Omarchy" else "Croma",
                    machineColor = if (i % 2 == 0) "#6750A4" else "#7AA2F7",
                    name = "Worker-$i",
                    role = "subordinate",
                    parentAgentId = "orch-root",
                    status = if (i % 2 == 0) "working" else "idle",
                    online = true,
                    cwd = "/home/drift/Workspace/RP/sub-$i",
                )
            }
        val all = listOf(root) + subordinates
        val edges =
            subordinates.mapIndexed {
                idx,
                s,
                ->
                MeshEdge(edgeId = "e-$idx", a = "orch-root", b = s.agentId, count = idx, active = true, lastAction = "send")
            }
        return all to edges
    }

    private fun node(
        id: String,
        name: String,
        role: String,
        parent: String? = null,
        subagents: List<MeshSubagent> = emptyList(),
    ) = MeshNode(
        agentId = id,
        machineId = "machine",
        machineName = "Test machine",
        name = name,
        status = "idle",
        online = true,
        role = role,
        parentAgentId = parent,
        subagents = subagents,
    )
}
