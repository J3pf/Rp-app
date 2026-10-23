package dev.j3pf.rp.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.components.AgentSkeletonList
import dev.j3pf.rp.ui.components.AppHeader
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.ConnectionBanner
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineHeroDropdown
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.util.parseColor

@Composable
fun SessionsSectionHeader(
    totalCount: Int,
    hasAgy: Boolean,
    hasPi: Boolean,
    selectedFilter: SessionQuickFilter,
    onSelectFilter: (SessionQuickFilter) -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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

@Composable
fun MachineHeader(
    machine: MachineSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(parseColor(machine.color), size = 12)
            Text(
                text = machine.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (machine.online) "● en línea" else "○ sin conexión",
                style = MaterialTheme.typography.labelSmall,
                color = if (machine.online) RpColors.success else MaterialTheme.colorScheme.outline,
            )
            if (machine.hostOnline) {
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentsContent(
    state: AgentsUiState,
    conn: ConnState,
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
            filterAgentGroups(state.groups, quickFilter, searchQuery)
        }

    val totalVisibleAgents = remember(filteredGroups) { filteredGroups.sumOf { it.agents.size } }
    val hasAgy = remember(state.allAgents) { state.allAgents.any { it.launcher == "agy" } }
    val hasPi = remember(state.allAgents) { state.allAgents.any { it.launcher != "agy" } }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
