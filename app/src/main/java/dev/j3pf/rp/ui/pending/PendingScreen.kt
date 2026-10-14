package dev.j3pf.rp.ui.pending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.j3pf.rp.ui.components.AppHeader
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineHeroDropdown
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.parseColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingItem(
    val prompt: Prompt,
    val agent: AgentSummary?,
)

data class PendingUiState(
    val machines: List<MachineSummary> = emptyList(),
    val agents: List<AgentSummary> = emptyList(),
    val items: List<PendingItem> = emptyList(),
    val selected: String = AppSettings.ALL,
)

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
            combine(prompts.open, agents.agents, machines.machines, settings.settings) { ps, ags, ms, st ->
                val byId = ags.associateBy { it.agentId }
                val items =
                    ps.map { PendingItem(it, byId[it.agentId]) }.filter { item ->
                        st.selectedContext == AppSettings.ALL || item.agent?.machineContext == st.selectedContext ||
                            item.agent?.machineId == st.selectedContext
                    }
                // Orden: por peligro y antigüedad (el DAO ya ordena; se mantiene estable aquí).
                PendingUiState(
                    ms,
                    ags,
                    items.sortedWith(
                        compareByDescending<PendingItem> { Danger.rank(it.prompt.danger) }.thenBy {
                            it.prompt.openedAt
                                ?: 0L
                        },
                    ),
                    st.selectedContext,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PendingUiState())

        fun select(ctx: String) {
            viewModelScope.launch { settings.setSelectedContext(ctx) }
        }
    }

/** Bandeja global: todos los prompts abiertos de todos los agentes; tocar abre la hoja de respuesta. */
@Composable
fun PendingScreen(
    onOpenPrompt: (String) -> Unit,
    vm: PendingViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            greeting = "¿Hay decisiones pendientes?",
            title = "Bandeja de Entrada",
        )

        MachineHeroDropdown(
            machines = st.machines,
            agents = st.agents,
            selected = st.selected,
            onSelect = vm::select,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (st.items.isEmpty()) {
            EmptyState(
                title = "Todo al día",
                subtitle = "Cuando un agente pregunte o solicite autorización aparecerá aquí de inmediato.",
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(st.items, key = { it.prompt.promptId }) { item ->
                    PendingRow(item) { onOpenPrompt(item.prompt.promptId) }
                }
            }
        }
    }
}

@Composable
private fun PendingRow(
    item: PendingItem,
    onClick: () -> Unit,
) {
    val p = item.prompt
    val isHighDanger = p.danger == Danger.HIGH

    SurfaceCard(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (isHighDanger) 2.dp else 1.dp,
        border =
            if (isHighDanger) {
                BorderStroke(1.dp, RpColors.danger.copy(alpha = 0.5f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.agent?.let {
                    Surface(
                        shape = CircleShape,
                        color = parseColor(it.machineColor).copy(alpha = 0.16f),
                        modifier = Modifier.size(20.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ColorDot(parseColor(it.machineColor), 8)
                        }
                    }
                }
                Text(
                    text = item.agent?.let { "${it.machineName} · ${agentTitle(it.sessionName, it.cwd, it.agentId)}" } ?: p.agentId,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (p.category) {
                    PromptCategory.PERMISSION -> TagChip("Autorización", RpColors.warning)
                    PromptCategory.QUESTION -> TagChip("Pregunta", MaterialTheme.colorScheme.primary)
                    else -> TagChip("Diálogo")
                }
                if (isHighDanger) TagChip("Peligro alto", RpColors.danger)
            }

            Text(
                text = p.title.ifBlank { "Pregunta" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            val detail =
                p.tool
                    ?.name
                    ?.let { "$it: " }
                    .orEmpty() + (p.message ?: "")
            if (detail.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
