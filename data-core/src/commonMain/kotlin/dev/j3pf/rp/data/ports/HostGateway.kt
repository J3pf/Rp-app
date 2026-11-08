package dev.j3pf.rp.data.ports

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.HerdrLayout
import dev.j3pf.rp.protocol.HostDirs
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.HostGitDiscardResult
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.HostSpawned
import kotlinx.coroutines.flow.StateFlow

/**
 * Relay-backed host operations and host-presence state.
 *
 * This is a pure delegate: asks pass through 1:1, in order, with no retry, backoff, deduplication,
 * or reordering. Ordering and retries belong to the caller/model; S19 exactly-once behavior remains
 * in the prompt sheet unless an orchestrator ruling changes that ownership.
 */
interface HostGateway {
    val hosts: StateFlow<Map<String, HostInfo>>

    fun start()

    fun clear()

    suspend fun listDirs(
        machineId: String,
        path: String,
    ): HostResult<HostDirs>

    suspend fun herdrLayout(machineId: String): HostResult<HerdrLayout>

    suspend fun spawn(req: HostSpawn): HostResult<HostSpawned>

    suspend fun gitDiff(
        machineId: String,
        cwd: String,
        path: String? = null,
    ): HostResult<HostGitDiffResult>

    suspend fun gitDiscard(
        machineId: String,
        cwd: String,
        path: String,
    ): HostResult<HostGitDiscardResult>

    /** Android keeps today's resume-argument derivation; common callers pass the agent unchanged. */
    suspend fun resumeAgent(agent: AgentSummary): HostResult<HostSpawned>
}

sealed interface HostResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : HostResult<T>

    data class Failed(
        val message: String,
    ) : HostResult<Nothing>
}
