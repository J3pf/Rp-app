package dev.j3pf.rp.ui.agents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.ConnectionBanner
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineFilterBar
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.MainViewModel
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.lastSegment
import dev.j3pf.rp.util.parseColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentsScreen(
    onOpenChat: (String) -> Unit,
    onNewAgent: () -> Unit,
    mainVm: MainViewModel,
    vm: AgentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val conn by mainVm.connState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            if (state.canSpawn) {
                ExtendedFloatingActionButton(
                    onClick = onNewAgent,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Nuevo agente") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ConnectionBanner(conn)
            MachineFilterBar(
                machines = state.machines,
                agents = state.allAgents,
                selected = state.selected,
                onSelect = vm::select,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (state.groups.isEmpty()) {
                EmptyState(
                    "Aún no hay agentes",
                    if (state.machines.isEmpty()) "Empareja una PC con `/rp pair` y abre Pi en ella."
                    else "Abre Pi con el plugin rp en tus PCs y aparecerán aquí.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    state.groups.forEach { g ->
                        stickyHeader(key = "h-${g.machine.machineId}") { MachineHeader(g.machine) }
                        items(g.agents, key = { it.agentId }) { a ->
                            AgentCard(a, onClick = { onOpenChat(a.agentId) }, onForget = { vm.forget(a.agentId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineHeader(m: MachineSummary) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(parseColor(m.color), 12)
            Text(m.name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            Text(
                if (m.online) "  ● en línea" else "  ○ sin conexión",
                style = MaterialTheme.typography.labelSmall,
                color = if (m.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            if (m.hostOnline) {
                Text("  rp-host ✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** Tarjeta de agente: nombre · carpeta · rama · modelo, chips de estado/lanzador/herdr/modo gentle y vista previa. */
@Composable
fun AgentCard(a: AgentSummary, onClick: () -> Unit, onForget: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(parseColor(a.machineColor), 8)
                Text(
                    agentTitle(a.sessionName, a.cwd, a.agentId),
                    Modifier.padding(start = 8.dp).weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.openPromptCount > 0) {
                    BadgedBox(badge = { Badge { Text("${a.openPromptCount}") } }) { Text("🔔") }
                }
                if (!a.online) {
                    Column {
                        Icon(Icons.Filled.Delete, "Olvidar", Modifier.size(20.dp).clickable { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Olvidar agente") }, onClick = { menu = false; onForget() })
                        }
                    }
                }
            }
            val meta = listOfNotNull(
                lastSegment(a.cwd).takeIf { it.isNotEmpty() }?.let { "📁 $it" },
                a.gitBranch?.let { "⎇ $it" },
                a.model?.label?.takeIf { it.isNotEmpty() },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(a)
                a.launcher?.let { TagChip(it) }
                a.herdr?.let { h ->
                    val loc = listOfNotNull(h.workspaceId?.let { w -> "ws $w" }, h.paneId?.let { p -> "pane $p" }).joinToString("·")
                    if (loc.isNotEmpty()) TagChip("herdr $loc")
                }
                a.gentleMode?.takeIf { it != "unknown" }?.let { TagChip(it.uppercase(), if (it == "auto" || it == "yolo") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary) }
            }
            val preview = if (a.redacted) "contenido oculto" else a.lastMessagePreview
            if (!preview.isNullOrBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (a.redacted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
