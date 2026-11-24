package dev.j3pf.rp.ui.shared

import dev.j3pf.rp.chat.DiffKind
import dev.j3pf.rp.chat.DiffLine
import dev.j3pf.rp.client.http.RelayHttpRequest
import dev.j3pf.rp.client.http.RelayHttpResponse
import dev.j3pf.rp.client.http.RelayHttpTransport
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.data.settings.SettingsKeys
import dev.j3pf.rp.protocol.AgentArchivedList
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import dev.j3pf.rp.ui.agents.resolveAgentCardDisplayMetadata
import dev.j3pf.rp.ui.chat.DeliveryChoice
import dev.j3pf.rp.ui.chat.MAX_DIFF_VIEW_LINES
import dev.j3pf.rp.ui.chat.capDiffLines
import dev.j3pf.rp.ui.chat.infoDialogRows
import dev.j3pf.rp.ui.chat.shouldAutoPinOnLocalOrAck
import dev.j3pf.rp.ui.components.MAX_HIGHLIGHT_CODE_LENGTH
import dev.j3pf.rp.ui.components.buildHighlightedAnnotatedString
import dev.j3pf.rp.ui.components.isAllowedMarkdownUri
import dev.j3pf.rp.ui.nav.AgentsRoute
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.nav.MeshRoute
import dev.j3pf.rp.ui.nav.PairRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedAppControllerHardeningTest {
    private fun sampleSession() =
        Session(
            relayId = "0123456789abcdef",
            relayUrl = "https://relay.example.com",
            relayName = "Studio Mac",
            deviceId = "dev-1",
            deviceToken = "tok-1",
            pushKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        )

    @Test
    fun loadArchivedUsesAgentArchivedRequestTypeTracksPaginationLoadingAndDeduplicates() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val sentEnvelopes = mutableListOf<Envelope>()
        lateinit var controller: SharedAppController
        controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                isSocketConnected = { true },
                sendSocketText = { raw ->
                    sentEnvelopes += Envelope.parse(raw)
                    true
                },
                connState = MutableStateFlow(ConnState.Connected),
            )

        controller.loadArchived()
        val firstReq = sentEnvelopes.last()
        assertEquals(MsgType.AGENT_ARCHIVED, firstReq.t)
        assertTrue(controller.archivedLoading.value)

        controller.wsClient.onText(
            Envelope
                .of(
                    MsgType.AGENT_ARCHIVED_LIST,
                    AgentArchivedList(
                        agents =
                            listOf(
                                ArchivedAgentSummary(agentId = "m1:a1", machineId = "m1", machineName = "Mac", archivedAt = 200L, lastSeenAt = 200L),
                                ArchivedAgentSummary(agentId = "m1:a2", machineId = "m1", machineName = "Mac", archivedAt = 100L, lastSeenAt = 100L),
                            ),
                        nextBefore = 100L,
                    ),
                    re = firstReq.id,
                ).encode(),
        )
        assertFalse(controller.archivedLoading.value)
        assertEquals(listOf("m1:a1", "m1:a2"), controller.archivedAgents.value.map { it.agentId })

        // Load second page: loading flag must become true while in-flight, and duplicate IDs must be deduplicated
        controller.loadMoreArchived()
        assertTrue(controller.archivedLoading.value)
        val secondReq = sentEnvelopes.last()
        assertEquals(MsgType.AGENT_ARCHIVED, secondReq.t)

        // Second concurrent loadMoreArchived while loading must be ignored
        val countBeforeDup = sentEnvelopes.size
        controller.loadMoreArchived()
        assertEquals(countBeforeDup, sentEnvelopes.size)

        controller.wsClient.onText(
            Envelope
                .of(
                    MsgType.AGENT_ARCHIVED_LIST,
                    AgentArchivedList(
                        agents =
                            listOf(
                                ArchivedAgentSummary(agentId = "m1:a2", machineId = "m1", machineName = "Mac", archivedAt = 100L, lastSeenAt = 100L),
                                ArchivedAgentSummary(agentId = "m1:a3", machineId = "m1", machineName = "Mac", archivedAt = 50L, lastSeenAt = 50L),
                            ),
                        nextBefore = null,
                    ),
                    re = secondReq.id,
                ).encode(),
        )
        assertFalse(controller.archivedLoading.value)
        assertEquals(listOf("m1:a1", "m1:a2", "m1:a3"), controller.archivedAgents.value.map { it.agentId })
    }

    @Test
    fun pushNotifyPrefsSendsTopLevelCriticalAlwaysAndCanonicalWireCategories() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val prefsPayloads = mutableListOf<NotifyPrefs>()
        val transport =
            object : RelayHttpTransport {
                override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
                    if (request.url.endsWith("/v1/devices/me/prefs") && request.body != null) {
                        prefsPayloads += RpJson.decodeFromString(NotifyPrefs.serializer(), request.body!!)
                    }
                    return ApiResult.Ok(RelayHttpResponse(200, """{"ok":true}"""))
                }
            }
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                httpTransport = transport,
            )

        controller.setCategoryEnabled(NotifyCategory.APPROVALS, false)
        controller.setCategoryEnabled(NotifyCategory.FINISHED, true)
        controller.setMachineCategory("m1", NotifyCategory.TERMINAL, false)
        controller.setCriticalAlways(false)

        val latest = prefsPayloads.last()
        assertEquals(false, latest.criticalAlways)
        assertFalse("criticalAlways" in latest.categories)
        assertEquals(false, latest.categories[NotifyCategory.WIRE_PERMISSION])
        assertEquals(true, latest.categories[NotifyCategory.WIRE_SETTLED])
        assertFalse(NotifyCategory.APPROVALS in latest.categories)
        assertEquals(false, latest.machines["m1"]?.categories?.get(NotifyCategory.WIRE_TERMINAL_WAIT))
    }

    @Test
    fun appLockStartsLockedOnColdLaunchAndNeverFailsOpenOnUnavailable() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val settingsBacking = InMemorySecretKeyValueBacking().apply {
            putString(SettingsKeys.K_APP_LOCK, "true")
        }
        val settingsStore = KeyValueSettingsStore(backing = settingsBacking)
        var authResult = BiometricAuthResult.UNAVAILABLE
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                settingsStore = settingsStore,
                biometricAuthorizer =
                    object : BiometricAuthorizer {
                        override suspend fun authenticate(reason: String): BiometricAuthResult = authResult
                    },
            )

        assertTrue(controller.settings.value.appLock)
        assertTrue(controller.isAppLocked.value, "Cold start with appLock=true and session must start locked")

        controller.unlockApp()
        assertTrue(controller.isAppLocked.value, "UNAVAILABLE must keep app locked (fail closed)")
        assertEquals(
            "Configura Face ID, Touch ID o código para desbloquear",
            controller.appLockMessage.value,
        )

        authResult = BiometricAuthResult.OK
        controller.unlockApp()
        assertFalse(controller.isAppLocked.value)
        assertNull(controller.appLockMessage.value)
    }

    @Test
    fun switchToNormalSendsCanonicalSlashCommandAndHandlesRejectionErrors() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val sentUserMessages = mutableListOf<UserMessage>()
        var nextAckError: String? = null
        lateinit var controller: SharedAppController
        controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                isSocketConnected = { true },
                sendSocketText = { raw ->
                    val env = Envelope.parse(raw)
                    if (env.t == MsgType.USER_MESSAGE) {
                        val msg = env.body<UserMessage>()
                        sentUserMessages += msg
                        val ack =
                            if (nextAckError != null) {
                                UserMessageAck(
                                    clientMsgId = msg.clientMsgId,
                                    disposition = "rejected",
                                    error = nextAckError,
                                )
                            } else {
                                UserMessageAck(
                                    clientMsgId = msg.clientMsgId,
                                    disposition = "delivered",
                                )
                            }
                        controller.wsClient.onText(
                            Envelope.of(MsgType.USER_MESSAGE_ACK, ack, re = env.id).encode(),
                        )
                    }
                    true
                },
                connState = MutableStateFlow(ConnState.Connected),
            )

        val session = controller.chatSessions.open("m1:a1")

        // Verify switchToNormal sends "/gentle:mode normal"
        controller.switchToNormal(session)
        assertEquals("/gentle:mode normal", sentUserMessages.last().text)

        // Verify SendResult.Failed("interactive_unavailable") sets banner & message
        nextAckError = "interactive_unavailable"
        controller.sendChatMessage(session, "do it", DeliveryChoice.AUTO, Delivery.FOLLOW_UP, working = false)
        assertTrue(controller.interactiveUnavailable.value)
        assertNotNull(controller.actionMessage.value)

        // Verify SendResult.Failed("herdr_unavailable") sets banner & message
        controller.consumeActionMessage()
        nextAckError = "herdr_unavailable"
        controller.sendChatMessage(session, "do it", DeliveryChoice.AUTO, Delivery.FOLLOW_UP, working = false)
        assertTrue(controller.interactiveUnavailable.value)
        assertNotNull(controller.actionMessage.value)

        // Verify SendResult.Failed("agent_blocked") surfaces message without flipping interactiveUnavailable
        controller.consumeActionMessage()
        nextAckError = "agent_blocked"
        controller.sendChatMessage(session, "do it", DeliveryChoice.AUTO, Delivery.FOLLOW_UP, working = false)
        assertFalse(controller.interactiveUnavailable.value)
        assertNotNull(controller.actionMessage.value)
    }

    @Test
    fun appBackgroundAndForegroundManageForegroundWebSocketLifecycle() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        var starts = 0
        var stops = 0
        var retries = 0
        var machineFetches = 0
        val transport =
            object : RelayHttpTransport {
                override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
                    if (request.url.endsWith("/v1/machines")) machineFetches++
                    return ApiResult.Ok(RelayHttpResponse(200, """{"machines":[],"prompts":[]}"""))
                }
            }
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                httpTransport = transport,
                startSocketConnection = { starts++ },
                stopSocketConnection = { stops++ },
                retrySocketConnection = { retries++ },
            )

        val startsAfterInit = starts
        val stopsAfterInit = stops
        val retriesAfterInit = retries
        val fetchesAfterInit = machineFetches

        controller.onAppBackgrounded()
        assertEquals(stopsAfterInit + 1, stops, "Backgrounding must stop foreground WS")

        controller.onAppForegrounded()
        assertTrue(starts > startsAfterInit, "Foregrounding must restart WS")
        assertTrue(retries > retriesAfterInit, "Foregrounding must retry/reconnect WS")
        assertTrue(machineFetches > fetchesAfterInit, "Foregrounding must refresh state")
    }

    @Test
    fun normalizeRelayUrlEnforcesStrictHttpsAndLoopbackOnlyHttp() {
        assertEquals("https://relay.example.com", SharedAppController.normalizeRelayUrl("relay.example.com"))
        assertEquals("https://relay.example.com:8443", SharedAppController.normalizeRelayUrl("HTTPS://relay.example.com:8443/"))
        assertEquals("http://localhost:8080", SharedAppController.normalizeRelayUrl("http://localhost:8080/"))
        assertEquals("http://127.0.0.1:9000", SharedAppController.normalizeRelayUrl("http://127.0.0.1:9000"))

        // Reject remote cleartext HTTP
        assertNull(SharedAppController.normalizeRelayUrl("http://relay.example.com"))
        assertNull(SharedAppController.normalizeRelayUrl("http://192.168.1.50:8080"))
        assertNull(SharedAppController.normalizeRelayUrl("http://localhost.evil.com"))

        // Reject unknown schemes
        assertNull(SharedAppController.normalizeRelayUrl("ws://relay.example.com"))
        assertNull(SharedAppController.normalizeRelayUrl("wss://relay.example.com"))
        assertNull(SharedAppController.normalizeRelayUrl("ftp://relay.example.com"))
        assertNull(SharedAppController.normalizeRelayUrl("javascript://alert(1)"))

        // Reject userinfo, query, fragment, whitespace, and control chars
        assertNull(SharedAppController.normalizeRelayUrl("https://user:pass@relay.example.com"))
        assertNull(SharedAppController.normalizeRelayUrl("http://localhost@evil.com"))
        assertNull(SharedAppController.normalizeRelayUrl("https://relay.example.com?foo=bar"))
        assertNull(SharedAppController.normalizeRelayUrl("https://relay.example.com#section"))
        assertNull(SharedAppController.normalizeRelayUrl("https://relay.example.com/path with space"))
        assertNull(SharedAppController.normalizeRelayUrl("https://relay.example.com\r\nX-Injected: 1"))
        assertNull(SharedAppController.normalizeRelayUrl("https://relay.example.com/\u007F"))
    }

    @Test
    fun onQrScannedWithActiveSessionNavigatesToPairRouteWithoutAutoPairing() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        var httpCalls = 0
        val transport =
            object : RelayHttpTransport {
                override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
                    if (request.url.contains("/healthz") || request.url.contains("/v1/pair")) {
                        httpCalls++
                    }
                    return ApiResult.Ok(RelayHttpResponse(200, """{"ok":true}"""))
                }
            }
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                httpTransport = transport,
            )

        controller.onQrScanned("rp://pair?u=https%3A%2F%2Fnew-relay.example.com&c=NEW123")

        assertEquals(PairRoute(url = "https://new-relay.example.com", code = "NEW123"), controller.currentRoute.value)
        assertEquals(0, httpCalls, "Must NOT auto-call pair() when a session is already active")
        assertEquals("https://relay.example.com", secrets.current()?.relayUrl)
    }

    @Test
    fun parsePairingQrPreservesUnescapedUtf8Characters() {
        val parsed = SharedAppController.parsePairingQr("rp://pair?u=https://estación.example.com/niño&c=CÓDIGO1")
        assertNotNull(parsed)
        assertEquals("https://estación.example.com/niño", parsed.first)
        assertEquals("CÓDIGO1", parsed.second)
    }

    @Test
    fun meshRefreshCancelsStaleJobAndNavigatingAwaySendsMeshUnsubscribe() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val firstResponse = CompletableDeferred<ApiResult<RelayHttpResponse>>()
        val secondResponse = CompletableDeferred<ApiResult<RelayHttpResponse>>()
        val sentEnvelopes = mutableListOf<Envelope>()
        var meshCalls = 0
        val transport =
            object : RelayHttpTransport {
                override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
                    if (request.url.contains("/v1/mesh")) {
                        meshCalls++
                        return if (meshCalls == 1) firstResponse.await() else secondResponse.await()
                    }
                    return ApiResult.Ok(RelayHttpResponse(200, "{}"))
                }
            }
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                httpTransport = transport,
                isSocketConnected = { true },
                sendSocketText = { raw ->
                    sentEnvelopes += Envelope.parse(raw)
                    true
                },
                connState = MutableStateFlow(ConnState.Connected),
            )

        controller.selectTab(AppTab.MESH)
        assertEquals(MeshRoute, controller.currentRoute.value)
        controller.selectMeshMachine("m2")

        val snapM2 =
            MeshSnapshot(
                nodes = listOf(MeshNode(agentId = "m2:a1", machineId = "m2", machineName = "M2", name = "M2 Agent", status = "idle")),
            )
        secondResponse.complete(ApiResult.Ok(RelayHttpResponse(200, RpJson.encodeToString(snapM2))))

        // Stale first response completes AFTER m2 response -> must NOT overwrite m2 snapshot
        val snapAll =
            MeshSnapshot(
                nodes = listOf(MeshNode(agentId = "m1:old", machineId = "m1", machineName = "M1", name = "Old Agent", status = "idle")),
            )
        firstResponse.complete(ApiResult.Ok(RelayHttpResponse(200, RpJson.encodeToString(snapAll))))

        assertEquals(listOf("m2:a1"), controller.meshState.value.snapshot?.nodes?.map { it.agentId })

        // Navigating away from MeshRoute must send MESH_UNSUBSCRIBE
        sentEnvelopes.clear()
        controller.selectTab(AppTab.AGENTS)
        assertEquals(AgentsRoute, controller.currentRoute.value)
        assertTrue(sentEnvelopes.any { it.t == MsgType.MESH_UNSUBSCRIBE }, "Expected MESH_UNSUBSCRIBE when leaving MeshRoute")
    }

    @Test
    fun answerPromptGuardsBusyPromptBeforeBiometricAuthToPreventDoubleTap() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val authGate = CompletableDeferred<BiometricAuthResult>()
        var authCalls = 0
        var answerCalls = 0
        val transport =
            object : RelayHttpTransport {
                override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
                    if (request.url.endsWith("/answer")) {
                        answerCalls++
                        return ApiResult.Ok(RelayHttpResponse(200, """{"ok":true}"""))
                    }
                    return ApiResult.Ok(RelayHttpResponse(200, "{}"))
                }
            }
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                httpTransport = transport,
                biometricAuthorizer =
                    object : BiometricAuthorizer {
                        override suspend fun authenticate(reason: String): BiometricAuthResult {
                            authCalls++
                            return authGate.await()
                        }
                    },
            )
        controller.wsClient.onText(
            Envelope
                .of(
                    MsgType.APP_WELCOME,
                    AppWelcome(
                        openPrompts =
                            listOf(
                                Prompt(
                                    promptId = "p-double",
                                    agentId = "m1:a1",
                                    kind = PromptKind.CONFIRM,
                                    category = PromptCategory.PERMISSION,
                                    danger = Danger.HIGH,
                                    title = "Run dangerous command?",
                                ),
                            ),
                    ),
                ).encode(),
        )

        // First tap suspends inside biometric auth
        controller.answerPrompt("p-double", Answer.confirm(true))
        assertEquals(1, authCalls)
        assertEquals("p-double", controller.busyPromptId.value)

        // Second tap while biometric auth is still pending must be ignored
        controller.answerPrompt("p-double", Answer.confirm(true))
        assertEquals(1, authCalls, "Double tap before biometric finishes must not re-invoke biometrics")

        authGate.complete(BiometricAuthResult.OK)
        assertEquals(1, answerCalls)
        assertNull(controller.busyPromptId.value)
    }

    @Test
    fun discardDiffFileSurfacesWsErrorAndNotConnected() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        var connected = false
        lateinit var controller: SharedAppController
        controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                isSocketConnected = { connected },
                sendSocketText = { raw ->
                    val env = Envelope.parse(raw)
                    if (env.t == MsgType.HOST_GIT_DISCARD) {
                        controller.wsClient.onText(
                            Envelope
                                .of(MsgType.ERROR, ErrorBody(code = "internal", message = "git checkout failed"), re = env.id)
                                .encode(),
                        )
                    }
                    true
                },
            )
        val agent = AgentSummary(agentId = "m1:a1", machineId = "m1", cwd = "/repo")

        controller.discardDiffFile(agent, "src/Main.kt")
        assertNotNull(controller.actionMessage.value, "NotConnected discard must surface error")

        controller.consumeActionMessage()
        connected = true
        controller.discardDiffFile(agent, "src/Main.kt")
        assertTrue(
            controller.actionMessage.value?.contains("git checkout failed") == true,
            "WsReply.Error discard must surface error message, got ${controller.actionMessage.value}",
        )
    }

    @Test
    fun buildAgentsUiStateSortsMachineGroupsLexicographicallyEvenWithNullLastSeenIn2026() {
        val epoch2026 = 1_795_000_000_000L
        val machines =
            listOf(
                MachineSummary(machineId = "m-idle", name = "IdleRecent"),
                MachineSummary(machineId = "m-working", name = "WorkingNullTs"),
                MachineSummary(machineId = "m-prompt", name = "PromptNullTs"),
            )
        val agents =
            listOf(
                AgentSummary(
                    agentId = "m-idle:1",
                    machineId = "m-idle",
                    status = "idle",
                    online = true,
                    lastSeenAt = epoch2026,
                ),
                AgentSummary(
                    agentId = "m-working:1",
                    machineId = "m-working",
                    status = "working",
                    online = true,
                    lastSeenAt = null,
                ),
                AgentSummary(
                    agentId = "m-prompt:1",
                    machineId = "m-prompt",
                    status = "idle",
                    online = true,
                    openPromptCount = 1,
                    lastSeenAt = null,
                ),
            )

        val state = SharedAppController.buildAgentsUiState(machines, agents, AppSettings.ALL)
        assertEquals(
            listOf("m-prompt", "m-working", "m-idle"),
            state.groups.map { it.machine.machineId },
        )
    }

    @Test
    fun chatHistoryStoreBoundsEntriesWithLruEviction() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                isSocketConnected = { true },
                sendSocketText = { true },
                connState = MutableStateFlow(ConnState.Connected),
            )

        // Populate 25 distinct chat histories via AGENT_HISTORY frames on opened sessions
        val total = SharedAppController.MAX_CACHED_CHAT_HISTORIES + 5
        for (i in 1..total) {
            val agentId = "m1:a$i"
            val session = controller.chatSessions.open(agentId)
            controller.wsClient.onText(
                Envelope
                    .of(
                        MsgType.AGENT_HISTORY,
                        AgentHistory(agentId = agentId, epoch = "1", events = emptyList()),
                    ).encode(),
            )
            controller.chatSessions.release(agentId)
            assertNotNull(session.state.value.epoch)
        }

        assertEquals(SharedAppController.MAX_CACHED_CHAT_HISTORIES, controller.cachedChatHistoryCount)
        val cachedIds = controller.cachedChatHistoryIds()
        assertFalse("m1:a1" in cachedIds, "Oldest entry must be evicted by LRU")
        assertTrue("m1:a$total" in cachedIds, "Most recent entry must be retained")
    }

    @Test
    fun disposeStopsSocketAndUnsubscribesMesh() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking()).apply { save(sampleSession()) }
        var stops = 0
        val sentEnvelopes = mutableListOf<Envelope>()
        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                stopSocketConnection = { stops++ },
                isSocketConnected = { true },
                sendSocketText = { raw ->
                    sentEnvelopes += Envelope.parse(raw)
                    true
                },
                connState = MutableStateFlow(ConnState.Connected),
            )

        controller.selectTab(AppTab.MESH)
        sentEnvelopes.clear()
        val stopsBefore = stops

        controller.dispose()

        assertEquals(stopsBefore + 1, stops)
        assertTrue(sentEnvelopes.any { it.t == MsgType.MESH_UNSUBSCRIBE })
    }

    @Test
    fun redactedFlagSuppressesSensitiveMetadataAndPreservesNonRedactedRendering() {
        val sensitive =
            AgentSummary(
                agentId = "m1:secret",
                machineId = "m1",
                machineName = "Workstation",
                sessionName = "Payroll Secret Refactor",
                cwd = "/Users/dev/secret-repo",
                gitBranch = "feat/payroll-keys",
                activeTask = "Rotate prod tokens",
                redacted = true,
            )
        val normal = sensitive.copy(redacted = false)

        val redactedCard = resolveAgentCardDisplayMetadata(sensitive)
        assertNull(redactedCard.folder)
        assertEquals("Sesión oculta", redactedCard.displayTopic)
        assertEquals("", redactedCard.projectContext)
        assertNull(redactedCard.activeTask)

        val normalCard = resolveAgentCardDisplayMetadata(normal)
        assertEquals("secret-repo", normalCard.folder)
        assertEquals("Payroll Secret Refactor", normalCard.displayTopic)
        assertEquals("📁 secret-repo · ⎇ feat/payroll-keys", normalCard.projectContext)
        assertEquals("Rotate prod tokens", normalCard.activeTask)

        val redactedInfo = infoDialogRows(sensitive).toMap()
        assertNull(redactedInfo["Carpeta"])
        assertNull(redactedInfo["Rama"])

        val normalInfo = infoDialogRows(normal).toMap()
        assertEquals("/Users/dev/secret-repo", normalInfo["Carpeta"])
        assertEquals("feat/payroll-keys", normalInfo["Rama"])
    }

    @Test
    fun autoPinOnLocalOrAckTriggersOnlyWhenLastItemKeyChanges() {
        assertTrue(shouldAutoPinOnLocalOrAck(lastItemKey = "local:1", settledLastKey = "ev:10"))
        assertTrue(shouldAutoPinOnLocalOrAck(lastItemKey = "ack:1", settledLastKey = "local:1"))
        assertFalse(shouldAutoPinOnLocalOrAck(lastItemKey = "local:1", settledLastKey = "local:1"))
        assertFalse(shouldAutoPinOnLocalOrAck(lastItemKey = "ack:1", settledLastKey = "ack:1"))
        assertFalse(shouldAutoPinOnLocalOrAck(lastItemKey = "ev:11", settledLastKey = "ev:10"))
        assertFalse(shouldAutoPinOnLocalOrAck(lastItemKey = null, settledLastKey = "local:1"))
    }

    @Test
    fun markdownUriAllowlistAndCodeAndDiffGuardsProtectUi() {
        assertTrue(isAllowedMarkdownUri("https://example.com/docs"))
        assertTrue(isAllowedMarkdownUri("HTTP://example.com"))
        assertTrue(isAllowedMarkdownUri("  HTTPS://example.com/a  "))

        assertFalse(isAllowedMarkdownUri("rp://pair?u=https://evil.com&c=123"))
        assertFalse(isAllowedMarkdownUri("intent://scan/#Intent;end"))
        assertFalse(isAllowedMarkdownUri("tel:+123456789"))
        assertFalse(isAllowedMarkdownUri("file:///etc/passwd"))
        assertFalse(isAllowedMarkdownUri("javascript:alert(1)"))

        // Blocks > 32 KB bypass syntax highlighting safely
        val largeCode = "val x = 1\n".repeat((MAX_HIGHLIGHT_CODE_LENGTH / 10) + 5)
        assertTrue(largeCode.length > MAX_HIGHLIGHT_CODE_LENGTH)
        val highlightedLarge = buildHighlightedAnnotatedString(largeCode, "kotlin", isDark = true)
        assertEquals(largeCode, highlightedLarge.text)
        assertTrue(highlightedLarge.spanStyles.isEmpty())

        // DiffView line cap truncates large diffs cleanly
        val manyLines = (1..(MAX_DIFF_VIEW_LINES + 75)).map { DiffLine(DiffKind.ADDED, "line $it") }
        val capped = capDiffLines(manyLines)
        assertEquals(MAX_DIFF_VIEW_LINES, capped.visible.size)
        assertEquals(75, capped.omittedCount)

        val smallLines = (1..10).map { DiffLine(DiffKind.SAME, "line $it") }
        val uncapped = capDiffLines(smallLines)
        assertEquals(10, uncapped.visible.size)
        assertEquals(0, uncapped.omittedCount)
    }
}
