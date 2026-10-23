package dev.j3pf.rp.ui.pending

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.ui.components.ALL_MACHINES
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingModelsTest {
    @Test
    fun filterPendingItemsMatchesContextAndMachineId() {
        val a1 = AgentSummary(agentId = "a1", machineId = "m1", machineContext = "work")
        val a2 = AgentSummary(agentId = "a2", machineId = "m2", machineContext = "personal")
        val p1 = createPrompt(promptId = "p1", agentId = "a1")
        val p2 = createPrompt(promptId = "p2", agentId = "a2")
        val items =
            listOf(
                PendingItem(prompt = p1, agent = a1),
                PendingItem(prompt = p2, agent = a2),
            )

        val all = filterPendingItems(items, ALL_MACHINES)
        assertEquals(2, all.size)

        val workOnly = filterPendingItems(items, "work")
        assertEquals(listOf("p1"), workOnly.map { it.prompt.promptId })

        val m2Only = filterPendingItems(items, "m2")
        assertEquals(listOf("p2"), m2Only.map { it.prompt.promptId })

        val none = filterPendingItems(items, "unknown")
        assertEquals(0, none.size)
    }

    @Test
    fun sortPendingItemsByDangerThenOpenedAt() {
        val pLowOld = createPrompt(promptId = "low-old", danger = Danger.LOW, openedAt = 100L)
        val pHighNew = createPrompt(promptId = "high-new", danger = Danger.HIGH, openedAt = 500L)
        val pHighOld = createPrompt(promptId = "high-old", danger = Danger.HIGH, openedAt = 200L)
        val pMed = createPrompt(promptId = "med", danger = Danger.MEDIUM, openedAt = 300L)

        val items =
            listOf(
                PendingItem(prompt = pLowOld, agent = null),
                PendingItem(prompt = pHighNew, agent = null),
                PendingItem(prompt = pMed, agent = null),
                PendingItem(prompt = pHighOld, agent = null),
            )

        val sorted = sortPendingItems(items)
        val sortedIds = sorted.map { it.prompt.promptId }

        assertEquals(listOf("high-old", "high-new", "med", "low-old"), sortedIds)
    }

    private fun createPrompt(
        promptId: String,
        agentId: String = "test-agent",
        danger: String = Danger.LOW,
        openedAt: Long = 1000L,
    ): Prompt =
        Prompt(
            promptId = promptId,
            agentId = agentId,
            category = PromptCategory.QUESTION,
            danger = danger,
            title = "Test Prompt $promptId",
            openedAt = openedAt,
        )
}
