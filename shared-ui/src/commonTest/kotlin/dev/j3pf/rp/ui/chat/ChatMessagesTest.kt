package dev.j3pf.rp.ui.chat

import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ToolRun
import dev.j3pf.rp.chat.ToolStatus
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.ModelInfo
import dev.j3pf.rp.protocol.SubagentInfo
import dev.j3pf.rp.ui.util.formatCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatMessagesTest {
    @Test
    fun testSlashCommandResolution() {
        val piAgent = AgentSummary(agentId = "m1:1", machineId = "m1", launcher = "pi")
        assertEquals(SlashCommandCatalogs.PI, SlashCommandCatalogs.resolve(piAgent))
        assertEquals(SlashCommandCatalogs.PI, SlashCommandCatalogs.resolve(null))

        val agyAgent = AgentSummary(agentId = "m1:2", machineId = "m1", launcher = "agy")
        assertEquals(SlashCommandCatalogs.ANTIGRAVITY, SlashCommandCatalogs.resolve(agyAgent))
        assertTrue(SlashCommandCatalogs.ANTIGRAVITY.commands.contains("/goal"))
        assertTrue(SlashCommandCatalogs.ANTIGRAVITY.commands.contains("/plan"))

        val claudeAgent =
            AgentSummary(
                agentId = "m1:3",
                machineId = "m1",
                launcher = "claude",
                model = ModelInfo(id = "claude-3-7-sonnet", provider = "anthropic"),
            )
        assertEquals(SlashCommandCatalogs.CLAUDE, SlashCommandCatalogs.resolve(claudeAgent))
        assertTrue(SlashCommandCatalogs.CLAUDE.commands.contains("/doctor"))

        val codexAgent = AgentSummary(agentId = "m1:4", machineId = "m1", launcher = "codex")
        assertEquals(SlashCommandCatalogs.CODEX, SlashCommandCatalogs.resolve(codexAgent))

        val gentleAgent = AgentSummary(agentId = "m1:5", machineId = "m1", launcher = "gentle-shell")
        assertEquals(SlashCommandCatalogs.GENTLE_SHELL, SlashCommandCatalogs.resolve(gentleAgent))
        assertTrue(SlashCommandCatalogs.GENTLE_SHELL.commands.contains("/mode"))
    }

    @Test
    fun testFormatCost() {
        assertEquals("0.000", formatCost(0.0))
        assertEquals("0.123", formatCost(0.123))
        assertEquals("1.500", formatCost(1.5))
        assertEquals("0.005", formatCost(0.005))
        assertEquals("12.345", formatCost(12.345))
    }

    @Test
    fun testChatMessagesStructure() {
        val userMsg = UserMsg(key = "u-1", text = "Hello agent", at = 1000L, fromMobile = true)
        assertEquals("Hello agent", userMsg.text)
        assertTrue(userMsg.fromMobile)

        val toolRun =
            ToolRun(
                toolCallId = "call-1",
                name = "bash",
                args = null,
                status = ToolStatus.OK,
                durationMs = 1500L,
            )
        assertEquals("bash", toolRun.name)
        assertEquals(ToolStatus.OK, toolRun.status)

        val assistantMsg =
            AssistantMsg(
                key = "a-1",
                at = 2000L,
                blocks =
                    listOf(
                        Block.Text("I ran the command."),
                        Block.Thinking("Let me check the results."),
                        Block.ToolCall("call-1", "bash", null),
                    ),
            )
        assertEquals(3, assistantMsg.blocks.size)

        val subagent =
            SubagentInfo(
                taskId = "task-1",
                agent = "research-agent",
                status = "completed",
                turns = 3,
                toolCalls = 5,
                cost = 0.042,
            )
        assertEquals("research-agent", subagent.agent)
        assertNotNull(subagent.cost)
        assertEquals("0.042", formatCost(subagent.cost!!))
    }
}
