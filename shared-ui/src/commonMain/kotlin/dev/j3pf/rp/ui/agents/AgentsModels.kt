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

private val HERDR_ORDER_PREFIX_REGEX = Regex("^#(\\d{1,6})\\|(.*)$")

fun herdrOrderOf(agent: AgentSummary): Int? {
    val rawTabId = agent.herdr?.tabId ?: return null
    val match = HERDR_ORDER_PREFIX_REGEX.matchEntire(rawTabId) ?: return null
    return match.groupValues[1].toIntOrNull()
}

fun cleanHerdrTabId(rawTabId: String?): String? {
    if (rawTabId == null) return null
    val match = HERDR_ORDER_PREFIX_REGEX.matchEntire(rawTabId)
    return if (match != null) {
        match.groupValues[2].takeIf { it.isNotEmpty() }
    } else {
        rawTabId.takeIf { it.isNotEmpty() }
    }
}

private fun attentionPriorityOf(agent: AgentSummary): Int =
    when {
        agent.openPromptCount > 0 || agent.status == "waiting_input" -> 3
        agent.status == "working" -> 2
        else -> 1
    }

/**
 * Ordena agentes dentro de una máquina siguiendo el panel `priority` de Herdr:
 * 1. Agentes en línea antes que desconectados.
 * 2. Prioridad de atención: esperando respuesta (`openPromptCount > 0` / `waiting_input`) > trabajando (`working`) > inactivo (`idle`).
 * 3. Dentro del mismo estado, respeta el orden exacto de Herdr (`#NNNN|tabId`, calculado por `state_change_seq`)
 *    sin que las tarjetas inactivas salten por actualizaciones de `lastSeenAt`.
 * 4. Para agentes sin índice de Herdr, usa `lastSeenAt` más reciente como desempate.
 */
fun sortAgentsWithinGroup(agents: List<AgentSummary>): List<AgentSummary> =
    agents.sortedWith { a, b ->
        val onlineCmp = b.online.compareTo(a.online)
        if (onlineCmp != 0) return@sortedWith onlineCmp

        val attentionCmp = attentionPriorityOf(b).compareTo(attentionPriorityOf(a))
        if (attentionCmp != 0) return@sortedWith attentionCmp

        val orderA = herdrOrderOf(a)
        val orderB = herdrOrderOf(b)
        if (orderA != null && orderB != null) {
            val orderCmp = orderA.compareTo(orderB)
            if (orderCmp != 0) orderCmp else a.agentId.compareTo(b.agentId)
        } else if (orderA != null) {
            -1
        } else if (orderB != null) {
            1
        } else {
            val seenCmp = (b.lastSeenAt ?: 0L).compareTo(a.lastSeenAt ?: 0L)
            if (seenCmp != 0) seenCmp else a.agentId.compareTo(b.agentId)
        }
    }

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
