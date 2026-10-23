package dev.j3pf.rp.ui.agents

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.lastSegment
import dev.j3pf.rp.ui.util.relativeTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentCardTest {
    @Test
    fun testAgentTitleFormatting() {
        val titleWithSession = agentTitle("Refactor Core", "/home/user/project", "agent-123")
        assertEquals("Refactor Core", titleWithSession)

        val titleWithoutSession = agentTitle(null, "/home/user/my-cool-project", "agent-123")
        assertEquals("my-cool-project", titleWithoutSession)
    }

    @Test
    fun testLastSegmentExtraction() {
        assertEquals("project", lastSegment("/home/user/project"))
        assertEquals("project", lastSegment("C:\\Users\\dev\\project"))
        assertEquals("", lastSegment(null))
    }

    @Test
    fun testRelativeTimeFormatting() {
        val now = 100_000_000L
        assertEquals("ahora", relativeTime(now - 10_000L, now))
        assertEquals("hace 5 min", relativeTime(now - 300_000L, now))
        assertEquals("hace 2 h", relativeTime(now - 7_200_000L, now))
    }

    @Test
    fun testAgentSummaryDataForCard() {
        val agent =
            AgentSummary(
                agentId = "m1:agent-a",
                machineId = "m1",
                sessionName = "Feature X",
                status = "working",
                online = true,
                openPromptCount = 0,
            )
        assertEquals("Feature X", agentTitle(agent.sessionName, agent.cwd, agent.agentId))
        assertTrue(agent.online)
    }
}
