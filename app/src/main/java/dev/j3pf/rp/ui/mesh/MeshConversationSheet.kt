package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshComm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshConversationSheet(
    detail: MeshConversationDetail,
    onDismiss: () -> Unit,
    onLoadOlder: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        MeshConversationContent(
            detail = detail,
            onDismiss = onDismiss,
            onLoadOlder = onLoadOlder,
        )
    }
}

@Composable
fun MeshConversationContent(
    detail: MeshConversationDetail,
    onDismiss: () -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Conversación entre ${detail.agentAName} y ${detail.agentBName}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${detail.items.size} mensajes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (detail.nextBefore != null) {
                item(key = "load-older") {
                    Button(
                        onClick = onLoadOlder,
                        enabled = !detail.isLoadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (detail.isLoadingMore) "Cargando mensajes anteriores…" else "Cargar mensajes anteriores")
                    }
                }
            }
            if (detail.isLoading && detail.items.isEmpty()) {
                item(key = "loading") {
                    Text("Cargando conversación…", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!detail.isLoading && detail.items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No hay mensajes en esta conversación.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(detail.items, key = { it.commKey }) { comm ->
                val isFromA = isFromAgentA(comm, detail.agentA, detail.agentB)
                ConversationBubble(
                    comm = comm,
                    senderName = if (isFromA) detail.agentAName else detail.agentBName,
                    isFromA = isFromA,
                )
            }
        }
    }
}

@Composable
private fun ConversationBubble(
    comm: MeshComm,
    senderName: String,
    isFromA: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromA) Arrangement.Start else Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = if (isFromA) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (isFromA) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        modifier = Modifier.weight(1f),
                    )
                    ActionBadge(comm.action.raw, isFromA)
                }
                Text(
                    formatMeshCommText(comm.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromA) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    formatMeshDateTime(comm.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFromA) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun ActionBadge(
    action: String,
    isFromA: Boolean,
) {
    val label = formatMeshCommAction(action)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color =
            if (isFromA) {
                MaterialTheme.colorScheme.surface.copy(
                    alpha = 0.65f,
                )
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
            },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun isFromAgentA(
    comm: MeshComm,
    agentA: String,
    agentB: String,
): Boolean =
    when {
        comm.fromAgentId != null -> comm.fromAgentId == agentA
        comm.toAgentId != null -> comm.toAgentId == agentB
        else -> false
    }
