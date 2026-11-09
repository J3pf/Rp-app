package dev.j3pf.rp.ui.pending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.ui.components.AppHeader
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineHeroDropdown
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.parseColor

/** Bandeja global: todos los prompts abiertos de todos los agentes; tocar abre la hoja de respuesta. */
@Composable
fun PendingContent(
    state: PendingUiState,
    onOpenPrompt: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    busyPromptId: String? = null,
    onQuickAnswer: ((Prompt, Answer) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            greeting = "¿Hay decisiones pendientes?",
            title = "Bandeja de Entrada",
        )

        MachineHeroDropdown(
            machines = state.machines,
            agents = state.agents,
            selected = state.selected,
            onSelect = onSelect,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (state.items.isEmpty()) {
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
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.prompt.promptId }) { item: PendingItem ->
                    PendingRow(
                        item = item,
                        busy = busyPromptId == item.prompt.promptId,
                        onQuickAnswer = onQuickAnswer,
                        onClick = { onOpenPrompt(item.prompt.promptId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingRow(
    item: PendingItem,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onQuickAnswer: ((Prompt, Answer) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = item.prompt
    val isHighDanger = p.danger == Danger.HIGH

    SurfaceCard(
        onClick = onClick,
        modifier =
            modifier
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
                val label =
                    item.agent?.let {
                        "${it.machineName} · ${agentTitle(it.sessionName, it.cwd, it.agentId)}"
                    } ?: p.agentId
                Text(
                    text = label,
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

            if (onQuickAnswer != null) {
                when {
                    p.category == PromptCategory.PERMISSION -> {
                        val opts = p.options
                        val allow = opts.firstOrNull { isPendingAllow(it) } ?: opts.firstOrNull()
                        val always = opts.firstOrNull { isPendingAlways(it) }
                        val deny = opts.lastOrNull { isPendingDeny(it) } ?: opts.takeIf { it.size > 1 }?.last()
                        val allowAnswer = if (allow == null) Answer.confirm(true) else Answer.option(allow.id)
                        val denyAnswer = if (deny == null) Answer(confirmed = false) else Answer(optionIds = listOf(deny.id))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { onQuickAnswer(p, allowAnswer) },
                                enabled = !busy,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(allow?.label ?: "Permitir", style = MaterialTheme.typography.labelMedium)
                            }
                            if (always != null && always != allow) {
                                FilledTonalButton(
                                    onClick = { onQuickAnswer(p, Answer.option(always.id)) },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Text(always.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            OutlinedButton(
                                onClick = { onQuickAnswer(p, denyAnswer) },
                                enabled = !busy,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, RpColors.danger.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RpColors.danger),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(deny?.label ?: "Denegar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    p.kind == PromptKind.CONFIRM && p.options.isEmpty() -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { onQuickAnswer(p, Answer.confirm(true)) },
                                enabled = !busy,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text("Sí", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { onQuickAnswer(p, Answer.confirm(false)) },
                                enabled = !busy,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text("No", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isPendingDeny(o: PromptOption) =
    o.label.lowercase().let {
        it.startsWith("deneg") ||
            it.startsWith("deny") ||
            it.startsWith("rechaz") ||
            it == "no"
    }

private fun isPendingAlways(o: PromptOption) = o.label.lowercase().let { "siempre" in it || "always" in it }

private fun isPendingAllow(o: PromptOption) =
    o.label.lowercase().let {
        (
            it.startsWith("permit") ||
                it.startsWith("allow") ||
                it.startsWith("acept") ||
                it == "sí" ||
                it == "si"
        ) &&
            !isPendingAlways(o)
    }
