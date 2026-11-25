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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.formatCompactTokens
import dev.j3pf.rp.protocol.parseHerdrTabMeta
import dev.j3pf.rp.ui.components.MetaTag
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.theme.rpSemantic
import dev.j3pf.rp.ui.theme.rpSurfaces
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.lastSegment
import dev.j3pf.rp.ui.util.relativeTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AgentsHeroStats(
    agents: List<AgentSummary>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RpShapes.card,
        color = MaterialTheme.colorScheme.rpSurfaces.elevatedDeck,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.rpSurfaces.hairline),
    ) {
        AgentsHeroStatsRow(
            agents = agents,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun AgentsHeroStatsRow(
    agents: List<AgentSummary>,
    modifier: Modifier = Modifier,
) {
    val semantic = MaterialTheme.colorScheme.rpSemantic
    val working = agents.count { (it.status == "working" || it.status == "compacting") && it.online }
    val waiting = agents.sumOf { it.openPromptCount }
    val onlineAgents = agents.count { it.online }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HeroStatCounter(
            count = working,
            label = "en ejecución",
            dotColor = if (working > 0) semantic.working else MaterialTheme.colorScheme.outline,
        )
        HeroStatCounter(
            count = waiting,
            label = "por responder",
            dotColor = if (waiting > 0) semantic.warning else MaterialTheme.colorScheme.outline,
        )
        HeroStatCounter(
            count = onlineAgents,
            label = "en línea",
            dotColor = if (onlineAgents > 0) semantic.success else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun HeroStatCounter(
    count: Int,
    label: String,
    dotColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal data class AgentCardDisplayMetadata(
    val folder: String?,
    val displayTopic: String,
    val projectContext: String,
    val activeTask: String?,
)

internal fun resolveAgentCardDisplayMetadata(a: AgentSummary): AgentCardDisplayMetadata {
    if (a.redacted) {
        return AgentCardDisplayMetadata(
            folder = null,
            displayTopic = "Sesión oculta",
            projectContext = "",
            activeTask = null,
        )
    }
    val folder = lastSegment(a.cwd).takeIf { it.isNotEmpty() }
    val rawTitle = agentTitle(a.sessionName, a.cwd, a.agentId)
    val displayTopic =
        if (folder != null && rawTitle.startsWith("$folder · ")) {
            rawTitle.removePrefix("$folder · ").trim().ifEmpty { rawTitle }
        } else {
            rawTitle
        }
    val projectContext =
        listOfNotNull(
            folder?.let { "📁 $it" },
            a.gitBranch?.takeIf { it.isNotBlank() }?.let { "⎇ $it" },
        ).joinToString(" · ")
    return AgentCardDisplayMetadata(
        folder = folder,
        displayTopic = displayTopic,
        projectContext = projectContext,
        activeTask = a.activeTask,
    )
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
    val semantic = MaterialTheme.colorScheme.rpSemantic
    val surfaces = MaterialTheme.colorScheme.rpSurfaces

    val isWorking = (a.status == AgentStatus.WORKING || a.status == AgentStatus.COMPACTING) && a.online
    val isWaiting = (a.openPromptCount > 0 || a.status == AgentStatus.WAITING_INPUT) && a.online
    val isDone = a.status == AgentStatus.DONE && a.online

    val statusColor =
        when {
            !a.online -> MaterialTheme.colorScheme.outline
            isWaiting -> semantic.warning
            isWorking -> semantic.working
            a.status == AgentStatus.ERROR -> semantic.danger
            isDone -> semantic.success
            else -> MaterialTheme.colorScheme.outline
        }

    val displayMetadata = resolveAgentCardDisplayMetadata(a)
    val displayTopic = displayMetadata.displayTopic

    val reducedMotion = LocalReducedMotion.current
    val needsAnim = (isWorking || isWaiting) && !reducedMotion
    val infiniteTransition = if (needsAnim) rememberInfiniteTransition(label = "cardStateAnim") else null

    val orbitProgress =
        if (isWorking) {
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
        } else {
            null
        }

    val pulseDuration = if (isWorking) 900 else 850

    val pulseProgress =
        infiniteTransition?.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "statePulseProgress",
        )

    val staticBorder =
        when {
            needsAnim && pulseProgress != null -> null
            isWorking -> BorderStroke(width = 1.6.dp, color = semantic.working.copy(alpha = 0.80f))
            isWaiting -> BorderStroke(width = 1.5.dp, color = semantic.warning.copy(alpha = 0.78f))
            isDone -> BorderStroke(width = 1.dp, color = semantic.success.copy(alpha = 0.55f))
            else -> BorderStroke(width = 1.dp, color = surfaces.hairline)
        }

    val animatedBorderModifier =
        when {
            isWorking && orbitProgress != null -> {
                Modifier.drawWithContent {
                    drawContent()
                    val orbit = orbitProgress.value
                    val pulse = pulseProgress?.value ?: 0.5f
                    val angle = orbit * 2.0 * PI
                    val hx = size.width / 2f + cos(angle).toFloat() * (size.width / 2f)
                    val hy = size.height / 2f + sin(angle).toFloat() * (size.height / 2f)
                    val radius = maxOf(size.width, size.height) * 0.58f
                    val cornerRadius = CornerRadius(RpShapes.cardCornerRadius.toPx(), RpShapes.cardCornerRadius.toPx())

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
            }

            isWaiting && pulseProgress != null -> {
                Modifier.drawWithContent {
                    drawContent()
                    val pulse = pulseProgress.value
                    val cornerRadius = CornerRadius(RpShapes.cardCornerRadius.toPx(), RpShapes.cardCornerRadius.toPx())
                    val borderColor = lerp(Color(0xFFFF8F00).copy(alpha = 0.50f), Color(0xFFFFD54F), pulse)

                    drawRoundRect(
                        color = Color(0xFFFFB300).copy(alpha = 0.08f + 0.24f * pulse),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 4.5.dp.toPx()),
                    )

                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = cornerRadius,
                        style = Stroke(width = (1.6f + 0.5f * pulse).dp.toPx()),
                    )
                }
            }

            else -> {
                Modifier
            }
        }

    SurfaceCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(animatedBorderModifier),
        shape = RpShapes.card,
        tonalElevation = if (isWorking || isWaiting) 2.dp else 1.dp,
        border = staticBorder,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 7.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Fila 1: Badge de agente + Carpeta / Rama Git a la izquierda (sin repetir modelo), estado y acciones a la derecha
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isAgy = a.launcher == "agy"
                Surface(
                    shape = RpShapes.extraSmall,
                    color = statusColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isAgy) "✦" else "π",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor,
                        )
                    }
                }

                val projectContext = displayMetadata.projectContext

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
                                    .background(semantic.warning),
                        )
                        Text(
                            text = "⚠ esperando",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.warning,
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
                                    .background(semantic.success),
                        )
                        Text(
                            text = "✓ completado",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.success,
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
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                        badge = {
                            Badge(containerColor = semantic.warning) {
                                Text("${a.openPromptCount}", color = semantic.onWarning)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Pendientes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
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
                                    modifier = Modifier.size(17.dp),
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

            // Fila 2: Título / tema de la conversación
            Text(
                text = displayTopic,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Fila 3: Señales primarias y metadata técnica compacta en la misma fila
            val modelTag = a.model?.label?.takeIf { it.isNotBlank() && it != "default" && it != "antigravity" && it != a.launcher }
            val launcherTag =
                a.launcher?.takeIf {
                    it.isNotBlank() && !it.equals("agy", ignoreCase = true) && !it.equals("pi", ignoreCase = true)
                }
            val modeTag = a.gentleMode?.takeIf { it != "unknown" }?.uppercase()

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusChip(a)
                val tabMeta = parseHerdrTabMeta(a.herdr?.tabId)
                val ctxTokens = a.contextTokens ?: tabMeta.contextTokens
                val ctxPct = a.contextPercent ?: tabMeta.contextPercent
                if (ctxPct != null || ctxTokens != null) {
                    val pressureColor =
                        when {
                            (ctxPct ?: 0) >= 85 -> semantic.danger
                            (ctxPct ?: 0) >= 60 -> semantic.warning
                            else -> semantic.success
                        }
                    val ctxLabel =
                        when {
                            ctxPct != null && ctxTokens != null -> "🧠 $ctxPct% · ${formatCompactTokens(ctxTokens)}"
                            ctxPct != null -> "🧠 $ctxPct%"
                            else -> "🧠 ${formatCompactTokens(ctxTokens!!)}"
                        }
                    TagChip(
                        text = ctxLabel,
                        color = pressureColor,
                    )
                }
                if (a.todoTotal > 0) {
                    val percent = a.todoCompleted * 100 / a.todoTotal
                    TagChip(
                        text = "Tareas: ${a.todoCompleted}/${a.todoTotal} ($percent%)",
                        color = if (a.todoCompleted == a.todoTotal) semantic.success else MaterialTheme.colorScheme.primary,
                    )
                }
                modelTag?.let { MetaTag(text = it) }
                launcherTag?.let { MetaTag(text = it) }
                modeTag?.let {
                    MetaTag(
                        text = it,
                        contentColor =
                            if (a.gentleMode == "auto" || a.gentleMode == "yolo") {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }

            // Tarea activa en curso si existe
            val active = displayMetadata.activeTask
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
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Vista previa del último mensaje con barra de acento lateral
            val preview = if (a.redacted) "contenido oculto" else a.lastMessagePreview
            if (!preview.isNullOrBlank()) {
                val stripeColor = statusColor.copy(alpha = if (a.redacted) 0.30f else 0.70f)
                Surface(
                    shape = RpShapes.small,
                    color = surfaces.insetBlock,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRect(
                                        color = stripeColor,
                                        topLeft = Offset.Zero,
                                        size = Size(width = 3.dp.toPx(), height = size.height),
                                    )
                                },
                    ) {
                        Text(
                            text = preview,
                            modifier = Modifier.padding(PaddingValues(start = 13.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)),
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
}
