package dev.j3pf.rp.ui.agents

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.lastSegment
import dev.j3pf.rp.ui.util.relativeTime

@Composable
fun AgentsHeroStats(
    agents: List<AgentSummary>,
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
) {
    val working = agents.count { it.status == "working" && it.online }
    val waiting = agents.sumOf { it.openPromptCount }
    val onlineAgents = agents.count { it.online }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (working > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    text = "$working en ejecución",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (waiting > 0) RpColors.warning else MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    text = "$waiting por responder",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (onlineAgents > 0) RpColors.success else MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    text = "$onlineAgents en línea",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun AgentCard(
    a: AgentSummary,
    onClick: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
    canResume: Boolean = false,
    onResume: () -> Unit = {},
) {
    val (menu, setMenu) = remember { mutableStateOf(false) }

    val statusColor =
        when {
            !a.online -> MaterialTheme.colorScheme.outline
            a.openPromptCount > 0 || a.status == "waiting_input" -> RpColors.warning
            a.status == "working" -> MaterialTheme.colorScheme.primary
            a.status == "compacting" -> MaterialTheme.colorScheme.tertiary
            a.status == "error" -> RpColors.danger
            else -> RpColors.success
        }

    val folder = lastSegment(a.cwd).takeIf { it.isNotEmpty() }
    val rawTitle = agentTitle(a.sessionName, a.cwd, a.agentId)
    val displayTopic =
        if (folder != null && rawTitle.startsWith("$folder · ")) {
            rawTitle.removePrefix("$folder · ").trim().ifEmpty { rawTitle }
        } else {
            rawTitle
        }

    val isWorking = a.status == "working" && a.online
    val reducedMotion = LocalReducedMotion.current
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha =
        if (isWorking && !reducedMotion) {
            pulseTransition
                .animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.90f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "borderPulse",
                ).value
        } else {
            0.35f
        }

    SurfaceCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(if (isWorking) 1.5.dp else 1.dp, statusColor.copy(alpha = borderAlpha)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Fila 1: Badge de agente + Carpeta / Rama Git a la izquierda, Tiempo relativo + acciones a la derecha
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val avatarScale: Float =
                    if (isWorking && !reducedMotion) {
                        pulseTransition
                            .animateFloat(
                                initialValue = 1.0f,
                                targetValue = 1.18f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                label = "avatarScale",
                            ).value
                    } else {
                        1.0f
                    }

                val isAgy = a.launcher == "agy"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    modifier =
                        Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                this.scaleX = avatarScale
                                this.scaleY = avatarScale
                            },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isAgy) "✦" else "π",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor,
                        )
                    }
                }

                // Píldora de carpeta + rama git
                val projectContext =
                    listOfNotNull(
                        folder?.let { "📁 $it" },
                        a.gitBranch?.takeIf { it.isNotBlank() }?.let { "⎇ $it" },
                        a.model?.label?.takeIf { it.isNotEmpty() },
                    ).joinToString(" · ")

                Text(
                    text = projectContext.ifEmpty { "Sesión" },
                    modifier = Modifier.weight(1f),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Slot dedicado y siempre visible para tiempo relativo de actividad
                if (isWorking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val dotAlpha =
                            if (!reducedMotion) {
                                pulseTransition
                                    .animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1.0f,
                                        animationSpec =
                                            infiniteRepeatable(
                                                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse,
                                            ),
                                        label = "dotAlpha",
                                    ).value
                            } else {
                                1.0f
                            }
                        Box(
                            modifier =
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = dotAlpha)),
                        )
                        Text(
                            text = "activo ahora",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else {
                    val timeText = relativeTime(a.lastSeenAt)
                    if (timeText.isNotBlank()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }

                if (a.openPromptCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = RpColors.warning) {
                                Text("${a.openPromptCount}", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Pendientes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (!a.online) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canResume) {
                            IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Reanudar agente",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { setMenu(true) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Opciones",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { setMenu(false) }) {
                                if (canResume) {
                                    DropdownMenuItem(
                                        text = { Text("Reanudar agente (/resume)") },
                                        onClick = {
                                            setMenu(false)
                                            onResume()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Olvidar agente", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        setMenu(false)
                                        onForget()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Fila 2: Título / tema de la conversación hasta en 2 líneas completas
            Text(
                text = displayTopic,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Chips de estado condensados y envueltos sin scroll horizontal
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusChip(a)
                if (a.todoTotal > 0) {
                    val percent = a.todoCompleted * 100 / a.todoTotal
                    TagChip(
                        text = "Tareas: ${a.todoCompleted}/${a.todoTotal} ($percent%)",
                        color = if (a.todoCompleted == a.todoTotal) RpColors.success else MaterialTheme.colorScheme.primary,
                    )
                }
                a.launcher?.let { launcher ->
                    TagChip(
                        text = if (launcher == "agy") "✦ agy" else launcher,
                        color = if (launcher == "agy") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    )
                }
                a.herdr?.let { h ->
                    val loc =
                        listOfNotNull(
                            h.workspaceId?.let { w -> "ws $w" },
                            h.paneId?.let { p -> "pane $p" },
                        ).joinToString("·")
                    if (loc.isNotEmpty()) TagChip("herdr $loc")
                }
                a.gentleMode?.takeIf { it != "unknown" }?.let {
                    TagChip(
                        text = it.uppercase(),
                        color =
                            if (it == "auto" || it == "yolo") {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                    )
                }
            }

            // Tarea activa en curso si existe
            val active = a.activeTask
            if (!active.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        active,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Vista previa del último mensaje
            val preview = if (a.redacted) "contenido oculto" else a.lastMessagePreview
            if (!preview.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = preview,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (a.redacted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
