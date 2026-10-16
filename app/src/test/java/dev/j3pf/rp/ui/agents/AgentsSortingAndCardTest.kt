package dev.j3pf.rp.ui.agents

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AgentsSortingAndCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `sortAgentsWithinGroup orders open prompts first, then working, then most recent lastSeenAt`() {
        val aIdleOld =
            AgentSummary(
                agentId = "idle-old",
                machineId = "m1",
                sessionName = "Idle Old",
                status = "idle",
                openPromptCount = 0,
                lastSeenAt = 1000L,
            )
        val aIdleRecent =
            AgentSummary(
                agentId = "idle-recent",
                machineId = "m1",
                sessionName = "Idle Recent",
                status = "idle",
                openPromptCount = 0,
                lastSeenAt = 5000L,
            )
        val aWorkingNoPrompt =
            AgentSummary(
                agentId = "working-no-prompt",
                machineId = "m1",
                sessionName = "Working No Prompt",
                status = "working",
                openPromptCount = 0,
                lastSeenAt = 2000L,
            )
        val aWaitingPrompt =
            AgentSummary(
                agentId = "waiting-prompt",
                machineId = "m1",
                sessionName = "Waiting Prompt",
                status = "waiting_input",
                openPromptCount = 2,
                lastSeenAt = 1500L,
            )
        val aWorkingWithPrompt =
            AgentSummary(
                agentId = "working-prompt",
                machineId = "m1",
                sessionName = "Working With Prompt",
                status = "working",
                openPromptCount = 1,
                lastSeenAt = 3000L,
            )

        val unsorted = listOf(aIdleOld, aIdleRecent, aWorkingNoPrompt, aWaitingPrompt, aWorkingWithPrompt)
        val sorted = sortAgentsWithinGroup(unsorted)

        // 1 y 2: Agentes con openPromptCount > 0 (working primero, luego waiting)
        assertEquals("working-prompt", sorted[0].agentId)
        assertEquals("waiting-prompt", sorted[1].agentId)

        // 3: Agente trabajando sin prompts
        assertEquals("working-no-prompt", sorted[2].agentId)

        // 4 y 5: Agentes inactivos ordenados por lastSeenAt descendente
        assertEquals("idle-recent", sorted[3].agentId)
        assertEquals("idle-old", sorted[4].agentId)
    }

    @Test
    fun `AgentCard displays status, active task, todo progress, relative activity time, and preview at a glance`() {
        val now = System.currentTimeMillis()
        val agent =
            AgentSummary(
                agentId = "agent-1",
                machineId = "m1",
                sessionName = "Refactor UI",
                cwd = "/workspace/project",
                gitBranch = "feat/refresh",
                status = "working",
                online = true,
                openPromptCount = 0,
                activeTask = "Migrando componentes de UI",
                todoCompleted = 3,
                todoTotal = 5,
                lastSeenAt = now - 120_000L, // hace 2 min
                lastMessagePreview = "Vista previa del último mensaje del agente",
            )

        rule.setContent {
            RpTheme {
                AgentCard(
                    a = agent,
                    onClick = {},
                    onForget = {},
                )
            }
        }

        // Título de sesión
        rule.onNodeWithText("Refactor UI").assertIsDisplayed()

        // Estado
        rule.onNodeWithText("Trabajando").assertIsDisplayed()

        // Tiempo relativo de actividad (en la tarjeta de agente working muestra "activo ahora")
        rule.onNodeWithText("activo ahora", substring = true).assertIsDisplayed()

        // Tarea activa visible con icono
        rule.onNodeWithText("Migrando componentes de UI").assertIsDisplayed()

        // Progreso de tareas (3/5 = 60%)
        rule.onNodeWithText("Tareas: 3/5 (60%)").assertIsDisplayed()

        // Vista previa del último mensaje visible
        rule.onNodeWithText("Vista previa del último mensaje del agente").assertIsDisplayed()
    }
}
