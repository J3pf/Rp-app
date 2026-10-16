package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult

@Composable
fun MeshScreen(
    onOpenChat: (String) -> Unit,
    viewModel: MeshViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onScreenVisible()
                    Lifecycle.Event.ON_STOP -> viewModel.onScreenInvisible()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenInvisible()
        }
    }

    MeshScreenContent(
        uiState = state,
        onOpenChat = onOpenChat,
        onToggleListView = viewModel::toggleListView,
        onSelectMachine = viewModel::selectMachine,
        onSelectGroup = viewModel::selectGroup,
        onNodeClick = viewModel::onNodeClick,
        onEdgeClick = viewModel::onEdgeClick,
        onViewportSizeChanged = viewModel::onViewportSizeChanged,
        onDismissNodeDetail = viewModel::dismissNodeDetail,
        onViewConversation = { peerId ->
            val nodeId = state.nodeDetail?.node?.agentId
            val edge = state.snapshot?.edges?.firstOrNull { it.a == nodeId && it.b == peerId || it.b == nodeId && it.a == peerId }
            edge?.let { viewModel.onEdgeClick(it.edgeId) }
        },
        onDismissConversation = viewModel::dismissConversationDetail,
        onLoadMoreConversation = viewModel::loadMoreConversation,
        onRetry = {
            viewModel.onScreenInvisible()
            viewModel.onScreenVisible()
        },
    )
}

/** Stateless body shared with Compose tests; screen state and actions remain owned by the ViewModel. */
@Composable
internal fun MeshScreenContent(
    uiState: MeshUiState,
    onOpenChat: (String) -> Unit,
    onToggleListView: () -> Unit = {},
    onSelectMachine: (String?) -> Unit = {},
    onSelectGroup: (String?) -> Unit = {},
    onNodeClick: (String) -> Unit = {},
    onEdgeClick: (String) -> Unit = {},
    onViewportSizeChanged: (Float, Float) -> Unit = { _, _ -> },
    onDismissNodeDetail: () -> Unit = {},
    onViewConversation: (String) -> Unit = {},
    onDismissConversation: () -> Unit = {},
    onLoadMoreConversation: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Red de agentes", style = MaterialTheme.typography.headlineSmall)
            TextButton(
                onClick = onToggleListView,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (uiState.isListView) "Vista de diagrama" else "Vista de lista")
            }
        }

        if (!uiState.isConnected) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Desconectado — mostrando datos locales",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reintentar") }
            }
        }

        if (uiState.machineChips.isNotEmpty()) {
            SelectorRow(label = "Máquinas") {
                uiState.machineChips.forEach { machine ->
                    FilterChip(
                        selected = uiState.selectedMachineId == machine.machineId,
                        onClick = { onSelectMachine(machine.machineId) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (machine.machineId != null && machine.color.isNotBlank()) {
                                    Surface(Modifier.size(10.dp), shape = CircleShape, color = parseRelayColor(machine.color)) {}
                                }
                                Text(
                                    if (machine.machineId == null) {
                                        if (machine.agentCount > 0) "Todas · ${machine.agentCount}" else "Todas"
                                    } else {
                                        "${machine.name} · ${machine.agentCount}"
                                    },
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        if (uiState.groupChips.isNotEmpty()) {
            SelectorRow(label = "Grupos") {
                uiState.groupChips.forEach { group ->
                    val groupLabel =
                        if (group.isSolo) {
                            "Sin grupo"
                        } else {
                            uiState.snapshot
                                ?.groups
                                ?.firstOrNull { it.groupId == group.groupId }
                                ?.orchestratorAgentId
                                ?.let { id ->
                                    uiState.snapshot.nodes
                                        .firstOrNull { it.agentId == id }
                                        ?.name
                                }
                                ?: group.label
                        }
                    FilterChip(
                        selected = uiState.selectedGroupId == group.groupId,
                        onClick = { onSelectGroup(group.groupId) },
                        label = { Text("$groupLabel · ${group.memberCount}") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        when {
            uiState.isLoading -> {
                LoadingSkeleton(Modifier.weight(1f).fillMaxWidth())
            }

            uiState.isEmpty || uiState.snapshot == null -> {
                EmptyMeshState(Modifier.weight(1f).fillMaxWidth())
            }

            uiState.isListView -> {
                MeshListView(
                    snapshot = uiState.snapshot,
                    layout = uiState.layoutResult,
                    onNodeClick = onNodeClick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            else -> {
                MeshCanvas(
                    layout = uiState.layoutResult,
                    activeBubbles = uiState.activeBubbles,
                    onNodeClick = onNodeClick,
                    onEdgeClick = onEdgeClick,
                    onViewportSizeChanged = onViewportSizeChanged,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        LiveStrip(uiState.liveComms)
    }

    uiState.nodeDetail?.let { detail ->
        MeshNodeDetailSheet(
            detail = detail,
            onDismiss = onDismissNodeDetail,
            onOpenChat = onOpenChat,
            onViewConversation = onViewConversation,
        )
    }
    uiState.conversationDetail?.let { detail ->
        MeshConversationSheet(
            detail = detail,
            onDismiss = onDismissConversation,
            onLoadOlder = onLoadMoreConversation,
        )
    }
}

@Composable
private fun SelectorRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cargando red de agentes…", style = MaterialTheme.typography.bodyMedium)
        repeat(4) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {}
        }
    }
}

@Composable
private fun EmptyMeshState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No hay agentes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Abre Pi con el plugin rp en tus PCs para ver la red de agentes aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveStrip(comms: List<dev.j3pf.rp.protocol.MeshComm>) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = { expanded = !expanded })
                .semantics { contentDescription = if (expanded) "Contraer mensajes en vivo" else "Expandir mensajes en vivo" },
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("En vivo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val visible = if (expanded) comms.take(5) else comms.take(1)
            visible.forEach { comm ->
                Text(
                    "${comm.fromName} → ${comm.toName}: ${formatMeshCommText(comm.text)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (comms.isEmpty()) {
                Text(
                    "Sin mensajes recientes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun parseRelayColor(raw: String) =
    runCatching {
        androidx.compose.ui.graphics
            .Color(android.graphics.Color.parseColor(raw))
    }.getOrElse { MaterialTheme.colorScheme.primary }
