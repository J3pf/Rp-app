package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshNodeDetailSheet(
    detail: MeshNodeDetail,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onViewConversation: (String) -> Unit,
    initiallyExpandedTechnicalDetails: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        MeshNodeDetailContent(
            detail = detail,
            onDismiss = onDismiss,
            onOpenChat = onOpenChat,
            onViewConversation = onViewConversation,
            initiallyExpandedTechnicalDetails = initiallyExpandedTechnicalDetails,
        )
    }
}

@Composable
fun MeshNodeDetailContent(
    detail: MeshNodeDetail,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onViewConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpandedTechnicalDetails: Boolean = false,
) {
    val node = detail.node
    val displayName = formatMeshDisplayName(node)
    val headerSubtitle =
        node.gitBranch
            ?.takeIf { it.isNotBlank() }
            ?.let { "Rama $it · " }
            .orEmpty() + formatMeshStatus(node.status)
    var showTechnicalDetails by remember { mutableStateOf(initiallyExpandedTechnicalDetails) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header with Avatar, Display Name, Role Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AgentAvatar(
                displayName = displayName,
                machineColor = node.machineColor,
                online = node.online,
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    headerSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BadgeText(formatMeshRole(node.role), emphasized = true)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeText(formatMeshMachineName(node.machineName))
            BadgeText(if (node.online) "En línea" else "Desconectado", emphasized = node.online)
        }
        node.activity?.takeIf { it.isNotBlank() }?.let { Text("Actividad: $it", style = MaterialTheme.typography.bodyMedium) }

        SectionTitle("Detalles")
        DetailLine("Máquina", node.machineName)
        DetailLine("Rama git", node.gitBranch ?: "No reportado")
        DetailLine("Modelo", node.model ?: "No reportado")

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

        // Collapsed Technical Details with Copy Action (Spec S1)
        SectionTitle("Detalles técnicos")
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showTechnicalDetails = !showTechnicalDetails },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (showTechnicalDetails) "Ocultar detalles técnicos" else "Mostrar detalles técnicos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (showTechnicalDetails) "▲" else "▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (showTechnicalDetails) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TechnicalCopyRow(label = "ID de agente", value = node.agentId)
                TechnicalCopyRow(label = "ID de máquina", value = node.machineId)
                node.groupId?.let { TechnicalCopyRow(label = "ID de grupo", value = it) }
                node.parentAgentId?.let { TechnicalCopyRow(label = "ID de agente padre", value = it) }
                TechnicalCopyRow(label = "Directorio (cwd)", value = node.cwd ?: "No reportado")
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

@Composable
private fun TechnicalCopyRow(
    label: String,
    value: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                    copied = true
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = if (copied) "Copiado" else "Copiar",
                    style = MaterialTheme.typography.labelMedium,
                )
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
    val fromDisp =
        if (!isIdLooking(comm.fromName) && !comm.fromName.equals("Agent", ignoreCase = true)) {
            comm.fromName
        } else {
            "Agente"
        }
    val toDisp =
        if (!isIdLooking(comm.toName) && !comm.toName.equals("Agent", ignoreCase = true)) {
            comm.toName
        } else {
            "Agente"
        }

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
            Text("$fromDisp → $toDisp", style = MaterialTheme.typography.labelMedium)
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
): String {
    val raw = if (comm.fromAgentId == node.agentId) comm.toName else comm.fromName
    return if (!isIdLooking(raw) && !raw.equals("Agent", ignoreCase = true)) raw else "Agente"
}

private fun formatDuration(durationMs: Long): String =
    when {
        durationMs < 1_000 -> "${durationMs}ms"
        durationMs < 60_000 -> "${durationMs / 1_000}s"
        else -> "${durationMs / 60_000}m ${(durationMs % 60_000) / 1_000}s"
    }

private fun formatTimestamp(at: Long): String = formatMeshTime(at)
