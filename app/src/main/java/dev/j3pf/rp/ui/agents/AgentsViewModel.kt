package dev.j3pf.rp.ui.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.CloseOutcome
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.HostResult
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de la sección Archivados y del diálogo de cierre; vive en el view model, no en Room. */
data class AgentsLifecycleState(
    val forgetAgentId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AgentsViewModel
    @Inject
    constructor(
        machines: MachinesRepository,
        agents: AgentsRepository,
        private val settings: SettingsRepository,
        private val connection: RelayConnection,
        private val hostRepo: HostRepository,
    ) : ViewModel() {
        private val isRefreshing = MutableStateFlow(false)
        private val _lifecycleState = MutableStateFlow(AgentsLifecycleState())
        val lifecycleState: StateFlow<AgentsLifecycleState> = _lifecycleState.asStateFlow()

        val state: StateFlow<AgentsUiState> =
            combine(
                machines.machines,
                agents.agents,
                settings.settings,
                isRefreshing,
            ) { ms, all, st, refreshing ->
                val sel = st.selectedContext
                val visibleMachines = if (sel == AppSettings.ALL) ms else ms.filter { it.context == sel || it.machineId == sel }
                val visibleAgents = if (sel == AppSettings.ALL) all else all.filter { it.machineContext == sel || it.machineId == sel }
                val byMachine = visibleAgents.groupBy { it.machineId }
                // Agrupado por PC (cabecera de color); orden: prompts primero, working segundo, lastSeenAt más reciente.
                val groups =
                    visibleMachines.mapNotNull { m ->
                        val list = byMachine[m.machineId].orEmpty()
                        if (list.isEmpty()) null else MachineGroup(m, sortAgentsWithinGroup(list))
                    } +
                        byMachine.filterKeys { id -> visibleMachines.none { it.machineId == id } }.map { (id, list) ->
                            MachineGroup(
                                MachineSummary(id, list.first().machineName, list.first().machineContext, list.first().machineColor),
                                sortAgentsWithinGroup(list),
                            )
                        }
                val sortedGroups =
                    groups.sortedByDescending { g ->
                        val hasPrompts = g.agents.any { it.openPromptCount > 0 }
                        val isWorking = g.agents.any { it.status == "working" && it.online }
                        val maxLastSeen = g.agents.maxOfOrNull { it.lastSeenAt ?: 0L } ?: 0L
                        (if (hasPrompts) 2_000_000_000_000L else 0L) +
                            (if (isWorking) 1_000_000_000_000L else 0L) +
                            maxLastSeen
                    }
                AgentsUiState(ms, all, sortedGroups, sel, canSpawn = visibleMachines.any { it.hostOnline }, refreshing = refreshing)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentsUiState())

        private val agentsRepo = agents

        fun refresh() {
            viewModelScope.launch {
                isRefreshing.value = true
                connection.retryNow()
                delay(600)
                isRefreshing.value = false
            }
        }

        fun select(ctx: String) {
            viewModelScope.launch { settings.setSelectedContext(ctx) }
        }

        fun forget(agentId: String) {
            _lifecycleState.update { it.copy(forgetAgentId = agentId, message = null) }
        }

        fun cancelForget() {
            _lifecycleState.update { it.copy(forgetAgentId = null) }
        }

        fun confirmForget() {
            val agentId = _lifecycleState.value.forgetAgentId ?: return
            _lifecycleState.update { it.copy(forgetAgentId = null) }
            viewModelScope.launch {
                when (val result = agentsRepo.close(agentId)) {
                    CloseOutcome.Closed -> Unit
                    CloseOutcome.Archived ->
                        _lifecycleState.update { it.copy(message = "No había un pane de Herdr vinculado; el historial quedó archivado.") }
                    is CloseOutcome.Failed -> _lifecycleState.update { it.copy(message = result.message) }
                }
            }
        }

        fun consumeLifecycleMessage() {
            _lifecycleState.update { it.copy(message = null) }
        }

        fun resumeAgent(
            agent: AgentSummary,
            onMessage: (String) -> Unit,
        ) {
            viewModelScope.launch {
                when (val r = hostRepo.resumeAgent(agent)) {
                    is HostResult.Ok -> onMessage("Agente reanudado en la PC")
                    is HostResult.Failed -> onMessage("No se pudo reanudar: ${r.message}")
                }
            }
        }
    }
