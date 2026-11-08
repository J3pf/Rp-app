package dev.j3pf.rp.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.ui.agents.AgentsContent
import dev.j3pf.rp.ui.agents.AgentsUiState
import dev.j3pf.rp.ui.chat.AutoDeliveryBanner
import dev.j3pf.rp.ui.chat.ChatComposer
import dev.j3pf.rp.ui.chat.ChatTopBar
import dev.j3pf.rp.ui.chat.DeliveryChoice
import dev.j3pf.rp.ui.chat.InfoDialog
import dev.j3pf.rp.ui.chat.MessageList
import dev.j3pf.rp.ui.chat.TodoBanner
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.diff.DiffSheet
import dev.j3pf.rp.ui.mesh.MeshScreenContent
import dev.j3pf.rp.ui.nav.AboutRoute
import dev.j3pf.rp.ui.nav.AddMachineRoute
import dev.j3pf.rp.ui.nav.AppBottomBar
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.nav.BatteryRoute
import dev.j3pf.rp.ui.nav.ChatRoute
import dev.j3pf.rp.ui.nav.DevicesRoute
import dev.j3pf.rp.ui.nav.MachinesRoute
import dev.j3pf.rp.ui.nav.MeshRoute
import dev.j3pf.rp.ui.nav.NewAgentRoute
import dev.j3pf.rp.ui.nav.NotificationSettingsRoute
import dev.j3pf.rp.ui.nav.OnboardingRoute
import dev.j3pf.rp.ui.nav.PairRoute
import dev.j3pf.rp.ui.nav.PendingRoute
import dev.j3pf.rp.ui.nav.ScanRoute
import dev.j3pf.rp.ui.nav.SecuritySettingsRoute
import dev.j3pf.rp.ui.nav.SettingsRoute
import dev.j3pf.rp.ui.newagent.HerdrPlacementSection
import dev.j3pf.rp.ui.newagent.NewAgentContent
import dev.j3pf.rp.ui.newagent.NewAgentUi
import dev.j3pf.rp.ui.onboarding.OnboardingContent
import dev.j3pf.rp.ui.onboarding.PairContent
import dev.j3pf.rp.ui.onboarding.QrScannerView
import dev.j3pf.rp.ui.pending.PendingContent
import dev.j3pf.rp.ui.pending.PendingUiState
import dev.j3pf.rp.ui.promptsheet.PromptContent
import dev.j3pf.rp.ui.promptsheet.PromptSheetChrome
import dev.j3pf.rp.ui.settings.AboutContent
import dev.j3pf.rp.ui.settings.AddMachineContent
import dev.j3pf.rp.ui.settings.BatteryContent
import dev.j3pf.rp.ui.settings.DevicesContent
import dev.j3pf.rp.ui.settings.HealthState
import dev.j3pf.rp.ui.settings.MachinesContent
import dev.j3pf.rp.ui.settings.NotificationSettingsContent
import dev.j3pf.rp.ui.settings.SecurityContent
import dev.j3pf.rp.ui.settings.SettingsContent
import dev.j3pf.rp.ui.settings.SettingsUiState
import dev.j3pf.rp.ui.theme.RpTheme
import dev.j3pf.rp.ui.theme.resolveThemeChoice
import kotlinx.coroutines.launch

@Composable
fun RemotePiApp(controller: SharedAppController? = null) {
    val scope = rememberCoroutineScope()
    val appController = controller ?: remember(scope) { createPlatformAppController(scope) }

    val settings by appController.settings.collectAsState()
    val machines by appController.machines.collectAsState()
    val agents by appController.agents.collectAsState()
    val conn by appController.connState.collectAsState()
    val route by appController.currentRoute.collectAsState()
    val pairState by appController.pairState.collectAsState()
    val agentsState by appController.agentsUiState.collectAsState(initial = AgentsUiState())
    val pendingState by appController.pendingUiState.collectAsState(initial = PendingUiState())
    val meshState by appController.meshState.collectAsState()
    val settingsState by appController.settingsUiState.collectAsState(initial = SettingsUiState())
    val newAgentUi by appController.newAgentUiState.collectAsState(initial = NewAgentUi())
    val devices by appController.devices.collectAsState()
    val pairCode by appController.pairCode.collectAsState()
    val openPrompts by appController.openPrompts.collectAsState()
    val activePromptId by appController.activePromptId.collectAsState()
    val busyPromptId by appController.busyPromptId.collectAsState()
    val promptSheetState by appController.promptSheetState.collectAsState()
    val promptSheetError by appController.promptSheetError.collectAsState()
    val promptSheetAuthMessage by appController.promptSheetAuthMessage.collectAsState()
    val pendingForgetAgentId by appController.pendingForgetAgentId.collectAsState()
    val actionMessage by appController.actionMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        appController.consumeActionMessage()
    }

    val themeChoice =
        remember(settings.appTheme, machines, agents) {
            resolveThemeChoice(settings.appTheme, machines, agents)
        }

    RpTheme(themeChoice = themeChoice) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val current = route) {
                is OnboardingRoute -> {
                    OnboardingContent(
                        onStart = { appController.navigateTo(PairRoute()) },
                    )
                }

                is PairRoute -> {
                    PairContent(
                        state = pairState,
                        initialUrl = current.url,
                        initialCode = current.code,
                        onScan = { appController.navigateTo(ScanRoute) },
                        onPair = appController::pair,
                        onClearError = appController::clearPairError,
                    )
                }

                is ScanRoute -> {
                    QrScannerView(
                        onResult = appController::onQrScanned,
                        onCancel = { appController.navigateBack() },
                    )
                }

                is ChatRoute -> {
                    SharedChatScreen(
                        agentId = current.agentId,
                        appController = appController,
                        snackbarHostState = snackbarHostState,
                    )
                }

                is NewAgentRoute -> {
                    val canGoUp =
                        remember(newAgentUi.form.path, newAgentUi.host) {
                            val p = newAgentUi.form.path.trimEnd('/', '\\')
                            val roots = newAgentUi.host?.roots.orEmpty()
                            p.isNotBlank() &&
                                roots.none { it.trimEnd('/', '\\') == p } &&
                                (p.contains('/') || p.contains('\\'))
                        }
                    NewAgentContent(
                        ui = newAgentUi,
                        onBack = { appController.navigateBack() },
                        onPickMachine = appController::pickNewAgentMachine,
                        onOpenDir = appController::openNewAgentDir,
                        onEnterDir = appController::enterNewAgentSubDir,
                        onUpDir = appController::upNewAgentDir,
                        canGoUp = canGoUp,
                        onSetLauncher = { l -> appController.updateNewAgentForm { it.copy(launcher = l) } },
                        onSetBackend = { b -> appController.updateNewAgentForm { it.copy(backend = b) } },
                        onSetName = { n -> appController.updateNewAgentForm { it.copy(name = n) } },
                        onSetPrompt = { p -> appController.updateNewAgentForm { it.copy(initialPrompt = p) } },
                        onCreate = appController::createNewAgent,
                        herdrSection = {
                            HerdrPlacementSection(
                                ui = newAgentUi,
                                onSetPlacement = appController::setNewAgentPlacement,
                                onSetWorkspace = { id -> appController.updateNewAgentForm { it.copy(workspaceId = id) } },
                                onSetPane = { id -> appController.updateNewAgentForm { it.copy(paneId = id) } },
                                onSetDirection = { d -> appController.updateNewAgentForm { it.copy(direction = d) } },
                                onRefreshLayout = appController::loadNewAgentHerdrLayout,
                            )
                        },
                        snackbarHostState = snackbarHostState,
                    )
                }

                is DevicesRoute -> {
                    DevicesContent(
                        devices = devices,
                        onBack = { appController.navigateBack() },
                        onRevoke = appController::revokeDevice,
                    )
                }

                is MachinesRoute -> {
                    MachinesContent(
                        machines = machines,
                        onBack = { appController.navigateBack() },
                        onEditMachine = appController::editMachine,
                        onRevokeMachine = appController::revokeMachine,
                    )
                }

                is AddMachineRoute -> {
                    val clipboard = LocalClipboardManager.current
                    AddMachineContent(
                        relayUrl = settingsState.session?.relayUrl.orEmpty(),
                        pairCode = pairCode,
                        onBack = { appController.navigateBack() },
                        onCopyCommand = { cmd -> clipboard.setText(AnnotatedString(cmd)) },
                        onNewCode = appController::generatePairCode,
                    )
                }

                is NotificationSettingsRoute -> {
                    NotificationSettingsContent(
                        health = HealthState(),
                        settings = settings,
                        machines = machines,
                        agents = agents,
                        onBack = { appController.navigateBack() },
                        onOpenSystemSettings = {},
                        onSendTestNotification = appController::sendTestNotification,
                        onSetDirectPush = appController::setDirectPush,
                        onSetCategory = appController::setCategoryEnabled,
                        onSetCriticalAlways = appController::setCriticalAlways,
                        onUnmuteAgent = { id -> appController.setAgentMuted(id, false) },
                        onSetMachineMute = appController::setMachineMute,
                        onSetQuietHours = appController::setMachineQuietHours,
                        onSetMachineCategory = appController::setMachineCategory,
                    )
                }

                is SecuritySettingsRoute -> {
                    SecurityContent(
                        settings = settings,
                        onBack = { appController.navigateBack() },
                        onSetRequireUnlock = appController::setRequireUnlock,
                        onSetAppLock = appController::setAppLock,
                        onSetFlagSecure = appController::setFlagSecure,
                    )
                }

                is BatteryRoute -> {
                    BatteryContent(
                        batteryExcluded = true,
                        onBack = { appController.navigateBack() },
                        onRequestIgnoreBatteryOptimizations = {},
                        onOpenBatterySettings = {},
                    )
                }

                is AboutRoute -> {
                    AboutContent(
                        versionName = appController.appVersion,
                        versionCode = "1",
                        flavor = "APNs",
                        onBack = { appController.navigateBack() },
                    )
                }

                else -> {
                    val selectedTab =
                        when (current) {
                            is MeshRoute -> AppTab.MESH
                            is PendingRoute -> AppTab.PENDING
                            is SettingsRoute -> AppTab.SETTINGS
                            else -> AppTab.AGENTS
                        }
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (selectedTab == AppTab.AGENTS) {
                                    QuotaDynamicIsland(
                                        machines = agentsState.machines,
                                        selectedMachineId =
                                            if (agentsState.selected == "all") null else agentsState.selected,
                                    )
                                }
                                AppBottomBar(
                                    selectedTab = selectedTab,
                                    pending = openPrompts.size,
                                    onSelectTab = appController::selectTab,
                                )
                            }
                        },
                    ) { innerPadding ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                        ) {
                            when (selectedTab) {
                                AppTab.AGENTS -> {
                                    AgentsContent(
                                        state = agentsState,
                                        conn = conn,
                                        snackbarHostState = snackbarHostState,
                                        bottomBar = {},
                                        onOpenChat = { id -> appController.navigateTo(ChatRoute(id)) },
                                        onNewAgent = { appController.navigateTo(NewAgentRoute) },
                                        onSelect = appController::selectContext,
                                        onRefresh = appController::refreshAll,
                                        onResume = { agent, _ -> appController.resumeAgent(agent) },
                                        onForget = appController::requestForgetAgent,
                                    )
                                }

                                AppTab.MESH -> {
                                    MeshScreenContent(
                                        uiState = meshState.copy(isConnected = conn == ConnState.Connected),
                                        onOpenChat = { id -> appController.navigateTo(ChatRoute(id)) },
                                        onToggleListView = appController::toggleMeshListView,
                                        onSelectMachine = appController::selectMeshMachine,
                                        onSelectGroup = appController::selectMeshGroup,
                                        onNodeClick = appController::selectMeshNode,
                                        onEdgeClick = appController::onMeshEdgeClick,
                                        onViewportSizeChanged = appController::onMeshViewportSizeChanged,
                                        onDismissNodeDetail = appController::dismissMeshNodeDetail,
                                        onViewConversation = appController::openMeshConversation,
                                        onDismissConversation = appController::dismissMeshConversation,
                                        onLoadMoreConversation = appController::loadMoreMeshConversation,
                                        onRetry = { appController.refreshMesh() },
                                    )
                                }

                                AppTab.PENDING -> {
                                    PendingContent(
                                        state = pendingState,
                                        onOpenPrompt = appController::openPromptSheet,
                                        onSelect = appController::selectContext,
                                    )
                                }

                                AppTab.SETTINGS -> {
                                    SettingsContent(
                                        state = settingsState,
                                        onNav = appController::navigateSettingsDest,
                                        onSelectTheme = appController::setTheme,
                                        onLogout = appController::logout,
                                        onSelectFontScale = appController::setChatFontScale,
                                        onSelectMessageSpacing = appController::setChatMessageSpacing,
                                        versionName = appController.appVersion,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (pendingForgetAgentId != null) {
                SharedAgentCloseConfirmationDialog(
                    onConfirm = {
                        val closingChatAgent = (route as? ChatRoute)?.agentId == pendingForgetAgentId
                        appController.confirmForgetAgent {
                            if (closingChatAgent) {
                                appController.navigateBack()
                            }
                        }
                    },
                    onDismiss = appController::cancelForgetAgent,
                )
            }

            val promptId = activePromptId
            if (promptId != null) {
                val activePrompt = openPrompts.firstOrNull { it.promptId == promptId }
                PromptSheetChrome(
                    state = promptSheetState,
                    onDismissRequest = appController::dismissPromptSheet,
                    errorMessage = promptSheetError,
                    authMessage = promptSheetAuthMessage,
                ) {
                    if (activePrompt != null) {
                        PromptContent(
                            prompt = activePrompt,
                            busy = busyPromptId == activePrompt.promptId,
                            onAnswer = { ans -> appController.answerPrompt(activePrompt.promptId, ans) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedAgentCloseConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar agente") },
        text = { Text("¿Quieres cerrar este agente o archivar su historial si ya no tiene una ventana activa?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Cerrar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun SharedChatScreen(
    agentId: String,
    appController: SharedAppController,
    snackbarHostState: SnackbarHostState,
) {
    val chatSession = remember(agentId) { appController.chatSessions.open(agentId) }
    DisposableEffect(agentId) {
        onDispose {
            appController.chatSessions.release(agentId)
        }
    }

    val chatState by chatSession.state.collectAsState()
    val syncing by chatSession.syncing.collectAsState()
    val agents by appController.agents.collectAsState()
    val settings by appController.settings.collectAsState()
    val conn by appController.connState.collectAsState()
    val busyPromptId by appController.busyPromptId.collectAsState()
    val showDiff by appController.showDiffSheet.collectAsState()
    val diffResult by appController.diffResult.collectAsState()
    val selectedDiffFile by appController.selectedDiffFile.collectAsState()
    val diffLoading by appController.diffLoading.collectAsState()

    val agent = agents.firstOrNull { it.agentId == agentId }
    var text by rememberSaveable(agentId) { mutableStateOf("") }
    var choice by rememberSaveable(agentId) { mutableStateOf(DeliveryChoice.AUTO) }
    var workingDelivery by rememberSaveable(agentId) { mutableStateOf(Delivery.FOLLOW_UP) }
    var showInfo by remember { mutableStateOf(false) }

    val working = chatState.working || agent?.status == "working"
    val muted = agentId in settings.mutedAgents

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ChatTopBar(
                    agent = agent,
                    sessionName = chatState.sessionName ?: agent?.sessionName,
                    model = chatState.model ?: agent?.model?.label,
                    thinkingLevel = chatState.thinkingLevel,
                    working = working,
                    diffFileCount = diffResult?.files?.size ?: 0,
                    chatFontScale = settings.chatFontScale,
                    chatMessageSpacing = settings.chatMessageSpacing,
                    muted = muted,
                    readOnly = agent?.online == false,
                    onBack = { appController.navigateBack() },
                    onAbort = { chatSession.abort() },
                    onOpenDiff = { appController.openDiffSheet(agent) },
                    onFontScaleChange = appController::setChatFontScale,
                    onMessageSpacingChange = appController::setChatMessageSpacing,
                    onInfo = { showInfo = true },
                    onToggleMute = { appController.setAgentMuted(agentId, !muted) },
                    onQuit = { appController.quitAgent(chatSession) },
                    onForget = { appController.requestForgetAgent(agentId) },
                )
                if (agent?.gentleMode == "auto") {
                    AutoDeliveryBanner()
                }
                if (chatState.todos.isNotEmpty()) {
                    TodoBanner(todos = chatState.todos)
                }
            }
        },
        bottomBar = {
            ChatComposer(
                agent = agent,
                working = working,
                text = text,
                onTextChange = { text = it },
                attachments = emptyList(),
                canSend = true,
                sending = false,
                choice = choice,
                onChoice = { choice = it },
                workingDelivery = workingDelivery,
                onWorkingDelivery = { workingDelivery = it },
                onPickDocuments = {},
                onPickImages = {},
                onCameraCapture = {},
                onRemoveAttachment = {},
                onRetryAttachment = {},
                onSend = {
                    val msg = text
                    appController.sendChatMessage(
                        session = chatSession,
                        text = msg,
                        choice = choice,
                        workingDelivery = workingDelivery,
                        working = working,
                        agent = agent,
                        onSent = { text = "" },
                    )
                },
                isVoiceAvailable = false,
                isListening = false,
                onVoiceToggle = {},
                onVoiceStop = {},
                voiceListeningText = "",
                voiceInputDescription = "Entrada de voz",
            )
        },
    ) { padding ->
        MessageList(
            displayItems = chatState.items,
            tools = chatState.tools,
            sending = false,
            isWorking = working,
            syncingHistory = syncing,
            refreshing = false,
            conn = conn,
            chatFontScale = settings.chatFontScale,
            chatMessageSpacing = settings.chatMessageSpacing,
            busyPromptId = busyPromptId,
            onAnswerPrompt = { prompt, answer -> appController.answerPrompt(prompt.promptId, answer) },
            onRetryUserMessage = { userMsg ->
                val cId = userMsg.clientMsgId ?: userMsg.key.removePrefix("local:")
                appController.scope.launch { chatSession.retry(cId) }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }

    if (showInfo && agent != null) {
        InfoDialog(
            agent = agent,
            liveModel = chatState.model,
            liveThinking = chatState.thinkingLevel,
            onDismiss = { showInfo = false },
        )
    }

    if (showDiff) {
        DiffSheet(
            diff = diffResult,
            selectedFile = selectedDiffFile,
            isLoading = diffLoading,
            onSelectFile = appController::selectDiffFile,
            onDiscardFile = { path -> appController.discardDiffFile(agent, path) },
            onRequestAdjustment = { filePath ->
                val prefix = if (text.isBlank()) "Ajusta en $filePath: " else "$text\nAjusta en $filePath: "
                text = prefix
                appController.dismissDiffSheet()
            },
            onDismiss = appController::dismissDiffSheet,
        )
    }
}
