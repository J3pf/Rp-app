package dev.j3pf.rp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.util.contextEmoji
import dev.j3pf.rp.util.contextLabel
import dev.j3pf.rp.util.parseColor
import dev.j3pf.rp.util.relativeTime

@Composable
fun ColorDot(color: Color, size: Int = 10, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

/**
 * Selector de PC persistente (PLAN-APP §3): "Todas" + un chip por contexto (Personal, Empresa…), con el color
 * de la PC, el número de agentes y 🔔 si hay prompts abiertos.
 */
@Composable
fun MachineFilterBar(
    machines: List<MachineSummary>,
    agents: List<AgentSummary>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexts = remember(machines) { machines.map { it.context }.distinct() }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val total = agents.size
            val prompts = agents.sumOf { it.openPromptCount }
            FilterChip(
                selected = selected == AppSettings.ALL,
                onClick = { onSelect(AppSettings.ALL) },
                label = { Text("Todas ($total${if (prompts > 0) " · $prompts🔔" else ""})") },
            )
        }
        items(contexts, key = { it }) { ctx ->
            val ms = machines.filter { it.context == ctx }
            val count = agents.count { it.machineContext == ctx }
            val prompts = agents.filter { it.machineContext == ctx }.sumOf { it.openPromptCount }
            val rawColor = ms.firstOrNull()?.color
            val dotColor = remember(rawColor) { parseColor(rawColor) }
            FilterChip(
                selected = selected == ctx,
                onClick = { onSelect(ctx) },
                leadingIcon = { ColorDot(dotColor) },
                label = { Text("${contextEmoji(ctx)} ${contextLabel(ctx)} ($count${if (prompts > 0) " · $prompts🔔" else ""})") },
            )
        }
    }
}

/** Chip de estado del agente: Trabajando ⏳ / Esperando tu respuesta 🔔 / Inactivo / Desconectado hace 3 h. */
@Composable
fun StatusChip(agent: AgentSummary, modifier: Modifier = Modifier) {
    val (text, color) = when {
        !agent.online -> "Desconectado ${relativeTime(agent.lastSeenAt)}".trim() to MaterialTheme.colorScheme.outline
        agent.openPromptCount > 0 || agent.status == AgentStatus.WAITING_INPUT -> "Esperando tu respuesta 🔔" to RpColors.warning
        agent.status == AgentStatus.WORKING -> "Trabajando ⏳" to MaterialTheme.colorScheme.primary
        agent.status == AgentStatus.COMPACTING -> "Compactando" to MaterialTheme.colorScheme.primary
        agent.status == AgentStatus.ERROR -> "Error" to RpColors.danger
        else -> "Inactivo" to RpColors.success
    }
    TagChip(text, color, modifier)
}

@Composable
fun TagChip(text: String, color: Color = MaterialTheme.colorScheme.secondary, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.size(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Banner de estado de la conexión con el relay (sólo se muestra si no está conectado). */
@Composable
fun ConnectionBanner(state: ConnState, modifier: Modifier = Modifier) {
    val text = when (state) {
        ConnState.Connected -> return
        ConnState.Connecting -> "Conectando con el relay…"
        ConnState.Disconnected -> "Sin conexión con el relay · reintentando"
        is ConnState.Fatal -> when (state.code) {
            4001 -> "El token fue revocado. Vuelve a emparejar el dispositivo."
            4002 -> "Versión de protocolo incompatible. Actualiza la app o el relay."
            else -> "Conexión cerrada (${state.code})"
        }
    }
    val fatal = state is ConnState.Fatal
    Surface(
        modifier.fillMaxWidth(),
        color = if (fatal) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun RowSpacer(w: Int = 8) = Spacer(Modifier.width(w.dp))

@Composable
fun SmallChip(text: String, modifier: Modifier = Modifier) {
    AssistChip(onClick = {}, label = { Text(text, maxLines = 1) }, modifier = modifier, colors = AssistChipDefaults.assistChipColors())
}

@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
