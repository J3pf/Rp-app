package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.theme.RpSpacing

/**
 * Compact vertical bullet list item for leaf agents hanging under manager nodes (Specs S4, S6).
 * Shows status dot, display name, accessible content description ("X, reporta a Y"),
 * and opens the node detail sheet on click.
 */
@Composable
fun MeshLeafItem(
    node: MeshNode,
    modifier: Modifier = Modifier,
    allNodes: List<MeshNode> = emptyList(),
    parentNode: MeshNode? = null,
    onClick: () -> Unit,
) {
    val displayName = formatMeshDisplayName(node, allNodes)
    val accessibleDesc = formatMeshNodeContentDescription(node, allNodes, parentNode)
    val isOffline = !node.online || node.status == "offline"

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clip(RpShapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleDesc
                },
        shape = RpShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isOffline) 0.35f else 0.65f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = RpSpacing.sm, vertical = 6.dp)
                    .alpha(if (isOffline) 0.65f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(status = node.status, online = node.online)
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
