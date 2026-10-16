package dev.j3pf.rp.ui.agents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.MainViewModel
import dev.j3pf.rp.ui.components.AgentSkeletonList
import dev.j3pf.rp.ui.components.AppHeader
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.ConnectionBanner
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineFilterBar
import dev.j3pf.rp.ui.components.MachineHeroDropdown
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.lastSegment
import dev.j3pf.rp.util.parseColor
import dev.j3pf.rp.util.relativeTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onOpenChat: (String) -> Unit,
    onNewAgent: () -> Unit,
    mainVm: MainViewModel,
    vm: AgentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val conn by mainVm.connState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewAgent,
                shape = RoundedCornerShape(999.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo agente", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        bottomBar = {
            QuotaDynamicIsland(
                machines = state.machines,
                selectedMachineId = if (state.selected == AppSettings.ALL) null else state.selected,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            ConnectionBanner(conn)

            AppHeader(
                greeting = "¿Qué está ejecutando Pi?",
                title = "Tus Agentes",
            )

            // Switcher de máquinas tipo Dropdown con Hero Card (design.md §1 y §6)
            MachineHeroDropdown(
                machines = state.machines,
                agents = state.allAgents,
                selected = state.selected,
                onSelect = vm::select,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            // Filtros rápidos si hay más de 1 contexto
            if (state.machines
                    .map { it.context }
                    .distinct()
                    .size > 1
            ) {
                MachineFilterBar(
                    machines = state.machines,
                    agents = state.allAgents,
                    selected = state.selected,
                    onSelect = vm::select,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.refreshing && state.groups.isEmpty() -> {
                        AgentSkeletonList(
                            count = 3,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    state.groups.isEmpty() -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            EmptyState(
                                title = "Aún no hay agentes",
                                subtitle =
                                    if (state.machines.isEmpty()) {
                                        "Empareja una PC con `/rp pair` y abre Pi en ella para comenzar."
                                    } else {
                                        "Abre Pi con el plugin rp en tus PCs y tus sesiones aparecerán aquí."
                                    },
                                actionLabel = if (state.canSpawn) "Nuevo agente" else null,
                                onAction = if (state.canSpawn) onNewAgent else null,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                },
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.groups.forEach { g ->
                                stickyHeader(key = "h-${g.machine.machineId}") {
                                    MachineHeader(g.machine)
                                }
                                items(g.agents, key = { it.agentId }) { a ->
                                    AgentCard(
                                        a = a,
                                        canResume = g.machine.hostOnline,
                                        onClick = { onOpenChat(a.agentId) },
                                        onResume = {
                                            if (g.machine.hostOnline) {
                                                vm.resumeAgent(a) { msg ->
                                                    scope.launch { snackbar.showSnackbar(msg) }
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbar.showSnackbar("Para reanudar agentes, ejecuta rp-host en ${g.machine.name}")
                                                }
                                            }
                                        },
                                        onForget = { vm.forget(a.agentId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineHeader(m: MachineSummary) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(parseColor(m.color), 12)
            Text(
                text = m.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (m.online) "● en línea" else "○ sin conexión",
                style = MaterialTheme.typography.labelSmall,
                color = if (m.online) RpColors.success else MaterialTheme.colorScheme.outline,
            )
            if (m.hostOnline) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = "rp-host ✓",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** Tarjeta de agente: nombre · carpeta · rama · modelo, chips de estado/lanzador/herdr/modo gentle y vista previa. */
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

    SurfaceCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fila superior: punto de color de máquina, título de sesión, campana y menú
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = parseColor(a.machineColor).copy(alpha = 0.16f),
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ColorDot(parseColor(a.machineColor), 8)
                    }
                }

                Text(
                    text = agentTitle(a.sessionName, a.cwd, a.agentId),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

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
                            IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Reanudar agente",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { setMenu(true) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Opciones",
                                    modifier = Modifier.size(20.dp),
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

            // Metadatos técnicos (carpeta, rama, modelo, tiempo relativo de actividad)
            val timeText = if (a.status == "working") "activo ahora" else relativeTime(a.lastSeenAt)
            val meta =
                listOfNotNull(
                    lastSegment(a.cwd).takeIf { it.isNotEmpty() },
                    a.gitBranch?.let { "git:$it" },
                    a.model?.label?.takeIf { it.isNotEmpty() },
                    timeText.takeIf { it.isNotEmpty() },
                ).joinToString("  •  ")

            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

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
                a.launcher?.let { TagChip(it) }
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
