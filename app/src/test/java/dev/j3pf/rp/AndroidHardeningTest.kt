package dev.j3pf.rp

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.client.http.RelayHttpRequest
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.OkHttpRelayTransport
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SessionRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.di.AppModule
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.platform.BiometricAuthorizerAndroid
import dev.j3pf.rp.protocol.AgentArchivedList
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import dev.j3pf.rp.push.AnswerWorker
import dev.j3pf.rp.push.DirectPushService
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.push.PromptActionHandler
import dev.j3pf.rp.push.PromptActionReceiver
import dev.j3pf.rp.push.PushTokenProvider
import dev.j3pf.rp.ui.agents.AgentsViewModel
import dev.j3pf.rp.ui.chat.ChatViewModel
import dev.j3pf.rp.ui.chat.DeliveryChoice
import dev.j3pf.rp.ui.settings.SettingsViewModel
import dev.j3pf.rp.util.BiometricGate
import dev.j3pf.rp.util.stableId
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidHardeningTest {
    private lateinit var ctx: Application
    private var server: MockWebServer? = null
    private var db: RpDatabase? = null
    private var scope: CoroutineScope? = null
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
    }

    @After
    fun tearDown() {
        runCatching {
            runBlocking {
                scope?.coroutineContext?.get(Job)?.cancelAndJoin()
            }
        }
        scope = null
        runCatching { db?.close() }
        db = null
        runCatching { server?.shutdown() }
        server = null
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
        Dispatchers.resetMain()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun isolatedSettings(scope: CoroutineScope): SettingsRepository {
        val f = File.createTempFile("hardening-settings-", ".preferences_pb").also {
            it.delete()
            tempFiles += it
        }
        return SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { f }))
    }

    // ─────────────────────────── B-01: Network I/O & IOException ───────────────────────────

    @Test
    fun `B-01 OkHttpRelayTransport returns ApiResult Network on mid-body disconnect`() {
        runBlocking {
            val srv = MockWebServer().also { it.start(); server = it }
            srv.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", 1024)
                    .setBody("partial")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val transport = OkHttpRelayTransport(testClient())
            val result = transport.execute(RelayHttpRequest(method = "GET", url = srv.url("/test").toString()))
            assertIs<ApiResult.Network>(result, "Mid-body IOException must return ApiResult.Network instead of throwing")
        }
    }

    @Test
    fun `B-01 RelayApi uploadBlob returns ApiResult Network on mid-body disconnect`() {
        runBlocking {
            val srv = MockWebServer().also { it.start(); server = it }
            srv.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", 1024)
                    .setBody("{\"blobId\":")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
            val api = RelayApi(testClient(), secrets)
            val result = api.uploadBlob("a.txt", "text/plain", "hello".toRequestBody("text/plain".toMediaType())) { _, _ -> }
            assertIs<ApiResult.Network>(result, "uploadBlob mid-body IOException must return ApiResult.Network")
        }
    }

    // ─────────────────────────── B-02 + S-01: Biometrics ───────────────────────────

    @Test
    fun `B-02 RpApp eagerly injects BiometricAuthorizer and S-01 BiometricGate uses BIOMETRIC_STRONG on API 30+`() {
        val field = RpApp::class.java.declaredFields.firstOrNull { it.name == "biometricAuthorizer" }
        assertNotNull(field, "RpApp must eagerly inject biometricAuthorizer before MainActivity.onCreate()")

        val gate = BiometricGate()
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, gate.authenticators, "API 30+ must require BIOMETRIC_STRONG or DEVICE_CREDENTIAL")
    }

    // ─────────────────────────── B-04: NotifyPrefs wire contract ───────────────────────────

    @Test
    fun `B-04 SettingsViewModel sends top-level criticalAlways and canonical wire categories`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val srv = MockWebServer().also { it.start(); server = it }
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
        val bus = MessageBus()
        val client = testClient()
        val api = RelayApi(client, secrets)
        val conn = RelayConnection(secrets, bus, client, testScope)
        val settings = isolatedSettings(testScope)
        val channels = NotificationChannels(ctx)
        val machines = MachinesRepository(database.machines(), secrets, bus, api, channels, testScope)
        val agents = AgentsRepository(database.agents(), secrets, bus, conn, settings, testScope)
        val prompts = PromptsRepository(
            ctx,
            database.prompts(),
            secrets,
            bus,
            conn,
            api,
            NotificationFactory(ctx, channels, settings, NotificationDeduper(database.dedup())),
            testScope,
        )
        val chat = ChatRepository(ctx, database.chat(), database.outbox(), conn, bus, secrets, prompts, api, testScope, database.agents())
        val sessionRepo = SessionRepository(
            ctx, api, secrets, settings, conn, machines, agents, chat, prompts,
            HostRepository(conn, bus, testScope), MeshRepository(conn, bus, api, testScope),
            database.dedup(), object : PushTokenProvider { override suspend fun currentToken(): String? = null }, channels,
        )
        val vm = SettingsViewModel(ctx, secrets, settings, machines, agents, sessionRepo, api, channels, conn)
        try {
            srv.enqueue(MockResponse().setBody("{}"))
            vm.setCategory(NotifyCategory.APPROVALS, false).join()
            val req = srv.takeRequest(5, TimeUnit.SECONDS)!!
            val wire = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertEquals(true, wire.criticalAlways, "criticalAlways must be sent at top level")
            assertNull(wire.categories["criticalAlways"], "criticalAlways must NOT be stuffed into categories")
            assertEquals(false, wire.categories[NotifyCategory.WIRE_PERMISSION], "approvals must map to canonical wire key permission")
            assertNull(wire.categories[NotifyCategory.APPROVALS], "local channel name approvals must not leak on wire")
        } finally {
            vm.viewModelScope.cancel()
            conn.stop()
        }
    }

    // ─────────────────────────── W-04: Redacted pushes & VISIBILITY_PRIVATE ───────────────────────────

    @Test
    fun `W-04 NotificationFactory honors redacted flag and sets VISIBILITY_PRIVATE across notifications`() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val settings = isolatedSettings(testScope)
        val factory = NotificationFactory(ctx, NotificationChannels(ctx), settings, NotificationDeduper(database.dedup()))
        val nm = ctx.getSystemService(NotificationManager::class.java)

        factory.showPrompt(
            PushPromptBody(
                promptId = "01REDACT",
                agentId = "m:r",
                agentLabel = "api",
                machineId = "M1",
                machineName = "PC",
                kind = PromptKind.CONFIRM,
                category = PromptCategory.PERMISSION,
                title = "Secret command rm -rf /",
                message = "Sensitive body",
                danger = Danger.HIGH,
                redacted = true,
            ),
        )
        val promptNotif = shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01REDACT"))
        assertNotNull(promptNotif)
        assertEquals(Notification.VISIBILITY_PRIVATE, promptNotif.visibility)
        assertFalse(promptNotif.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Secret"))
        assertFalse(promptNotif.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().contains("Sensitive"))

        factory.showSettled(
            PushSettledBody(
                agentId = "m:r",
                agentLabel = "api",
                preview = "Top secret output",
                message = "More secret",
                machineId = "M1",
                machineName = "PC",
                redacted = true,
            ),
        )
        val settledNotif = shadowOf(nm).getNotification(NotificationFactory.SETTLED_TAG, stableId("settled:m:r"))
        assertNotNull(settledNotif)
        assertEquals(Notification.VISIBILITY_PRIVATE, settledNotif.visibility)
        assertFalse(settledNotif.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("secret", ignoreCase = true))

        factory.showSimple(
            PushKind.ERROR,
            JsonObject(
                mapOf(
                    "agentId" to JsonPrimitive("m:r"),
                    "machineId" to JsonPrimitive("M1"),
                    "detail" to JsonPrimitive("Secret stacktrace"),
                    "redacted" to JsonPrimitive(true),
                ),
            ),
        )
        val errNotif = shadowOf(nm).getNotification(NotificationFactory.SIMPLE_TAG, stableId("${PushKind.ERROR}:m:r"))
        assertNotNull(errNotif)
        assertEquals(Notification.VISIBILITY_PRIVATE, errNotif.visibility)
        assertFalse(errNotif.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("Secret"))

        factory.showWorking(
            AgentSummary(
                agentId = "m:w",
                machineId = "M1",
                machineName = "PC",
                sessionName = "Work",
                status = AgentStatus.WORKING,
                activeTask = "Editing",
            ),
        )
        val workingNotif = shadowOf(nm).getNotification(NotificationFactory.WORKING_TAG, stableId("working:m:w"))
        assertNotNull(workingNotif)
        assertEquals(Notification.VISIBILITY_PRIVATE, workingNotif.visibility)
    }

    // ─────────────────────────── C-05 + W-06: ChatViewModel rejection & COMMAND auth ───────────────────────────

    @Test
    fun `C-05 and W-06 ChatViewModel maps rejected SendResult Failed and gates COMMAND in Auto mode`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val srv = MockWebServer().also { it.start(); server = it }
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
        val bus = MessageBus()
        val conn = RelayConnection(secrets, bus, testClient(), testScope)
        val settings = isolatedSettings(testScope)
        val api = RelayApi(testClient(), secrets)
        val factory = NotificationFactory(ctx, NotificationChannels(ctx), settings, NotificationDeduper(database.dedup()))
        val prompts = PromptsRepository(ctx, database.prompts(), secrets, bus, conn, api, factory, testScope)
        val chats = ChatRepository(ctx, database.chat(), database.outbox(), conn, bus, secrets, prompts, api, testScope, database.agents())
        val agentsRepo = AgentsRepository(database.agents(), secrets, bus, conn, settings, testScope)
        val attachments = AttachmentsRepository(ctx, api)
        val host = HostRepository(conn, bus, testScope)

        val agentId = "m:auto-1"
        database.agents().upsert(
            listOf(
                AgentSummary(
                    agentId = agentId,
                    machineId = "m",
                    machineName = "PC",
                    sessionName = "Auto",
                    status = "idle",
                    online = true,
                    gentleMode = "auto",
                ).toEntity("r1"),
            ),
        )
        srv.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        webSocket.send(Envelope.of(MsgType.APP_WELCOME, AppWelcome()).encode())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val env = runCatching { Envelope.parse(text) }.getOrNull() ?: return
                        if (env.t == MsgType.AGENT_SUBSCRIBE) {
                            webSocket.send(Envelope(t = "ok", re = env.id).encode())
                        } else if (env.t == MsgType.USER_MESSAGE) {
                            val u = env.body<UserMessage>()
                            val ack = UserMessageAck(clientMsgId = u.clientMsgId, disposition = "rejected", error = "interactive_unavailable")
                            webSocket.send(Envelope.of(MsgType.USER_MESSAGE_ACK, ack, re = env.id).encode())
                        }
                    }
                },
            ),
        )
        conn.start()
        withTimeout(10_000) { conn.state.first { it == ConnState.Connected } }

        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("agentId" to agentId)),
            chats = chats,
            agentsRepo = agentsRepo,
            settingsRepo = settings,
            attachmentsRepo = attachments,
            hostRepo = host,
            connection = conn,
            gate = BiometricAuthorizerAndroid(BiometricGate(), ctx),
        )
        try {
            withTimeout(10_000) { vm.state.first { it.agent?.gentleMode == "auto" } }

            // W-06: COMMAND must require auth in Auto mode when requireUnlockToApprove is true
            vm.setChoice(DeliveryChoice.COMMAND)
            assertTrue(vm.needsAuth(), "DeliveryChoice.COMMAND must require auth in Auto mode")

            // C-05: rejected ack -> SendResult.Failed("interactive_unavailable") must set interactiveUnavailable=true & Spanish copy
            vm.send("hello") {}
            val failedState = withTimeout(10_000) {
                vm.state.first { it.message == "No se pudo teclear en la terminal: el mensaje no autoriza acciones en Auto" }
            }
            assertTrue(failedState.interactiveUnavailable, "interactiveUnavailable must be set on SendResult.Failed")
        } finally {
            vm.viewModelScope.cancel()
            conn.stop()
        }
    }

    // ─────────────────────────── W-06 + W-15: PromptActionHandler & AnswerWorker ───────────────────────────

    @Test
    fun `W-06 and W-15 PromptActionHandler computes auth locally and does not markAnswered on Failed`() = runBlocking {
        val srv = MockWebServer().also { it.start(); server = it }
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
        val settings = isolatedSettings(testScope)
        settings.setRequireUnlock(true)
        val deduper = NotificationDeduper(database.dedup())
        val factory = NotificationFactory(ctx, NotificationChannels(ctx), settings, deduper)
        val handler = PromptActionHandler(RelayApi(testClient(), secrets), factory, database.prompts(), database.outbox(), secrets)

        database.prompts().upsert(
            listOf(
                Prompt(
                    promptId = "01LOCKED",
                    agentId = "m:s",
                    kind = PromptKind.CONFIRM,
                    category = PromptCategory.PERMISSION,
                    danger = Danger.HIGH,
                    title = "Allow?",
                ).toEntity("r1", System.currentTimeMillis()),
            ),
        )
        val km = ctx.getSystemService(KeyguardManager::class.java)
        shadowOf(km).setIsDeviceLocked(true)

        // Tampered intent claims EXTRA_AUTH_REQUIRED = false, but device is locked & prompt is PERMISSION/HIGH
        val tampered = Intent(PromptActionReceiver.ACTION_ANSWER).apply {
            putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, "01LOCKED")
            putExtra(PromptActionReceiver.EXTRA_ANSWER_KIND, PromptActionReceiver.ANSWER_CONFIRM_YES)
            putExtra(PromptActionReceiver.EXTRA_AUTH_REQUIRED, false)
        }
        handler.handle(ctx, tampered)
        assertEquals(0, srv.requestCount, "Tampered EXTRA_AUTH_REQUIRED=false must NOT bypass lockscreen auth")

        // Unlock device; now test AnswerOutcome.Failed (400) dismisses notification WITHOUT calling deduper.markAnswered
        shadowOf(km).setIsDeviceLocked(false)
        factory.showPrompt(
            PushPromptBody(
                promptId = "01LOCKED",
                agentId = "m:s",
                agentLabel = "api",
                machineId = "M1",
                machineName = "PC",
                kind = PromptKind.CONFIRM,
                category = PromptCategory.PERMISSION,
                title = "Allow?",
            ),
        )
        srv.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad_request"}"""))
        handler.handle(ctx, tampered)
        assertFalse(deduper.answeredRecently("01LOCKED"), "AnswerOutcome.Failed must NOT mark prompt as answered in deduper")
    }

    @Test
    fun `W-15 AnswerWorker updates stuck Retrying notification on exhaustion and permanent failure`() = runBlocking {
        val srv = MockWebServer().also { it.start(); server = it }
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
        val settings = isolatedSettings(testScope)
        val channels = NotificationChannels(ctx)
        val factory = NotificationFactory(ctx, channels, settings, NotificationDeduper(database.dedup()))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val promptId = "01RETRYFAIL"
        val notifId = NotificationFactory.notificationId(promptId)

        factory.showRetrying(notifId, "api")
        val before = shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, notifId)
        assertEquals(ctx.getString(R.string.notif_retrying), before.extras.getString(Notification.EXTRA_TEXT))

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == AnswerWorker::class.java.name) {
                    AnswerWorker(appContext, workerParameters, RelayApi(testClient(), secrets), database.prompts(), secrets, factory, channels)
                } else null
        }
        val input = Data.Builder()
            .putString(AnswerWorker.KEY_PROMPT_ID, promptId)
            .putString(AnswerWorker.KEY_ANSWER, RpJson.encodeToString(Answer.serializer(), Answer.confirm(true)))
            .build()

        srv.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val worker = TestListenableWorkerBuilder.from(ctx, AnswerWorker::class.java)
            .setInputData(input)
            .setRunAttemptCount(AnswerWorker.MAX_ATTEMPTS)
            .setWorkerFactory(workerFactory)
            .build()
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())

        val after = shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, notifId)
        assertTrue(
            after == null || after.extras.getString(Notification.EXTRA_TEXT) != ctx.getString(R.string.notif_retrying),
            "Exhausted AnswerWorker must not leave Retrying notification stuck",
        )
    }

    // ─────────────────────────── W-11 + W-12: AgentsViewModel ───────────────────────────

    @Test
    fun `W-11 and W-12 AgentsViewModel deduplicates archived pagination and sorts groups lexicographically`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val srv = MockWebServer().also { it.start(); server = it }
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
        val bus = MessageBus()
        val conn = RelayConnection(secrets, bus, testClient(), testScope)
        val settings = isolatedSettings(testScope)
        val api = RelayApi(testClient(), secrets)
        val machines = MachinesRepository(database.machines(), secrets, bus, api, NotificationChannels(ctx), testScope)
        val agents = AgentsRepository(database.agents(), secrets, bus, conn, settings, testScope)
        val host = HostRepository(conn, bus, testScope)

        val epoch2026 = 1_795_000_000_000L
        database.machines().upsert(
            listOf(
                MachineSummary("m-prompt", "PromptPC", online = true).toEntity("r1"),
                MachineSummary("m-idle", "IdlePC", online = true).toEntity("r1"),
            ),
        )
        database.agents().upsert(
            listOf(
                AgentSummary(
                    agentId = "m-prompt:a1",
                    machineId = "m-prompt",
                    machineName = "PromptPC",
                    status = "waiting_input",
                    online = true,
                    lastSeenAt = null,
                ).toEntity("r1"),
                AgentSummary(
                    agentId = "m-idle:a2",
                    machineId = "m-idle",
                    machineName = "IdlePC",
                    status = "working",
                    online = true,
                    lastSeenAt = epoch2026,
                ).toEntity("r1"),
            ),
        )
        database.prompts().upsert(
            listOf(Prompt(promptId = "p1", agentId = "m-prompt:a1", title = "Ask").toEntity("r1", 100L)),
        )

        srv.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val env = Envelope.parse(text)
                        if (env.t == MsgType.AGENT_ARCHIVED) {
                            val before = runCatching { env.body<dev.j3pf.rp.protocol.AgentArchivedRequest>().before }.getOrNull()
                            val list = if (before == null) {
                                AgentArchivedList(
                                    agents = listOf(ArchivedAgentSummary("arc-1", "m1", archivedAt = 20L, lastSeenAt = 20L)),
                                    nextBefore = 10L,
                                )
                            } else {
                                AgentArchivedList(
                                    agents = listOf(
                                        ArchivedAgentSummary("arc-1", "m1", archivedAt = 20L, lastSeenAt = 20L),
                                        ArchivedAgentSummary("arc-2", "m1", archivedAt = 10L, lastSeenAt = 10L),
                                    ),
                                    nextBefore = null,
                                )
                            }
                            webSocket.send(Envelope.of(MsgType.AGENT_ARCHIVED, list, re = env.id).encode())
                        }
                    }
                },
            ),
        )
        conn.start()
        withTimeout(10_000) { conn.state.first { it == ConnState.Connected } }

        val vm = AgentsViewModel(machines, agents, settings, conn, host)
        try {
            val ui = withTimeout(10_000) { vm.state.first { it.groups.size == 2 } }
            assertEquals(
                listOf("m-prompt", "m-idle"),
                ui.groups.map { it.machine.machineId },
                "Prompted group with null lastSeenAt must outrank working group with 2026 epoch timestamp",
            )

            vm.loadArchived()
            withTimeout(10_000) { vm.lifecycleState.first { it.archivedNextBefore == 10L } }
            vm.loadMoreArchived()
            val paged = withTimeout(10_000) { vm.lifecycleState.first { it.archivedAgents.size == 2 } }
            assertEquals(listOf("arc-1", "arc-2"), paged.archivedAgents.map { it.agentId }, "Duplicate agentIds across pages must be deduplicated")
        } finally {
            vm.viewModelScope.cancel()
            conn.stop()
        }
    }

    // ─────────────────────────── W-13: APP_WELCOME preserves working telemetry ───────────────────────────

    @Test
    fun `W-13 AgentsRepository preserves working agent telemetry on APP_WELCOME`() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession("https://relay.example"))
        val bus = MessageBus()
        val conn = RelayConnection(secrets, bus, testClient(), testScope)
        val settings = isolatedSettings(testScope)
        val agentsRepo = AgentsRepository(database.agents(), secrets, bus, conn, settings, testScope)
        agentsRepo.start()

        database.agents().upsert(
            listOf(
                AgentSummary(
                    agentId = "m1:a1",
                    machineId = "m1",
                    status = AgentStatus.WORKING,
                    online = true,
                    todoCompleted = 4,
                    todoTotal = 6,
                    activeTask = "Compiling",
                ).toEntity("r1"),
            ),
        )
        bus.publish(
            Envelope.of(
                MsgType.APP_WELCOME,
                AppWelcome(
                    agents = listOf(
                        AgentSummary(
                            agentId = "m1:a1",
                            machineId = "m1",
                            status = AgentStatus.WORKING,
                            online = true,
                            todoCompleted = 0,
                            todoTotal = 0,
                            activeTask = null,
                        ),
                    ),
                ),
            ),
        )
        kotlinx.coroutines.delay(250)
        val saved = database.agents().get("r1", "m1:a1")!!
        assertEquals(4, saved.todoCompleted, "APP_WELCOME must preserve todoCompleted for working agents")
        assertEquals(6, saved.todoTotal, "APP_WELCOME must preserve todoTotal for working agents")
        assertEquals("Compiling", saved.activeTask, "APP_WELCOME must preserve activeTask for working agents")
    }

    // ─────────────────────────── C-11: AppModule CoroutineExceptionHandler ───────────────────────────

    @Test
    fun `C-11 AppModule appScope installs a CoroutineExceptionHandler`() {
        val appScope = AppModule.appScope()
        try {
            assertNotNull(
                appScope.coroutineContext[CoroutineExceptionHandler],
                "AppModule.appScope() must install a CoroutineExceptionHandler",
            )
        } finally {
            appScope.cancel()
        }
    }

    // ─────────────────────────── S-02: Camera temp file cleanup ───────────────────────────

    @Test
    fun `S-02 AttachmentsRepository prepare deletes temporary camera photo immediately`() = runBlocking {
        val cameraDir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val tempCam = File(cameraDir, "IMG_test_${System.nanoTime()}.jpg").apply {
            writeBytes(ByteArray(32) { 0x11 })
        }
        val repo = AttachmentsRepository(ctx, RelayApi(testClient(), FakeSecretStore(ctx, null)))
        val res = repo.prepare(Uri.fromFile(tempCam), compressImages = false)
        assertTrue(res.isSuccess)
        res.getOrNull()?.let { tempFiles += it.file }
        assertFalse(tempCam.exists(), "Raw camera temp photo in cacheDir/camera must be deleted immediately after prepare()")
    }

    // ─────────────────────────── C-09: DirectPushService direct flavor standby/Doze ───────────────────────────

    @Test
    fun `C-09 DirectPushService keeps socket alive on standby and Doze when no FCM channel exists`() {
        // Google-free direct flavor (hasAlternatePushChannel = false): MUST stay connected in background even on Doze/standby
        assertTrue(
            DirectPushService.shouldRunDirectSocket(
                inForeground = false,
                deviceIdleOrSaver = true,
                idleStandby = true,
                hasActiveWork = false,
                hasAlternatePushChannel = false,
            ),
        )
        // When app is in foreground, foreground RelayConnection owns the WS
        assertFalse(
            DirectPushService.shouldRunDirectSocket(
                inForeground = true,
                deviceIdleOrSaver = false,
                idleStandby = false,
                hasActiveWork = false,
                hasAlternatePushChannel = false,
            ),
        )
        // With active FCM channel, idle standby/Doze may sleep the direct WS
        assertFalse(
            DirectPushService.shouldRunDirectSocket(
                inForeground = false,
                deviceIdleOrSaver = true,
                idleStandby = false,
                hasActiveWork = false,
                hasAlternatePushChannel = true,
            ),
        )
        assertFalse(
            DirectPushService.shouldRunDirectSocket(
                inForeground = false,
                deviceIdleOrSaver = false,
                idleStandby = true,
                hasActiveWork = false,
                hasAlternatePushChannel = true,
            ),
        )
    }

    // ─────────────────────────── C-11: SecretStore transient error preservation ───────────────────────────

    @Test
    fun `C-11 SecretStore preserves encrypted credentials on transient KeyStore or IO errors`() {
        val store = dev.j3pf.rp.data.secure.SecretStore(ctx)
        val savedSession = testSession("https://relay.example")
        store.save(savedSession)
        val prefs = ctx.getSharedPreferences("rp_secrets", Context.MODE_PRIVATE)
        assertEquals(1, prefs.all.size)
        assertTrue(prefs.contains("session_v2"))

        // Simulate transient KeyStoreException on cold read: must NOT wipe prefs
        val transientStore =
            object : dev.j3pf.rp.data.secure.SecretStore(ctx) {
                override fun buildAead(): com.google.crypto.tink.Aead = throw java.security.KeyStoreException("Keymaster busy")
            }
        assertNull(transientStore.current())
        assertEquals(1, prefs.all.size, "Transient KeyStoreException must NOT wipe stored encrypted secrets")

        // Verify next healthy read still recovers the exact session intact
        val recovered = dev.j3pf.rp.data.secure.SecretStore(ctx)
        assertEquals(savedSession, recovered.current())

        // Verify classification helper directly
        assertFalse(dev.j3pf.rp.data.secure.SecretStore.isPermanentKeysetCorruption(java.io.IOException("disk busy")))
        assertFalse(dev.j3pf.rp.data.secure.SecretStore.isPermanentKeysetCorruption(java.security.KeyStoreException("locked")))
        assertFalse(dev.j3pf.rp.data.secure.SecretStore.isPermanentKeysetCorruption(RuntimeException("transient")))
        assertTrue(dev.j3pf.rp.data.secure.SecretStore.isPermanentKeysetCorruption(javax.crypto.AEADBadTagException("bad tag")))
        assertTrue(dev.j3pf.rp.data.secure.SecretStore.isPermanentKeysetCorruption(java.security.GeneralSecurityException("decryption failed")))
    }

    // ─────────────────────────── W-02: Cleartext localhost gated on BuildConfig.DEBUG ───────────────────────────

    @Test
    fun `W-02 SessionRepository normalizeUrl rejects cleartext localhost when allowCleartextLocalhost is false`() {
        assertNull(SessionRepository.normalizeUrl("http://10.0.2.2:8080", allowCleartextLocalhost = false))
        assertNull(SessionRepository.normalizeUrl("http://127.0.0.1:8080", allowCleartextLocalhost = false))
        assertNull(SessionRepository.normalizeUrl("http://localhost:8080", allowCleartextLocalhost = false))
        assertEquals("https://relay.example.com", SessionRepository.normalizeUrl("https://relay.example.com", allowCleartextLocalhost = false))
        assertEquals("http://10.0.2.2:8080", SessionRepository.normalizeUrl("http://10.0.2.2:8080", allowCleartextLocalhost = true))
    }

    // ─────────────────────────── S-03: MeshViewModel coalesced bubble expiration ───────────────────────────

    @Test
    fun `S-03 MeshViewModel coalesces bubble expiration onto a single job`() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val srv = MockWebServer().also { it.start(); server = it }
        val testScope = CoroutineScope(SupervisorJob() + dispatcher).also { scope = it }
        val database = inMemoryDb(ctx).also { db = it }
        val secrets = FakeSecretStore(ctx, testSession(srv.url("/").toString()))
        val bus = MessageBus()
        val conn = RelayConnection(secrets, bus, testClient(), testScope)
        val api = RelayApi(testClient(), secrets)
        val meshRepo = MeshRepository(conn, bus, api, testScope)
        val machinesRepo = MachinesRepository(database.machines(), secrets, bus, api, NotificationChannels(ctx), testScope)
        val settings = isolatedSettings(testScope)
        val vm = dev.j3pf.rp.ui.mesh.MeshViewModel(meshRepo, machinesRepo, settings, dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine())
        var fakeNow = 10_000L
        vm.clockMs = { fakeNow }
        try {
            bus.publish(
                Envelope.of(
                    MsgType.MESH_COMM,
                    dev.j3pf.rp.protocol.MeshCommEvent(
                        dev.j3pf.rp.protocol.MeshComm(commKey = "c1", fromName = "A", toName = "B", text = "first", at = 1L),
                    ),
                ),
            )
            fakeNow = 13_000L
            bus.publish(
                Envelope.of(
                    MsgType.MESH_COMM,
                    dev.j3pf.rp.protocol.MeshCommEvent(
                        dev.j3pf.rp.protocol.MeshComm(commKey = "c2", fromName = "A", toName = "B", text = "second", at = 2L),
                    ),
                ),
            )
            assertEquals(2, vm.uiState.value.activeBubbles.size)

            // Advance to c1 expiry (16_000L): c1 expires while c2 (expires at 19_000L) remains active
            fakeNow = 16_000L
            dispatcher.scheduler.advanceTimeBy(3_000L)
            dispatcher.scheduler.runCurrent()
            assertEquals(listOf("c2"), vm.uiState.value.activeBubbles.map { it.comm.commKey })

            // Advance to c2 expiry (19_000L): c2 expires and activeBubbles becomes empty
            fakeNow = 19_000L
            dispatcher.scheduler.advanceTimeBy(3_000L)
            dispatcher.scheduler.runCurrent()
            assertTrue(vm.uiState.value.activeBubbles.isEmpty())
        } finally {
            vm.viewModelScope.cancel()
            conn.stop()
        }
    }
}
