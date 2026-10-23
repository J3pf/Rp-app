package dev.j3pf.rp.ui.agents

import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary

enum class SessionQuickFilter(
    val label: String,
) {
    ALL("Todas"),
    ACTIVE("En línea"),
    AGY("✦ agy"),
    PI("π pi"),
}

data class MachineGroup(
    val machine: MachineSummary,
    val agents: List<AgentSummary>,
)

/** Ordena agentes dentro de una máquina: prompts abiertos primero, en línea antes que offline, working segundo, lastSeenAt más reciente después. */
fun sortAgentsWithinGroup(agents: List<AgentSummary>): List<AgentSummary> =
    agents.sortedWith(
        compareByDescending<AgentSummary> { it.openPromptCount > 0 }
            .thenByDescending { it.online }
            .thenByDescending { it.status == "working" }
            .thenByDescending { it.lastSeenAt ?: 0L },
    )

data class AgentsUiState(
    val machines: List<MachineSummary> = emptyList(),
    val allAgents: List<AgentSummary> = emptyList(),
    val groups: List<MachineGroup> = emptyList(),
    val selected: String = AppSettings.ALL,
    val isLoading: Boolean = false,
    /** Alguna PC visible tiene `rp-host` conectado → se puede crear agentes. */
    val canSpawn: Boolean = false,
    val refreshing: Boolean = false,
)

fun filterAgentGroups(
    groups: List<MachineGroup>,
    quickFilter: SessionQuickFilter,
    searchQuery: String,
): List<MachineGroup> {
    val q = searchQuery.trim().lowercase()
    if (quickFilter == SessionQuickFilter.ALL && q.isEmpty()) {
        return groups
    }
    return groups.mapNotNull { group ->
        val matching =
            group.agents.filter { a ->
                val matchesFilter =
                    when (quickFilter) {
                        SessionQuickFilter.ALL -> true
                        SessionQuickFilter.ACTIVE -> a.online
                        SessionQuickFilter.AGY -> a.launcher == "agy"
                        SessionQuickFilter.PI -> a.launcher != "agy"
                    }
                val matchesQuery =
                    if (q.isEmpty()) {
                        true
                    } else {
                        (a.sessionName?.lowercase()?.contains(q) == true) ||
                            (a.cwd?.lowercase()?.contains(q) == true) ||
                            (a.gitBranch?.lowercase()?.contains(q) == true) ||
                            (a.lastMessagePreview?.lowercase()?.contains(q) == true)
                    }
                matchesFilter && matchesQuery
            }
        if (matching.isEmpty()) null else group.copy(agents = matching)
    }
}
