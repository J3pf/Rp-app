package dev.j3pf.rp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpMotion
import dev.j3pf.rp.ui.theme.rpSkeleton
import dev.j3pf.rp.util.contextEmoji
import dev.j3pf.rp.util.contextLabel
import dev.j3pf.rp.util.parseColor
import dev.j3pf.rp.util.relativeTime

@Composable
fun ColorDot(
    color: Color,
    size: Int = 10,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

/**
 * Tarjeta base con radio de 20dp y sombra sutil según design.md.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 1.dp,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null && !reducedMotion) RpMotion.PRESSED_SCALE else 1.0f,
        animationSpec = RpMotion.springSnappy,
        label = "cardPressScale",
    )

    val cardModifier =
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColor),
            elevation = CardDefaults.cardElevation(defaultElevation = tonalElevation),
            border = border,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColor),
            elevation = CardDefaults.cardElevation(defaultElevation = tonalElevation),
            border = border,
            content = content,
        )
    }
}

/**
 * Cabecera humana para pantallas principales (design.md §1 y §6).
 */
@Composable
fun AppHeader(
    greeting: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Hero Card interactivo para conmutar entre máquinas mediante un Dropdown estilizado (design.md §1 y §6).
 */
@Composable
fun MachineHeroDropdown(
    machines: List<MachineSummary>,
    agents: List<AgentSummary>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Determinar la etiqueta y estado del elemento seleccionado
    val (selectedTitle, selectedSubtitle, selectedColor) =
        when {
            selected == AppSettings.ALL -> {
                val total = agents.size
                val online = machines.count { it.online }
                Triple(
                    "Todas las máquinas",
                    "$total ${if (total == 1) "agente" else "agentes"} · $online ${if (online == 1) "PC activa" else "PCs activas"}",
                    MaterialTheme.colorScheme.primary,
                )
            }

            machines.any { it.machineId == selected } -> {
                val m = machines.first { it.machineId == selected }
                val count = agents.count { it.machineId == m.machineId }
                Triple(
                    m.name,
                    "${contextLabel(
                        m.context,
                    )} · $count ${if (count == 1) "agente" else "agentes"}${if (m.online) " · en línea" else " · desconectada"}",
                    parseColor(m.color),
                )
            }

            else -> {
                val count = agents.count { it.machineContext == selected }
                Triple(
                    contextLabel(selected),
                    "$count ${if (count == 1) "agente" else "agentes"} en este contexto",
                    parseColor(machines.firstOrNull { it.context == selected }?.color),
                )
            }
        }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SurfaceCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = selectedColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ColorDot(selectedColor, size = 14)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = selectedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = selectedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Cambiar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Desplegar máquinas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            // Opción "Todas las máquinas"
            val totalAgents = agents.size
            val totalPrompts = agents.sumOf { it.openPromptCount }
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ColorDot(MaterialTheme.colorScheme.primary, size = 10)
                            Column {
                                Text(
                                    "Todas las máquinas",
                                    fontWeight = if (selected == AppSettings.ALL) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    "$totalAgents agentes${if (totalPrompts > 0) " · $totalPrompts pendientes" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (selected == AppSettings.ALL) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                onClick = {
                    onSelect(AppSettings.ALL)
                    expanded = false
                },
            )

            // Contextos o Máquinas individuales
            val contexts = machines.map { it.context }.distinct()
            if (contexts.size > 1) {
                Text(
                    text = "POR CONTEXTO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                contexts.forEach { ctx ->
                    val ms = machines.filter { it.context == ctx }
                    val count = agents.count { it.machineContext == ctx }
                    val prompts = agents.filter { it.machineContext == ctx }.sumOf { it.openPromptCount }
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ColorDot(parseColor(ms.firstOrNull()?.color), size = 10)
                                    Column {
                                        Text(
                                            contextLabel(ctx),
                                            fontWeight = if (selected == ctx) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        Text(
                                            "$count agentes${if (prompts > 0) " · $prompts pendientes" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (selected == ctx) {
                                    Icon(
                                        Icons.Filled.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(ctx)
                            expanded = false
                        },
                    )
                }
            }

            if (machines.isNotEmpty()) {
                Text(
                    text = "MÁQUINAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                machines.forEach { m ->
                    val count = agents.count { it.machineId == m.machineId }
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ColorDot(parseColor(m.color), size = 10)
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                m.name,
                                                fontWeight = if (selected == m.machineId) FontWeight.Bold else FontWeight.Normal,
                                            )
                                            Text(
                                                if (m.online) "●" else "○",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (m.online) RpColors.success else MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                        Text(
                                            "${contextLabel(m.context)} · $count agentes${if (m.hostOnline) " · rp-host ✓" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (selected == m.machineId) {
                                    Icon(
                                        Icons.Filled.Check,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(m.machineId)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Desvanecimiento sutil derivado del tema en bordes horizontales para indicar scroll.
 */
fun Modifier.horizontalEdgeFade(
    scrollState: ScrollState? = null,
    edgeColor: Color = Color.Unspecified,
    length: Dp = 24.dp,
): Modifier =
    composed {
        val resolvedColor = if (edgeColor.isSpecified) edgeColor else MaterialTheme.colorScheme.background
        val density = LocalDensity.current
        val lengthPx = with(density) { length.toPx() }

        drawWithContent {
            drawContent()
            val canBackward = scrollState?.canScrollBackward ?: true
            val canForward = scrollState?.canScrollForward ?: true

            if (canBackward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor, resolvedColor.copy(alpha = 0f)),
                            startX = 0f,
                            endX = lengthPx,
                        ),
                    size = size.copy(width = lengthPx),
                )
            }
            if (canForward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor.copy(alpha = 0f), resolvedColor),
                            startX = size.width - lengthPx,
                            endX = size.width,
                        ),
                    topLeft = Offset(size.width - lengthPx, 0f),
                    size = size.copy(width = lengthPx),
                )
            }
        }
    }

fun Modifier.horizontalEdgeFade(
    lazyListState: LazyListState,
    edgeColor: Color = Color.Unspecified,
    length: Dp = 24.dp,
): Modifier =
    composed {
        val resolvedColor = if (edgeColor.isSpecified) edgeColor else MaterialTheme.colorScheme.background
        val density = LocalDensity.current
        val lengthPx = with(density) { length.toPx() }

        drawWithContent {
            drawContent()
            val canBackward = lazyListState.canScrollBackward
            val canForward = lazyListState.canScrollForward

            if (canBackward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor, resolvedColor.copy(alpha = 0f)),
                            startX = 0f,
                            endX = lengthPx,
                        ),
                    size = size.copy(width = lengthPx),
                )
            }
            if (canForward && size.width > lengthPx * 2f) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(resolvedColor.copy(alpha = 0f), resolvedColor),
                            startX = size.width - lengthPx,
                            endX = size.width,
                        ),
                    topLeft = Offset(size.width - lengthPx, 0f),
                    size = size.copy(width = lengthPx),
                )
            }
        }
    }

/**
 * Selector de PC persistente (chips horizontales para filtrado rápido).
 */
@Composable
fun MachineFilterBar(
    machines: List<MachineSummary>,
    agents: List<AgentSummary>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexts = remember(machines) { machines.map { it.context }.distinct() }
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        modifier = modifier.fillMaxWidth().horizontalEdgeFade(rowState, edgeColor = MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val total = agents.size
            val prompts = agents.sumOf { it.openPromptCount }
            FilterChip(
                selected = selected == AppSettings.ALL,
                onClick = { onSelect(AppSettings.ALL) },
                shape = RoundedCornerShape(10.dp),
                label = { Text("Todas ($total${if (prompts > 0) " · $prompts" else ""})") },
            )
        }
        items(contexts, key = { it }) { ctx ->
            val ms = machines.filter { it.context == ctx }
            val count = agents.count { it.machineContext == ctx }
            val prompts = agents.filter { it.machineContext == ctx }.sumOf { it.openPromptCount }
            val rawColor = ms.firstOrNull()?.color
            val dotColor = remember(rawColor) { parseColor(rawColor) }
            FilterChip(
                selected = selected == ctx,
                onClick = { onSelect(ctx) },
                shape = RoundedCornerShape(10.dp),
                leadingIcon = { ColorDot(dotColor) },
                label = { Text("${contextLabel(ctx)} ($count${if (prompts > 0) " · $prompts" else ""})") },
            )
        }
    }
}

/** Chip de estado del agente: Trabajando / Esperando respuesta / Inactivo / Desconectado hace 3 h. */
@Composable
fun StatusChip(
    agent: AgentSummary,
    modifier: Modifier = Modifier,
) {
    val (text, targetColor) =
        when {
            !agent.online -> "Desconectado ${relativeTime(agent.lastSeenAt)}".trim() to MaterialTheme.colorScheme.outline
            agent.openPromptCount > 0 || agent.status == AgentStatus.WAITING_INPUT -> "Esperando respuesta" to RpColors.warning
            agent.status == AgentStatus.WORKING -> "Trabajando" to MaterialTheme.colorScheme.primary
            agent.status == AgentStatus.COMPACTING -> "Compactando" to MaterialTheme.colorScheme.primary
            agent.status == AgentStatus.ERROR -> "Error" to RpColors.danger
            else -> "Inactivo" to RpColors.success
        }

    val reducedMotion = LocalReducedMotion.current
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = RpMotion.adaptiveSpec(reducedMotion, tween(durationMillis = 300)),
        label = "statusColor",
    )

    val isWorking = (agent.status == AgentStatus.WORKING || agent.status == AgentStatus.COMPACTING) && agent.online
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulseTransition")
    val pulseScale by if (isWorking && !reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 2.2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "pulseScale",
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }
    val pulseAlpha by if (isWorking && !reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "pulseAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0.0f) }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = animatedColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, animatedColor.copy(alpha = 0.25f)),
        contentColor = animatedColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                if (isWorking && !reducedMotion && pulseAlpha > 0f) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }.clip(CircleShape)
                            .background(animatedColor),
                    )
                }
                ColorDot(animatedColor, size = 6)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TagChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.20f)),
        contentColor = color,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Pincel de brillo animado para Skeletons (design.md §1: Carga = skeleton).
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val reducedMotion = LocalReducedMotion.current
    val tokens = MaterialTheme.colorScheme.rpSkeleton
    val base = tokens.base
    val highlight = tokens.highlight
    if (reducedMotion) {
        return Brush.linearGradient(
            colors = listOf(base, highlight),
            start = Offset(0f, 0f),
            end = Offset(400f, 400f),
        )
    }
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmerTranslate",
    )
    val shimmerColors =
        listOf(
            base,
            highlight,
            base,
        )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim),
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(brush),
    )
}

/**
 * Lista de skeletons que simulan la carga de tarjetas de agentes sin usar spinners de pantalla completa.
 */
@Composable
fun AgentCardSkeleton(modifier: Modifier = Modifier) {
    SurfaceCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBox(modifier = Modifier.size(12.dp), shape = CircleShape)
                SkeletonBox(modifier = Modifier.weight(1f).height(20.dp), shape = RoundedCornerShape(6.dp))
                SkeletonBox(modifier = Modifier.width(60.dp).height(20.dp), shape = RoundedCornerShape(12.dp))
            }
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.65f).height(14.dp), shape = RoundedCornerShape(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(modifier = Modifier.width(70.dp).height(22.dp), shape = RoundedCornerShape(10.dp))
                SkeletonBox(modifier = Modifier.width(90.dp).height(22.dp), shape = RoundedCornerShape(10.dp))
            }
        }
    }
}

@Composable
fun AgentSkeletonList(
    count: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) {
            AgentCardSkeleton()
        }
    }
}

/**
 * Empty state con ilustración simple + una frase + un CTA opcional (design.md §1).
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurfaceCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            icon()
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = actionLabel,
                        onClick = onAction,
                    )
                }
            }
        }
    }
}

/**
 * Botón primario tipo pill con altura de 48-52dp (design.md §5 y §8).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.height(50.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Banner de estado de la conexión con el relay (sólo se muestra si no está conectado). */
@Composable
fun ConnectionBanner(
    state: ConnState,
    modifier: Modifier = Modifier,
) {
    val text =
        when (state) {
            ConnState.Connected -> {
                return
            }

            ConnState.Connecting -> {
                "Conectando con el relay…"
            }

            ConnState.Disconnected -> {
                "Sin conexión con el relay · reintentando"
            }

            is ConnState.Fatal -> {
                when (state.code) {
                    4001 -> "El token fue revocado. Vuelve a emparejar el dispositivo."
                    4002 -> "Versión de protocolo incompatible. Actualiza la app o el relay."
                    else -> "Conexión cerrada (${state.code})"
                }
            }
        }
    val fatal = state is ConnState.Fatal
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        color = if (fatal) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun RowSpacer(w: Int = 8) = Spacer(Modifier.width(w.dp))

@Composable
fun SmallChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = {},
        label = { Text(text, maxLines = 1) },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
fun LabeledRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
