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

    @Test
    fun testResolveCardAnimationFrameMsRespectsLifecycleReducedMotionAndState() {
        assertEquals(
            null,
            resolveCardAnimationFrameMs(hasActive = true, hasDone = true, reducedMotion = true, isForeground = true),
        )
        assertEquals(
            null,
            resolveCardAnimationFrameMs(hasActive = true, hasDone = true, reducedMotion = false, isForeground = false),
        )
        assertEquals(
            ACTIVE_CARD_FRAME_MS,
            resolveCardAnimationFrameMs(hasActive = true, hasDone = false, reducedMotion = false, isForeground = true),
        )
        assertEquals(
            ACTIVE_CARD_FRAME_MS,
            resolveCardAnimationFrameMs(hasActive = true, hasDone = true, reducedMotion = false, isForeground = true),
        )
        assertEquals(
            DONE_CARD_FRAME_MS,
            resolveCardAnimationFrameMs(hasActive = false, hasDone = true, reducedMotion = false, isForeground = true),
        )
        assertEquals(
            null,
            resolveCardAnimationFrameMs(hasActive = false, hasDone = false, reducedMotion = false, isForeground = true),
        )
    }

    @Test
    fun testCardOrbitAndBreathPhasesStayBoundedAndPeriodic() {
        assertEquals(0f, cardOrbitPhase(0L, 2_000L), 0.0001f)
        assertEquals(0.5f, cardOrbitPhase(1_000L, 2_000L), 0.0001f)
        assertEquals(0f, cardOrbitPhase(2_000L, 2_000L), 0.0001f)

        assertEquals(0f, cardBreathPhase(0L, 3_600L), 0.0001f)
        assertEquals(1f, cardBreathPhase(1_800L, 3_600L), 0.0001f)
        assertEquals(0f, cardBreathPhase(3_600L, 3_600L), 0.0001f)
        for (t in 0L..4_000L step 66L) {
            val v = cardBreathPhase(t, 3_600L)
            assertTrue(v in 0f..1f, "Expected breath phase in [0, 1], got $v at t=$t")
        }
    }
}
