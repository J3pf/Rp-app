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
                        "${roleName(node.role)} · ${node.status.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BadgeText(roleName(node.role), emphasized = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeText(node.machineName)
                BadgeText(if (node.online) "Online" else "Offline", emphasized = node.online)
            }
            node.activity?.takeIf { it.isNotBlank() }?.let { Text("Activity: $it", style = MaterialTheme.typography.bodyMedium) }

            SectionTitle("Details")
            DetailLine("Machine", node.machineName)
            DetailLine("cwd", node.cwd ?: "Not reported")
            DetailLine("Git branch", node.gitBranch ?: "Not reported")
            DetailLine("Model", node.model ?: "Not reported")
            DetailLine("Thinking", "Not reported by relay")

            SectionTitle("TODOs")
            node.todos?.let { todos ->
                DetailLine("Progress", "${todos.done} of ${todos.total} tasks")
                DetailLine("In progress", todos.inProgress.toString())
                DetailLine("Pending", todos.pending.toString())
                if (todos.blocked > 0) BadgeText("Blocked · ${todos.blocked}", emphasized = true)
                todos.current?.takeIf { it.isNotBlank() }?.let { current ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BadgeText("Current", emphasized = true)
                            Text(
                                current,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                Text(
                    "Full TODO item details are not included in the mesh data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
                ?: Text(
                    "No TODO progress reported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            SectionTitle("Subagents")
            if (node.subagents.isEmpty()) {
                Text("No subagents", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                BadgeText(subagent.status, emphasized = subagent.status == "working")
                            }
                            DetailLine("Task", subagent.taskId)
                            DetailLine("Agent", subagent.agent)
                            subagent.durationMs?.let { DetailLine("Duration", formatDuration(it)) }
                            subagent.tokens?.let { tokens ->
                                val count = tokens.total ?: listOfNotNull(tokens.prompt, tokens.completion).sum().takeIf { it > 0 }
                                count?.let { DetailLine("Tokens", it.toString()) }
                            }
                            subagent.tools?.takeIf { it.isNotEmpty() }?.let { DetailLine("Tools", it.joinToString(", ")) }
                            subagent.thinking?.let { DetailLine("Thinking", it) }
                        }
                    }
                }
            }

            SectionTitle("Recent conversations")
            if (detail.isLoadingComms && detail.recentComms.isEmpty()) {
                Text("Loading conversations…", style = MaterialTheme.typography.bodyMedium)
            } else if (detail.recentComms.isEmpty()) {
                Text(
                    "No recent conversations",
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
                            Text("View conversation with $peerName")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onOpenChat(node.agentId) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Open chat")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Close")
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
                BadgeText(if (incoming) "In" else "Out", emphasized = incoming)
                Spacer(Modifier.width(8.dp))
                BadgeText(actionName(comm.action.raw))
                Spacer(Modifier.weight(1f))
                Text(
                    formatTimestamp(comm.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${comm.fromName} → ${comm.toName}", style = MaterialTheme.typography.labelMedium)
            Text(comm.displayText, style = MaterialTheme.typography.bodyMedium)
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

private fun roleName(role: String): String =
    when (role) {
        "orchestrator" -> "Orchestrator"
        "subordinate" -> "Subordinate"
        else -> "Solo agent"
    }

private fun actionName(action: String): String =
    when (action) {
        "ask" -> "Ask"
        "reply" -> "Reply"
        "handover" -> "Handover"
        "send" -> "Send"
        "message" -> "Message"
        else -> "Message"
    }

private fun formatDuration(durationMs: Long): String =
    when {
        durationMs < 1_000 -> "${durationMs}ms"
        durationMs < 60_000 -> "${durationMs / 1_000}s"
        else -> "${durationMs / 60_000}m ${(durationMs % 60_000) / 1_000}s"
    }

private fun formatTimestamp(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
