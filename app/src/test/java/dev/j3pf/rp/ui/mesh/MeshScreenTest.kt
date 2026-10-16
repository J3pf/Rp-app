package dev.j3pf.rp.ui.mesh

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    uiState = MeshUiState(
                        isLoading = false,
                        snapshot = empty,
                        machineChips = listOf(
                            MeshMachineChip(machineId = null, name = "", color = "", agentCount = 0),
                        ),
                        groupChips = listOf(
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
        val nodes = listOf(node("orchestrator", "Orchestrator", "orchestrator"), node("worker", "Worker", "subordinate"))
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

        rule.onNodeWithText("Vista de lista").performClick()
        rule.onNodeWithText("Vista de diagrama").assertIsDisplayed()
        rule.onNodeWithText("Orchestrator").assertIsDisplayed()
        rule.onNodeWithText("Worker").assertIsDisplayed()
        rule.onNodeWithContentDescription("Orquestador Orchestrator, Inactivo").assertIsDisplayed()
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
        rule.onNodeWithText("Review Agent").assertExists()
        rule.onNodeWithText("Detalles").assertExists()
        rule.onNodeWithText("TODOs").assertExists()
        rule.onNodeWithText("Máquina").assertExists()
        rule.onNodeWithText("Directorio (cwd)").assertExists()
        rule.onNodeWithText("Progreso").assertExists()
        rule.onNodeWithText("No hay subagentes").assertExists()
        rule.onNodeWithText("Sin conversaciones recientes").assertExists()
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
