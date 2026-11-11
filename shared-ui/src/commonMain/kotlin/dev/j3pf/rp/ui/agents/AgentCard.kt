package dev.j3pf.rp.ui.agents

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.protocol.AgentStatus
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AgentsHeroStats(
    agents: List<AgentSummary>,
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
) {
    val working = agents.count { (it.status == "working" || it.status == "compacting") && it.online }
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
                        if (working > 0) RpColors.working else MaterialTheme.colorScheme.outline,
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

    val isWorking = (a.status == AgentStatus.WORKING || a.status == AgentStatus.COMPACTING) && a.online
    val isWaiting = (a.openPromptCount > 0 || a.status == AgentStatus.WAITING_INPUT) && a.online
    val isDone = a.status == AgentStatus.DONE && a.online

    val statusColor =
        when {
            !a.online -> MaterialTheme.colorScheme.outline
            isWaiting -> RpColors.warning
            isWorking -> RpColors.working
            a.status == AgentStatus.ERROR -> RpColors.danger
            isDone -> RpColors.success
            else -> MaterialTheme.colorScheme.outline
        }

    val folder = lastSegment(a.cwd).takeIf { it.isNotEmpty() }
    val rawTitle = agentTitle(a.sessionName, a.cwd, a.agentId)
    val displayTopic =
        if (folder != null && rawTitle.startsWith("$folder · ")) {
            rawTitle.removePrefix("$folder · ").trim().ifEmpty { rawTitle }
        } else {
            rawTitle
        }

    val reducedMotion = LocalReducedMotion.current
    val infiniteTransition = if (isWorking && !reducedMotion) rememberInfiniteTransition(label = "workingCardAnim") else null

    val orbitProgress =
        infiniteTransition?.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "workingOrbitProgress",
        )

    val pulseProgress =
        infiniteTransition?.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "workingPulseProgress",
        )

    val staticBorder =
        when {
            isWorking && orbitProgress != null -> null
            isWorking -> BorderStroke(width = 1.75.dp, color = RpColors.working.copy(alpha = 0.85f))
            isWaiting -> BorderStroke(width = 1.5.dp, color = RpColors.warning.copy(alpha = 0.70f))
            isDone -> BorderStroke(width = 1.dp, color = RpColors.success.copy(alpha = 0.50f))
            else -> BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }

    val animatedBorderModifier =
        if (isWorking && orbitProgress != null) {
            Modifier.drawWithContent {
                drawContent()
                val orbit = orbitProgress.value
                val pulse = pulseProgress?.value ?: 0.5f
                val angle = orbit * 2.0 * PI
                val hx = size.width / 2f + cos(angle).toFloat() * (size.width / 2f)
                val hy = size.height / 2f + sin(angle).toFloat() * (size.height / 2f)
                val radius = maxOf(size.width, size.height) * 0.58f
                val cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())

                // Halo exterior suave que respira y acompaña al cometa
                drawRoundRect(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.25f + 0.20f * pulse),
                                    Color(0xFF7C4DFF).copy(alpha = 0.14f + 0.10f * pulse),
                                    Color.Transparent,
                                ),
                            center = Offset(hx, hy),
                            radius = radius * 1.15f,
                        ),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 5.dp.toPx()),
                )

                // Borde cometa de alto contraste que orbita el perímetro
                drawRoundRect(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFF00E5FF),
                                    Color(0xFF818CF8),
                                    Color(0xFF00B0FF).copy(alpha = 0.20f),
                                ),
                            center = Offset(hx, hy),
                            radius = radius,
                        ),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 2.2.dp.toPx()),
                )
            }
        } else {
            Modifier
        }

    SurfaceCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .then(animatedBorderModifier),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (isWorking) 2.dp else 1.dp,
        border = staticBorder,
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
                val isAgy = a.launcher == "agy"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(24.dp),
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

                // Slot dedicado y siempre visible para tiempo relativo de actividad / estado
                if (isWorking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                        )
                        Text(
                            text = if (a.status == AgentStatus.COMPACTING) "compactando" else "⚡ activo ahora",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else if (isWaiting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(RpColors.warning),
                        )
                        Text(
                            text = "esperando",
                            style = MaterialTheme.typography.labelSmall,
                            color = RpColors.warning,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else if (isDone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(RpColors.success),
                        )
                        Text(
                            text = "✓ completado",
                            style = MaterialTheme.typography.labelSmall,
                            color = RpColors.success,
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

                if (!a.online || !a.herdr?.paneId.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!a.online && canResume) {
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
                                if (!a.online && canResume) {
                                    DropdownMenuItem(
                                        text = { Text("Reanudar agente (/resume)") },
                                        onClick = {
                                            setMenu(false)
                                            onResume()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (a.online) "Cerrar agente" else "Olvidar agente",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
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
