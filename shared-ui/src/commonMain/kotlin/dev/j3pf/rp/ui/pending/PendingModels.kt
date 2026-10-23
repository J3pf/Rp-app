package dev.j3pf.rp.ui.pending

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.ui.components.ALL_MACHINES

data class PendingItem(
    val prompt: Prompt,
    val agent: AgentSummary?,
)

data class PendingUiState(
    val machines: List<MachineSummary> = emptyList(),
    val agents: List<AgentSummary> = emptyList(),
    val items: List<PendingItem> = emptyList(),
    val selected: String = ALL_MACHINES,
)

fun sortPendingItems(items: List<PendingItem>): List<PendingItem> =
    items.sortedWith(
        compareByDescending<PendingItem> { Danger.rank(it.prompt.danger) }.thenBy {
            it.prompt.openedAt ?: 0L
        },
    )

fun filterPendingItems(
    items: List<PendingItem>,
    selectedContext: String,
): List<PendingItem> =
    items.filter { item ->
        selectedContext == ALL_MACHINES ||
            item.agent?.machineContext == selectedContext ||
            item.agent?.machineId == selectedContext
    }
