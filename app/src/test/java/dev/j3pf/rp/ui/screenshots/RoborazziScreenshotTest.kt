package dev.j3pf.rp.ui.screenshots

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.CommAction
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshGroup
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.ModelInfo
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.QuotaWindow
import dev.j3pf.rp.ui.agents.AgentsContent
import dev.j3pf.rp.ui.agents.AgentsUiState
import dev.j3pf.rp.ui.agents.MachineGroup
import dev.j3pf.rp.ui.agents.sortAgentsWithinGroup
import dev.j3pf.rp.ui.chat.ChatContent
import dev.j3pf.rp.ui.chat.ChatUiState
import dev.j3pf.rp.ui.chat.MessageList
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.mesh.LiveCommBubble
import dev.j3pf.rp.ui.mesh.MeshGroupChip
import dev.j3pf.rp.ui.mesh.MeshMachineChip
import dev.j3pf.rp.ui.mesh.MeshScreenContent
import dev.j3pf.rp.ui.mesh.MeshUiState
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.nav.AppBottomBar
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.newagent.NewAgentContent
import dev.j3pf.rp.ui.newagent.NewAgentForm
import dev.j3pf.rp.ui.newagent.NewAgentUi
import dev.j3pf.rp.ui.onboarding.OnboardingScreen
import dev.j3pf.rp.ui.pending.PendingContent
import dev.j3pf.rp.ui.pending.PendingItem
import dev.j3pf.rp.ui.pending.PendingUiState
import dev.j3pf.rp.ui.promptsheet.PromptContent
import dev.j3pf.rp.ui.settings.HealthState
import dev.j3pf.rp.ui.settings.SettingsContent
import dev.j3pf.rp.ui.settings.SettingsUiState
import dev.j3pf.rp.ui.theme.LocalReducedMotion
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
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
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
        val groups =
            listOf(
                MachineGroup(machineWork, sortAgentsWithinGroup(listOf(agentWorking, agentIdle))),
                MachineGroup(machinePersonal, sortAgentsWithinGroup(listOf(agentWaiting))),
            )
        val uiState =
            AgentsUiState(
                machines = listOf(machineWork, machinePersonal),
                allAgents = sampleAgents,
                groups = groups,
                selected = "all",
            )

        captureScreen("agents_populated") {
            AgentsContent(
                state = uiState,
                conn = ConnState.Connected,
                bottomBar = {
                    Column {
                        QuotaDynamicIsland(
                            machines = uiState.machines,
                            selectedMachineId = null,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        AppBottomBar(selectedTab = AppTab.AGENTS, pending = 2)
                    }
                },
                onOpenChat = {},
                onNewAgent = {},
                onSelect = {},
                onRefresh = {},
                onResume = { _: AgentSummary, _: String -> },
                onForget = {},
            )
        }
    }

    // ───────────────────────────── 2. Agents Empty ─────────────────────────────
    @Test
    fun screenshotAgentsEmpty() {
        val uiState =
            AgentsUiState(
                machines = listOf(machineWork, machinePersonal),
                allAgents = emptyList(),
                groups = emptyList(),
                selected = "all",
            )

        captureScreen("agents_empty") {
            AgentsContent(
                state = uiState,
                conn = ConnState.Connected,
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.AGENTS, pending = 0)
                },
                onOpenChat = {},
                onNewAgent = {},
                onSelect = {},
                onRefresh = {},
                onResume = { _: AgentSummary, _: String -> },
                onForget = {},
            )
        }
    }

    // ───────────────────────────── 3. Agents Skeleton ─────────────────────────────
    @Test
    fun screenshotAgentsSkeleton() {
        val uiState =
            AgentsUiState(
                machines = listOf(machineWork, machinePersonal),
                allAgents = emptyList(),
                groups = emptyList(),
                selected = "all",
                isLoading = true,
                refreshing = false,
            )

        captureScreen("agents_skeleton") {
            AgentsContent(
                state = uiState,
                conn = ConnState.Connected,
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.AGENTS, pending = 0)
                },
                onOpenChat = {},
                onNewAgent = {},
                onSelect = {},
                onRefresh = {},
                onResume = { _: AgentSummary, _: String -> },
                onForget = {},
            )
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

        val uiState = ChatUiState(agent = agentWorking, chat = ChatState(items = chatMessages))

        captureScreen("chat_streaming") {
            ChatContent(
                ui = uiState,
                text = "",
                onText = {},
                onBack = {},
                onSend = {},
                onChoice = {},
                onWorkingDelivery = {},
                onPicked = {},
                onRemoveAttachment = {},
            )
        }
    }

    // ───────────────────────────── 5. Chat Scrolled Up (Jump Button Visible) ─────────────────────────────
    @Test
    fun screenshotChatScrolledUp() {
        val historyMessages =
            (1..20).map { i ->
                UserMsg(key = "hist-$i", text = "Mensaje histórico #$i en la conversación previa", at = 1000L + i)
            }
        val uiState = ChatUiState(agent = agentWorking, chat = ChatState(items = historyMessages))

        captureScreen("chat_scrolled_up") {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 6, initialFirstVisibleItemScrollOffset = 50)
            ChatContent(
                ui = uiState,
                listState = listState,
            )
        }
    }

    // ───────────────────────────── 6. Mesh List View ─────────────────────────────
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
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    MeshScreenContent(
                        uiState = meshUiState,
                        onOpenChat = {},
                    )
                }
            }
        }
    }

    // ───────────────────────────── 7. Mesh Diagram View ─────────────────────────────
    @Test
    fun screenshotMeshDiagram() {
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

        val edges =
            listOf(
                MeshEdge(
                    edgeId = "edge-1",
                    a = "node-orch",
                    b = "node-sub1",
                    count = 12,
                    active = true,
                    lastAction = "ask",
                    lastPreview = "Validando pruebas de UI",
                ),
                MeshEdge(
                    edgeId = "edge-2",
                    a = "node-orch",
                    b = "node-sub2",
                    count = 5,
                    active = false,
                    lastAction = "reply",
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

        val snapshot = MeshSnapshot(groups = groups, nodes = nodes, edges = edges)
        val layout = MeshLayoutEngine().computeLayout(nodes, edges)

        val comm =
            MeshComm(
                commKey = "comm-bubble-1",
                fromAgentId = "node-orch",
                toAgentId = "node-sub1",
                fromName = "Orquestador",
                toName = "Worker UI",
                action = CommAction.ASK,
                text = "Coordinando tareas de UI",
            )

        val meshUiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                layoutResult = layout,
                isListView = false,
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
                liveComms = listOf(comm),
                activeBubbles = listOf(LiveCommBubble(comm = comm, expiresAtMs = System.currentTimeMillis() + 6000L)),
            )

        captureScreen("mesh_diagram") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    MeshScreenContent(
                        uiState = meshUiState,
                        onOpenChat = {},
                    )
                }
            }
        }
    }

    // ───────────────────────────── 8. Pending ─────────────────────────────
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

        val uiState =
            PendingUiState(
                machines = listOf(machineWork, machinePersonal),
                agents = sampleAgents,
                items = pendingItems,
                selected = "all",
            )

        captureScreen("pending") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.PENDING, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    PendingContent(
                        state = uiState,
                        onOpenPrompt = {},
                        onSelect = {},
                    )
                }
            }
        }
    }

    // ───────────────────────────── 9. Settings ─────────────────────────────
    @Test
    fun screenshotSettings() {
        val uiState =
            SettingsUiState(
                session =
                    Session(
                        relayId = "r1",
                        relayUrl = "https://relay.j3pf.dev",
                        relayName = "Relay Principal",
                        deviceId = "d1",
                        deviceToken = "tok",
                        pushKey = "key",
                    ),
                settings = AppSettings(appTheme = "auto"),
                conn = ConnState.Connected,
                health = HealthState(notificationsEnabled = true, batteryExcluded = true),
            )

        captureScreen("settings") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.SETTINGS, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    SettingsContent(
                        state = uiState,
                        onNav = {},
                        onSelectTheme = {},
                        onLogout = {},
                    )
                }
            }
        }
    }

    // ───────────────────────────── 10. New Agent ─────────────────────────────
    @Test
    fun screenshotNewAgent() {
        val uiState =
            NewAgentUi(
                machines = listOf(machineWork, machinePersonal),
                form =
                    NewAgentForm(
                        machineId = "m-work",
                        path = "/home/drift/Workspace/RP/Rp-app",
                        launcher = "gentle-shell",
                        name = "UI-Refresh",
                        initialPrompt = "Hacer un refactor completo de la UI manteniendo los temas",
                    ),
            )

        captureScreen("new_agent") {
            NewAgentContent(
                ui = uiState,
                onBack = {},
                onPickMachine = {},
                onOpenDir = {},
                onEnterDir = {},
                onUpDir = {},
                canGoUp = true,
                onSetLauncher = {},
                onSetBackend = {},
                onSetName = {},
                onSetPrompt = {},
                onCreate = {},
            )
        }
    }

    // ───────────────────────────── 11. Onboarding ─────────────────────────────
    @Test
    fun screenshotOnboarding() {
        captureScreen("onboarding") {
            OnboardingScreen(onStart = {})
        }
    }

    // ───────────────────────────── 12. Prompt Sheet (Open) ─────────────────────────────
    @Test
    fun screenshotPromptSheet() {
        val criticalPrompt =
            Prompt(
                promptId = "prompt-crit-1",
                agentId = "a-1",
                title = "Autorizar ejecución de migración de base de datos",
                message = "El agente solicita ejecutar: ./gradlew flywayMigrate -Denv=prod",
                category = PromptCategory.PERMISSION,
                danger = Danger.HIGH,
            )

        captureScreen("prompt_sheet") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RpShapes.sheet,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Column(Modifier.padding(20.dp)) {
                    PromptContent(
                        prompt = criticalPrompt,
                        busy = false,
                        onAnswer = {},
                    )
                }
            }
        }
    }
}
