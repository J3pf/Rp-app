package dev.j3pf.rp.ui.pending

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineFilterBar
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.parseColor
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingItem(val prompt: Prompt, val agent: AgentSummary?)

data class PendingUiState(
    val machines: List<MachineSummary> = emptyList(),
    val agents: List<AgentSummary> = emptyList(),
    val items: List<PendingItem> = emptyList(),
    val selected: String = AppSettings.ALL,
)

@HiltViewModel
class PendingViewModel @Inject constructor(
    prompts: PromptsRepository,
    agents: AgentsRepository,
    machines: MachinesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<PendingUiState> = combine(prompts.open, agents.agents, machines.machines, settings.settings) { ps, ags, ms, st ->
        val byId = ags.associateBy { it.agentId }
        val items = ps.map { PendingItem(it, byId[it.agentId]) }.filter { item ->
            st.selectedContext == AppSettings.ALL || item.agent?.machineContext == st.selectedContext
        }
        // Orden: por peligro y antigüedad (el DAO ya ordena; se mantiene estable aquí).
        PendingUiState(ms, ags, items.sortedWith(compareByDescending<PendingItem> { Danger.rank(it.prompt.danger) }.thenBy { it.prompt.openedAt ?: 0L }), st.selectedContext)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PendingUiState())

    fun select(ctx: String) { viewModelScope.launch { settings.setSelectedContext(ctx) } }
}

/** Bandeja global: todos los prompts abiertos de todos los agentes; tocar abre la hoja de respuesta. */
@Composable
fun PendingScreen(onOpenPrompt: (String) -> Unit, vm: PendingViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column {
        MachineFilterBar(st.machines, st.agents, st.selected, vm::select, Modifier.padding(vertical = 8.dp))
        if (st.items.isEmpty()) {
            EmptyState("Nada pendiente", "Cuando un agente pregunte o pida autorización aparecerá aquí.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(st.items, key = { it.prompt.promptId }) { PendingRow(it) { onOpenPrompt(it.prompt.promptId) } }
            }
        }
    }
}

@Composable
private fun PendingRow(item: PendingItem, onClick: () -> Unit) {
    val p = item.prompt
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.agent?.let { ColorDot(parseColor(it.machineColor), 8) }
                Text(
                    item.agent?.let { "${it.machineName} · ${agentTitle(it.sessionName, it.cwd, it.agentId)}" } ?: p.agentId,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (p.category) {
                    PromptCategory.PERMISSION -> TagChip("Autorización", RpColors.warning)
                    PromptCategory.QUESTION -> TagChip("Pregunta", MaterialTheme.colorScheme.primary)
                    else -> TagChip("Diálogo")
                }
                if (p.danger == Danger.HIGH) TagChip("Peligro alto", RpColors.danger)
            }
            Text(p.title.ifBlank { "Pregunta" }, style = MaterialTheme.typography.titleSmall)
            val detail = p.tool?.name?.let { "$it: " }.orEmpty() + (p.message ?: "")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
