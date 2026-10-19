package dev.j3pf.rp.ui.agents

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import dev.j3pf.rp.ui.components.MachineHeroDropdown
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.lastSegment
import dev.j3pf.rp.util.parseColor
import dev.j3pf.rp.util.relativeTime
import kotlinx.coroutines.launch

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

private enum class SessionQuickFilter(
    val label: String,
) {
    ALL("Todas"),
    ACTIVE("En línea"),
    AGY("✦ agy"),
    PI("π pi"),
}

@Composable
private fun SessionsSectionHeader(
    totalCount: Int,
    hasAgy: Boolean,
    hasPi: Boolean,
    selectedFilter: SessionQuickFilter,
    onSelectFilter: (SessionQuickFilter) -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "SESIONES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                val visibleFilters =
                    buildList {
                        add(SessionQuickFilter.ALL)
                        add(SessionQuickFilter.ACTIVE)
                        if (hasAgy) add(SessionQuickFilter.AGY)
                        if (hasPi) add(SessionQuickFilter.PI)
                    }
                visibleFilters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                        border =
                            if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            } else {
                                null
                            },
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onSelectFilter(filter) },
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searchActive) "Cerrar búsqueda" else "Buscar sesión",
                        tint =
                            if (searchActive || searchQuery.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (searchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text(
                        "Filtrar por proyecto, tema o rama...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon =
                    if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Limpiar búsqueda",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentsContent(
    state: AgentsUiState,
    conn: dev.j3pf.rp.data.net.ConnState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {
        QuotaDynamicIsland(
            machines = state.machines,
            selectedMachineId = if (state.selected == AppSettings.ALL) null else state.selected,
        )
    },
    onOpenChat: (String) -> Unit,
    onNewAgent: () -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onResume: (AgentSummary, String) -> Unit,
    onForget: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val isFabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 }
    }
    var quickFilter by rememberSaveable { mutableStateOf(SessionQuickFilter.ALL) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredGroups =
        remember(state.groups, quickFilter, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (quickFilter == SessionQuickFilter.ALL && q.isEmpty()) {
                state.groups
            } else {
                state.groups.mapNotNull { group ->
                    val matching =
                        group.agents.filter { a ->
                            val matchesFilter =
                                when (quickFilter) {
                                    SessionQuickFilter.ALL -> true
                                    SessionQuickFilter.ACTIVE -> a.online
                                    SessionQuickFilter.AGY -> a.launcher == "agy"
                                    SessionQuickFilter.PI -> a.launcher != "agy"
                                }
                            val matchesQuery =
                                if (q.isEmpty()) {
                                    true
                                } else {
                                    (a.sessionName?.lowercase()?.contains(q) == true) ||
                                        (a.cwd?.lowercase()?.contains(q) == true) ||
                                        (a.gitBranch?.lowercase()?.contains(q) == true) ||
                                        (a.lastMessagePreview?.lowercase()?.contains(q) == true)
                                }
                            matchesFilter && matchesQuery
                        }
                    if (matching.isEmpty()) null else group.copy(agents = matching)
                }
            }
        }

    val totalVisibleAgents = remember(filteredGroups) { filteredGroups.sumOf { it.agents.size } }
    val hasAgy = remember(state.allAgents) { state.allAgents.any { it.launcher == "agy" } }
    val hasPi = remember(state.allAgents) { state.allAgents.any { it.launcher != "agy" } }

    Scaffold(
        modifier = modifier,
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewAgent,
                expanded = isFabExpanded,
                shape = RoundedCornerShape(999.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = "Nuevo agente") },
                text = { Text("Nuevo agente", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            AgentsHeroStats(
                agents = state.allAgents,
                machines = state.machines,
            )

            // Switcher de máquinas tipo Hardware Context Bar (design.md §1 y §6)
            MachineHeroDropdown(
                machines = state.machines,
                agents = state.allAgents,
                selected = state.selected,
                onSelect = onSelect,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )

            if (state.allAgents.isNotEmpty()) {
                SessionsSectionHeader(
                    totalCount = totalVisibleAgents,
                    hasAgy = hasAgy,
                    hasPi = hasPi,
                    selectedFilter = quickFilter,
                    onSelectFilter = { quickFilter = it },
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onToggleSearch = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                    onSearchQueryChange = { searchQuery = it },
                )
            }

            val reducedMotion = LocalReducedMotion.current
            PullToRefreshBox(
                isRefreshing = state.refreshing && !reducedMotion,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    (state.isLoading || (state.refreshing && state.groups.isEmpty())) -> {
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

                    filteredGroups.isEmpty() -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            EmptyState(
                                title = "Sin sesiones que coincidan",
                                subtitle = "Prueba cambiando el filtro rápido o limpiando la búsqueda.",
                                actionLabel = "Mostrar todas",
                                onAction = {
                                    quickFilter = SessionQuickFilter.ALL
                                    searchQuery = ""
                                    searchActive = false
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                },
                            )
                        }
                    }

                    else -> {
                        val showGroupHeaders = filteredGroups.size > 1
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filteredGroups.forEach { g ->
                                if (showGroupHeaders) {
                                    stickyHeader(key = "h-${g.machine.machineId}") {
                                        MachineHeader(g.machine)
                                    }
                                }
                                items(g.agents, key = { it.agentId }) { a ->
                                    AgentCard(
                                        a = a,
                                        modifier = Modifier.animateItem(),
                                        canResume = g.machine.hostOnline,
                                        onClick = { onOpenChat(a.agentId) },
                                        onResume = { onResume(a, g.machine.name) },
                                        onForget = { onForget(a.agentId) },
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

    AgentsContent(
        state = state,
        conn = conn,
        snackbarHostState = snackbar,
        onOpenChat = onOpenChat,
        onNewAgent = onNewAgent,
        onSelect = vm::select,
        onRefresh = vm::refresh,
        onResume = { a, machineName ->
            if (state.machines.find { it.name == machineName }?.hostOnline == true) {
                vm.resumeAgent(a) { msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            } else {
                scope.launch {
                    snackbar.showSnackbar("Para reanudar agentes, ejecuta rp-host en $machineName")
                }
            }
        },
        onForget = vm::forget,
    )
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
            ColorDot(parseColor(m.color), size = 12)
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

/** Tarjeta de agente: carpeta/rama separadas del tema de conversación (2 líneas), chips de estado y vista previa. */
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

    SurfaceCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
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
                val isWorking = a.status == "working" && a.online
                val reducedMotion = LocalReducedMotion.current
                val avatarPulseTransition = rememberInfiniteTransition(label = "avatarPulse")
                val avatarScale: Float =
                    if (isWorking && !reducedMotion) {
                        avatarPulseTransition
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
                val timeText = if (a.status == "working" && a.online) "activo ahora" else relativeTime(a.lastSeenAt)
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (a.status == "working" && a.online) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
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
