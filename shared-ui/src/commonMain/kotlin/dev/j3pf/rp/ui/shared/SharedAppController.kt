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
import dev.j3pf.rp.client.ws.RelayLock
import dev.j3pf.rp.client.ws.RelayMessageBus
import dev.j3pf.rp.client.ws.RelaySocketDriver
import dev.j3pf.rp.client.ws.RelaySocketListener
import dev.j3pf.rp.client.ws.RelayWsClient
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
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
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.HerdrLayout
import dev.j3pf.rp.protocol.HerdrTarget
import dev.j3pf.rp.protocol.HostDirs
import dev.j3pf.rp.protocol.HostGitDiff
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.HostGitDiscard
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.HostListDirs
import dev.j3pf.rp.protocol.HostMachineRef
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.HostsUpdate
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachinePrefs
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MachinesUpdate
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshCommEvent
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MeshSubscribe
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.QuietHours
import dev.j3pf.rp.protocol.ResolvedBy
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.ui.agents.AgentsUiState
import dev.j3pf.rp.ui.agents.MachineGroup
import dev.j3pf.rp.ui.agents.sortAgentsWithinGroup
import dev.j3pf.rp.ui.chat.DeliveryChoice
import dev.j3pf.rp.ui.mesh.LiveCommBubble
import dev.j3pf.rp.ui.mesh.MeshConversationDetail
import dev.j3pf.rp.ui.mesh.MeshGroupChip
import dev.j3pf.rp.ui.mesh.MeshMachineChip
import dev.j3pf.rp.ui.mesh.MeshNodeDetail
import dev.j3pf.rp.ui.mesh.MeshUiState
import dev.j3pf.rp.ui.mesh.formatMeshDisplayName
import dev.j3pf.rp.ui.mesh.isIdLooking
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
import kotlinx.coroutines.delay
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

/** Default [BiometricAuthorizer] for non-device/headless environments. */
object PermissiveBiometricAuthorizer : BiometricAuthorizer {
    override suspend fun authenticate(reason: String): BiometricAuthResult = BiometricAuthResult.OK
}

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
    val biometricAuthorizer: BiometricAuthorizer = PermissiveBiometricAuthorizer,
    val appVersion: String = APP_VERSION,
    private val pushPlatform: SessionPushPlatform = SessionPushPlatform.IOS,
    private val deviceNameProvider: () -> String = { "RemotePi iOS" },
    private val apnsEnvironmentProvider: () -> String? = { "sandbox" },
    private val currentApnsTokenProvider: () -> String? = { null },
    private val startSocketConnection: () -> Unit = {},
    private val stopSocketConnection: () -> Unit = {},
    private val retrySocketConnection: () -> Unit = { startSocketConnection() },
    private val isSocketConnected: () -> Boolean = { false },
    private val sendSocketText: (String) -> Boolean = { false },
    val connState: StateFlow<ConnState> = MutableStateFlow(ConnState.Disconnected),
) {
    private val storeLock = RelayLock()

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

    private val _pendingForgetAgentId = MutableStateFlow<String?>(null)
    val pendingForgetAgentId: StateFlow<String?> = _pendingForgetAgentId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

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

    private val _promptSheetAuthMessage = MutableStateFlow<String?>(null)
    val promptSheetAuthMessage: StateFlow<String?> = _promptSheetAuthMessage.asStateFlow()

    private val meshLayoutEngine = MeshLayoutEngine()
    private var meshViewportWidth = DEFAULT_MESH_VIEWPORT_SIZE
    private var meshViewportHeight = DEFAULT_MESH_VIEWPORT_SIZE
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
                storeLock.withLock { outboxStore[msg.clientMsgId] = msg }
            }

            override suspend fun deleteOutbox(clientMsgId: String) {
                storeLock.withLock { outboxStore.remove(clientMsgId) }
            }

            override suspend fun isOutboxPending(
                relayId: String,
                clientMsgId: String,
            ): Boolean = storeLock.withLock { outboxStore.containsKey(clientMsgId) }

            override suspend fun findOutboxMessage(
                relayId: String,
                clientMsgId: String,
            ): UserMessage? = storeLock.withLock { outboxStore[clientMsgId] }

            override suspend fun allPendingOutbox(relayId: String): List<OutboxItem> =
                storeLock.withLock { outboxStore.map { (id, msg) -> OutboxItem(id, msg) } }

            override suspend fun clearOutbox() {
                storeLock.withLock { outboxStore.clear() }
            }

            override suspend fun loadInitialState(agentId: String): ChatState? = storeLock.withLock { chatHistoryStore[agentId] }

            override suspend fun persistHistory(
                agentId: String,
                history: AgentHistory,
                epochChanged: Boolean,
            ) {
                storeLock.withLock {
                    val prev = if (epochChanged) ChatState() else (chatHistoryStore[agentId] ?: ChatState())
                    chatHistoryStore[agentId] = ChatReducer.applyHistory(prev, history)
                }
            }

            override suspend fun persistEvent(
                agentId: String,
                event: AgentEvent,
            ) {
                val now = currentTimeMillis()
                storeLock.withLock {
                    val prev = chatHistoryStore[agentId] ?: ChatState()
                    chatHistoryStore[agentId] = ChatReducer.reduce(prev, event, now = now)
                }
            }

            override suspend fun clearChat(relayId: String) {
                storeLock.withLock { chatHistoryStore.clear() }
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
                        storeLock.withLock {
                            chatHistoryStore.clear()
                            outboxStore.clear()
                        }
                        _machines.value = emptyList()
                        _agents.value = emptyList()
                        _openPrompts.value = emptyList()
                    }

                    override suspend fun clearCredentialsAndSettings() {
                        secrets.clear()
                        settingsStore.wipe()
                    }

                    override suspend fun currentToken(): String? = null

                    override suspend fun currentApnsToken(): String? = currentApnsTokenProvider()

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
                        storeLock.withLock { backStack.clear() }
                    }
                    startSocketConnection()
                    refreshAll()
                } else {
                    stopSocketConnection()
                    storeLock.withLock { backStack.clear() }
                    _currentRoute.value = OnboardingRoute
                }
            }
        }
        scope.launch {
            connState.collect { st ->
                if (st == ConnState.Connected) {
                    chatDeliveryPolicy.flushOutbox()
                    if (_currentRoute.value is MeshRoute) {
                        sendMeshSubscribe(_meshState.value.selectedMachineId)
                    }
                }
            }
        }
        scope.launch {
            combine(_machines, _hosts) { ms, hs -> ms to hs }.collect { (ms, hs) ->
                val online = ms.filter { it.hostOnline || hs[it.machineId]?.online == true }
                val curId = _newAgentForm.value.machineId
                if (curId == null && online.isNotEmpty()) {
                    pickNewAgentMachine(online.first().machineId)
                } else if (curId != null && _newAgentForm.value.path.isEmpty()) {
                    val h = hs[curId]
                    if (h != null && h.roots.isNotEmpty()) {
                        val root = h.roots.first()
                        _newAgentForm.update {
                            it.copy(
                                path = root,
                                launcher = it.launcher ?: h.defaultLauncher ?: h.launchers.firstOrNull(),
                                backend = it.backend ?: h.defaultBackend,
                            )
                        }
                        loadHostDirs(curId, root)
                    }
                }
            }
        }
        scope.launch {
            wsClient.messages.collect { env ->
                handleIncomingEnvelope(env)
            }
        }
    }

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    fun sendAppHello() {
        val s = secrets.current() ?: return
        wsClient.send(
            Envelope.of(
                MsgType.APP_HELLO,
                AppHello(deviceId = s.deviceId, app = AppInfo(version = appVersion)),
            ),
        )
    }

    private fun syncAgentPromptCounts(prompts: List<Prompt> = _openPrompts.value) {
        val counts = prompts.groupingBy { it.agentId }.eachCount()
        _agents.update { list ->
            list.map { a ->
                val count = counts[a.agentId] ?: 0
                if (a.openPromptCount == count) a else a.copy(openPromptCount = count)
            }
        }
    }

    private fun handleIncomingEnvelope(env: Envelope) {
        when (env.t) {
            MsgType.APP_WELCOME -> {
                val welcome = runCatching { env.body<AppWelcome>() }.getOrNull() ?: return
                _machines.value = welcome.machines
                _openPrompts.value = welcome.openPrompts
                val counts = welcome.openPrompts.groupingBy { it.agentId }.eachCount()
                val onlineHerdrMachines =
                    welcome.agents
                        .filter { it.online && (it.herdr != null || it.agentId.contains(":herdr:")) }
                        .map { it.machineId }
                        .toSet()
                val (staleHerdr, liveAgents) =
                    welcome.agents.partition {
                        !it.online && it.machineId in onlineHerdrMachines && (it.herdr != null || it.agentId.contains(":herdr:"))
                    }
                _agents.value =
                    liveAgents.map { a ->
                        val count = counts[a.agentId] ?: a.openPromptCount
                        if (a.openPromptCount == count) a else a.copy(openPromptCount = count)
                    }
                if (staleHerdr.isNotEmpty()) {
                    scope.launch {
                        staleHerdr.forEach { stale ->
                            wsClient.request(Envelope.of(MsgType.AGENT_FORGET, AgentRef(stale.agentId), id = newId()))
                        }
                    }
                }
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
                val counts = _openPrompts.value.groupingBy { it.agentId }.eachCount()
                _agents.update { current ->
                    val map = current.associateBy { it.agentId }.toMutableMap()
                    upd.remove.forEach { map.remove(it) }
                    upd.upsert.forEach { a ->
                        val count = counts[a.agentId] ?: a.openPromptCount
                        map[a.agentId] = if (a.openPromptCount == count) a else a.copy(openPromptCount = count)
                    }
                    map.values.toList()
                }
            }

            MsgType.PROMPT_OPENED -> {
                val prompt = runCatching { env.body<Prompt>() }.getOrNull() ?: return
                _openPrompts.update { list ->
                    list.filterNot { it.promptId == prompt.promptId } + prompt
                }
                syncAgentPromptCounts()
            }

            MsgType.PROMPT_RESOLVED -> {
                val resolved = runCatching { env.body<PromptResolved>() }.getOrNull() ?: return
                _openPrompts.update { list ->
                    list.filterNot { it.promptId == resolved.promptId }
                }
                syncAgentPromptCounts()
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
                val comm =
                    runCatching { env.body<MeshCommEvent>().comm }
                        .recoverCatching { env.body<MeshComm>() }
                        .getOrNull() ?: return
                onIncomingMeshComm(comm)
            }
        }
    }

    // ───────────────────────────── Navigation ─────────────────────────────

    fun navigateTo(route: Any) {
        val current = _currentRoute.value
        if (current != route) {
            storeLock.withLock { backStack.add(current) }
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
        storeLock.withLock { backStack.clear() }
        _currentRoute.value = tab.route
        if (tab == AppTab.MESH) {
            refreshMesh()
        }
    }

    fun navigateBack(): Boolean {
        val previous = storeLock.withLock { backStack.removeLastOrNull() } ?: return false
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
                    storeLock.withLock { backStack.clear() }
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
        val session = secrets.current() ?: return
        retrySocketConnection()
        if (isSocketConnected()) {
            wsClient.send(
                Envelope.of(
                    MsgType.APP_HELLO,
                    AppHello(deviceId = session.deviceId, app = AppInfo(version = appVersion)),
                ),
            )
        }
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
            syncAgentPromptCounts()
            refreshingAgents.value = false
        }
    }

    fun selectContext(context: String) {
        scope.launch { settingsStore.setSelectedContext(context) }
    }

    fun requestForgetAgent(agentId: String) {
        _pendingForgetAgentId.value = agentId
        _actionMessage.value = null
    }

    fun cancelForgetAgent() {
        _pendingForgetAgentId.value = null
    }

    fun confirmForgetAgent(onClosed: () -> Unit = {}) {
        val agentId = _pendingForgetAgentId.value ?: return
        _pendingForgetAgentId.value = null
        closeOrForgetAgent(agentId, onClosed)
    }

    fun forgetAgent(agentId: String) {
        closeOrForgetAgent(agentId)
    }

    fun closeOrQuitAgent(agentId: String) {
        closeOrForgetAgent(agentId)
    }

    private fun closeOrForgetAgent(
        agentId: String,
        onClosed: () -> Unit = {},
    ) {
        scope.launch {
            val ref = AgentRef(agentId)
            val target = _agents.value.firstOrNull { it.agentId == agentId }
            val targetPaneId = target?.herdr?.paneId
            val sharedActivePane =
                target != null &&
                    !target.online &&
                    !targetPaneId.isNullOrBlank() &&
                    _agents.value.any { other ->
                        other.agentId != agentId &&
                            other.machineId == target.machineId &&
                            other.online &&
                            other.herdr?.paneId == targetPaneId
                    }
            if (sharedActivePane) {
                when (wsClient.request(Envelope.of(MsgType.AGENT_FORGET, ref, id = newId()))) {
                    is WsReply.Ok -> {
                        _agents.update { list -> list.filterNot { it.agentId == agentId } }
                        onClosed()
                    }
                    else -> {
                        _actionMessage.value = "No se pudo archivar el agente"
                    }
                }
                return@launch
            }
            when (val reply = wsClient.request(Envelope.of(MsgType.AGENT_CLOSE, ref, id = newId()))) {
                is WsReply.Ok -> {
                    _agents.update { list -> list.filterNot { it.agentId == agentId } }
                    onClosed()
                }

                is WsReply.Error -> {
                    if (reply.code == ErrorCodes.NOT_FOUND) {
                        _agents.update { list -> list.filterNot { it.agentId == agentId } }
                        onClosed()
                    } else if (reply.code == ErrorCodes.AGENT_CLOSE_UNSUPPORTED) {
                        when (wsClient.request(Envelope.of(MsgType.AGENT_FORGET, ref, id = newId()))) {
                            is WsReply.Ok -> {
                                _agents.update { list -> list.filterNot { it.agentId == agentId } }
                                _actionMessage.value = "No había un pane de Herdr vinculado; el historial quedó archivado."
                                onClosed()
                            }

                            else -> {
                                _actionMessage.value = "No se pudo archivar el agente"
                            }
                        }
                    } else {
                        _actionMessage.value =
                            if (reply.message.isBlank()) {
                                "No se pudo cerrar el agente"
                            } else {
                                "No se pudo cerrar el agente: ${reply.message}"
                            }
                    }
                }

                WsReply.Timeout -> {
                    _actionMessage.value = "Tiempo de espera agotado al cerrar el agente"
                }

                WsReply.NotConnected -> {
                    _actionMessage.value = "Sin conexión con el relay"
                }
            }
        }
    }

    fun quitAgent(
        session: ChatSession,
        onDone: () -> Unit = { navigateBack() },
    ) {
        scope.launch {
            session.send("/quit", emptyList(), Delivery.AUTO, DeliveryMethod.AUTO, asCommand = true)
            onDone()
        }
    }

    fun resumeAgent(agent: AgentSummary) {
        val cwd = agent.cwd ?: return
        val host = _hosts.value[agent.machineId]
        val effectiveBackend = host?.defaultBackend ?: if (host?.backends?.herdr != null) "herdr" else null
        val target = if (effectiveBackend == "herdr") HerdrTarget(type = "workspace") else null
        scope.launch {
            val reply =
                wsClient.request(
                    Envelope.of(
                        MsgType.HOST_SPAWN,
                        HostSpawn(
                            machineId = agent.machineId,
                            cwd = cwd,
                            name = agent.sessionName,
                            launcher = agent.launcher ?: host?.defaultLauncher,
                            backend = effectiveBackend,
                            herdrTarget = target,
                            sessionId = agent.agentId.substringAfter(':', agent.agentId),
                        ),
                    ),
                    timeoutMs = 30_000,
                )
            when (reply) {
                is WsReply.Ok -> _actionMessage.value = "Agente reanudado en la PC"
                is WsReply.Error -> _actionMessage.value = "No se pudo reanudar: ${reply.message.ifEmpty { reply.code }}"
                else -> _actionMessage.value = "No se pudo reanudar el agente"
            }
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
        _promptSheetAuthMessage.value = null
    }

    fun dismissPromptSheet() {
        _activePromptId.value = null
        _promptSheetError.value = null
        _promptSheetAuthMessage.value = null
    }

    fun requiresPromptAuth(prompt: Prompt): Boolean =
        _settings.value.requireUnlockToApprove &&
            (prompt.category == PromptCategory.PERMISSION || prompt.danger == Danger.HIGH)

    fun requiresChatSendAuth(
        agent: AgentSummary?,
        choice: DeliveryChoice,
    ): Boolean =
        _settings.value.requireUnlockToApprove &&
            (choice == DeliveryChoice.TYPED || (choice == DeliveryChoice.AUTO && agent?.gentleMode == "auto"))

    fun answerPrompt(
        promptId: String,
        answer: Answer,
    ) {
        if (_busyPromptId.value == promptId) return
        val prompt = _openPrompts.value.firstOrNull { it.promptId == promptId }
        _promptSheetError.value = null
        _promptSheetAuthMessage.value = null
        scope.launch {
            if (prompt != null && requiresPromptAuth(prompt)) {
                when (biometricAuthorizer.authenticate("Confirma tu identidad", prompt.title.take(80))) {
                    BiometricAuthResult.OK -> {}
                    BiometricAuthResult.FAILED -> {
                        _promptSheetAuthMessage.value = "Desbloqueo cancelado"
                        _actionMessage.value = "Desbloqueo cancelado"
                        return@launch
                    }

                    BiometricAuthResult.UNAVAILABLE -> {
                        _promptSheetAuthMessage.value = "Configura un bloqueo de pantalla para aprobar"
                        _actionMessage.value = "Configura un bloqueo de pantalla para aprobar"
                        return@launch
                    }
                }
            }
            _busyPromptId.value = promptId
            val outcome =
                if (isSocketConnected()) {
                    when (
                        val reply =
                            wsClient.request(
                                Envelope.of(MsgType.PROMPT_ANSWER, PromptAnswerRequest(promptId, answer), id = newId()),
                            )
                    ) {
                        is WsReply.Ok -> {
                            val res = runCatching { reply.envelope.body<PromptAnswerResult>() }.getOrNull()
                            when {
                                res?.alreadyResolved == true -> AnswerOutcome.AlreadyResolved
                                res?.ok == false && res.reason == "agent_offline_queued" -> AnswerOutcome.Sent
                                res?.ok == false -> AnswerOutcome.Failed(res.reason ?: "Error")
                                else -> AnswerOutcome.Sent
                            }
                        }

                        is WsReply.Error -> {
                            if (reply.code == ErrorCodes.CONFLICT) {
                                AnswerOutcome.AlreadyResolved
                            } else {
                                httpClient.answerPrompt(promptId, answer)
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
                    syncAgentPromptCounts()
                    if (_activePromptId.value == promptId) {
                        _activePromptId.value = null
                    }
                    bus.publish(
                        Envelope.of(
                            MsgType.PROMPT_RESOLVED,
                            PromptResolved(promptId = promptId, by = ResolvedBy.REMOTE, answer = answer),
                        ),
                    )
                }

                is AnswerOutcome.AlreadyResolved -> {
                    _openPrompts.update { list -> list.filterNot { it.promptId == promptId } }
                    syncAgentPromptCounts()
                    if (_activePromptId.value == promptId) {
                        _promptSheetState.value = PromptSheetChromeState.AlreadyResolved
                    }
                    bus.publish(
                        Envelope.of(
                            MsgType.PROMPT_RESOLVED,
                            PromptResolved(promptId = promptId, by = ResolvedBy.REMOTE),
                        ),
                    )
                }

                is AnswerOutcome.Queued -> {
                    if (_activePromptId.value == promptId) {
                        _promptSheetState.value =
                            PromptSheetChromeState.Queued("Sin conexión — respuesta en cola")
                    }
                }

                is AnswerOutcome.Failed -> {
                    _promptSheetError.value = outcome.message
                    _actionMessage.value = outcome.message
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
        agent: AgentSummary? = _agents.value.firstOrNull { it.agentId == session.agentId },
        onSent: () -> Unit = {},
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val isSlash = trimmed.startsWith("/")
        val asCommand = isSlash && (choice == DeliveryChoice.COMMAND || choice == DeliveryChoice.AUTO)
        val method =
            when (choice) {
                DeliveryChoice.TYPED -> DeliveryMethod.INTERACTIVE
                DeliveryChoice.EXTENSION -> DeliveryMethod.API
                else -> DeliveryMethod.AUTO
            }
        val delivery = if (working) workingDelivery else Delivery.AUTO
        scope.launch {
            if (requiresChatSendAuth(agent, choice)) {
                when (biometricAuthorizer.authenticate("Enviar como tecleado", "Este mensaje autoriza acciones en modo Auto")) {
                    BiometricAuthResult.OK -> {}
                    BiometricAuthResult.UNAVAILABLE -> {
                        _actionMessage.value = "Configura un bloqueo de pantalla para enviar como tecleado"
                        return@launch
                    }

                    BiometricAuthResult.FAILED -> {
                        _actionMessage.value = "Desbloqueo cancelado"
                        return@launch
                    }
                }
            }
            onSent()
            session.send(
                text = trimmed,
                delivery = delivery,
                method = method,
                asCommand = asCommand,
            )
        }
    }

    fun openDiffSheet(
        agent: AgentSummary?,
        targetFile: String? = null,
    ) {
        val target = agent ?: return
        val cwd = target.cwd ?: return
        _diffResult.value = null
        _selectedDiffFile.value = targetFile
        _showDiffSheet.value = true
        _diffLoading.value = true
        scope.launch {
            val reply =
                wsClient.request(
                    Envelope.of(MsgType.HOST_GIT_DIFF, HostGitDiff(machineId = target.machineId, cwd = cwd, path = targetFile)),
                )
            if (reply is WsReply.Ok) {
                val diff = runCatching { reply.envelope.body<HostGitDiffResult>() }.getOrNull()
                _diffResult.value = diff
                _selectedDiffFile.value = targetFile ?: diff?.files?.firstOrNull()?.path
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
        if (machineId != null) {
            pickNewAgentMachine(machineId)
        } else {
            _newAgentForm.value = NewAgentForm()
        }
    }

    fun pickNewAgentMachine(machineId: String) {
        val host = _hosts.value[machineId]
        val rootPath = host?.roots?.firstOrNull().orEmpty()
        _newAgentForm.value =
            NewAgentForm(
                machineId = machineId,
                path = rootPath,
                launcher = host?.defaultLauncher ?: host?.launchers?.firstOrNull(),
                backend = host?.defaultBackend,
            )
        if (rootPath.isNotBlank()) {
            loadHostDirs(machineId, rootPath)
        }
        if (host?.backends?.herdr != null) {
            loadNewAgentHerdrLayout()
        }
    }

    fun openNewAgentDir(path: String) {
        val machineId = _newAgentForm.value.machineId ?: return
        _newAgentForm.update { it.copy(path = path, error = null) }
        loadHostDirs(machineId, path)
    }

    fun enterNewAgentSubDir(subDir: String) {
        val cur = _newAgentForm.value.path
        val sep = if (cur.contains('\\') && !cur.contains('/')) "\\" else "/"
        val trimmed = cur.trimEnd('/', '\\')
        val next = if (trimmed.isEmpty()) subDir else "$trimmed$sep$subDir"
        openNewAgentDir(next)
    }

    fun upNewAgentDir() {
        val cur = _newAgentForm.value.path
        val machineId = _newAgentForm.value.machineId
        val host = machineId?.let { _hosts.value[it] }
        val trimmed = cur.trimEnd('/', '\\')
        if (host != null && host.roots.any { it.trimEnd('/', '\\') == trimmed }) {
            openNewAgentDir("")
            return
        }
        val idx = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        if (idx > 0) {
            openNewAgentDir(trimmed.substring(0, idx))
        } else {
            openNewAgentDir("")
        }
    }

    private fun loadHostDirs(
        machineId: String,
        path: String,
    ) {
        _newAgentForm.update { it.copy(loadingDirs = true, error = null) }
        scope.launch {
            val reply = wsClient.request(Envelope.of(MsgType.HOST_LIST_DIRS, HostListDirs(machineId, path), id = newId()))
            when (reply) {
                is WsReply.Ok -> {
                    val hostDirs = runCatching { reply.envelope.body<HostDirs>() }.getOrNull()
                    val dirs = hostDirs?.dirs?.sorted().orEmpty()
                    _newAgentForm.update { it.copy(dirs = dirs, loadingDirs = false) }
                }

                is WsReply.Error -> {
                    _newAgentForm.update { it.copy(dirs = emptyList(), loadingDirs = false, error = reply.message.ifEmpty { reply.code }) }
                }

                else -> {
                    _newAgentForm.update { it.copy(dirs = emptyList(), loadingDirs = false) }
                }
            }
        }
    }

    fun loadNewAgentHerdrLayout() {
        val machineId = _newAgentForm.value.machineId ?: return
        _newAgentForm.update { it.copy(loadingLayout = true) }
        scope.launch {
            val reply = wsClient.request(Envelope.of(MsgType.HOST_HERDR_LAYOUT, HostMachineRef(machineId), id = newId()))
            val layout =
                if (reply is WsReply.Ok) {
                    runCatching { reply.envelope.body<HerdrLayout>() }.getOrNull()
                } else {
                    null
                }
            _newAgentForm.update { it.copy(layout = layout, loadingLayout = false) }
        }
    }

    fun setNewAgentPlacement(placement: HerdrPlacement) {
        _newAgentForm.update { it.copy(placement = placement) }
        if (placement != HerdrPlacement.NEW_WORKSPACE) {
            loadNewAgentHerdrLayout()
        }
    }

    fun updateNewAgentForm(transform: (NewAgentForm) -> NewAgentForm) {
        _newAgentForm.update(transform)
    }

    fun createNewAgent() {
        val f = _newAgentForm.value
        val onlineHostMachines = _machines.value.filter { it.hostOnline || _hosts.value[it.machineId]?.online == true }
        val machineId = f.machineId ?: onlineHostMachines.firstOrNull()?.machineId ?: return
        val host = _hosts.value[machineId]
        val ui = NewAgentUi(machines = onlineHostMachines, host = host, form = f.copy(machineId = machineId))
        if (!ui.canCreate) return
        val effectiveBackend = ui.effectiveBackend
        _newAgentForm.update { it.copy(spawning = true, error = null) }
        scope.launch {
            val target =
                if (effectiveBackend == "herdr") {
                    when (f.placement) {
                        HerdrPlacement.NEW_WORKSPACE -> HerdrTarget(type = "workspace")
                        HerdrPlacement.TAB -> HerdrTarget(type = "tab", workspaceId = f.workspaceId)
                        HerdrPlacement.SPLIT ->
                            HerdrTarget(type = "split", paneId = f.paneId, direction = f.direction)
                    }
                } else {
                    null
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
                            backend = effectiveBackend,
                            herdrTarget = target,
                            initialPrompt = f.initialPrompt.trim().ifEmpty { null },
                        ),
                        id = newId(),
                    ),
                    timeoutMs = 30_000,
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

    private fun sendMeshSubscribe(machineId: String?) {
        wsClient.send(Envelope.of(MsgType.MESH_SUBSCRIBE, MeshSubscribe(machineId), id = newId()))
    }

    fun refreshMesh(machineId: String? = _meshState.value.selectedMachineId) {
        if (secrets.current() == null) return
        _meshState.update { it.copy(isLoading = it.snapshot == null, selectedMachineId = machineId, errorMessage = null) }
        scope.launch {
            sendMeshSubscribe(machineId)
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
        _meshState.update { current ->
            val machineId = current.selectedMachineId
            val machineChips = buildMeshMachineChips(_machines.value, snapshot)
            val groupChips = buildMeshGroupChips(snapshot, machineId)
            var nextGroupId = current.selectedGroupId
            if (nextGroupId != null && groupChips.none { it.groupId == nextGroupId }) {
                nextGroupId = null
            }
            if (nextGroupId == null && groupChips.isNotEmpty()) {
                nextGroupId = mostActiveMeshGroup(snapshot, groupChips, machineId)
            }
            current
                .copy(
                    isLoading = false,
                    snapshot = snapshot,
                    selectedGroupId = nextGroupId,
                    machineChips = machineChips,
                    groupChips = groupChips,
                    errorMessage = null,
                ).withComputedLayout(meshLayoutEngine, meshViewportWidth, meshViewportHeight)
        }
    }

    private fun onIncomingMeshComm(comm: MeshComm) {
        val now = currentTimeMillis()
        val expiresAt = now + MESH_BUBBLE_LIFETIME_MS
        _meshState.update { state ->
            state.copy(
                liveComms = (listOf(comm) + state.liveComms.filterNot { it.commKey == comm.commKey }).take(MAX_MESH_LIVE_COMMS),
                activeBubbles =
                    (
                        listOf(LiveCommBubble(comm, expiresAt)) +
                            state.activeBubbles.filterNot { it.comm.commKey == comm.commKey }
                    ).filter { it.expiresAtMs > now }
                        .take(MAX_MESH_ACTIVE_BUBBLES),
            )
        }
        scope.launch {
            delay(MESH_BUBBLE_LIFETIME_MS)
            val pruneNow = currentTimeMillis()
            _meshState.update { st ->
                st.copy(activeBubbles = st.activeBubbles.filter { it.expiresAtMs > pruneNow })
            }
        }
    }

    fun toggleMeshListView() {
        _meshState.update { it.copy(isListView = !it.isListView) }
    }

    fun selectMeshMachine(machineId: String?) {
        scope.launch { settingsStore.setMeshSelectedMachine(machineId) }
        _meshState.update { current ->
            val snapshot = current.snapshot
            val groupChips = buildMeshGroupChips(snapshot, machineId)
            var nextGroup = current.selectedGroupId
            if (snapshot != null) {
                if (nextGroup != null && groupChips.none { it.groupId == nextGroup }) nextGroup = null
                if (nextGroup == null && groupChips.isNotEmpty()) {
                    nextGroup = mostActiveMeshGroup(snapshot, groupChips, machineId)
                }
            }
            current
                .copy(
                    selectedMachineId = machineId,
                    selectedGroupId = nextGroup,
                    groupChips = groupChips,
                ).withComputedLayout(meshLayoutEngine, meshViewportWidth, meshViewportHeight)
        }
        refreshMesh(machineId)
    }

    fun selectMeshGroup(groupId: String?) {
        scope.launch { settingsStore.setMeshSelectedGroup(groupId) }
        _meshState.update { current ->
            current
                .copy(selectedGroupId = groupId)
                .withComputedLayout(meshLayoutEngine, meshViewportWidth, meshViewportHeight)
        }
    }

    fun onMeshViewportSizeChanged(
        width: Float,
        height: Float,
    ) {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        if (w == meshViewportWidth && h == meshViewportHeight && _meshState.value.layoutResult != null) return
        meshViewportWidth = w
        meshViewportHeight = h
        _meshState.update { it.withComputedLayout(meshLayoutEngine, meshViewportWidth, meshViewportHeight) }
    }

    fun selectMeshNode(agentId: String) {
        val nodes = _meshState.value.snapshot?.nodes ?: return
        val node = nodes.firstOrNull { it.agentId == agentId } ?: return
        _meshState.update {
            it.copy(nodeDetail = MeshNodeDetail(node = node, isLoadingComms = true), conversationDetail = null)
        }
        scope.launch {
            val comms =
                when (val r = httpClient.getMeshAgentComms(agentId)) {
                    is ApiResult.Ok -> r.value.items
                    else -> emptyList()
                }
            _meshState.update { st ->
                val detail = st.nodeDetail?.takeIf { it.node.agentId == agentId } ?: return@update st
                st.copy(nodeDetail = detail.copy(recentComms = comms, isLoadingComms = false))
            }
        }
    }

    fun dismissMeshNodeDetail() {
        _meshState.update { it.copy(nodeDetail = null) }
    }

    fun onMeshEdgeClick(edgeId: String) {
        val snapshot = _meshState.value.snapshot ?: return
        val edge = snapshot.edges.firstOrNull { it.edgeId == edgeId } ?: return
        openMeshConversationBetween(edge.a, edge.b)
    }

    fun openMeshConversation(peerAgentId: String) {
        val nodeA = _meshState.value.nodeDetail?.node ?: return
        openMeshConversationBetween(nodeA.agentId, peerAgentId)
    }

    private fun openMeshConversationBetween(
        agentA: String,
        agentB: String,
    ) {
        val currentSnapshot = _meshState.value.snapshot
        val allNodes = currentSnapshot?.nodes.orEmpty()
        val nodesById = allNodes.associateBy { it.agentId }
        val nodeA = nodesById[agentA]
        val nodeB = nodesById[agentB]
        val nameA =
            nodeA?.let { formatMeshDisplayName(it, allNodes) }
                ?: if (!isIdLooking(agentA) && !agentA.equals("Agent", ignoreCase = true)) agentA else "Agente"
        val nameB =
            nodeB?.let { formatMeshDisplayName(it, allNodes) }
                ?: if (!isIdLooking(agentB) && !agentB.equals("Agent", ignoreCase = true)) agentB else "Agente"
        val detail =
            MeshConversationDetail(
                agentA = agentA,
                agentB = agentB,
                agentAName = nameA,
                agentBName = nameB,
                isLoading = true,
            )
        _meshState.update { it.copy(nodeDetail = null, conversationDetail = detail) }
        scope.launch { loadMeshConversationPage(detail, before = null, append = false) }
    }

    fun loadMoreMeshConversation() {
        val detail = _meshState.value.conversationDetail ?: return
        val before = detail.nextBefore ?: return
        if (detail.isLoading || detail.isLoadingMore) return
        _meshState.update { st ->
            st.copy(conversationDetail = st.conversationDetail?.copy(isLoadingMore = true))
        }
        scope.launch { loadMeshConversationPage(detail, before = before, append = true) }
    }

    private suspend fun loadMeshConversationPage(
        detail: MeshConversationDetail,
        before: Long?,
        append: Boolean,
    ) {
        val r = httpClient.getMeshConversation(detail.agentA, detail.agentB, before, limit = 50)
        _meshState.update { st ->
            val current =
                st.conversationDetail?.takeIf {
                    it.agentA == detail.agentA && it.agentB == detail.agentB
                } ?: return@update st
            val response = (r as? ApiResult.Ok)?.value
            val items = response?.items.orEmpty()
            val merged =
                if (append) {
                    (current.items + items).distinctBy { it.commKey }
                } else {
                    items.distinctBy { it.commKey }
                }
            val nextBefore =
                if (response != null) {
                    response.nextBefore
                } else if (append) {
                    current.nextBefore
                } else {
                    null
                }
            st.copy(
                conversationDetail =
                    current.copy(
                        items = merged,
                        nextBefore = nextBefore,
                        isLoading = false,
                        isLoadingMore = false,
                    ),
            )
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
                is ApiResult.Ok -> {
                    val myDeviceId = secrets.current()?.deviceId
                    _devices.value = r.value.map { it.copy(current = it.current || it.deviceId == myDeviceId) }
                }

                else -> {
                    _actionMessage.value = "No se pudo cargar la lista de dispositivos"
                }
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        scope.launch {
            if (httpClient.revokeDevice(deviceId) is ApiResult.Ok) {
                _devices.update { list -> list.filterNot { it.deviceId == deviceId } }
                _actionMessage.value = "Dispositivo revocado"
            } else {
                _actionMessage.value = "No se pudo revocar"
            }
        }
    }

    fun editMachine(
        machineId: String,
        name: String,
        context: String,
        color: String,
    ) {
        scope.launch {
            val patch =
                MachinePatch(
                    name = name.trim().ifEmpty { null },
                    context = context.trim().ifEmpty { null },
                    color = color,
                )
            when (val r = httpClient.patchMachine(machineId, patch)) {
                is ApiResult.Ok -> {
                    _machines.update { list ->
                        list.map { if (it.machineId == machineId) r.value else it }
                    }
                    _actionMessage.value = "PC actualizada"
                }

                else -> {
                    _actionMessage.value = "No se pudo guardar"
                }
            }
        }
    }

    fun revokeMachine(machineId: String) {
        scope.launch {
            if (httpClient.revokeMachine(machineId) is ApiResult.Ok) {
                _machines.update { list -> list.filterNot { it.machineId == machineId } }
                _actionMessage.value = "PC revocada"
            } else {
                _actionMessage.value = "No se pudo revocar"
            }
        }
    }

    fun generatePairCode() {
        _pairCode.value = null
        scope.launch {
            when (val r = httpClient.createMachineCode()) {
                is ApiResult.Ok -> _pairCode.value = r.value.code
                else -> _actionMessage.value = "No se pudo generar el código"
            }
        }
    }

    fun sendTestNotification() {
        scope.launch { httpClient.pushTest() }
    }

    fun setDirectPush(enabled: Boolean) {
        scope.launch { sessionPolicy.applyPushChannels(enabled) }
    }

    private suspend fun pushNotifyPrefs() {
        val cfg = _settings.value
        val prefs = cfg.notifyPrefs.copy(categories = cfg.notifyPrefs.categories + ("criticalAlways" to cfg.criticalAlways))
        httpClient.putPrefs(prefs)
    }

    fun setCategoryEnabled(
        category: String,
        enabled: Boolean,
    ) {
        scope.launch {
            val cur = _settings.value.notifyPrefs
            val next = cur.copy(categories = cur.categories + (category to enabled))
            settingsStore.setNotifyPrefs(next)
            _settings.update { it.copy(notifyPrefs = next) }
            pushNotifyPrefs()
        }
    }

    fun setCriticalAlways(enabled: Boolean) {
        scope.launch {
            settingsStore.setCriticalAlways(enabled)
            _settings.update { it.copy(criticalAlways = enabled) }
            pushNotifyPrefs()
        }
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
            _settings.update { it.copy(notifyPrefs = next) }
            pushNotifyPrefs()
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
            val qh = if (from.isBlank() || to.isBlank()) null else QuietHours(from, to, tz.ifBlank { null })
            val next = cur.copy(machines = cur.machines + (machineId to prevMachine.copy(quietHours = qh)))
            settingsStore.setNotifyPrefs(next)
            _settings.update { it.copy(notifyPrefs = next) }
            pushNotifyPrefs()
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
            _settings.update { it.copy(notifyPrefs = next) }
            pushNotifyPrefs()
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
            storeLock.withLock { backStack.clear() }
            _currentRoute.value = OnboardingRoute
        }
    }

    companion object {
        const val APP_VERSION = "0.7.0"
        const val SOLO_GROUP_ID = "solo"
        private const val MAX_MESH_LIVE_COMMS = 20
        private const val MAX_MESH_ACTIVE_BUBBLES = 50
        private const val MESH_BUBBLE_LIFETIME_MS = 6_000L
        private const val DEFAULT_MESH_VIEWPORT_SIZE = 800f

        fun buildAgentsUiState(
            machines: List<MachineSummary>,
            agents: List<AgentSummary>,
            selectedContext: String,
            refreshing: Boolean = false,
        ): AgentsUiState {
            val visibleMachines =
                if (selectedContext == AppSettings.ALL) {
                    machines
                } else {
                    machines.filter { it.context == selectedContext || it.machineId == selectedContext }
                }
            val visibleAgents =
                if (selectedContext == AppSettings.ALL) {
                    agents
                } else {
                    agents.filter { it.machineContext == selectedContext || it.machineId == selectedContext }
                }
            val byMachine = visibleAgents.groupBy { it.machineId }
            val groups =
                visibleMachines.mapNotNull { m ->
                    val list = byMachine[m.machineId].orEmpty()
                    if (list.isEmpty()) null else MachineGroup(m, sortAgentsWithinGroup(list))
                } +
                    byMachine
                        .filterKeys { id -> visibleMachines.none { it.machineId == id } }
                        .map { (id, list) ->
                            val first = list.first()
                            MachineGroup(
                                MachineSummary(id, first.machineName, first.machineContext, first.machineColor),
                                sortAgentsWithinGroup(list),
                            )
                        }
            val sortedGroups =
                groups.sortedByDescending { g ->
                    val hasPrompts = g.agents.any { it.openPromptCount > 0 }
                    val isWorking = g.agents.any { it.status == "working" && it.online }
                    val maxLastSeen = g.agents.maxOfOrNull { it.lastSeenAt ?: 0L } ?: 0L
                    (if (hasPrompts) 2_000_000_000_000L else 0L) +
                        (if (isWorking) 1_000_000_000_000L else 0L) +
                        maxLastSeen
                }
            return AgentsUiState(
                machines = machines,
                allAgents = agents,
                groups = sortedGroups,
                selected = selectedContext,
                isLoading = false,
                canSpawn = visibleMachines.any { it.hostOnline },
                refreshing = refreshing,
            )
        }

        private fun isSoloNode(node: MeshNode): Boolean = node.role == "solo" || node.groupId == null

        private fun meshMembersOf(
            snapshot: MeshSnapshot,
            groupId: String?,
            machineId: String? = null,
        ): Set<String> {
            val machineNodes = snapshot.nodes.asSequence().filter { machineId == null || it.machineId == machineId }
            if (groupId == SOLO_GROUP_ID) {
                return machineNodes.filter(::isSoloNode).mapTo(mutableSetOf()) { it.agentId }
            }
            val group = snapshot.groups.firstOrNull { it.groupId == groupId } ?: return emptySet()
            val groupMembers = group.memberAgentIds.toSet()
            return machineNodes
                .filter { it.groupId == groupId || it.agentId in groupMembers }
                .mapTo(mutableSetOf()) { it.agentId }
        }

        private fun buildMeshMachineChips(
            machines: List<MachineSummary>,
            snapshot: MeshSnapshot?,
        ): List<MeshMachineChip> {
            val nodeCounts =
                snapshot
                    ?.nodes
                    .orEmpty()
                    .groupingBy { it.machineId }
                    .eachCount()
            val byId = linkedMapOf<String, MeshMachineChip>()
            machines.forEach { m ->
                byId[m.machineId] =
                    MeshMachineChip(
                        machineId = m.machineId,
                        name = m.name,
                        color = m.color,
                        icon = m.icon,
                        agentCount = nodeCounts[m.machineId] ?: m.agentCount,
                    )
            }
            snapshot?.nodes.orEmpty().forEach { node ->
                if (node.machineId !in byId) {
                    byId[node.machineId] =
                        MeshMachineChip(
                            machineId = node.machineId,
                            name = node.machineName,
                            color = node.machineColor,
                            agentCount = nodeCounts[node.machineId] ?: 0,
                        )
                }
            }
            val allAgentCount = snapshot?.nodes?.size ?: machines.sumOf { it.agentCount }
            return listOf(MeshMachineChip(machineId = null, name = "", color = "", agentCount = allAgentCount)) + byId.values
        }

        private fun buildMeshGroupChips(
            snapshot: MeshSnapshot?,
            machineId: String? = null,
        ): List<MeshGroupChip> {
            if (snapshot == null) return emptyList()
            val groups =
                snapshot.groups.mapNotNull { group ->
                    val count = meshMembersOf(snapshot, group.groupId, machineId).size
                    if (machineId != null && count == 0) {
                        null
                    } else {
                        MeshGroupChip(
                            groupId = group.groupId,
                            label = group.label.ifEmpty { group.groupId },
                            memberCount = count,
                        )
                    }
                }
            val soloNodes = snapshot.nodes.filter { (machineId == null || it.machineId == machineId) && isSoloNode(it) }
            return groups +
                if (soloNodes.isNotEmpty()) {
                    listOf(MeshGroupChip(SOLO_GROUP_ID, "", isSolo = true, memberCount = soloNodes.size))
                } else {
                    emptyList()
                }
        }

        private fun mostActiveMeshGroup(
            snapshot: MeshSnapshot,
            chips: List<MeshGroupChip>,
            machineId: String? = null,
        ): String? =
            chips
                .maxWithOrNull(
                    compareBy<MeshGroupChip> { meshActivityFor(snapshot, it, machineId) }
                        .thenByDescending { it.groupId.orEmpty() },
                )?.groupId

        private fun meshActivityFor(
            snapshot: MeshSnapshot,
            chip: MeshGroupChip,
            machineId: String? = null,
        ): Long {
            val members = meshMembersOf(snapshot, chip.groupId, machineId)
            val nodeActivity =
                snapshot.nodes
                    .filter { it.agentId in members }
                    .mapNotNull { it.lastCommAt }
                    .maxOrNull() ?: Long.MIN_VALUE
            val edgeActivity =
                snapshot.edges
                    .filter { it.a in members && it.b in members }
                    .mapNotNull { it.lastAt }
                    .maxOrNull() ?: Long.MIN_VALUE
            return maxOf(nodeActivity, edgeActivity)
        }

        private fun MeshUiState.withComputedLayout(
            layoutEngine: MeshLayoutEngine,
            width: Float,
            height: Float,
        ): MeshUiState {
            val mesh = snapshot ?: return copy(layoutResult = null)
            val selectedGroupMembers =
                if (selectedGroupId != null && selectedGroupId != SOLO_GROUP_ID) {
                    mesh.groups
                        .firstOrNull { it.groupId == selectedGroupId }
                        ?.memberAgentIds
                        ?.toSet()
                        .orEmpty()
                } else {
                    emptySet()
                }
            val filteredNodes =
                mesh.nodes.filter { node ->
                    val machineMatches = selectedMachineId == null || node.machineId == selectedMachineId
                    val groupMatches =
                        when (selectedGroupId) {
                            SOLO_GROUP_ID -> isSoloNode(node)
                            null -> true
                            else -> node.groupId == selectedGroupId || node.agentId in selectedGroupMembers
                        }
                    machineMatches && groupMatches
                }
            val visibleIds = filteredNodes.mapTo(mutableSetOf()) { it.agentId }
            val filteredEdges = mesh.edges.filter { it.a in visibleIds && it.b in visibleIds }
            val layout =
                layoutEngine.computeLayout(
                    filteredNodes,
                    filteredEdges,
                    width,
                    height,
                    layoutResult,
                )
            return copy(layoutResult = layout)
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
    biometricAuthorizer: BiometricAuthorizer = PermissiveBiometricAuthorizer,
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
        biometricAuthorizer = biometricAuthorizer,
        startSocketConnection = { socketPolicy.start() },
        stopSocketConnection = { socketPolicy.stop() },
        retrySocketConnection = { socketPolicy.retryNow() },
        isSocketConnected = { socketPolicy.isConnected },
        sendSocketText = { socketPolicy.send(it) },
        connState = socketPolicy.state,
    ).also { controllerRef = it }
}
