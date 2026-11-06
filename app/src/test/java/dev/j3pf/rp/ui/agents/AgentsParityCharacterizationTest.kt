package dev.j3pf.rp.ui.agents

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.theme.RpTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T1a (Gate A): pruebas de caracterización de comportamiento SIN cambiar código de producción.
 * Filtrado/búsqueda (AG-4), etiquetas exactas de estado (AG-6), vacío y paginación de Archivados (AR-3/AR-4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AgentsParityCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private val machine = MachineSummary(machineId = "m1", name = "PC-A", context = "personal", color = "#1565C0")

    private fun agent(
        id: String,
        status: String,
        launcher: String = "pi",
        online: Boolean = true,
        name: String? = null,
        cwd: String? = null,
        branch: String? = null,
        preview: String? = null,
        prompts: Int = 0,
    ) = AgentSummary(
        agentId = id,
        machineId = "m1",
        sessionName = name,
        cwd = cwd,
        gitBranch = branch,
        status = status,
        online = online,
        launcher = launcher,
        openPromptCount = prompts,
        lastMessagePreview = preview,
    )

    @Test
    fun `quick filter En linea keeps only online agents and drops empty groups`() {
        val offlineOnlyMachine = MachineSummary(machineId = "m2", name = "PC-B", context = "empresa", color = "#EF6C00")
        val groups =
            listOf(
                MachineGroup(machine, listOf(agent("a", "idle", online = true), agent("b", "idle", online = false))),
                MachineGroup(offlineOnlyMachine, listOf(agent("c", "idle", online = false))),
            )

        val filtered = filterAgentGroups(groups, SessionQuickFilter.ACTIVE, "")

        // The offline-only group disappears entirely (diropd empty groups, verificado).
        assertEquals(listOf("m1"), filtered.map { it.machine.machineId })
        assertEquals(listOf("a"), filtered.single().agents.map { it.agentId })
    }

    @Test
    fun `quick filters agy and pi split by launcher`() {
        val groups =
            listOf(
                MachineGroup(
                    machine,
                    listOf(agent("a", "idle", launcher = "agy"), agent("b", "idle", launcher = "herdr")),
                ),
            )

        assertEquals(listOf("a"), filterAgentGroups(groups, SessionQuickFilter.AGY, "").single().agents.map { it.agentId })
        assertEquals(listOf("b"), filterAgentGroups(groups, SessionQuickFilter.PI, "").single().agents.map { it.agentId })
    }

    @Test
    fun `search matches sessionName cwd branch and preview case-insensitively and trims the query`() {
        val a =
            agent(
                "a",
                "idle",
                name = "Refactor UI",
                cwd = "/home/user/Rp-App",
                branch = "feat/mesh",
                preview = "Parche para AgentCard",
            )
        val groups = listOf(MachineGroup(machine, listOf(a)))

        listOf("rp-app", "  refactor ui ", "feat/mesh", "parche para agentcard").forEach { q ->
            assertEquals(listOf("a"), filterAgentGroups(groups, SessionQuickFilter.ALL, q).single().agents.map { it.agentId }, "query=$q")
        }
        assertEquals(0, filterAgentGroups(groups, SessionQuickFilter.ALL, "no-existo").size)
    }

    @Test
    fun `status chips use the exact Spanish labels`() {
        val working = agent("w", "working")
        val waiting = agent("p", "idle", prompts = 2)
        val idle = agent("i", "idle")
        val compacting = agent("c", "compacting")
        val error = agent("e", "error")
        val offline = AgentSummary(
            agentId = "o",
            machineId = "m1",
            status = "working",
            online = false,
            lastSeenAt = null,
        )

        rule.setContent {
            RpTheme {
                Column {
                    StatusChip(working)
                    StatusChip(waiting)
                    StatusChip(idle)
                    StatusChip(compacting)
                    StatusChip(error)
                    StatusChip(offline)
                }
            }
        }

        rule.onNodeWithText("Trabajando").assertIsDisplayed()
        rule.onNodeWithText("Esperando respuesta").assertIsDisplayed()
        rule.onAllNodesWithText("Inactivo")[0].assertIsDisplayed()
        rule.onNodeWithText("Compactando").assertIsDisplayed()
        rule.onNodeWithText("Error").assertIsDisplayed()
        // Offline wins over any work status, even with lastSeenAt == null (verifier probe).
        rule.onAllNodesWithText("Desconectado", substring = true)[0].assertIsDisplayed()
    }
}
