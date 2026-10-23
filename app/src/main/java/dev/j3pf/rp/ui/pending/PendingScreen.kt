package dev.j3pf.rp.ui.pending

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.Prompt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingViewModel
    @Inject
    constructor(
        prompts: PromptsRepository,
        agents: AgentsRepository,
        machines: MachinesRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<PendingUiState> =
            combine(
                prompts.open,
                agents.agents,
                machines.machines,
                settings.settings,
            ) { ps: List<Prompt>, ags: List<AgentSummary>, ms: List<MachineSummary>, st: AppSettings ->
                val byId = ags.associateBy { a: AgentSummary -> a.agentId }
                val rawItems = ps.map { p: Prompt -> PendingItem(p, byId[p.agentId]) }
                val filtered = filterPendingItems(rawItems, st.selectedContext)
                PendingUiState(
                    machines = ms,
                    agents = ags,
                    items = sortPendingItems(filtered),
                    selected = st.selectedContext,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PendingUiState())

        fun select(ctx: String) {
            viewModelScope.launch { settings.setSelectedContext(ctx) }
        }
    }

@Composable
fun PendingScreen(
    onOpenPrompt: (String) -> Unit,
    vm: PendingViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    PendingContent(
        state = st,
        onOpenPrompt = onOpenPrompt,
        onSelect = vm::select,
    )
}
