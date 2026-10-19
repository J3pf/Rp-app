package dev.j3pf.rp.ui.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
                AgentsUiState(ms, all, groups, sel, canSpawn = visibleMachines.any { it.hostOnline }, refreshing = refreshing)
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
            viewModelScope.launch { agentsRepo.forget(agentId) }
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
