package dev.j3pf.rp.data.ports

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import kotlinx.coroutines.flow.Flow

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
 * Per-agent observation is derived in common code on top of [agents]; [observe]
 * is not a port member to avoid competing sources of truth (orchestrator ruling).
 */
interface AgentsGate {
    val agents: Flow<List<AgentSummary>>

    fun start()

    suspend fun clear()

    suspend fun close(agentId: String): CloseOutcome

    suspend fun archived(
        machineId: String? = null,
        before: Long? = null,
    ): ArchivedAgentsResult

    suspend fun purge(agentId: String): Boolean
}
