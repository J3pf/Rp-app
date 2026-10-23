package dev.j3pf.rp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.rpSkeleton
import dev.j3pf.rp.ui.util.contextLabel
import dev.j3pf.rp.ui.util.parseColor

const val ALL_MACHINES = AppSettings.ALL

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
                .padding(horizontal = 20.dp, vertical = 6.dp),
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

data class MachineHeroState(
    val overlineLabel: String,
    val selectedTitle: String,
    val selectedSubtitle: String,
    val selectedColor: Color,
)

fun resolveMachineHeroState(
    machines: List<MachineSummary>,
    agents: List<AgentSummary>,
    selected: String,
    primaryColor: Color,
): MachineHeroState {
    val singleMachine = machines.singleOrNull()
    val activeMachine =
        when {
            selected == ALL_MACHINES && singleMachine != null -> singleMachine
            else -> machines.firstOrNull { it.machineId == selected }
        }

    return when {
        activeMachine != null -> {
            val m = activeMachine
            val count = agents.count { it.machineId == m.machineId }
            val onlineCount = agents.count { it.machineId == m.machineId && it.online }
            val pcStatus = if (m.online) " · PC en línea" else " · PC desconectada"
            MachineHeroState(
                overlineLabel = "PC CONECTADA · ${contextLabel(m.context).uppercase()}",
                selectedTitle = m.name,
                selectedSubtitle = "$onlineCount de $count sesiones activas$pcStatus",
                selectedColor = parseColor(m.color),
            )
        }

        selected == ALL_MACHINES -> {
            val total = agents.size
            val online = machines.count { it.online }
            val agentWord = if (total == 1) "agente" else "agentes"
            val pcWord = if (online == 1) "PC activa" else "PCs activas"
            MachineHeroState(
                overlineLabel = "EQUIPOS VINCULADOS",
                selectedTitle = "Todas las máquinas",
                selectedSubtitle = "$total $agentWord · $online $pcWord",
                selectedColor = primaryColor,
            )
        }

        else -> {
            val count = agents.count { it.machineContext == selected }
            MachineHeroState(
                overlineLabel = "CONTEXTO",
                selectedTitle = contextLabel(selected),
                selectedSubtitle = "$count ${if (count == 1) "agente" else "agentes"} en este contexto",
                selectedColor = parseColor(machines.firstOrNull { it.context == selected }?.color),
            )
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

    val singleMachine = machines.singleOrNull()
    val activeMachine =
        when {
            selected == ALL_MACHINES && singleMachine != null -> singleMachine
            else -> machines.firstOrNull { it.machineId == selected }
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val heroState =
        remember(machines, agents, selected, primaryColor) {
            resolveMachineHeroState(machines, agents, selected, primaryColor)
        }
    val (overlineLabel, selectedTitle, selectedSubtitle, selectedColor) = heroState

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SurfaceCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f),
            tonalElevation = 3.dp,
            border = BorderStroke(1.2.dp, selectedColor.copy(alpha = 0.55f)),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = selectedColor.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, selectedColor.copy(alpha = 0.45f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Computer,
                            contentDescription = null,
                            tint = selectedColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = overlineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = selectedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = selectedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (activeMachine?.hostOnline == true) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                            ) {
                                Text(
                                    text = "rp-host ✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
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
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (machines.size > 1) {
                            Text(
                                text = "PC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                    fontWeight = if (selected == ALL_MACHINES) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    "$totalAgents agentes${if (totalPrompts > 0) " · $totalPrompts pendientes" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (selected == ALL_MACHINES) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                onClick = {
                    onSelect(ALL_MACHINES)
                    expanded = false
                },
            )

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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    ColorDot(parseColor(ms.firstOrNull()?.color), size = 10)
                                    Column {
                                        Text(
                                            contextLabel(ctx),
                                            fontWeight = if (selected == ctx) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        Text(
                                            "$count agentes${if (prompts > 0) " · $prompts" else ""}",
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
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
                                        val hostBadge = if (m.hostOnline) " · rp-host ✓" else ""
                                        Text(
                                            "${contextLabel(m.context)} · $count agentes$hostBadge",
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
                selected = selected == ALL_MACHINES,
                onClick = { onSelect(ALL_MACHINES) },
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
    modifier: Modifier = Modifier,
    count: Int = 3,
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
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
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

fun connectionBannerMessage(state: ConnState): String? =
    when (state) {
        ConnState.Connected -> null
        ConnState.Connecting -> "Conectando con el relay…"
        ConnState.Disconnected -> "Sin conexión con el relay · reintentando"
        is ConnState.Fatal -> {
            when (state.code) {
                4001 -> "Token revocado · Vuelve a emparejar el dispositivo"
                4002 -> "Protocolo incompatible · Actualiza la app"
                else -> "Conexión cerrada (${state.code})"
            }
        }
    }

/** Banner flotante de estado de la conexión con el relay (cápsula animada y centrada). */
@Composable
fun ConnectionBanner(
    state: ConnState,
    modifier: Modifier = Modifier,
) {
    val isVisible = state !is ConnState.Connected
    val text = connectionBannerMessage(state)
    val fatal = state is ConnState.Fatal
    val connecting = state is ConnState.Connecting

    AnimatedVisibility(
        visible = isVisible && text != null,
        enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(180)),
        exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(150)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val containerColor =
                if (fatal) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                }

            val contentColor =
                if (fatal) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else if (connecting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    RpColors.warning
                }

            val borderColor =
                if (fatal) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                } else if (connecting) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    RpColors.warning.copy(alpha = 0.45f)
                }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, borderColor),
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val reducedMotion = LocalReducedMotion.current
                    if (connecting) {
                        if (reducedMotion) {
                            CircularProgressIndicator(
                                progress = { 0.75f },
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.6.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.6.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else if (fatal) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                        )
                    } else {
                        val infiniteTransition = rememberInfiniteTransition(label = "connPulsing")
                        val pulseAlpha by if (reducedMotion) {
                            remember { mutableFloatStateOf(1f) }
                        } else {
                            infiniteTransition.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 1f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                label = "pulseAlpha",
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = pulseAlpha }
                                    .clip(CircleShape)
                                    .background(RpColors.warning),
                        )
                    }

                    Text(
                        text = text.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
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
