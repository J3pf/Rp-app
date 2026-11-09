package dev.j3pf.rp.ui.pending

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.ui.components.rememberActivity
import dev.j3pf.rp.ui.promptsheet.AnswerUi
import dev.j3pf.rp.ui.promptsheet.PromptSheetViewModel
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
    promptVm: PromptSheetViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val promptStatus by promptVm.status.collectAsStateWithLifecycle()
    val activity = rememberActivity()
    val scope = rememberCoroutineScope()
    var busyPromptId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(promptStatus) {
        when (promptStatus) {
            AnswerUi.Done, AnswerUi.AlreadyResolved, is AnswerUi.Queued, is AnswerUi.Error -> {
                promptVm.reset()
                busyPromptId = null
            }
            else -> {}
        }
    }

    PendingContent(
        state = st,
        onOpenPrompt = onOpenPrompt,
        onSelect = vm::select,
        busyPromptId = busyPromptId,
        onQuickAnswer = { p, answer ->
            scope.launch {
                if (promptVm.requiresAuth(p)) {
                    if (activity == null) {
                        onOpenPrompt(p.promptId)
                        return@launch
                    }
                    if (promptVm.gate.authenticate("Confirma tu identidad", p.title.take(80)) != BiometricAuthResult.OK) {
                        return@launch
                    }
                }
                busyPromptId = p.promptId
                promptVm.answer(p.promptId, answer)
            }
        },
    )
}
