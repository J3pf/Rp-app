package dev.j3pf.rp.ui.newagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.SectionTitle
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.util.contextEmoji
import dev.j3pf.rp.util.parseColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    vm: NewAgentViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val f = ui.form
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(f.created) { if (f.created) onCreated() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo agente") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // 1) PC
            SectionTitle("1 · PC")
            if (ui.machines.isEmpty()) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Servicio rp-host requerido",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Para crear nuevos agentes y espacios (Workspaces de Herdr) desde tu dispositivo móvil, es necesario tener el daemon rp-host activo en tu PC.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Card(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    clipboard.setText(AnnotatedString("npx @j3pf/pi-rp rp-host run"))
                                    scope.launch { snackbar.showSnackbar("Comando copiado al portapapeles") }
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "npx @j3pf/pi-rp rp-host run",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Filled.ContentCopy, "Copiar comando", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "Al ejecutarlo, tu PC aparecerá aquí automáticamente para seleccionar carpetas, lanzadores y espacios.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.machines.forEach { m ->
                        FilterChip(
                            selected = f.machineId == m.machineId,
                            onClick = { vm.pickMachine(m.machineId) },
                            leadingIcon = { ColorDot(parseColor(m.color)) },
                            label = { Text(m.name) },
                        )
                    }
                }
            }

            if (f.machineId != null) {
                // 2) Carpeta
                SectionTitle("2 · Carpeta")
                val roots = ui.host?.roots.orEmpty()
                if (roots.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        roots.forEach { r -> FilterChip(f.path.startsWith(r), { vm.openDir(r) }, label = { Text(r) }) }
                    }
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.path.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (f.loadingDirs) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        HorizontalDivider()
                        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                            if (vm.canGoUp) {
                                ListItem(
                                    headlineContent = { Text("..", fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier.clickable { vm.up() },
                                )
                            }
                            f.dirs.forEach { d ->
                                ListItem(
                                    headlineContent = { Text(d, fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier.clickable { vm.enter(d) },
                                )
                            }
                            if (f.dirs.isEmpty() && !f.loadingDirs) {
                                Text("Sin subcarpetas", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // 3) Lanzador
                SectionTitle("3 · Lanzador")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.host
                        ?.launchers
                        .orEmpty()
                        .forEach { l -> FilterChip(f.launcher == l, { vm.setLauncher(l) }, label = { Text(l) }) }
                }

                // 4) Dónde
                SectionTitle("4 · Dónde")
                if (ui.backends.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ui.backends.forEach { b ->
                            FilterChip(ui.effectiveBackend == b, { vm.setBackend(b) }, label = { Text(backendLabel(b)) })
                        }
                    }
                } else {
                    Text(ui.backends.firstOrNull()?.let(::backendLabel) ?: "Sin backend disponible", Modifier.padding(horizontal = 16.dp))
                }
                if (ui.effectiveBackend == "herdr") HerdrPlacementSection(ui, vm)

                // 5) Nombre y prompt
                SectionTitle("5 · Nombre y prompt inicial (opcional)")
                OutlinedTextField(f.name, vm::setName, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = {
                    Text("Nombre de la sesión")
                }, singleLine = true)
                OutlinedTextField(
                    f.initialPrompt,
                    vm::setPrompt,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Prompt inicial") },
                    minLines = 3,
                )
                f.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = vm::create,
                    enabled = ui.canCreate,
                    shape = RpShapes.pill,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
                ) {
                    if (f.spawning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Crear agente", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun backendLabel(b: String) =
    when (b) {
        "herdr" -> "herdr"
        "tmux" -> "tmux"
        "wt" -> "Windows Terminal"
        "console" -> "Consola"
        else -> b
    }

/** Lista viva de workspaces / pestañas / paneles de herdr de la PC (`host.herdr_layout`). */
@Composable
private fun HerdrPlacementSection(
    ui: NewAgentUi,
    vm: NewAgentViewModel,
) {
    val f = ui.form
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(f.placement == HerdrPlacement.NEW_WORKSPACE, {
            vm.setPlacement(HerdrPlacement.NEW_WORKSPACE)
        }, label = { Text("Nuevo espacio (Workspace)") })
        FilterChip(f.placement == HerdrPlacement.TAB, { vm.setPlacement(HerdrPlacement.TAB) }, label = { Text("Pestaña en espacio…") })
        FilterChip(f.placement == HerdrPlacement.SPLIT, { vm.setPlacement(HerdrPlacement.SPLIT) }, label = { Text("Junto a un agente…") })
    }
    if (f.placement == HerdrPlacement.NEW_WORKSPACE) return
    if (f.loadingLayout) CircularProgressIndicator(Modifier.padding(16.dp).size(20.dp), strokeWidth = 2.dp)
    val ws = f.layout?.workspaces.orEmpty()
    if (f.placement == HerdrPlacement.TAB) {
        ws.forEach { w ->
            ListItem(
                headlineContent = { Text(w.label.ifBlank { w.id }) },
                supportingContent = { Text("${w.tabs.size} pestañas" + (w.cwd?.let { " · $it" } ?: "")) },
                trailingContent = { if (f.workspaceId == w.id) Text("✓") },
                modifier = Modifier.clickable { vm.setWorkspace(w.id) },
            )
        }
    } else {
        ws.forEach { w ->
            w.tabs.forEach { t ->
                t.panes.forEach { p ->
                    ListItem(
                        headlineContent = { Text("${w.label.ifBlank { w.id }} › ${t.label.ifBlank { t.id }} › ${p.id}") },
                        supportingContent = { p.agent?.let { Text("agente: $it") } },
                        trailingContent = { if (f.paneId == p.id) Text("✓") },
                        modifier = Modifier.clickable { vm.setPane(p.id) },
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(f.direction == "right", { vm.setDirection("right") }, label = { Text("A la derecha") })
            FilterChip(f.direction == "down", { vm.setDirection("down") }, label = { Text("Debajo") })
        }
    }
    TextButton(onClick = vm::loadLayout, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Actualizar lista") }
}
