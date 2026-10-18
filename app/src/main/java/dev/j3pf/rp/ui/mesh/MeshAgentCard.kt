package dev.j3pf.rp.ui.mesh

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.theme.RpSpacing

/**
 * Modern agent card replacing bare circles in the mesh graph (Spec S2).
 * Displays avatar with machine tint, human display name, secondary line,
 * role badge, status indicator, one-line activity, TODO progress, and attached subagents.
 */
@Composable
fun MeshAgentCard(
    node: MeshNode,
    modifier: Modifier = Modifier,
    allNodes: List<MeshNode> = emptyList(),
    onClick: () -> Unit,
    reducedMotion: Boolean = LocalReducedMotion.current,
) {
    val displayName = formatMeshDisplayName(node, allNodes)
    val secondaryLine = formatMeshSecondaryLine(node)
    val accessibleDesc = formatMeshNodeContentDescription(node, allNodes)
    val isWorking = node.status == "working"
    val isOffline = !node.online || node.status == "offline"

    val cardColor =
        if (isOffline) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RpShapes.medium)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleDesc
                }.workingCardBorder(
                    isWorking = isWorking,
                    reducedMotion = reducedMotion,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.secondary,
                ),
        shape = RpShapes.medium,
        color = cardColor,
        tonalElevation = if (isWorking) 4.dp else 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = RpSpacing.md, vertical = RpSpacing.sm)
                    .alpha(if (isOffline) 0.65f else 1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Header prioritizes the agent name; only the orchestrator spends width on its role badge.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RpSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AgentAvatar(
                    displayName = displayName,
                    machineColor = node.machineColor,
                    online = node.online,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (node.role == "orchestrator") {
                    MeshRoleBadge(role = node.role)
                }
            }

            // Status and current activity have priority over secondary machine/branch details.
            val activityText = node.activity?.takeIf { it.isNotBlank() } ?: node.todos?.current?.takeIf { it.isNotBlank() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusDot(status = node.status, online = node.online)
                Text(
                    text = formatMeshStatus(node.status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = meshStatusColor(node.status, node.online),
                )
                if (isWorking && !reducedMotion) {
                    WorkingActivityDots()
                }
                if (!activityText.isNullOrBlank()) {
                    Text(
                        text = "· $activityText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            Text(
                text = secondaryLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // TODO Progress Bar
            node.todos?.let { todos ->
                if (todos.total > 0) {
                    val progress = (todos.done.toFloat() / todos.total.toFloat()).coerceIn(0f, 1f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(4.dp).clip(RpShapes.pill),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            text = "${todos.done}/${todos.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Attached Subagent Chips
            if (node.subagents.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                ) {
                    node.subagents.take(3).forEach { subagent ->
                        MeshSubagentChip(
                            subagent = subagent,
                            reducedMotion = reducedMotion,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (node.subagents.size > 3) {
                        Surface(
                            shape = RpShapes.pill,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.height(20.dp),
                        ) {
                            Text(
                                text = "+${node.subagents.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Avatar for an agent node showing the first letter of its display name,
 * tinted with the machine color.
 */
@Composable
fun AgentAvatar(
    displayName: String,
    machineColor: String,
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    val initial = displayName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "A"
    val accentColor = parseRelayColor(machineColor)
    Surface(
        modifier = modifier.size(28.dp),
        shape = CircleShape,
        color = accentColor.copy(alpha = if (online) 0.22f else 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (online) 0.80f else 0.40f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (online) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Badge for agent role (Orquestador, Subordinado, Subagente, Individual).
 */
@Composable
fun MeshRoleBadge(
    role: String,
    modifier: Modifier = Modifier,
) {
    val roleText = formatMeshRole(role)
    val (bgColor, textColor) =
        when (role.lowercase()) {
            "orchestrator" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            "subordinate" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RpShapes.pill,
        color = bgColor,
        modifier = modifier.heightIn(min = 20.dp),
    ) {
        Text(
            text = roleText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Attached subagent chip showing subagent label/name and active/running indicator.
 */
@Composable
fun MeshSubagentChip(
    subagent: MeshSubagent,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = LocalReducedMotion.current,
) {
    val isWorking = subagent.status == "working" || subagent.status == "running"
    val label = subagent.label?.takeIf(String::isNotBlank) ?: subagent.agent
    val chipBg =
        if (isWorking) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        }

    Surface(
        shape = RpShapes.small,
        color = chipBg,
        modifier = modifier.heightIn(min = 22.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            if (isWorking && !reducedMotion) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = if (isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ) {}
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWorking) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusDot(
    status: String,
    online: Boolean,
) {
    Surface(
        modifier = Modifier.size(8.dp),
        shape = CircleShape,
        color = meshStatusColor(status, online),
    ) {}
}

@Composable
fun meshStatusColor(
    status: String,
    online: Boolean,
): Color {
    val base =
        when (status.lowercase()) {
            "working" -> MaterialTheme.colorScheme.primary
            "waiting_input" -> MaterialTheme.colorScheme.tertiary
            "error" -> MaterialTheme.colorScheme.error
            "compacting" -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outline
        }
    return if (online) base else MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun WorkingActivityDots() {
    val transition = rememberInfiniteTransition(label = "workingDots")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dotsPulse",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    ) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .size(3.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun Modifier.workingCardBorder(
    isWorking: Boolean,
    reducedMotion: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
): Modifier {
    if (!isWorking) {
        return this.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RpShapes.medium)
    }
    if (reducedMotion) {
        return this.border(1.5.dp, primaryColor, RpShapes.medium)
    }
    val transition = rememberInfiniteTransition(label = "workingCardBorder")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "workingBorderProgress",
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    return this.drawWithCache {
        val strokePx = 1.5.dp.toPx()
        val cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
        val offset = progress * (size.width + size.height)
        val brush =
            Brush.linearGradient(
                colors = listOf(primaryColor, secondaryColor, primaryColor),
                start = Offset(offset, offset),
                end = Offset(offset + size.width, offset + size.height),
            )
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                brush = brush,
                style = Stroke(width = strokePx),
                cornerRadius = cornerRadius,
            )
        }
    }
}
