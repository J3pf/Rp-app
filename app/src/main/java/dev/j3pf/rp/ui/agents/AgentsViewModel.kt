package dev.j3pf.rp.ui.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MachineGroup(val machine: MachineSummary, val agents: List<AgentSummary>)

data class AgentsUiState(
    val machines: List<MachineSummary> = emptyList(),
    val allAgents: List<AgentSummary> = emptyList(),
    val groups: List<MachineGroup> = emptyList(),
    val selected: String = AppSettings.ALL,
    /** Alguna PC visible tiene `rp-host` conectado → se puede crear agentes. */
    val canSpawn: Boolean = false,
)

@HiltViewModel
class AgentsViewModel @Inject constructor(
    machines: MachinesRepository,
    agents: AgentsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AgentsUiState> = combine(machines.machines, agents.agents, settings.settings) { ms, all, st ->
        val sel = st.selectedContext
        val visibleMachines = if (sel == AppSettings.ALL) ms else ms.filter { it.context == sel }
        val visibleAgents = if (sel == AppSettings.ALL) all else all.filter { it.machineContext == sel }
        val byMachine = visibleAgents.groupBy { it.machineId }
        // Agrupado por PC (cabecera de color); las PC sin agentes también se muestran si están online.
        val groups = visibleMachines.mapNotNull { m ->
            val list = byMachine[m.machineId].orEmpty()
            if (list.isEmpty()) null else MachineGroup(m, list)
        } + byMachine.filterKeys { id -> visibleMachines.none { it.machineId == id } }.map { (id, list) ->
            MachineGroup(MachineSummary(id, list.first().machineName, list.first().machineContext, list.first().machineColor), list)
        }
        AgentsUiState(ms, all, groups, sel, canSpawn = visibleMachines.any { it.hostOnline })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentsUiState())

    private val agentsRepo = agents

    fun select(ctx: String) { viewModelScope.launch { settings.setSelectedContext(ctx) } }
    fun forget(agentId: String) { viewModelScope.launch { agentsRepo.forget(agentId) } }
}
