package dev.j3pf.rp.data.ports

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

sealed interface CloseOutcome {
    data object Closed : CloseOutcome

    data object Archived : CloseOutcome

    data class Failed(
        val message: String,
    ) : CloseOutcome
}

sealed interface ArchivedAgentsResult {
    data class Ok(
        val agents: List<ArchivedAgentSummary>,
        val nextBefore: Long?,
    ) : ArchivedAgentsResult

    data class Failed(
        val message: String,
    ) : ArchivedAgentsResult
}

/**
 * Port contract for agent lifecycle and observation (Unit P2).
 *
 * Pure delegate: asks pass through 1:1, in order, with no retry or reordering.
 */
interface AgentsGate {
    val agents: Flow<List<AgentSummary>>

    fun observe(agentId: String): Flow<AgentSummary?> =
        agents.map { list -> list.firstOrNull { it.agentId == agentId } }.distinctUntilChanged()

    fun start()

    suspend fun clear()

    suspend fun close(agentId: String): CloseOutcome

    suspend fun archived(
        machineId: String? = null,
        before: Long? = null,
    ): ArchivedAgentsResult

    suspend fun purge(agentId: String): Boolean
}
