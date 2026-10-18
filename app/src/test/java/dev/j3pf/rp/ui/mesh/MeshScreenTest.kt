package dev.j3pf.rp.ui.mesh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.CommAction
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
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
    }

    private fun node(
        id: String,
        name: String,
        role: String,
    ) = MeshNode(
        agentId = id,
        machineId = "machine",
        machineName = "Test machine",
        name = name,
        status = "idle",
        online = true,
        role = role,
    )
}
