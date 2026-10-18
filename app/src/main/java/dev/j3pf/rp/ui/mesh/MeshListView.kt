package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult

/** An accessible, touch-friendly alternative to the topology diagram (Spec S2, S4). */
@Composable
fun MeshListView(
    snapshot: MeshSnapshot?,
    layout: MeshLayoutResult?,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleNodes =
        if (layout != null) {
            layout.nodes.values.map { it.node }
        } else {
            snapshot?.nodes.orEmpty()
        }
    val groups = snapshot?.groups.orEmpty()

    fun isNodeSolo(node: MeshNode): Boolean =
        node.role == "solo" || (node.groupId == null && groups.none { node.agentId in it.memberAgentIds })

    fun resolveGroupId(node: MeshNode): String {
        if (isNodeSolo(node)) return SOLO_KEY
        val groupId = node.groupId
        if (!groupId.isNullOrBlank()) return groupId
        val matchingGroup = groups.firstOrNull { node.agentId in it.memberAgentIds }
        return matchingGroup?.groupId ?: SOLO_KEY
    }

    val groupedNodes = visibleNodes.groupBy { resolveGroupId(it) }
    val groupTitles =
        groups.associate { group ->
            val orch = group.orchestratorAgentId?.let { orchId -> visibleNodes.firstOrNull { it.agentId == orchId } }
            group.groupId to formatMeshGroupChipLabel(group, orch, visibleNodes)
        }
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
                        text = if (groupId == SOLO_KEY) "Sin grupo" else groupTitles[groupId] ?: "Grupo de agentes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    nodes.forEach { node ->
                        MeshAgentCard(
                            node = node,
                            allNodes = visibleNodes,
                            onClick = { onNodeClick(node.agentId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private const val SOLO_KEY = "__mesh_solo__"
