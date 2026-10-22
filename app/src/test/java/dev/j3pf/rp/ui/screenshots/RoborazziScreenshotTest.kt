package dev.j3pf.rp.ui.screenshots

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
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
import dev.j3pf.rp.protocol.HostBackends
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshGroup
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MeshSubagent
import dev.j3pf.rp.protocol.MeshTodos
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
import dev.j3pf.rp.ui.components.LocalHighlightDispatcher
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
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rES-w411dp-h891dp-xxhdpi")
class RoborazziScreenshotTest {
    @org.junit.Before
    fun setUpLocale() {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
    }

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

    private val fixedTestTimestamp = 1776510000000L

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

    private val roborazziOptions =
        RoborazziOptions(
            compareOptions =
                RoborazziOptions.CompareOptions(
                    changeThreshold = 0f,
                ),
        )

    private fun captureScreen(
        screen: String,
        content: @Composable () -> Unit,
    ) {
        themes.forEach { (themeName, themeChoice) ->
            modes.forEach { (modeName, darkTheme) ->
                captureRoboImage(
                    filePath = "src/test/screenshots/${screen}_${themeName}_$modeName.png",
                    roborazziOptions = roborazziOptions,
                ) {
                    CompositionLocalProvider(
                        LocalReducedMotion provides true,
                        LocalHighlightDispatcher provides Dispatchers.Unconfined,
                    ) {
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

        val uiState =
            ChatUiState(
                agent = agentWorking,
                chat = ChatState(items = chatMessages),
                conn = ConnState.Connected,
            )

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
        val uiState =
            ChatUiState(
                agent = agentWorking,
                chat = ChatState(items = historyMessages),
                conn = ConnState.Connected,
                unreadCount = 5,
            )

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
        val (_, meshUiState) = build4AgentsScenario()
        val listState = meshUiState.copy(isListView = true)

        captureScreen("mesh") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    MeshScreenContent(
                        uiState = listState,
                        onOpenChat = {},
                    )
                }
            }
        }
    }

    // ───────────────────────────── 7. Mesh Diagram View ─────────────────────────────
    @Test
    fun screenshotMeshDiagram() {
        val (_, meshUiState) = build4AgentsScenario()

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

    @Test
    fun screenshotMeshDiagram1Agent() {
        val (_, meshUiState) = build1AgentScenario()

        captureScreen("mesh_diagram_1agent") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 0)
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

    @Test
    fun screenshotMeshDiagram12Agents() {
        val (_, meshUiState) = build12AgentsScenario()

        captureScreen("mesh_diagram_12agents") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 0)
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

    @Test
    @Config(qualifiers = "es-rES-w360dp-h640dp-xhdpi")
    fun screenshotMeshDiagram360dp() {
        val (snapshot, meshUiState) = build4AgentsScenario()
        val layout360 = MeshLayoutEngine().computeLayout(snapshot.nodes, snapshot.edges, width = 360f, height = 640f)
        val state360 = meshUiState.copy(layoutResult = layout360)

        captureScreen("mesh_diagram_360dp") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    MeshScreenContent(
                        uiState = state360,
                        onOpenChat = {},
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "es-rES-w800dp-h480dp-xhdpi")
    fun screenshotMeshDiagramLandscape() {
        val (snapshot, meshUiState) = build4AgentsScenario()
        val landscapeLayout = MeshLayoutEngine().computeLayout(snapshot.nodes, snapshot.edges, width = 800f, height = 240f)
        val landscapeState = meshUiState.copy(layoutResult = landscapeLayout)

        captureScreen("mesh_diagram_landscape") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    MeshScreenContent(
                        uiState = landscapeState,
                        onOpenChat = {},
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "es-rES-w800dp-h1280dp-xhdpi")
    fun screenshotMeshDiagramTablet() {
        val (snapshot, meshUiState) = build4AgentsScenario()
        val tabletLayout = MeshLayoutEngine().computeLayout(snapshot.nodes, snapshot.edges, width = 800f, height = 1280f)
        val tabletState = meshUiState.copy(layoutResult = tabletLayout)

        captureScreen("mesh_diagram_tablet") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 2)
                },
            ) { padding: PaddingValues ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    MeshScreenContent(
                        uiState = tabletState,
                        onOpenChat = {},
                    )
                }
            }
        }
    }

    @Test
    fun screenshotMeshDiagramReducedMotion() {
        val (_, meshUiState) = build4AgentsScenario()

        captureScreen("mesh_diagram_reduced_motion") {
            CompositionLocalProvider(LocalReducedMotion provides true) {
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
    }

    @Test
    fun screenshotMeshDiagramMultiRoot() {
        val (_, meshUiState) = buildMultiRootScenario()

        captureScreen("mesh_diagram_multiroot") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 1)
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

    @Test
    fun screenshotMeshDiagramDeepTree() {
        val (_, meshUiState) = buildDeepTreeScenario()

        captureScreen("mesh_diagram_deeptree") {
            Scaffold(
                bottomBar = {
                    AppBottomBar(selectedTab = AppTab.MESH, pending = 1)
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

    @Test
    fun screenshotMeshNodeDetailCollapsed() {
        val (snapshot, _) = build4AgentsScenario()
        val node = snapshot.nodes.first()
        val detail =
            dev.j3pf.rp.ui.mesh.MeshNodeDetail(
                node = node,
                recentComms =
                    listOf(
                        MeshComm(
                            commKey = "c1",
                            fromAgentId = node.agentId,
                            toAgentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                            fromName = "Rp-app",
                            toName = "Rp-relay",
                            action = CommAction.ASK,
                            text = "Validando endpoints de red",
                            at = fixedTestTimestamp - 30_000L,
                        ),
                    ),
            )

        captureScreen("mesh_node_detail_collapsed") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RpShapes.sheet,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                dev.j3pf.rp.ui.mesh.MeshNodeDetailContent(
                    detail = detail,
                    onDismiss = {},
                    onOpenChat = {},
                    onViewConversation = {},
                    initiallyExpandedTechnicalDetails = false,
                )
            }
        }
    }

    @Test
    fun screenshotMeshNodeDetailExpanded() {
        val (snapshot, _) = build4AgentsScenario()
        val node = snapshot.nodes.first()
        val detail =
            dev.j3pf.rp.ui.mesh.MeshNodeDetail(
                node = node,
                recentComms =
                    listOf(
                        MeshComm(
                            commKey = "c1",
                            fromAgentId = node.agentId,
                            toAgentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                            fromName = "Rp-app",
                            toName = "Rp-relay",
                            action = CommAction.ASK,
                            text = "Validando endpoints de red",
                            at = fixedTestTimestamp - 30_000L,
                        ),
                    ),
            )

        captureScreen("mesh_node_detail_expanded") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RpShapes.sheet,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                dev.j3pf.rp.ui.mesh.MeshNodeDetailContent(
                    detail = detail,
                    onDismiss = {},
                    onOpenChat = {},
                    onViewConversation = {},
                    initiallyExpandedTechnicalDetails = true,
                )
            }
        }
    }

    @Test
    fun screenshotMeshConversation() {
        val detail =
            dev.j3pf.rp.ui.mesh.MeshConversationDetail(
                agentA = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                agentB = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                agentAName = "Rp-app",
                agentBName = "Rp-relay",
                items =
                    listOf(
                        MeshComm(
                            commKey = "c1",
                            fromAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                            toAgentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                            fromName = "Rp-app",
                            toName = "Rp-relay",
                            action = CommAction.ASK,
                            text = "Iniciando refactor de la red de agentes",
                            at = fixedTestTimestamp - 60_000L,
                        ),
                        MeshComm(
                            commKey = "c2",
                            fromAgentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                            toAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                            fromName = "Rp-relay",
                            toName = "Rp-app",
                            action = CommAction.REPLY,
                            text = "Contrato de red validado y listo",
                            at = fixedTestTimestamp - 30_000L,
                        ),
                    ),
            )

        captureScreen("mesh_conversation") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RpShapes.sheet,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                dev.j3pf.rp.ui.mesh.MeshConversationContent(
                    detail = detail,
                    onDismiss = {},
                    onLoadOlder = {},
                )
            }
        }
    }

    private fun build4AgentsScenario(): Pair<MeshSnapshot, MeshUiState> {
        val nodes =
            listOf(
                MeshNode(
                    agentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    machineId = "m-omarchy",
                    machineName = "Omarchy",
                    machineColor = "#6750A4",
                    name = "01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4",
                    role = "orchestrator",
                    status = "working",
                    online = true,
                    cwd = "/home/drift/Workspace/RP/Rp-app",
                    gitBranch = "main",
                    activity = "Coordinando refactor de red",
                    todos =
                        dev.j3pf.rp.protocol
                            .MeshTodos(total = 6, done = 3, inProgress = 1, blocked = 0, pending = 2),
                    subagents =
                        listOf(
                            dev.j3pf.rp.protocol.MeshSubagent(
                                taskId = "t1",
                                agent = "Worker UI",
                                label = "Pruebas UI",
                                status = "working",
                            ),
                        ),
                ),
                MeshNode(
                    agentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                    machineId = "m-omarchy",
                    machineName = "Omarchy",
                    machineColor = "#6750A4",
                    name = "01a14fbb-44e3-765f-99e1-…",
                    role = "subordinate",
                    parentAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    status = "working",
                    online = true,
                    cwd = "/home/drift/Workspace/RP/Rp-relay",
                    gitBranch = "main",
                    activity = "Validando endpoints",
                    todos =
                        dev.j3pf.rp.protocol
                            .MeshTodos(total = 4, done = 2, inProgress = 1, blocked = 0, pending = 1),
                ),
                MeshNode(
                    agentId = "subagent-chat-01a14fbb-2812-7332",
                    machineId = "m-croma",
                    machineName = "Croma",
                    machineColor = "#7AA2F7",
                    name = "subagent-chat-01a14fbb-2812-7332",
                    role = "subordinate",
                    parentAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    status = "idle",
                    online = true,
                    cwd = "/home/drift/Workspace/RP",
                    gitBranch = "feat/mesh",
                ),
                MeshNode(
                    agentId = "node-croma-plugin",
                    machineId = "m-croma",
                    machineName = "Croma",
                    machineColor = "#7AA2F7",
                    name = "Worker Plugin",
                    role = "subordinate",
                    parentAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    status = "offline",
                    online = false,
                    cwd = "/home/drift/Workspace/RP/Rp-plugin",
                    model = "sonnet",
                ),
            )

        val edges =
            listOf(
                MeshEdge(
                    edgeId = "edge-1",
                    a = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    b = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                    count = 14,
                    active = true,
                    lastAction = "ask",
                    lastPreview = "Coordinando tareas de UI",
                ),
                MeshEdge(
                    edgeId = "edge-2",
                    a = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    b = "subagent-chat-01a14fbb-2812-7332",
                    count = 6,
                    active = false,
                    lastAction = "reply",
                ),
                MeshEdge(
                    edgeId = "edge-3",
                    a = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    b = "node-croma-plugin",
                    count = 1,
                    active = false,
                ),
            )

        val groups =
            listOf(
                MeshGroup(
                    groupId = "grp-1",
                    label = "01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4",
                    orchestratorAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                    memberAgentIds =
                        listOf(
                            "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                            "01a14fbb-44e3-765f-99e1-970655bf47bf",
                            "subagent-chat-01a14fbb-2812-7332",
                            "node-croma-plugin",
                        ),
                ),
            )

        val snapshot = MeshSnapshot(groups = groups, nodes = nodes, edges = edges)
        val layout = MeshLayoutEngine().computeLayout(nodes, edges, width = 411f, height = 750f)

        val comm =
            MeshComm(
                commKey = "comm-bubble-1",
                fromAgentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                toAgentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                fromName = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                toName = "01a14fbb-44e3-765f-99e1-…",
                action = CommAction.ASK,
                text = "Coordinando refactor de UI",
            )

        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                layoutResult = layout,
                isListView = false,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 4),
                        MeshMachineChip(machineId = "m-omarchy", name = "Omarchy", color = "#6750A4", agentCount = 4),
                        MeshMachineChip(machineId = "m-croma", name = "Croma", color = "#7AA2F7", agentCount = 3),
                    ),
                groupChips =
                    listOf(
                        MeshGroupChip(groupId = "grp-1", label = "Rp-app", memberCount = 4),
                    ),
                liveComms = listOf(comm),
                activeBubbles = listOf(LiveCommBubble(comm = comm, expiresAtMs = Long.MAX_VALUE)),
            )
        return snapshot to uiState
    }

    private fun build1AgentScenario(): Pair<MeshSnapshot, MeshUiState> {
        val node =
            MeshNode(
                agentId = "01a14fbb-solo",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Solo Worker",
                role = "solo",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
                gitBranch = "main",
                activity = "Ejecutando tareas",
                todos =
                    dev.j3pf.rp.protocol
                        .MeshTodos(total = 5, done = 2, inProgress = 1, blocked = 0, pending = 2),
            )
        val snapshot = MeshSnapshot(nodes = listOf(node))
        val layout = MeshLayoutEngine().computeLayout(listOf(node), emptyList(), width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                layoutResult = layout,
                isListView = false,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 1),
                        MeshMachineChip(machineId = "m-omarchy", name = "Omarchy", color = "#6750A4", agentCount = 1),
                    ),
                groupChips =
                    listOf(
                        MeshGroupChip(groupId = "solo", label = "Sin grupo", isSolo = true, memberCount = 1),
                    ),
            )
        return snapshot to uiState
    }

    private fun build12AgentsScenario(): Pair<MeshSnapshot, MeshUiState> {
        val root =
            MeshNode(
                agentId = "orch-root",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Orquestador Maestro",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP",
                gitBranch = "main",
                activity = "Gestionando enjambre",
                todos =
                    dev.j3pf.rp.protocol
                        .MeshTodos(total = 12, done = 7, inProgress = 1, blocked = 0, pending = 4),
            )
        val subordinates =
            (1..11).map { i ->
                val status =
                    when (i % 3) {
                        0 -> "working"
                        1 -> "idle"
                        else -> if (i == 11) "offline" else "working"
                    }
                MeshNode(
                    agentId = "sub-$i",
                    machineId = if (i % 2 == 0) "m-omarchy" else "m-croma",
                    machineName = if (i % 2 == 0) "Omarchy" else "Croma",
                    machineColor = if (i % 2 == 0) "#6750A4" else "#7AA2F7",
                    name = "Worker-$i",
                    role = "subordinate",
                    parentAgentId = "orch-root",
                    status = status,
                    online = status != "offline",
                    cwd = "/home/drift/Workspace/RP/sub-$i",
                    gitBranch = "feat/sub-$i",
                    activity = if (status == "working") "Tarea $i" else null,
                )
            }
        val allNodes = listOf(root) + subordinates
        val edges =
            subordinates.mapIndexed { idx, sub ->
                MeshEdge(
                    edgeId = "edge-$idx",
                    a = "orch-root",
                    b = sub.agentId,
                    count = idx + 1,
                    active = sub.status == "working",
                    lastAction = "send",
                )
            }
        val snapshot = MeshSnapshot(nodes = allNodes, edges = edges)
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 411f, height = 750f)
        val comm12 =
            MeshComm(
                commKey = "comm-12",
                fromAgentId = "orch-root",
                toAgentId = "sub-1",
                fromName = "Orquestador Maestro",
                toName = "Worker-1",
                action = CommAction.SEND,
                text = "Coordinando enjambre de 12 agentes",
            )
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                layoutResult = layout,
                isListView = false,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 12),
                        MeshMachineChip(machineId = "m-omarchy", name = "Omarchy", color = "#6750A4", agentCount = 6),
                        MeshMachineChip(machineId = "m-croma", name = "Croma", color = "#7AA2F7", agentCount = 6),
                    ),
                groupChips =
                    listOf(
                        MeshGroupChip(groupId = "grp-all", label = "Orquestador Maestro", memberCount = 12),
                    ),
                liveComms = listOf(comm12),
                activeBubbles = listOf(LiveCommBubble(comm = comm12, expiresAtMs = Long.MAX_VALUE)),
            )
        return snapshot to uiState
    }

    private fun buildMultiRootScenario(): Pair<MeshSnapshot, MeshUiState> {
        val rootApp =
            MeshNode(
                agentId = "orch-app",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Orquestador App",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
                gitBranch = "main",
                activity = "Coordinando UI",
                todos = MeshTodos(total = 5, done = 3, inProgress = 1, blocked = 0, pending = 1),
            )
        val workerUI =
            MeshNode(
                agentId = "worker-ui",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Worker UI",
                role = "subordinate",
                parentAgentId = "orch-app",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app/ui",
            )
        val rootRelay =
            MeshNode(
                agentId = "orch-relay",
                machineId = "m-croma",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Orquestador Relay",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-relay",
                gitBranch = "main",
                activity = "Gestionando sockets",
                todos = MeshTodos(total = 4, done = 2, inProgress = 1, blocked = 0, pending = 1),
            )
        val workerApi =
            MeshNode(
                agentId = "worker-api",
                machineId = "m-croma",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Worker API",
                role = "subordinate",
                parentAgentId = "orch-relay",
                status = "idle",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-relay/api",
            )
        val soloNode =
            MeshNode(
                agentId = "solo-plugin",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Worker Plugin",
                role = "solo",
                status = "idle",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-plugin",
            )
        val allNodes = listOf(rootApp, workerUI, rootRelay, workerApi, soloNode)
        val edges =
            listOf(
                MeshEdge("edge-app", "orch-app", "worker-ui", count = 3, active = true),
                MeshEdge("edge-relay", "orch-relay", "worker-api", count = 2, active = false),
                MeshEdge("edge-peer", "orch-app", "orch-relay", count = 12, active = true, lastAction = "ask"),
            )
        val snapshot = MeshSnapshot(nodes = allNodes, edges = edges)
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                layoutResult = layout,
                machineChips =
                    listOf(
                        MeshMachineChip(machineId = null, name = "Todas", color = "", agentCount = 5),
                        MeshMachineChip(machineId = "m-omarchy", name = "Omarchy", color = "#6750A4", agentCount = 3),
                        MeshMachineChip(machineId = "m-croma", name = "Croma", color = "#7AA2F7", agentCount = 2),
                    ),
            )
        return snapshot to uiState
    }

    private fun buildDeepTreeScenario(): Pair<MeshSnapshot, MeshUiState> {
        val ceo =
            MeshNode(
                agentId = "ceo",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "CEO Orquestador",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP",
                activity = "Estrategia de arquitectura",
            )
        val dirProd =
            MeshNode(
                agentId = "dir-prod",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Director Producto",
                role = "subordinate",
                parentAgentId = "ceo",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
                subagents =
                    listOf(
                        dev.j3pf.rp.protocol
                            .MeshSubagent("s1", "UI Lead", "Sprint 1", "working"),
                    ),
            )
        val dirInfra =
            MeshNode(
                agentId = "dir-infra",
                machineId = "m-croma",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Director Infraestructura",
                role = "subordinate",
                parentAgentId = "ceo",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-relay",
                subagents =
                    listOf(
                        dev.j3pf.rp.protocol
                            .MeshSubagent("s2", "Infra Lead", "Sockets", "working"),
                    ),
            )
        val teamLead =
            MeshNode(
                agentId = "lead-dev",
                machineId = "m-omarchy",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Líder Desarrollo",
                role = "subordinate",
                parentAgentId = "dir-prod",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app/dev",
                subagents =
                    listOf(
                        dev.j3pf.rp.protocol
                            .MeshSubagent("s3", "Scrum", "Tasks", "working"),
                    ),
            )
        val leaves =
            (1..4).map { i ->
                MeshNode(
                    agentId = "dev-leaf-$i",
                    machineId = "m-omarchy",
                    machineName = "Omarchy",
                    machineColor = "#6750A4",
                    name = "Especialista $i",
                    role = "subordinate",
                    parentAgentId = "lead-dev",
                    status = if (i % 2 == 0) "working" else "idle",
                    online = true,
                    cwd = "/home/drift/Workspace/RP/Rp-app/spec-$i",
                )
            }
        val allNodes = listOf(ceo, dirProd, dirInfra, teamLead) + leaves
        val edges =
            listOf(
                MeshEdge("e-p1", "ceo", "dir-prod"),
                MeshEdge("e-p2", "ceo", "dir-infra"),
                MeshEdge("e-p3", "dir-prod", "lead-dev"),
            ) + leaves.map { MeshEdge("e-l-${it.agentId}", "lead-dev", it.agentId) }
        val snapshot = MeshSnapshot(nodes = allNodes, edges = edges)
        val layout = MeshLayoutEngine().computeLayout(allNodes, edges, width = 411f, height = 750f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = snapshot,
                layoutResult = layout,
            )
        return snapshot to uiState
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
        val sampleHost =
            HostInfo(
                machineId = "m-work",
                hostname = "PC Empresa",
                roots = listOf("/home/drift/Workspace"),
                online = true,
                backends =
                    HostBackends(
                        herdr = "herdr",
                        tmux = "tmux",
                    ),
                defaultBackend = "herdr",
                launchers = listOf("gentle-shell", "pi"),
                defaultLauncher = "gentle-shell",
            )

        val uiState =
            NewAgentUi(
                machines = listOf(machineWork, machinePersonal),
                host = sampleHost,
                form =
                    NewAgentForm(
                        machineId = "m-work",
                        path = "/home/drift/Workspace/RP/Rp-app",
                        dirs = listOf("app", "protocol", "gradle"),
                        launcher = "gentle-shell",
                        backend = "herdr",
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
