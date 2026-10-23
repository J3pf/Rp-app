package dev.j3pf.rp.ui.newagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.SectionTitle
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.util.parseColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentContent(
    ui: NewAgentUi,
    onBack: () -> Unit,
    onPickMachine: (String) -> Unit,
    onOpenDir: (String) -> Unit,
    onEnterDir: (String) -> Unit,
    onUpDir: () -> Unit,
    canGoUp: Boolean,
    onSetLauncher: (String) -> Unit,
    onSetBackend: (String) -> Unit,
    onSetName: (String) -> Unit,
    onSetPrompt: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    herdrSection: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val f = ui.form
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Nuevo agente") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
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
                            "Para crear nuevos agentes y espacios (Workspaces de Herdr) desde tu dispositivo móvil, " +
                                "es necesario tener el daemon rp-host activo en tu PC.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Card(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    clipboardManager.setText(AnnotatedString("npx @j3pf/pi-rp rp-host run"))
                                    scope.launch { snackbarHostState.showSnackbar("Comando copiado al portapapeles") }
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
                            "Al ejecutarlo, tu PC aparecerá aquí automáticamente para " +
                                "seleccionar carpetas, lanzadores y espacios.",
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
                            onClick = { onPickMachine(m.machineId) },
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
                        roots.forEach { r -> FilterChip(f.path.startsWith(r), { onOpenDir(r) }, label = { Text(r) }) }
                    }
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RpShapes.card) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(f.path.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (f.loadingDirs) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        HorizontalDivider()
                        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                            if (canGoUp) {
                                ListItem(
                                    headlineContent = { Text("..", fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier.clickable { onUpDir() },
                                )
                            }
                            f.dirs.forEach { d ->
                                ListItem(
                                    headlineContent = { Text(d, fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier.clickable { onEnterDir(d) },
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
                        .forEach { l -> FilterChip(f.launcher == l, { onSetLauncher(l) }, label = { Text(l) }) }
                }

                // 4) Dónde
                SectionTitle("4 · Dónde")
                if (ui.backends.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ui.backends.forEach { b ->
                            FilterChip(ui.effectiveBackend == b, { onSetBackend(b) }, label = { Text(backendLabel(b)) })
                        }
                    }
                } else {
                    Text(
                        ui.backends.firstOrNull()?.let(::backendLabel) ?: "Sin backend disponible",
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (ui.effectiveBackend == "herdr") herdrSection()

                // 5) Nombre y prompt
                SectionTitle("5 · Nombre y prompt inicial (opcional)")
                OutlinedTextField(
                    value = f.name,
                    onValueChange = onSetName,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = {
                        Text("Nombre de la sesión")
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = f.initialPrompt,
                    onValueChange = onSetPrompt,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Prompt inicial") },
                    minLines = 3,
                )
                f.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = onCreate,
                    enabled = ui.canCreate,
                    shape = RpShapes.pill,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
                ) {
                    if (f.spawning) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Crear agente", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Lista viva de workspaces / pestañas / paneles de herdr de la PC (`host.herdr_layout`). */
@Composable
fun HerdrPlacementSection(
    ui: NewAgentUi,
    onSetPlacement: (HerdrPlacement) -> Unit,
    onSetWorkspace: (String) -> Unit,
    onSetPane: (String) -> Unit,
    onSetDirection: (String) -> Unit,
    onRefreshLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = ui.form
    Column(modifier = modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = f.placement == HerdrPlacement.NEW_WORKSPACE,
                onClick = { onSetPlacement(HerdrPlacement.NEW_WORKSPACE) },
                label = { Text("Nuevo espacio (Workspace)") },
            )
            FilterChip(
                selected = f.placement == HerdrPlacement.TAB,
                onClick = { onSetPlacement(HerdrPlacement.TAB) },
                label = { Text("Pestaña en espacio…") },
            )
            FilterChip(
                selected = f.placement == HerdrPlacement.SPLIT,
                onClick = { onSetPlacement(HerdrPlacement.SPLIT) },
                label = { Text("Junto a un agente…") },
            )
        }
        if (f.placement == HerdrPlacement.NEW_WORKSPACE) return@Column
        if (f.loadingLayout) CircularProgressIndicator(Modifier.padding(16.dp).size(20.dp), strokeWidth = 2.dp)
        val ws = f.layout?.workspaces.orEmpty()
        if (f.placement == HerdrPlacement.TAB) {
            ws.forEach { w ->
                ListItem(
                    headlineContent = { Text(w.label.ifBlank { w.id }) },
                    supportingContent = { Text("${w.tabs.size} pestañas" + (w.cwd?.let { " · $it" } ?: "")) },
                    trailingContent = { if (f.workspaceId == w.id) Text("✓") },
                    modifier = Modifier.clickable { onSetWorkspace(w.id) },
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
                            modifier = Modifier.clickable { onSetPane(p.id) },
                        )
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(f.direction == "right", { onSetDirection("right") }, label = { Text("A la derecha") })
                FilterChip(f.direction == "down", { onSetDirection("down") }, label = { Text("Debajo") })
            }
        }
        TextButton(onClick = onRefreshLayout, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Actualizar lista")
        }
    }
}
