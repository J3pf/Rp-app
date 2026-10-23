package dev.j3pf.rp.ui.mesh

import dev.j3pf.rp.protocol.CommAction
import dev.j3pf.rp.protocol.MeshComm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshModelsTest {
    @Test
    fun `MeshMachineChip properties and defaults`() {
        val allMachines = MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 5)
        assertEquals(null, allMachines.machineId)
        assertEquals("Todas", allMachines.name)
        assertEquals(5, allMachines.agentCount)

        val specific = MeshMachineChip(machineId = "m1", name = "MacBook Pro", color = "#FF0000", agentCount = 2)
        assertEquals("m1", specific.machineId)
        assertEquals("MacBook Pro", specific.name)
        assertEquals("#FF0000", specific.color)
    }

    @Test
    fun `MeshGroupChip properties and defaults`() {
        val soloGroup = MeshGroupChip(groupId = "solo", label = "Sin grupo", memberCount = 3, isSolo = true)
        assertTrue(soloGroup.isSolo)
        assertEquals("solo", soloGroup.groupId)
        assertEquals("Sin grupo", soloGroup.label)

        val cluster = MeshGroupChip(groupId = "grp-1", label = "Frontend Team", memberCount = 4, isSolo = false)
        assertFalse(cluster.isSolo)
        assertEquals("grp-1", cluster.groupId)
        assertEquals(4, cluster.memberCount)
    }

    @Test
    fun `LiveCommBubble holds comm and expiration`() {
        val comm =
            MeshComm(
                commKey = "c1",
                fromAgentId = "a1",
                toAgentId = "a2",
                fromName = "Worker 1",
                toName = "Worker 2",
                action = CommAction.MESSAGE,
                text = "Building shared UI module",
            )
        val bubble = LiveCommBubble(comm = comm, expiresAtMs = 5000L)
        assertEquals("c1", bubble.comm.commKey)
        assertEquals(5000L, bubble.expiresAtMs)
    }

    @Test
    fun `MeshUiState initial defaults`() {
        val state = MeshUiState()
        assertTrue(state.isConnected)
        assertFalse(state.isListView)
        assertFalse(state.isEmpty)
        assertTrue(state.isLoading)
        assertEquals(null, state.snapshot)
        assertEquals(null, state.errorMessage)
        assertEquals(emptyList(), state.machineChips)
        assertEquals(emptyList(), state.groupChips)
        assertEquals(emptyList(), state.activeBubbles)
    }
}
