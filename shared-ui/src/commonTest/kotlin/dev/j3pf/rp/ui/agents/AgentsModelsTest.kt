package dev.j3pf.rp.ui.agents

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.formatCompactTokens
import dev.j3pf.rp.protocol.parseHerdrTabMeta
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

    @Test
    fun sortAgentsWithinGroupPreservesExactHerdrVisualOrderWithoutJumping() {
        val ws1Idle =
            AgentSummary(
                agentId = "m1:herdr:ws1",
                machineId = "m-1",
                online = true,
                status = "idle",
                openPromptCount = 0,
                lastSeenAt = 100L,
                herdr = HerdrInfo(paneId = "w1:p1", tabId = "#0001|w1:t1", workspaceId = "w1"),
            )
        val ws2Working =
            AgentSummary(
                agentId = "m1:herdr:ws2",
                machineId = "m-1",
                online = true,
                status = "working",
                openPromptCount = 0,
                lastSeenAt = 50L,
                herdr = HerdrInfo(paneId = "w2:p1", tabId = "#0002|w2:t1", workspaceId = "w2"),
            )
        val ws3IdleRecent =
            AgentSummary(
                agentId = "m1:herdr:ws3",
                machineId = "m-1",
                online = true,
                status = "idle",
                openPromptCount = 0,
                lastSeenAt = 9_999L,
                herdr = HerdrInfo(paneId = "w3:p1", tabId = "#0003|w3:t1", workspaceId = "w3"),
            )

        val sorted = sortAgentsWithinGroup(listOf(ws3IdleRecent, ws1Idle, ws2Working))
        // En Herdr, se preserva el orden exacto de tabs (#0001, #0002, #0003) sin que agentes que trabajan salten
        assertEquals(listOf("m1:herdr:ws1", "m1:herdr:ws2", "m1:herdr:ws3"), sorted.map { it.agentId })
        assertEquals("w1:t1", cleanHerdrTabId(ws1Idle.herdr?.tabId))
    }

    @Test
    fun sortAgentsWithinGroupSurfacesUrgentPromptsInHerdrGroup() {
        val ws1Idle =
            AgentSummary(
                agentId = "m1:herdr:ws1",
                machineId = "m-1",
                online = true,
                status = "idle",
                openPromptCount = 0,
                herdr = HerdrInfo(paneId = "w1:p1", tabId = "#0001|w1:t1", workspaceId = "w1"),
            )
        val ws2Prompt =
            AgentSummary(
                agentId = "m1:herdr:ws2",
                machineId = "m-1",
                online = true,
                status = "waiting_input",
                openPromptCount = 1,
                herdr = HerdrInfo(paneId = "w2:p1", tabId = "#0002|w2:t1", workspaceId = "w2"),
            )

        val sorted = sortAgentsWithinGroup(listOf(ws1Idle, ws2Prompt))
        assertEquals(listOf("m1:herdr:ws2", "m1:herdr:ws1"), sorted.map { it.agentId })
    }

    @Test
    fun parseHerdrTabMetaAndFormatCompactTokens() {
        val meta = parseHerdrTabMeta("#0002~done@128000/256000|w2:t1")
        assertEquals(2, meta.orderIndex)
        assertTrue(meta.isDone)
        assertEquals(128_000, meta.contextTokens)
        assertEquals(256_000, meta.contextWindow)
        assertEquals(50, meta.contextPercent)
        assertEquals("w2:t1", meta.cleanTabId)

        assertEquals("950", formatCompactTokens(950))
        assertEquals("9.3k", formatCompactTokens(9_320))
        assertEquals("118k", formatCompactTokens(118_400))
        assertEquals("256k", formatCompactTokens(256_000))
        assertEquals("1.2M", formatCompactTokens(1_200_000))
    }
}
