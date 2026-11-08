package dev.j3pf.rp.ui.shared

import dev.j3pf.rp.chat.ChatReducer
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.client.chat.ChatDeliveryPolicy
import dev.j3pf.rp.client.chat.ChatPlatform
import dev.j3pf.rp.client.chat.ChatSession
import dev.j3pf.rp.client.chat.ChatSessionRegistry
import dev.j3pf.rp.client.chat.ChatSocketConnection
import dev.j3pf.rp.client.chat.OutboxItem
import dev.j3pf.rp.client.http.RelayCredentials
import dev.j3pf.rp.client.http.RelayHttpClient
import dev.j3pf.rp.client.http.RelayHttpRequest
import dev.j3pf.rp.client.http.RelayHttpResponse
import dev.j3pf.rp.client.http.RelayHttpTransport
import dev.j3pf.rp.client.session.SessionPlatform
import dev.j3pf.rp.client.session.SessionPolicy
import dev.j3pf.rp.client.session.SessionPushPlatform
import dev.j3pf.rp.client.ws.ReconnectingSocketPolicy
import dev.j3pf.rp.client.ws.RelayMessageBus
import dev.j3pf.rp.client.ws.RelaySocketDriver
import dev.j3pf.rp.client.ws.RelaySocketListener
import dev.j3pf.rp.client.ws.RelayWsClient
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.ports.SecretStore
import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.PairResult
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AppHello
import dev.j3pf.rp.protocol.AppInfo
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.HerdrTarget
import dev.j3pf.rp.protocol.HostDirs
import dev.j3pf.rp.protocol.HostGitDiff
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.HostGitDiscard
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.HostListDirs
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.HostsUpdate
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachinePrefs
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MachinesUpdate
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.QuietHours
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.ui.agents.AgentsUiState
import dev.j3pf.rp.ui.agents.MachineGroup
import dev.j3pf.rp.ui.agents.sortAgentsWithinGroup
import dev.j3pf.rp.ui.chat.DeliveryChoice
import dev.j3pf.rp.ui.mesh.MeshConversationDetail
import dev.j3pf.rp.ui.mesh.MeshGroupChip
import dev.j3pf.rp.ui.mesh.MeshMachineChip
import dev.j3pf.rp.ui.mesh.MeshNodeDetail
import dev.j3pf.rp.ui.mesh.MeshUiState
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.nav.AboutRoute
import dev.j3pf.rp.ui.nav.AddMachineRoute
import dev.j3pf.rp.ui.nav.AgentsRoute
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.nav.BatteryRoute
import dev.j3pf.rp.ui.nav.DevicesRoute
import dev.j3pf.rp.ui.nav.MachinesRoute
import dev.j3pf.rp.ui.nav.MeshRoute
import dev.j3pf.rp.ui.nav.NewAgentRoute
import dev.j3pf.rp.ui.nav.NotificationSettingsRoute
import dev.j3pf.rp.ui.nav.OnboardingRoute
import dev.j3pf.rp.ui.nav.PairRoute
import dev.j3pf.rp.ui.nav.ScanRoute
import dev.j3pf.rp.ui.nav.SecuritySettingsRoute
import dev.j3pf.rp.ui.newagent.HerdrPlacement
import dev.j3pf.rp.ui.newagent.NewAgentForm
import dev.j3pf.rp.ui.newagent.NewAgentUi
import dev.j3pf.rp.ui.onboarding.PairUiState
import dev.j3pf.rp.ui.pending.PendingItem
import dev.j3pf.rp.ui.pending.PendingUiState
import dev.j3pf.rp.ui.pending.filterPendingItems
import dev.j3pf.rp.ui.pending.sortPendingItems
import dev.j3pf.rp.ui.promptsheet.PromptSheetChromeState
import dev.j3pf.rp.ui.settings.HealthState
import dev.j3pf.rp.ui.settings.SettingsDest
import dev.j3pf.rp.ui.settings.SettingsUiState
import dev.j3pf.rp.ui.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Multiplatform composition root & screen coordinator for `RemotePiApp()` (O3/O4).
 *
 * Connects [SecretStore], [SettingsStore], [RelayHttpClient], [RelayWsClient],
 * [ReconnectingSocketPolicy], [SessionPolicy], [ChatDeliveryPolicy], and [ChatSessionRegistry]
 * to the 5 shared UI screens (Onboarding, Agents, Mesh, Pending, Settings, plus Chat, NewAgent,
 * PromptSheet, and DiffSheet).
 */
class SharedAppController(
    val scope: CoroutineScope,
    val secrets: SecretStore = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()),
    val settingsStore: SettingsStore = KeyValueSettingsStore(),
    httpTransport: RelayHttpTransport = NoOpRelayHttpTransport(),
    private val pushPlatform: SessionPushPlatform = SessionPushPlatform.IOS,
    private val deviceNameProvider: () -> String = { "RemotePi iOS" },
    private val apnsEnvironmentProvider: () -> String? = { "sandbox" },
    private val startSocketConnection: () -> Unit = {},
    private val stopSocketConnection: () -> Unit = {},
    private val isSocketConnected: () -> Boolean = { false },
    private val sendSocketText: (String) -> Boolean = { false },
    val connState: StateFlow<ConnState> = MutableStateFlow(ConnState.Disconnected),
) {
    val bus = RelayMessageBus()

    val wsClient =
        RelayWsClient(
            isConnected = isSocketConnected,
            sendText = sendSocketText,
            newId = ::newId,
            bus = bus,
        )

    val httpClient =
        RelayHttpClient(
            transport = httpTransport,
            credentials = {
                secrets.current()?.let { s ->
                    RelayCredentials(s.relayUrl, s.deviceToken)
                }
            },
            encodeQueryComponent = ::percentEncodeComponent,
            invalidResponse = { IllegalStateException(it) },
        )

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _currentRoute =
        MutableStateFlow<Any>(
            if (secrets.current() != null) AgentsRoute else OnboardingRoute,
        )
    val currentRoute: StateFlow<Any> = _currentRoute.asStateFlow()
    private val backStack = mutableListOf<Any>()

    private val _pairState = MutableStateFlow(PairUiState())
    val pairState: StateFlow<PairUiState> = _pairState.asStateFlow()

    private val _machines = MutableStateFlow<List<MachineSummary>>(emptyList())
    val machines: StateFlow<List<MachineSummary>> = _machines.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentSummary>>(emptyList())
    val agents: StateFlow<List<AgentSummary>> = _agents.asStateFlow()

    private val _openPrompts = MutableStateFlow<List<Prompt>>(emptyList())
    val openPrompts: StateFlow<List<Prompt>> = _openPrompts.asStateFlow()

    private val _hosts = MutableStateFlow<Map<String, HostInfo>>(emptyMap())
    val hosts: StateFlow<Map<String, HostInfo>> = _hosts.asStateFlow()

    private val refreshingAgents = MutableStateFlow(false)

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private val _pairCode = MutableStateFlow<String?>(null)
    val pairCode: StateFlow<String?> = _pairCode.asStateFlow()

    private val _activePromptId = MutableStateFlow<String?>(null)
    val activePromptId: StateFlow<String?> = _activePromptId.asStateFlow()

    private val _busyPromptId = MutableStateFlow<String?>(null)
    val busyPromptId: StateFlow<String?> = _busyPromptId.asStateFlow()

    private val _promptSheetState = MutableStateFlow<PromptSheetChromeState>(PromptSheetChromeState.Content)
    val promptSheetState: StateFlow<PromptSheetChromeState> = _promptSheetState.asStateFlow()

    private val _promptSheetError = MutableStateFlow<String?>(null)
    val promptSheetError: StateFlow<String?> = _promptSheetError.asStateFlow()

    private val meshLayoutEngine = MeshLayoutEngine()
    private val _meshState = MutableStateFlow(MeshUiState(isLoading = false))
    val meshState: StateFlow<MeshUiState> = _meshState.asStateFlow()

    private val _newAgentForm = MutableStateFlow(NewAgentForm())
    val newAgentForm: StateFlow<NewAgentForm> = _newAgentForm.asStateFlow()

    private val _diffResult = MutableStateFlow<HostGitDiffResult?>(null)
    val diffResult: StateFlow<HostGitDiffResult?> = _diffResult.asStateFlow()

    private val _selectedDiffFile = MutableStateFlow<String?>(null)
    val selectedDiffFile: StateFlow<String?> = _selectedDiffFile.asStateFlow()

    private val _diffLoading = MutableStateFlow(false)
    val diffLoading: StateFlow<Boolean> = _diffLoading.asStateFlow()

    private val _showDiffSheet = MutableStateFlow(false)
    val showDiffSheet: StateFlow<Boolean> = _showDiffSheet.asStateFlow()

    private val chatHistoryStore = mutableMapOf<String, ChatState>()
    private val outboxStore = mutableMapOf<String, UserMessage>()

    private val chatPlatform =
        object : ChatPlatform {
            override fun currentSession(): Session? = secrets.current()

            override suspend fun touchAgent(
                relayId: String,
                agentId: String,
                timestamp: Long,
            ) {}

            override suspend fun putOutbox(
                relayId: String,
                msg: UserMessage,
                createdAt: Long,
            ) {
                outboxStore[msg.clientMsgId] = msg
            }

            override suspend fun deleteOutbox(clientMsgId: String) {
                outboxStore.remove(clientMsgId)
            }

            override suspend fun isOutboxPending(
                relayId: String,
                clientMsgId: String,
            ): Boolean = outboxStore.containsKey(clientMsgId)

            override suspend fun findOutboxMessage(
                relayId: String,
                clientMsgId: String,
            ): UserMessage? = outboxStore[clientMsgId]

            override suspend fun allPendingOutbox(relayId: String): List<OutboxItem> = outboxStore.map { (id, msg) -> OutboxItem(id, msg) }

            override suspend fun clearOutbox() {
                outboxStore.clear()
            }

            override suspend fun loadInitialState(agentId: String): ChatState? = chatHistoryStore[agentId]

            override suspend fun persistHistory(
                agentId: String,
                history: AgentHistory,
                epochChanged: Boolean,
            ) {
                val prev = if (epochChanged) ChatState() else (chatHistoryStore[agentId] ?: ChatState())
                chatHistoryStore[agentId] = ChatReducer.applyHistory(prev, history)
            }

            override suspend fun persistEvent(
                agentId: String,
                event: AgentEvent,
            ) {
                val prev = chatHistoryStore[agentId] ?: ChatState()
                chatHistoryStore[agentId] = ChatReducer.reduce(prev, event, now = currentTimeMillis())
            }

            override suspend fun clearChat(relayId: String) {
                chatHistoryStore.clear()
            }

            override fun enqueueBackgroundSend() {}
        }

    private val chatSocketConnection =
        object : ChatSocketConnection {
            override val isConnected: Boolean get() = isSocketConnected()
            override val state: StateFlow<ConnState> get() = connState
            override val messages: Flow<Envelope> get() = wsClient.messages

            override fun send(env: Envelope): Boolean = wsClient.send(env)

            override suspend fun request(
                env: Envelope,
                timeoutMs: Long,
            ): WsReply = wsClient.request(env, timeoutMs)
        }

    val chatDeliveryPolicy =
        ChatDeliveryPolicy(
            scope = scope,
            platform = chatPlatform,
            isWsConnected = isSocketConnected,
            requestWs = { wsClient.request(it) },
            httpSendMessage = { httpClient.sendMessage(it) },
            newId = ::newId,
            now = ::currentTimeMillis,
        )

    val chatSessions =
        ChatSessionRegistry(
            create = { agentId ->
                ChatSession(
                    agentId = agentId,
                    scope = scope,
                    platform = chatPlatform,
                    connection = chatSocketConnection,
                    deliveryPolicy = chatDeliveryPolicy,
                    promptFlow = { targetAgentId ->
                        openPrompts.map { list -> list.filter { it.agentId == targetAgentId } }
                    },
                    newId = ::newId,
                    now = ::currentTimeMillis,
                )
            },
            onStart = { it.start() },
            onStop = { it.stop() },
        )

    val sessionPolicy =
        SessionPolicy(
            normalizeUrl = ::normalizeRelayUrl,
            healthz = { httpClient.healthz(it) },
            pairDevice = { url, code, name, fcm -> httpClient.pairDevice(url, code, name, fcm) },
            putPush = { fcm, direct, platform, apns, env -> httpClient.putPush(fcm, direct, platform, apns, env) },
            revokeDevice = { httpClient.revokeDevice(it) },
            platform =
                object : SessionPlatform {
                    override fun currentSession(): Session? = secrets.current()

                    override fun newRelayId(): String = secrets.newRelayId()

                    override fun saveSession(session: Session) {
                        secrets.save(session)
                    }

                    override suspend fun clearLocalSession() {
                        stopSocketConnection()
                        chatSessions.clear()
                        chatHistoryStore.clear()
                        outboxStore.clear()
                        _machines.value = emptyList()
                        _agents.value = emptyList()
                        _openPrompts.value = emptyList()
                    }

                    override suspend fun clearCredentialsAndSettings() {
                        secrets.clear()
                        settingsStore.wipe()
                    }

                    override suspend fun currentToken(): String? = null

                    override fun apnsEnvironment(): String? = apnsEnvironmentProvider()

                    override fun pushPlatform(): SessionPushPlatform = pushPlatform

                    override fun deviceName(): String = deviceNameProvider()

                    override suspend fun directPush(): Boolean = _settings.value.directPush

                    override suspend fun setDirectPush(direct: Boolean) {
                        settingsStore.setDirectPush(direct)
                    }

                    override fun startDirectPush() {}

                    override fun stopDirectPush() {}

                    override fun startConnection() {
                        startSocketConnection()
                    }
                },
        )

    val agentsUiState: Flow<AgentsUiState> =
        combine(_machines, _agents, _settings, refreshingAgents) { machines, agents, cfg, refreshing ->
            buildAgentsUiState(machines, agents, cfg.selectedContext, refreshing)
        }

    val pendingUiState: Flow<PendingUiState> =
        combine(_machines, _agents, _openPrompts, _settings) { machines, agents, prompts, cfg ->
            val agentMap = agents.associateBy { it.agentId }
            val allItems = sortPendingItems(prompts.map { p -> PendingItem(p, agentMap[p.agentId]) })
            val filtered = filterPendingItems(allItems, cfg.selectedContext)
            PendingUiState(
                machines = machines,
                agents = agents,
                items = filtered,
                selected = cfg.selectedContext,
            )
        }

    val settingsUiState: Flow<SettingsUiState> =
        combine(secrets.session, connState, _settings, _machines, _agents) { session, conn, cfg, machines, agents ->
            SettingsUiState(
                session = session,
                conn = conn,
                settings = cfg,
                machines = machines,
                health = HealthState(),
                agents = agents,
            )
        }

    val newAgentUiState: Flow<NewAgentUi> =
        combine(_machines, _hosts, _newAgentForm) { machines, hosts, form ->
            val onlineHostMachines = machines.filter { it.hostOnline || hosts[it.machineId]?.online == true }
            val effectiveMachineId = form.machineId ?: onlineHostMachines.firstOrNull()?.machineId
            val host = effectiveMachineId?.let { hosts[it] }
            val adjustedForm =
                if (form.machineId == null && effectiveMachineId != null) {
                    form.copy(
                        machineId = effectiveMachineId,
                        path = form.path.ifEmpty { host?.roots?.firstOrNull().orEmpty() },
                    )
                } else {
                    form
                }
            NewAgentUi(
                machines = onlineHostMachines,
                host = host,
                form = adjustedForm,
            )
        }

    init {
        scope.launch {
            settingsStore.settings.collectLatest { cfg ->
                _settings.value = cfg
            }
        }
        scope.launch {
            secrets.session.collectLatest { s ->
                if (s != null) {
                    if (_currentRoute.value is OnboardingRoute || _currentRoute.value is PairRoute || _currentRoute.value is ScanRoute) {
                        _currentRoute.value = AgentsRoute
                        backStack.clear()
                    }
                    startSocketConnection()
                    refreshAll()
                } else {
                    stopSocketConnection()
                    backStack.clear()
                    _currentRoute.value = OnboardingRoute
                }
            }
        }
        scope.launch {
            wsClient.messages.collect { env ->
                handleIncomingEnvelope(env)
            }
        }
    }

    fun sendAppHello() {
        val s = secrets.current() ?: return
        wsClient.send(
            Envelope.of(
                MsgType.APP_HELLO,
                AppHello(deviceId = s.deviceId, app = AppInfo(version = "0.5.0")),
            ),
        )
    }

    private fun handleIncomingEnvelope(env: Envelope) {
        when (env.t) {
            MsgType.APP_WELCOME -> {
                val welcome = runCatching { env.body<AppWelcome>() }.getOrNull() ?: return
                _machines.value = welcome.machines
                _agents.value = welcome.agents
                _openPrompts.value = welcome.openPrompts
            }

            MsgType.MACHINES_UPDATE -> {
                val upd = runCatching { env.body<MachinesUpdate>() }.getOrNull() ?: return
                _machines.update { current ->
                    val map = current.associateBy { it.machineId }.toMutableMap()
                    upd.remove.forEach { map.remove(it) }
                    upd.upsert.forEach { map[it.machineId] = it }
                    map.values.toList()
                }
            }

            MsgType.AGENTS_UPDATE -> {
                val upd = runCatching { env.body<AgentsUpdate>() }.getOrNull() ?: return
                _agents.update { current ->
                    val map = current.associateBy { it.agentId }.toMutableMap()
                    upd.remove.forEach { map.remove(it) }
                    upd.upsert.forEach { map[it.agentId] = it }
                    map.values.toList()
                }
            }

            MsgType.PROMPT_OPENED -> {
                val prompt = runCatching { env.body<Prompt>() }.getOrNull() ?: return
                _openPrompts.update { list ->
                    list.filterNot { it.promptId == prompt.promptId } + prompt
                }
            }

            MsgType.PROMPT_RESOLVED -> {
                val resolved = runCatching { env.body<PromptResolved>() }.getOrNull() ?: return
                _openPrompts.update { list ->
                    list.filterNot { it.promptId == resolved.promptId }
                }
                if (_activePromptId.value == resolved.promptId) {
                    _promptSheetState.value = PromptSheetChromeState.AlreadyResolved
                }
            }

            MsgType.HOSTS_UPDATE -> {
                val upd = runCatching { env.body<HostsUpdate>() }.getOrNull() ?: return
                _hosts.update { current ->
                    val map = current.toMutableMap()
                    upd.remove.forEach { map.remove(it) }
                    upd.upsert.forEach { map[it.machineId] = it }
                    map
                }
            }

            MsgType.MESH_SNAPSHOT, MsgType.MESH_UPDATE -> {
                val snap = runCatching { env.body<MeshSnapshot>() }.getOrNull() ?: return
                applyMeshSnapshot(snap)
            }

            MsgType.MESH_COMM -> {
                val comm = runCatching { env.body<MeshComm>() }.getOrNull() ?: return
                _meshState.update { st ->
                    st.copy(liveComms = (listOf(comm) + st.liveComms).take(50))
                }
            }
        }
    }

    // ───────────────────────────── Navigation ─────────────────────────────

    fun navigateTo(route: Any) {
        val current = _currentRoute.value
        if (current != route) {
            backStack.add(current)
            _currentRoute.value = route
        }
        when (route) {
            is MeshRoute -> refreshMesh()
            is DevicesRoute -> loadDevices()
            is AddMachineRoute -> generatePairCode()
            is NewAgentRoute -> initNewAgentForm()
        }
    }

    fun selectTab(tab: AppTab) {
        backStack.clear()
        _currentRoute.value = tab.route
        if (tab == AppTab.MESH) {
            refreshMesh()
        }
    }

    fun navigateBack(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        _currentRoute.value = previous
        return true
    }

    // ───────────────────────────── Onboarding & Pairing ─────────────────────────────

    fun clearPairError() {
        _pairState.update { it.copy(error = null) }
    }

    fun pair(
        url: String,
        code: String,
    ) {
        if (_pairState.value.busy) return
        _pairState.value = PairUiState(busy = true)
        scope.launch {
            when (val res = sessionPolicy.pair(url, code)) {
                is PairResult.Success -> {
                    settingsStore.setOnboardingDone(true)
                    _pairState.value = PairUiState(done = true)
                    backStack.clear()
                    _currentRoute.value = AgentsRoute
                }

                is PairResult.Failure -> {
                    _pairState.value = PairUiState(error = res.message)
                }
            }
        }
    }

    fun onQrScanned(raw: String) {
        val parsed = parsePairingQr(raw)
        if (parsed != null) {
            val (u, c) = parsed
            _currentRoute.value = PairRoute(url = u, code = c)
            pair(u, c)
        } else {
            _pairState.value = PairUiState(error = "Código QR no reconocido")
            _currentRoute.value = PairRoute()
        }
    }

    // ───────────────────────────── Agents & Prompts ─────────────────────────────

    fun refreshAll() {
        if (secrets.current() == null) return
        scope.launch {
            refreshingAgents.value = true
            when (val mRes = httpClient.machines()) {
                is ApiResult.Ok -> _machines.value = mRes.value
                else -> {}
            }
            when (val pRes = httpClient.openPrompts()) {
                is ApiResult.Ok -> _openPrompts.value = pRes.value
                else -> {}
            }
            refreshingAgents.value = false
        }
    }

    fun selectContext(context: String) {
        scope.launch { settingsStore.setSelectedContext(context) }
    }

    fun forgetAgent(agentId: String) {
        _agents.update { list -> list.filterNot { it.agentId == agentId } }
        wsClient.send(Envelope.of(MsgType.AGENT_FORGET, AgentRef(agentId)))
    }

    fun closeOrQuitAgent(agentId: String) {
        scope.launch {
            wsClient.request(Envelope.of(MsgType.AGENT_CLOSE, AgentRef(agentId)))
        }
    }

    fun resumeAgent(agent: AgentSummary) {
        val cwd = agent.cwd ?: return
        scope.launch {
            wsClient.request(
                Envelope.of(
                    MsgType.HOST_SPAWN,
                    HostSpawn(
                        machineId = agent.machineId,
                        cwd = cwd,
                        name = agent.sessionName,
                        launcher = agent.launcher,
                        sessionId = agent.agentId.substringAfter(':', agent.agentId),
                    ),
                ),
            )
        }
    }

    fun openPromptSheet(promptId: String) {
        _activePromptId.value = promptId
        _promptSheetState.value =
            if (_openPrompts.value.any { it.promptId == promptId }) {
                PromptSheetChromeState.Content
            } else {
                PromptSheetChromeState.PromptMissing
            }
        _promptSheetError.value = null
    }

    fun dismissPromptSheet() {
        _activePromptId.value = null
        _promptSheetError.value = null
    }

    fun answerPrompt(
        promptId: String,
        answer: Answer,
    ) {
        if (_busyPromptId.value == promptId) return
        _busyPromptId.value = promptId
        _promptSheetError.value = null
        scope.launch {
            val outcome =
                if (isSocketConnected()) {
                    when (
                        val reply =
                            wsClient.request(
                                Envelope.of(MsgType.PROMPT_ANSWER, PromptAnswerRequest(promptId, answer)),
                            )
                    ) {
                        is WsReply.Ok -> {
                            val res = runCatching { reply.envelope.body<PromptAnswerResult>() }.getOrNull()
                            if (res?.alreadyResolved == true) {
                                AnswerOutcome.AlreadyResolved
                            } else if (res?.ok == false) {
                                AnswerOutcome.Failed(res.reason ?: "Error")
                            } else {
                                AnswerOutcome.Sent
                            }
                        }

                        else -> httpClient.answerPrompt(promptId, answer)
                    }
                } else {
                    httpClient.answerPrompt(promptId, answer)
                }
            _busyPromptId.value = null
            when (outcome) {
                is AnswerOutcome.Sent -> {
                    _openPrompts.update { list -> list.filterNot { it.promptId == promptId } }
                    if (_activePromptId.value == promptId) {
                        _activePromptId.value = null
                    }
                }

                is AnswerOutcome.AlreadyResolved -> {
                    _openPrompts.update { list -> list.filterNot { it.promptId == promptId } }
                    if (_activePromptId.value == promptId) {
                        _promptSheetState.value = PromptSheetChromeState.AlreadyResolved
                    }
                }

                is AnswerOutcome.Queued -> {
                    if (_activePromptId.value == promptId) {
                        _promptSheetState.value =
                            PromptSheetChromeState.Queued("Sin conexión — respuesta en cola")
                    }
                }

                is AnswerOutcome.Failed -> {
                    _promptSheetError.value = outcome.message
                }
            }
        }
    }

    // ───────────────────────────── Chat & Diff ─────────────────────────────

    fun sendChatMessage(
        session: ChatSession,
        text: String,
        choice: DeliveryChoice,
        workingDelivery: String,
        working: Boolean,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val asCommand = choice == DeliveryChoice.COMMAND || trimmed.startsWith("/")
        val method =
            when (choice) {
                DeliveryChoice.TYPED -> DeliveryMethod.INTERACTIVE
                DeliveryChoice.EXTENSION -> DeliveryMethod.API
                else -> DeliveryMethod.AUTO
            }
        val delivery = if (working) workingDelivery else Delivery.AUTO
        scope.launch {
            session.send(
                text = trimmed,
                delivery = delivery,
                method = method,
                asCommand = asCommand,
            )
        }
    }

    fun openDiffSheet(agent: AgentSummary?) {
        val target = agent ?: return
        val cwd = target.cwd ?: return
        _showDiffSheet.value = true
        _diffLoading.value = true
        scope.launch {
            val reply =
                wsClient.request(
                    Envelope.of(MsgType.HOST_GIT_DIFF, HostGitDiff(machineId = target.machineId, cwd = cwd)),
                )
            if (reply is WsReply.Ok) {
                val diff = runCatching { reply.envelope.body<HostGitDiffResult>() }.getOrNull()
                _diffResult.value = diff
                _selectedDiffFile.value = diff?.files?.firstOrNull()?.path
            }
            _diffLoading.value = false
        }
    }

    fun selectDiffFile(path: String) {
        _selectedDiffFile.value = path
    }

    fun discardDiffFile(
        agent: AgentSummary?,
        path: String,
    ) {
        val target = agent ?: return
        val cwd = target.cwd ?: return
        scope.launch {
            wsClient.request(
                Envelope.of(
                    MsgType.HOST_GIT_DISCARD,
                    HostGitDiscard(machineId = target.machineId, cwd = cwd, path = path),
                ),
            )
            openDiffSheet(target)
        }
    }

    fun dismissDiffSheet() {
        _showDiffSheet.value = false
    }

    // ───────────────────────────── New Agent ─────────────────────────────

    private fun initNewAgentForm() {
        val onlineHostMachines =
            _machines.value.filter { it.hostOnline || _hosts.value[it.machineId]?.online == true }
        val machineId = onlineHostMachines.firstOrNull()?.machineId
        val host = machineId?.let { _hosts.value[it] }
        val rootPath = host?.roots?.firstOrNull().orEmpty()
        _newAgentForm.value = NewAgentForm(machineId = machineId, path = rootPath)
        if (machineId != null && rootPath.isNotBlank()) {
            loadHostDirs(machineId, rootPath)
        }
    }

    fun pickNewAgentMachine(machineId: String) {
        val host = _hosts.value[machineId]
        val rootPath = host?.roots?.firstOrNull().orEmpty()
        _newAgentForm.update {
            it.copy(machineId = machineId, path = rootPath, dirs = emptyList(), error = null)
        }
        if (rootPath.isNotBlank()) {
            loadHostDirs(machineId, rootPath)
        }
    }

    fun openNewAgentDir(path: String) {
        val machineId = _newAgentForm.value.machineId ?: return
        _newAgentForm.update { it.copy(path = path, error = null) }
        loadHostDirs(machineId, path)
    }

    fun enterNewAgentSubDir(subDir: String) {
        val cur = _newAgentForm.value.path.trimEnd('/')
        val next = if (cur.isEmpty()) subDir else "$cur/$subDir"
        openNewAgentDir(next)
    }

    fun upNewAgentDir() {
        val cur = _newAgentForm.value.path.trimEnd('/')
        val parent = cur.substringBeforeLast('/', "")
        if (parent.isNotBlank()) {
            openNewAgentDir(parent)
        }
    }

    private fun loadHostDirs(
        machineId: String,
        path: String,
    ) {
        _newAgentForm.update { it.copy(loadingDirs = true) }
        scope.launch {
            val reply = wsClient.request(Envelope.of(MsgType.HOST_LIST_DIRS, HostListDirs(machineId, path)))
            val dirs =
                if (reply is WsReply.Ok) {
                    runCatching { reply.envelope.body<HostDirs>() }.getOrNull()?.dirs.orEmpty()
                } else {
                    emptyList()
                }
            _newAgentForm.update { it.copy(dirs = dirs, loadingDirs = false) }
        }
    }

    fun updateNewAgentForm(transform: (NewAgentForm) -> NewAgentForm) {
        _newAgentForm.update(transform)
    }

    fun createNewAgent() {
        val f = _newAgentForm.value
        val machineId = f.machineId ?: return
        if (f.path.isBlank() || f.spawning) return
        _newAgentForm.update { it.copy(spawning = true, error = null) }
        scope.launch {
            val target =
                when (f.placement) {
                    HerdrPlacement.NEW_WORKSPACE -> HerdrTarget(type = "workspace")
                    HerdrPlacement.TAB -> HerdrTarget(type = "tab", workspaceId = f.workspaceId)
                    HerdrPlacement.SPLIT ->
                        HerdrTarget(type = "split", paneId = f.paneId, direction = f.direction)
                }
            val reply =
                wsClient.request(
                    Envelope.of(
                        MsgType.HOST_SPAWN,
                        HostSpawn(
                            machineId = machineId,
                            cwd = f.path,
                            name = f.name.trim().ifEmpty { null },
                            launcher = f.launcher,
                            backend = f.backend,
                            herdrTarget = if (f.backend == "herdr") target else null,
                            initialPrompt = f.initialPrompt.trim().ifEmpty { null },
                        ),
                    ),
                )
            when (reply) {
                is WsReply.Ok -> {
                    _newAgentForm.update { it.copy(spawning = false, created = true) }
                    navigateBack()
                }

                is WsReply.Error -> {
                    _newAgentForm.update {
                        it.copy(spawning = false, error = reply.message.ifBlank { reply.code })
                    }
                }

                else -> {
                    _newAgentForm.update {
                        it.copy(spawning = false, error = "No se pudo contactar con rp-host")
                    }
                }
            }
        }
    }

    // ───────────────────────────── Mesh ─────────────────────────────

    fun refreshMesh(machineId: String? = _meshState.value.selectedMachineId) {
        if (secrets.current() == null) return
        _meshState.update { it.copy(isLoading = true, selectedMachineId = machineId, errorMessage = null) }
        scope.launch {
            wsClient.send(Envelope.empty(MsgType.MESH_SUBSCRIBE))
            when (val res = httpClient.getMesh(machineId)) {
                is ApiResult.Ok -> applyMeshSnapshot(res.value)
                is ApiResult.Http ->
                    _meshState.update { it.copy(isLoading = false, errorMessage = "HTTP ${res.code}") }
                is ApiResult.Network ->
                    _meshState.update {
                        it.copy(isLoading = false, errorMessage = res.error.message ?: "Error de red")
                    }
            }
        }
    }

    private fun applyMeshSnapshot(snapshot: MeshSnapshot) {
        val layout = meshLayoutEngine.computeLayout(snapshot.nodes, snapshot.edges)
        val machineChips =
            listOf(MeshMachineChip(null, "Todas", "#6750A4", agentCount = snapshot.nodes.size)) +
                _machines.value.map { m ->
                    MeshMachineChip(
                        machineId = m.machineId,
                        name = m.name,
                        color = m.color,
                        icon = m.icon,
                        agentCount = snapshot.nodes.count { it.machineId == m.machineId },
                    )
                }
        val groupChips =
            snapshot.groups.map { g ->
                MeshGroupChip(
                    groupId = g.groupId,
                    label = g.label.ifEmpty { g.groupId },
                    memberCount = snapshot.nodes.count { it.groupId == g.groupId },
                )
            }
        _meshState.update { st ->
            st.copy(
                isLoading = false,
                snapshot = snapshot,
                layoutResult = layout,
                machineChips = machineChips,
                groupChips = groupChips,
                errorMessage = null,
            )
        }
    }

    fun toggleMeshListView() {
        _meshState.update { it.copy(isListView = !it.isListView) }
    }

    fun selectMeshMachine(machineId: String?) {
        scope.launch { settingsStore.setMeshSelectedMachine(machineId) }
        refreshMesh(machineId)
    }

    fun selectMeshGroup(groupId: String?) {
        scope.launch { settingsStore.setMeshSelectedGroup(groupId) }
        _meshState.update { it.copy(selectedGroupId = groupId) }
    }

    fun selectMeshNode(agentId: String) {
        val nodes = _meshState.value.snapshot?.nodes ?: return
        val node = nodes.firstOrNull { it.agentId == agentId } ?: return
        _meshState.update {
            it.copy(nodeDetail = MeshNodeDetail(node = node, isLoadingComms = true))
        }
        scope.launch {
            val comms =
                when (val r = httpClient.getMeshAgentComms(agentId)) {
                    is ApiResult.Ok -> r.value.items
                    else -> emptyList()
                }
            _meshState.update { st ->
                st.copy(nodeDetail = st.nodeDetail?.copy(recentComms = comms, isLoadingComms = false))
            }
        }
    }

    fun dismissMeshNodeDetail() {
        _meshState.update { it.copy(nodeDetail = null) }
    }

    fun openMeshConversation(peerAgentId: String) {
        val nodeA = _meshState.value.nodeDetail?.node ?: return
        val snapshot = _meshState.value.snapshot
        val nodes = snapshot?.nodes ?: emptyList()
        val nodeB = nodes.firstOrNull { it.agentId == peerAgentId }
        _meshState.update {
            it.copy(
                conversationDetail =
                    MeshConversationDetail(
                        agentA = nodeA.agentId,
                        agentB = peerAgentId,
                        agentAName = nodeA.name,
                        agentBName = nodeB?.name ?: peerAgentId,
                        isLoading = true,
                    ),
            )
        }
        scope.launch {
            when (val r = httpClient.getMeshConversation(nodeA.agentId, peerAgentId)) {
                is ApiResult.Ok -> {
                    _meshState.update { st ->
                        st.copy(
                            conversationDetail =
                                st.conversationDetail?.copy(
                                    items = r.value.items,
                                    nextBefore = r.value.nextBefore,
                                    isLoading = false,
                                ),
                        )
                    }
                }

                else -> {
                    _meshState.update { st ->
                        st.copy(conversationDetail = st.conversationDetail?.copy(isLoading = false))
                    }
                }
            }
        }
    }

    fun dismissMeshConversation() {
        _meshState.update { it.copy(conversationDetail = null) }
    }

    // ───────────────────────────── Settings & Subscreens ─────────────────────────────

    fun navigateSettingsDest(dest: SettingsDest) {
        val route =
            when (dest) {
                SettingsDest.Devices -> DevicesRoute
                SettingsDest.Machines -> MachinesRoute
                SettingsDest.AddMachine -> AddMachineRoute
                SettingsDest.Notifications -> NotificationSettingsRoute
                SettingsDest.Security -> SecuritySettingsRoute
                SettingsDest.Battery -> BatteryRoute
                SettingsDest.About -> AboutRoute
            }
        navigateTo(route)
    }

    fun loadDevices() {
        scope.launch {
            when (val r = httpClient.devices()) {
                is ApiResult.Ok -> _devices.value = r.value
                else -> {}
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        scope.launch {
            httpClient.revokeDevice(deviceId)
            loadDevices()
        }
    }

    fun editMachine(
        machineId: String,
        name: String,
        context: String,
        color: String,
    ) {
        scope.launch {
            when (val r = httpClient.patchMachine(machineId, MachinePatch(name = name, context = context, color = color))) {
                is ApiResult.Ok -> {
                    _machines.update { list ->
                        list.map { if (it.machineId == machineId) r.value else it }
                    }
                }

                else -> {}
            }
        }
    }

    fun revokeMachine(machineId: String) {
        scope.launch {
            httpClient.revokeMachine(machineId)
            _machines.update { list -> list.filterNot { it.machineId == machineId } }
        }
    }

    fun generatePairCode() {
        _pairCode.value = null
        scope.launch {
            when (val r = httpClient.createMachineCode()) {
                is ApiResult.Ok -> _pairCode.value = r.value.code
                else -> {}
            }
        }
    }

    fun sendTestNotification() {
        scope.launch { httpClient.pushTest() }
    }

    fun setDirectPush(enabled: Boolean) {
        scope.launch { sessionPolicy.applyPushChannels(enabled) }
    }

    fun setCategoryEnabled(
        category: String,
        enabled: Boolean,
    ) {
        scope.launch {
            val cur = _settings.value.notifyPrefs
            val next = cur.copy(categories = cur.categories + (category to enabled))
            settingsStore.setNotifyPrefs(next)
            httpClient.putPrefs(next)
        }
    }

    fun setCriticalAlways(enabled: Boolean) {
        scope.launch { settingsStore.setCriticalAlways(enabled) }
    }

    fun setAgentMuted(
        agentId: String,
        muted: Boolean,
    ) {
        scope.launch { settingsStore.setAgentMuted(agentId, muted) }
    }

    fun setMachineMute(
        machineId: String,
        muted: Boolean,
    ) {
        scope.launch {
            val cur = _settings.value.notifyPrefs
            val prevMachine = cur.machines[machineId] ?: MachinePrefs()
            val next = cur.copy(machines = cur.machines + (machineId to prevMachine.copy(mute = muted)))
            settingsStore.setNotifyPrefs(next)
            httpClient.putPrefs(next)
        }
    }

    fun setMachineQuietHours(
        machineId: String,
        from: String,
        to: String,
        tz: String,
    ) {
        scope.launch {
            val cur = _settings.value.notifyPrefs
            val prevMachine = cur.machines[machineId] ?: MachinePrefs()
            val qh = if (from.isBlank() || to.isBlank()) null else QuietHours(from, to, tz)
            val next = cur.copy(machines = cur.machines + (machineId to prevMachine.copy(quietHours = qh)))
            settingsStore.setNotifyPrefs(next)
            httpClient.putPrefs(next)
        }
    }

    fun setMachineCategory(
        machineId: String,
        category: String,
        enabled: Boolean,
    ) {
        scope.launch {
            val cur = _settings.value.notifyPrefs
            val prevMachine = cur.machines[machineId] ?: MachinePrefs()
            val nextCats = prevMachine.categories + (category to enabled)
            val next = cur.copy(machines = cur.machines + (machineId to prevMachine.copy(categories = nextCats)))
            settingsStore.setNotifyPrefs(next)
            httpClient.putPrefs(next)
        }
    }

    fun setTheme(theme: String) {
        scope.launch { settingsStore.setAppTheme(theme) }
    }

    fun setChatFontScale(scale: Float) {
        scope.launch { settingsStore.setChatFontScale(scale) }
    }

    fun setChatMessageSpacing(spacing: String) {
        scope.launch { settingsStore.setChatMessageSpacing(spacing) }
    }

    fun setRequireUnlock(enabled: Boolean) {
        scope.launch { settingsStore.setRequireUnlock(enabled) }
    }

    fun setAppLock(enabled: Boolean) {
        scope.launch { settingsStore.setAppLock(enabled) }
    }

    fun setFlagSecure(enabled: Boolean) {
        scope.launch { settingsStore.setFlagSecure(enabled) }
    }

    fun logout() {
        scope.launch {
            sessionPolicy.logout()
            backStack.clear()
            _currentRoute.value = OnboardingRoute
        }
    }

    companion object {
        fun buildAgentsUiState(
            machines: List<MachineSummary>,
            agents: List<AgentSummary>,
            selectedContext: String,
            refreshing: Boolean = false,
        ): AgentsUiState {
            val filteredMachines =
                machines.filter { m ->
                    selectedContext == AppSettings.ALL ||
                        m.context == selectedContext ||
                        m.machineId == selectedContext
                }
            val visibleMachineIds = filteredMachines.map { it.machineId }.toSet()
            val groups =
                filteredMachines.map { m ->
                    MachineGroup(
                        machine = m,
                        agents = sortAgentsWithinGroup(agents.filter { it.machineId == m.machineId }),
                    )
                }
            val canSpawn =
                filteredMachines.any { it.hostOnline } ||
                    (selectedContext == AppSettings.ALL && machines.any { it.hostOnline })
            return AgentsUiState(
                machines = machines,
                allAgents = agents.filter { selectedContext == AppSettings.ALL || it.machineId in visibleMachineIds },
                groups = groups,
                selected = selectedContext,
                isLoading = false,
                canSpawn = canSpawn,
                refreshing = refreshing,
            )
        }

        fun normalizeRelayUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme =
                when {
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                    trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                    else -> "https://$trimmed"
                }
            val afterScheme = withScheme.substringAfter("://", "")
            if (afterScheme.isBlank() || afterScheme.contains(' ')) return null
            return withScheme
        }

        fun parsePairingQr(raw: String): Pair<String, String>? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("rp://pair", ignoreCase = true)) return null
            val query = trimmed.substringAfter('?', "")
            if (query.isEmpty()) return null
            val params =
                query
                    .split('&')
                    .mapNotNull { part ->
                        val idx = part.indexOf('=')
                        if (idx <= 0) null else part.substring(0, idx) to percentDecodeComponent(part.substring(idx + 1))
                    }.toMap()
            val u = params["u"] ?: params["url"] ?: return null
            val c = params["c"] ?: params["code"] ?: return null
            if (u.isBlank() || c.isBlank()) return null
            return u to c
        }

        fun percentEncodeComponent(value: String): String {
            val bytes = value.encodeToByteArray()
            val sb = StringBuilder(bytes.size)
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                when {
                    c == ' '.code -> sb.append('+')
                    (c in 'a'.code..'z'.code) ||
                        (c in 'A'.code..'Z'.code) ||
                        (c in '0'.code..'9'.code) ||
                        c == '-'.code ||
                        c == '_'.code ||
                        c == '.'.code ||
                        c == '*'.code -> sb.append(c.toChar())
                    else -> {
                        sb.append('%')
                        sb.append(HEX_UPPER[(c ushr 4) and 0x0F])
                        sb.append(HEX_UPPER[c and 0x0F])
                    }
                }
            }
            return sb.toString()
        }

        private fun percentDecodeComponent(value: String): String {
            val out = ByteArray(value.length)
            var outIdx = 0
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '+') {
                    out[outIdx++] = ' '.code.toByte()
                    i++
                } else if (ch == '%' && i + 2 < value.length) {
                    val hi = value[i + 1].digitToIntOrNull(16)
                    val lo = value[i + 2].digitToIntOrNull(16)
                    if (hi != null && lo != null) {
                        out[outIdx++] = ((hi shl 4) or lo).toByte()
                        i += 3
                    } else {
                        out[outIdx++] = ch.code.toByte()
                        i++
                    }
                } else {
                    out[outIdx++] = ch.code.toByte()
                    i++
                }
            }
            return out.decodeToString(0, outIdx)
        }

        private fun newId(): String {
            val bytes = Random.nextBytes(8)
            val sb = StringBuilder(16)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                sb.append(HEX_LOWER[(v ushr 4) and 0x0F])
                sb.append(HEX_LOWER[v and 0x0F])
            }
            return sb.toString()
        }

        private const val HEX_UPPER = "0123456789ABCDEF"
        private const val HEX_LOWER = "0123456789abcdef"
    }
}

private class NoOpRelayHttpTransport : RelayHttpTransport {
    override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> =
        ApiResult.Network(IllegalStateException("Offline"))
}

internal class NoOpSocketDriver : RelaySocketDriver<String, Unit> {
    override fun connect(
        request: String,
        listener: RelaySocketListener<Unit>,
    ) {}

    override fun send(
        socket: Unit,
        text: String,
    ): Boolean = false

    override fun close(
        socket: Unit,
        code: Int,
        reason: String,
    ) {}

    override fun cancel(socket: Unit) {}
}

internal fun createInMemorySocketController(
    scope: CoroutineScope,
    secrets: SecretStore = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()),
    settingsStore: SettingsStore = KeyValueSettingsStore(),
    httpTransport: RelayHttpTransport = NoOpRelayHttpTransport(),
): SharedAppController {
    var controllerRef: SharedAppController? = null
    val socketPolicy =
        ReconnectingSocketPolicy(
            scope = scope,
            driver = NoOpSocketDriver(),
            requestProvider = { secrets.current()?.relayUrl },
            onSocketOpen = { controllerRef?.sendAppHello() },
            onSocketText = { text -> controllerRef?.wsClient?.onText(text) },
            onSocketClosed = { controllerRef?.wsClient?.failPending(WsReply.NotConnected) },
        )
    return SharedAppController(
        scope = scope,
        secrets = secrets,
        settingsStore = settingsStore,
        httpTransport = httpTransport,
        startSocketConnection = { socketPolicy.start() },
        stopSocketConnection = { socketPolicy.stop() },
        isSocketConnected = { socketPolicy.isConnected },
        sendSocketText = { socketPolicy.send(it) },
        connState = socketPolicy.state,
    ).also { controllerRef = it }
}
