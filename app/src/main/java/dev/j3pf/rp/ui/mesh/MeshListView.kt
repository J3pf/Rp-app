package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult

/** An accessible, touch-friendly alternative to the topology diagram. */
@Composable
fun MeshListView(
    snapshot: MeshSnapshot?,
    layout: MeshLayoutResult?,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleNodes =
        layout
            ?.nodes
            ?.values
            ?.map { it.node }
            .orEmpty()
    val groups = snapshot?.groups.orEmpty()
    val groupedNodes = visibleNodes.groupBy { node -> if (isSolo(node)) SOLO_KEY else node.groupId ?: SOLO_KEY }
    val groupTitles = groups.associate { it.groupId to (it.label.ifBlank { it.orchestratorAgentId ?: it.groupId }) }
    val orderedGroups =
        buildList {
            groups.forEach { group ->
                groupedNodes[group.groupId]?.takeIf { it.isNotEmpty() }?.let { add(group.groupId to it) }
            }
            groupedNodes[SOLO_KEY]?.takeIf { it.isNotEmpty() }?.let { add(SOLO_KEY to it) }
            groupedNodes.forEach { (id, nodes) ->
                if (id != SOLO_KEY && id !in groups.map { it.groupId }) add(id to nodes)
            }
        }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        orderedGroups.forEach { (groupId, nodes) ->
            item(key = "group-$groupId") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (groupId == SOLO_KEY) "Solo" else groupTitles[groupId] ?: "Agent group",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    nodes.forEach { node ->
                        MeshNodeListCard(node = node, onClick = { onNodeClick(node.agentId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MeshNodeListCard(
    node: MeshNode,
    onClick: () -> Unit,
) {
    val status = node.status.replace('_', ' ')
    val taskLabel = node.todos?.let { ", ${it.done} of ${it.total} tasks" }.orEmpty()
    val accessibleLabel = "${roleName(node.role)} ${node.name}, $status$taskLabel"
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleLabel
                },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = statusColor(node.status, node.online),
                ) {}
                Text(node.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(roleName(node.role), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${node.machineName} · ${node.status.replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium)
            node.activity
                ?.takeIf {
                    it.isNotBlank()
                }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            node.todos?.let { todos ->
                Text(
                    "${todos.done} of ${todos.total} tasks · ${todos.inProgress} in progress · ${todos.blocked} blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (node.subagents.isNotEmpty()) {
                Text("Subagents", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                node.subagents.forEach { subagent ->
                    Text(
                        "${subagent.agent}${subagent.label?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()} · ${subagent.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun isSolo(node: MeshNode): Boolean = node.role == "solo" || node.groupId == null

private fun roleName(role: String): String =
    when (role) {
        "orchestrator" -> "Orchestrator"
        "subordinate" -> "Subordinate"
        else -> "Agent"
    }

@Composable
private fun statusColor(
    status: String,
    online: Boolean,
) = (
    when (status.lowercase()) {
        "working" -> MaterialTheme.colorScheme.primary
        "waiting_input" -> MaterialTheme.colorScheme.tertiary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
).let { if (online) it else MaterialTheme.colorScheme.outlineVariant }

private const val SOLO_KEY = "__mesh_solo__"
