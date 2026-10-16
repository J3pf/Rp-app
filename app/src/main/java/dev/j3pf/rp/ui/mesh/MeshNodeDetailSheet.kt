package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshNode
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshNodeDetailSheet(
    detail: MeshNodeDetail,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onViewConversation: (String) -> Unit,
) {
    val node = detail.node
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(node.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${formatMeshRole(node.role)} · ${formatMeshStatus(node.status)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BadgeText(formatMeshRole(node.role), emphasized = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeText(node.machineName)
                BadgeText(if (node.online) "En línea" else "Desconectado", emphasized = node.online)
            }
            node.activity?.takeIf { it.isNotBlank() }?.let { Text("Actividad: $it", style = MaterialTheme.typography.bodyMedium) }

            SectionTitle("Detalles")
            DetailLine("Máquina", node.machineName)
            DetailLine("Directorio (cwd)", node.cwd ?: "No reportado")
            DetailLine("Rama git", node.gitBranch ?: "No reportado")
            DetailLine("Modelo", node.model ?: "No reportado")
            DetailLine("Razonamiento", "No reportado")

            SectionTitle("TODOs")
            node.todos?.let { todos ->
                DetailLine("Progreso", formatMeshTodos(todos.done, todos.total))
                DetailLine("En progreso", todos.inProgress.toString())
                DetailLine("Pendientes", todos.pending.toString())
                if (todos.blocked > 0) BadgeText("Bloqueadas · ${todos.blocked}", emphasized = true)
                todos.current?.takeIf { it.isNotBlank() }?.let { current ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BadgeText("Actual", emphasized = true)
                            Text(
                                current,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                Text(
                    "Los detalles completos de los elementos TODO no se incluyen en los datos de la red.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
                ?: Text(
                    "No se reporta progreso de TODOs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            SectionTitle("Subagentes")
            if (node.subagents.isEmpty()) {
                Text("No hay subagentes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                node.subagents.forEach { subagent ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    subagent.label?.takeIf(String::isNotBlank) ?: subagent.agent,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                BadgeText(formatMeshSubagentStatus(subagent.status), emphasized = subagent.status == "working")
                            }
                            DetailLine("Tarea", subagent.taskId)
                            DetailLine("Agente", subagent.agent)
                            subagent.durationMs?.let { DetailLine("Duración", formatDuration(it)) }
                            subagent.tokens?.let { tokens ->
                                val count = tokens.total ?: listOfNotNull(tokens.prompt, tokens.completion).sum().takeIf { it > 0 }
                                count?.let { DetailLine("Tokens", it.toString()) }
                            }
                            subagent.tools?.takeIf { it.isNotEmpty() }?.let { DetailLine("Herramientas", it.joinToString(", ")) }
                            subagent.thinking?.let { DetailLine("Razonamiento", it) }
                        }
                    }
                }
            }

            SectionTitle("Conversaciones recientes")
            if (detail.isLoadingComms && detail.recentComms.isEmpty()) {
                Text("Cargando conversaciones…", style = MaterialTheme.typography.bodyMedium)
            } else if (detail.recentComms.isEmpty()) {
                Text(
                    "Sin conversaciones recientes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                detail.recentComms.forEach { comm ->
                    RecentCommRow(node = node, comm = comm)
                    val peerId = peerId(node, comm)
                    val peerName = peerName(node, comm)
                    if (peerId != null) {
                        OutlinedButton(
                            onClick = { onViewConversation(peerId) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Ver conversación con $peerName")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onOpenChat(node.agentId) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Abrir chat")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable
private fun RecentCommRow(
    node: MeshNode,
    comm: MeshComm,
) {
    val incoming = comm.toAgentId == node.agentId
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeText(if (incoming) "Entrante" else "Saliente", emphasized = incoming)
                Spacer(Modifier.width(8.dp))
                BadgeText(formatMeshCommAction(comm.action.raw))
                Spacer(Modifier.weight(1f))
                Text(
                    formatTimestamp(comm.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${comm.fromName} → ${comm.toName}", style = MaterialTheme.typography.labelMedium)
            Text(formatMeshCommText(comm.text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.36f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.64f))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BadgeText(
    label: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun peerId(
    node: MeshNode,
    comm: MeshComm,
): String? =
    when (node.agentId) {
        comm.fromAgentId -> comm.toAgentId
        comm.toAgentId -> comm.fromAgentId
        else -> null
    }

private fun peerName(
    node: MeshNode,
    comm: MeshComm,
): String = if (comm.fromAgentId == node.agentId) comm.toName else comm.fromName

private fun formatDuration(durationMs: Long): String =
    when {
        durationMs < 1_000 -> "${durationMs}ms"
        durationMs < 60_000 -> "${durationMs / 1_000}s"
        else -> "${durationMs / 60_000}m ${(durationMs % 60_000) / 1_000}s"
    }

private fun formatTimestamp(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
