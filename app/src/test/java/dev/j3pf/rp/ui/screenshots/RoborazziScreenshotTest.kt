package dev.j3pf.rp.ui.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.CommAction
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshGroup
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.ModelInfo
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.QuotaWindow
import dev.j3pf.rp.ui.agents.AgentCard
import dev.j3pf.rp.ui.chat.ChatUiState
import dev.j3pf.rp.ui.chat.MessageList
import dev.j3pf.rp.ui.components.AgentSkeletonList
import dev.j3pf.rp.ui.components.AppHeader
import dev.j3pf.rp.ui.components.EmptyState
import dev.j3pf.rp.ui.components.MachineFilterBar
import dev.j3pf.rp.ui.components.MachineHeroDropdown
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.components.SectionTitle
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.mesh.MeshGroupChip
import dev.j3pf.rp.ui.mesh.MeshMachineChip
import dev.j3pf.rp.ui.mesh.MeshScreenContent
import dev.j3pf.rp.ui.mesh.MeshUiState
import dev.j3pf.rp.ui.pending.PendingItem
import dev.j3pf.rp.ui.pending.PendingRow
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.theme.RpTheme
import dev.j3pf.rp.ui.theme.RpThemeChoice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RoborazziScreenshotTest {
    private val themes =
        listOf(
            "cute" to RpThemeChoice.GENTLE_CUTE,
            "sexy" to RpThemeChoice.GENTLE_SEXY,
            "omarchy" to RpThemeChoice.OMARCHY,
        )

    private val modes =
        listOf(
            "light" to false,
            "dark" to true,
        )

    private val machineWork =
        MachineSummary(
            machineId = "m-work",
            name = "PC Empresa",
            context = "work",
            color = "#7AA2F7",
            online = true,
            hostOnline = true,
            quotas =
                listOf(
                    MachineQuota(
                        family = "anthropic",
                        label = "Claude 3.5 Sonnet",
                        windows = listOf(QuotaWindow(id = "daily", label = "Diario", usedPercent = 42.0)),
                    ),
                    MachineQuota(
                        family = "openai",
                        label = "GPT-4o",
                        windows = listOf(QuotaWindow(id = "daily", label = "Diario", usedPercent = 78.0)),
                    ),
                ),
        )

    private val machinePersonal =
        MachineSummary(
            machineId = "m-personal",
            name = "MacBook Pro",
            context = "personal",
            color = "#D81B60",
            online = true,
            hostOnline = true,
        )

    private val agentWorking =
        AgentSummary(
            agentId = "a-1",
            machineId = "m-work",
            machineName = "PC Empresa",
            machineColor = "#7AA2F7",
            sessionName = "api-gateway",
            cwd = "/home/drift/Workspace/api",
            gitBranch = "feat/auth",
            model = ModelInfo(provider = "anthropic", id = "claude-3-5"),
            status = "working",
            online = true,
            launcher = "gentle-shell",
            herdr = HerdrInfo(paneId = "p1", workspaceId = "w1"),
            gentleMode = "auto",
            activeTask = "Compilando middleware de autorización JWT",
            todoCompleted = 3,
            todoTotal = 5,
            lastMessagePreview = "Compilación completada sin errores. Iniciando pruebas unitarias.",
            lastSeenAt = System.currentTimeMillis() - 60_000L,
        )

    private val agentWaiting =
        AgentSummary(
            agentId = "a-2",
            machineId = "m-personal",
            machineName = "MacBook Pro",
            machineColor = "#D81B60",
            sessionName = "db-migrate",
            cwd = "/Users/dev/db",
            gitBranch = "main",
            model = ModelInfo(provider = "openai", id = "gpt-4o"),
            status = "waiting_input",
            online = true,
            openPromptCount = 2,
            activeTask = "Esperando confirmación de migración V2",
            lastMessagePreview = "¿Deseas ejecutar la migración sobre la base de datos de producción?",
            lastSeenAt = System.currentTimeMillis() - 300_000L,
        )

    private val agentIdle =
        AgentSummary(
            agentId = "a-3",
            machineId = "m-work",
            machineName = "PC Empresa",
            machineColor = "#7AA2F7",
            sessionName = "docs-gen",
            cwd = "/home/drift/Workspace/docs",
            gitBranch = "main",
            model = ModelInfo(provider = "deepseek", id = "deepseek-coder"),
            status = "idle",
            online = false,
            lastSeenAt = System.currentTimeMillis() - 3600_000L * 2,
            lastMessagePreview = "Documentación sincronizada correctamente.",
        )

    private val sampleAgents = listOf(agentWorking, agentWaiting, agentIdle)

    private fun captureScreen(
        screen: String,
        content: @Composable () -> Unit,
    ) {
        themes.forEach { (themeName, themeChoice) ->
            modes.forEach { (modeName, darkTheme) ->
                captureRoboImage("src/test/screenshots/${screen}_${themeName}_$modeName.png") {
                    CompositionLocalProvider(LocalReducedMotion provides true) {
                        RpTheme(themeChoice = themeChoice, darkTheme = darkTheme) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────── 1. Agents Populated ─────────────────────────────
    @Test
    fun screenshotAgentsPopulated() {
        captureScreen("agents_populated") {
            Scaffold(
                bottomBar = {
                    QuotaDynamicIsland(
                        machines = listOf(machineWork, machinePersonal),
                        selectedMachineId = null,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    AppHeader(greeting = "¿Qué está ejecutando Pi?", title = "Tus Agentes")
                    MachineHeroDropdown(
                        machines = listOf(machineWork, machinePersonal),
                        agents = sampleAgents,
                        selected = "all",
                        onSelect = {},
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    MachineFilterBar(
                        machines = listOf(machineWork, machinePersonal),
                        agents = sampleAgents,
                        selected = "all",
                        onSelect = {},
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sampleAgents, key = { it.agentId }) { a ->
                            AgentCard(a = a, onClick = {}, onForget = {})
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────── 2. Agents Empty ─────────────────────────────
    @Test
    fun screenshotAgentsEmpty() {
        captureScreen("agents_empty") {
            Scaffold { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    AppHeader(greeting = "¿Qué está ejecutando Pi?", title = "Tus Agentes")
                    EmptyState(
                        title = "Aún no hay agentes",
                        subtitle = "Abre Pi con el plugin rp en tus PCs y tus sesiones aparecerán aquí.",
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
        }
    }

    // ───────────────────────────── 3. Agents Skeleton ─────────────────────────────
    @Test
    fun screenshotAgentsSkeleton() {
        captureScreen("agents_skeleton") {
            Scaffold { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    AppHeader(greeting = "¿Qué está ejecutando Pi?", title = "Tus Agentes")
                    AgentSkeletonList(count = 4, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    // ───────────────────────────── 4. Chat Streaming ─────────────────────────────
    @Test
    fun screenshotChatStreaming() {
        val chatMessages =
            listOf(
                UserMsg(key = "u-1", text = "Hola Pi, ¿puedes optimizar el servicio de autenticación y agregar logs de auditoría?"),
                AssistantMsg(
                    key = "a-1",
                    blocks =
                        listOf(
                            Block.Text("¡Claro que sí! Analizando los endpoints de autenticación y preparando la refactorización..."),
                            Block.Thinking("Se requiere auditar cada inicio de sesión fallido y emitir eventos mediante SSE."),
                            Block.Text(
                                "He preparado la implementación con auditoría segura:\n\n" +
                                    "```kotlin\nsuspend fun authenticate(req: AuthRequest): AuthResult {\n" +
                                    "    auditLog.recordAttempt(req.username)\n" +
                                    "    return tokenService.issue(req.username)\n" +
                                    "}\n```\n\n" +
                                    "Streaming en curso con fondo anclado en pantalla visible.",
                            ),
                        ),
                    streaming = true,
                ),
            )

        captureScreen("chat_streaming") {
            Scaffold { padding ->
                MessageList(
                    ui = ChatUiState(chat = ChatState(items = chatMessages)),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _, _ -> },
                )
            }
        }
    }

    // ───────────────────────────── 5. Mesh ─────────────────────────────
    @Test
    fun screenshotMesh() {
        val nodes =
            listOf(
                MeshNode(
                    agentId = "node-orch",
                    machineId = "m-work",
                    machineName = "PC Empresa",
                    machineColor = "#7AA2F7",
                    name = "Orquestador Principal",
                    role = "orchestrator",
                    status = "working",
                    online = true,
                    cwd = "/workspace/project",
                    gitBranch = "main",
                ),
                MeshNode(
                    agentId = "node-sub1",
                    machineId = "m-work",
                    machineName = "PC Empresa",
                    machineColor = "#7AA2F7",
                    name = "Worker UI",
                    role = "subordinate",
                    status = "working",
                    online = true,
                    cwd = "/workspace/ui",
                    gitBranch = "feat/refresh",
                ),
                MeshNode(
                    agentId = "node-sub2",
                    machineId = "m-personal",
                    machineName = "MacBook Pro",
                    machineColor = "#D81B60",
                    name = "Worker Protocol",
                    role = "subordinate",
                    status = "idle",
                    online = true,
                    cwd = "/workspace/protocol",
                    gitBranch = "main",
                ),
            )

        val groups =
            listOf(
                MeshGroup(
                    groupId = "grp-1",
                    label = "Orquestador",
                    orchestratorAgentId = "node-orch",
                    memberAgentIds = listOf("node-orch", "node-sub1", "node-sub2"),
                ),
            )

        val snapshot = MeshSnapshot(groups = groups, nodes = nodes, edges = emptyList())

        val meshUiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                isListView = true,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 3),
                        MeshMachineChip(machineId = "m-work", name = "PC Empresa", color = "#7AA2F7", agentCount = 2),
                        MeshMachineChip(machineId = "m-personal", name = "MacBook Pro", color = "#D81B60", agentCount = 1),
                    ),
                groupChips =
                    listOf(
                        MeshGroupChip(groupId = "grp-1", label = "Orquestador", memberCount = 3),
                    ),
                liveComms =
                    listOf(
                        MeshComm(
                            commKey = "comm-1",
                            fromName = "Orquestador",
                            toName = "Worker UI",
                            action = CommAction.ASK,
                            text = "Validando pruebas de UI",
                        ),
                    ),
            )

        captureScreen("mesh") {
            MeshScreenContent(
                uiState = meshUiState,
                onOpenChat = {},
            )
        }
    }

    // ───────────────────────────── 6. Pending ─────────────────────────────
    @Test
    fun screenshotPending() {
        val pendingItems =
            listOf(
                PendingItem(
                    prompt =
                        Prompt(
                            promptId = "p-1",
                            agentId = "a-1",
                            title = "Ejecutar comando crítico en servidor",
                            message = "rm -rf /tmp/build-cache && ./deploy.sh",
                            category = PromptCategory.PERMISSION,
                            danger = Danger.HIGH,
                        ),
                    agent = agentWorking,
                ),
                PendingItem(
                    prompt =
                        Prompt(
                            promptId = "p-2",
                            agentId = "a-2",
                            title = "¿Cuál estrategia de release deseas emplear?",
                            message = "Selecciona si prefieres stacked-to-main o feature-branch-chain.",
                            category = PromptCategory.QUESTION,
                            danger = Danger.LOW,
                        ),
                    agent = agentWaiting,
                ),
            )

        captureScreen("pending") {
            Scaffold { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    AppHeader(greeting = "¿Hay decisiones pendientes?", title = "Bandeja de Entrada")
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pendingItems, key = { it.prompt.promptId }) { item ->
                            PendingRow(item = item, onClick = {})
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────── 7. Settings ─────────────────────────────
    @Test
    fun screenshotSettings() {
        captureScreen("settings") {
            Scaffold { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    AppHeader(greeting = "Configuración y estado", title = "Ajustes")

                    SectionTitle("Relay")
                    SurfaceCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = RpShapes.card,
                    ) {
                        ListItem(
                            headlineContent = { Text("Relay Principal", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("https://relay.j3pf.dev", style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                Text("● conectado", style = MaterialTheme.typography.labelMedium, color = RpColors.success)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    SectionTitle("Preferencias")
                    SurfaceCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = RpShapes.card,
                    ) {
                        ListItem(
                            headlineContent = { Text("Tema de la app") },
                            supportingContent = { Text("Sincronizado con Pi (automático)", style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ListItem(
                            headlineContent = { Text("Notificaciones") },
                            supportingContent = { Text("Activas · Todas las categorías", style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ListItem(
                            headlineContent = { Text("Seguridad") },
                            supportingContent = { Text("Desbloqueo biométrico activo", style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    SectionTitle("Acerca de")
                    SurfaceCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = RpShapes.card,
                    ) {
                        ListItem(
                            headlineContent = { Text("Versión 0.3.0") },
                            supportingContent = { Text("Licencias de código abierto", style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }

    // ───────────────────────────── 8. New Agent ─────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun screenshotNewAgent() {
        captureScreen("new_agent") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Nuevo agente") },
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    SectionTitle("1 · PC")
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = true, onClick = {}, label = { Text("PC Empresa") })
                        FilterChip(selected = false, onClick = {}, label = { Text("MacBook Pro") })
                    }

                    SectionTitle("2 · Carpeta")
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RpShapes.card) {
                        Column(Modifier.padding(14.dp)) {
                            Text("/home/drift/Workspace/RP/Rp-app", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    SectionTitle("3 · Lanzador")
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = true, onClick = {}, label = { Text("gentle-shell") })
                        FilterChip(selected = false, onClick = {}, label = { Text("herdr") })
                        FilterChip(selected = false, onClick = {}, label = { Text("tmux") })
                    }

                    SectionTitle("4 · Nombre y prompt inicial (opcional)")
                    OutlinedTextField(
                        value = "UI-Refresh",
                        onValueChange = {},
                        label = { Text("Nombre de la sesión") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    OutlinedTextField(
                        value = "Hacer un refactor completo de la UI manteniendo los temas",
                        onValueChange = {},
                        label = { Text("Prompt inicial") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        minLines = 3,
                    )

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {},
                        shape = RpShapes.pill,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                    ) {
                        Text("Crear agente", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
