package dev.j3pf.rp.ui.agents

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentsModelsTest {
    private val machine =
        MachineSummary(
            machineId = "m-1",
            name = "Workstation",
            context = "work",
            color = "#1565C0",
            online = true,
        )

    private val agentPrompt =
        AgentSummary(
            agentId = "a-prompt",
            machineId = "m-1",
            machineName = "Workstation",
            machineContext = "work",
            machineColor = "#1565C0",
            sessionName = "feature/auth",
            cwd = "/home/dev/auth",
            gitBranch = "feat/jwt",
            online = true,
            openPromptCount = 2,
            status = "idle",
            launcher = "agy",
            lastSeenAt = 100L,
        )

    private val agentWorking =
        AgentSummary(
            agentId = "a-working",
            machineId = "m-1",
            machineName = "Workstation",
            machineContext = "work",
            machineColor = "#1565C0",
            sessionName = "refactor/db",
            cwd = "/home/dev/db",
            gitBranch = "refactor",
            online = true,
            openPromptCount = 0,
            status = "working",
            launcher = "pi",
            lastSeenAt = 200L,
        )

    private val agentOffline =
        AgentSummary(
            agentId = "a-offline",
            machineId = "m-1",
            machineName = "Workstation",
            machineContext = "work",
            machineColor = "#1565C0",
            sessionName = "docs/api",
            cwd = "/home/dev/docs",
            gitBranch = "main",
            online = false,
            openPromptCount = 0,
            status = "idle",
            launcher = "pi",
            lastSeenAt = 300L,
        )

    @Test
    fun agentsUiStateDefaults() {
        val defaultState = AgentsUiState()
        assertTrue(defaultState.machines.isEmpty())
        assertTrue(defaultState.allAgents.isEmpty())
        assertTrue(defaultState.groups.isEmpty())
        assertEquals("all", defaultState.selected)
        assertFalse(defaultState.isLoading)
        assertFalse(defaultState.canSpawn)
        assertFalse(defaultState.refreshing)
    }

    @Test
    fun sortAgentsWithinGroupPriority() {
        val sorted = sortAgentsWithinGroup(listOf(agentOffline, agentWorking, agentPrompt))
        assertEquals(3, sorted.size)
        assertEquals("a-prompt", sorted[0].agentId)
        assertEquals("a-working", sorted[1].agentId)
        assertEquals("a-offline", sorted[2].agentId)
    }

    @Test
    fun filterAgentGroupsByQuickFilter() {
        val group = MachineGroup(machine, listOf(agentPrompt, agentWorking, agentOffline))
        val groups = listOf(group)

        val active = filterAgentGroups(groups, SessionQuickFilter.ACTIVE, "")
        assertEquals(1, active.size)
        assertEquals(listOf("a-prompt", "a-working"), active[0].agents.map { it.agentId })

        val agy = filterAgentGroups(groups, SessionQuickFilter.AGY, "")
        assertEquals(1, agy.size)
        assertEquals(listOf("a-prompt"), agy[0].agents.map { it.agentId })

        val pi = filterAgentGroups(groups, SessionQuickFilter.PI, "")
        assertEquals(1, pi.size)
        assertEquals(listOf("a-working", "a-offline"), pi[0].agents.map { it.agentId })
    }

    @Test
    fun filterAgentGroupsBySearchQuery() {
        val group = MachineGroup(machine, listOf(agentPrompt, agentWorking, agentOffline))
        val groups = listOf(group)

        val byBranch = filterAgentGroups(groups, SessionQuickFilter.ALL, "jwt")
        assertEquals(1, byBranch.size)
        assertEquals(listOf("a-prompt"), byBranch[0].agents.map { it.agentId })

        val byCwd = filterAgentGroups(groups, SessionQuickFilter.ALL, "docs")
        assertEquals(1, byCwd.size)
        assertEquals(listOf("a-offline"), byCwd[0].agents.map { it.agentId })

        val noMatch = filterAgentGroups(groups, SessionQuickFilter.ALL, "nonexistent")
        assertTrue(noMatch.isEmpty())
    }
}
