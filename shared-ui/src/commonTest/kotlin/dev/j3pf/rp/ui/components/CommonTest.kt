package dev.j3pf.rp.ui.components

import androidx.compose.ui.graphics.Color
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommonTest {
    @Test
    fun allMachinesConstantValue() {
        assertEquals("all", ALL_MACHINES)
    }

    @Test
    fun connectionBannerMessages() {
        assertNull(connectionBannerMessage(ConnState.Connected))
        assertEquals("Conectando con el relay…", connectionBannerMessage(ConnState.Connecting))
        assertEquals(
            "Sin conexión con el relay · reintentando",
            connectionBannerMessage(ConnState.Disconnected),
        )
        assertEquals(
            "Token revocado · Vuelve a emparejar el dispositivo",
            connectionBannerMessage(ConnState.Fatal(4001, "Token revoked")),
        )
        assertEquals(
            "Protocolo incompatible · Actualiza la app",
            connectionBannerMessage(ConnState.Fatal(4002, "Protocol incompatible")),
        )
        assertEquals(
            "Conexión cerrada (5000)",
            connectionBannerMessage(ConnState.Fatal(5000, "Unexpected error")),
        )
    }

    @Test
    fun resolveMachineHeroStateWhenAllSelectedWithMultipleMachines() {
        val machines =
            listOf(
                MachineSummary(
                    machineId = "m1",
                    name = "MacBook Pro",
                    context = "work",
                    color = "#4285F4",
                    online = true,
                ),
                MachineSummary(
                    machineId = "m2",
                    name = "Linux Server",
                    context = "personal",
                    color = "#34A853",
                    online = false,
                ),
            )
        val agents =
            listOf(
                createAgentSummary(agentId = "a1", machineId = "m1", machineContext = "work", online = true),
                createAgentSummary(agentId = "a2", machineId = "m2", machineContext = "personal", online = false),
            )

        val state =
            resolveMachineHeroState(
                machines = machines,
                agents = agents,
                selected = ALL_MACHINES,
                primaryColor = Color.Magenta,
            )

        assertEquals("EQUIPOS VINCULADOS", state.overlineLabel)
        assertEquals("Todas las máquinas", state.selectedTitle)
        assertEquals("2 agentes · 1 PC activa", state.selectedSubtitle)
        assertEquals(Color.Magenta, state.selectedColor)
    }

    @Test
    fun resolveMachineHeroStateWhenSingleMachinePresentAndAllSelected() {
        val single =
            MachineSummary(
                machineId = "m1",
                name = "Studio Mac",
                context = "personal",
                color = "#FF0000",
                online = true,
            )
        val agents =
            listOf(
                createAgentSummary(agentId = "a1", machineId = "m1", machineContext = "personal", online = true),
            )

        val state =
            resolveMachineHeroState(
                machines = listOf(single),
                agents = agents,
                selected = ALL_MACHINES,
                primaryColor = Color.Blue,
            )

        assertEquals("PC CONECTADA · PERSONAL", state.overlineLabel)
        assertEquals("Studio Mac", state.selectedTitle)
        assertEquals("1 de 1 sesiones activas · PC en línea", state.selectedSubtitle)
    }

    @Test
    fun resolveMachineHeroStateWhenSpecificMachineSelected() {
        val m1 =
            MachineSummary(
                machineId = "m1",
                name = "Workstation",
                context = "work",
                color = "#00FF00",
                online = false,
            )
        val state =
            resolveMachineHeroState(
                machines = listOf(m1),
                agents = emptyList(),
                selected = "m1",
                primaryColor = Color.Blue,
            )

        assertEquals("PC CONECTADA · EMPRESA", state.overlineLabel)
        assertEquals("Workstation", state.selectedTitle)
        assertEquals("0 de 0 sesiones activas · PC desconectada", state.selectedSubtitle)
    }

    @Test
    fun resolveMachineHeroStateWhenContextSelected() {
        val m1 =
            MachineSummary(
                machineId = "m1",
                name = "Mac",
                context = "work",
                color = "#0000FF",
                online = true,
            )
        val agents =
            listOf(
                createAgentSummary(agentId = "a1", machineId = "m1", machineContext = "work", online = true),
                createAgentSummary(agentId = "a2", machineId = "m1", machineContext = "work", online = true),
            )
        val state =
            resolveMachineHeroState(
                machines = listOf(m1),
                agents = agents,
                selected = "work",
                primaryColor = Color.Blue,
            )

        assertEquals("CONTEXTO", state.overlineLabel)
        assertEquals("Empresa", state.selectedTitle)
        assertEquals("2 agentes en este contexto", state.selectedSubtitle)
    }

    private fun createAgentSummary(
        agentId: String,
        machineId: String,
        machineContext: String,
        online: Boolean,
    ): AgentSummary =
        AgentSummary(
            agentId = agentId,
            machineId = machineId,
            machineContext = machineContext,
            machineName = "test",
            status = AgentStatus.IDLE,
            online = online,
            lastSeenAt = 1_000_000L,
            sessionName = "test-session",
            cwd = "/home/test",
            openPromptCount = 0,
        )
}
