package dev.j3pf.rp.data

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.RpApp
import dev.j3pf.rp.data.db.AgentDao
import dev.j3pf.rp.data.db.AgentEntity
import dev.j3pf.rp.data.db.ChatDao
import dev.j3pf.rp.data.db.ChatRecordEntity
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.ProgressBody
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.di.AppModule
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerfRepoTest {
    private lateinit var context: Application
    private lateinit var db: RpDatabase
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = inMemoryDb(context)
        scheduler = TestCoroutineScheduler()
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun appModuleOkHttpUses45SecondForegroundPingInterval() {
        val client = AppModule.okHttp()
        assertEquals(45_000, client.pingIntervalMillis)
    }

    @Test
    fun rpAppInjectsAppScopeForOffMainThreadAttachmentsCleanup() {
        val field = RpApp::class.java.declaredFields.firstOrNull { it.name == "appScope" }
        assertNotNull(field, "RpApp must inject @AppScope CoroutineScope for background attachments cleanup")
    }

    @Test
    fun androidSecretStoreLoadsLegacySixKeysAndUpgradesToSessionV2OnSave() {
        val legacySession =
            Session(
                relayId = "legacy-relay-id",
                relayUrl = "https://legacy.example:10000",
                relayName = "Legacy Desktop",
                deviceId = "dev-legacy",
                deviceToken = "legacy-secret-token",
                pushKey = "legacy-push-key",
            )
        val helperStore =
            object : SecretStore(context) {
                fun exposeAead(): Aead = buildAead()
            }
        val aead = helperStore.exposeAead()

        fun enc(
            key: String,
            value: String,
        ): String = Base64.encodeToString(aead.encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

        val prefs = context.getSharedPreferences(SecretStore.PREFS, Context.MODE_PRIVATE)
        prefs
            .edit()
            .clear()
            .putString(SecretStore.K_RELAY_ID, enc(SecretStore.K_RELAY_ID, legacySession.relayId))
            .putString(SecretStore.K_URL, enc(SecretStore.K_URL, legacySession.relayUrl))
            .putString(SecretStore.K_NAME, enc(SecretStore.K_NAME, legacySession.relayName))
            .putString(SecretStore.K_DEVICE_ID, enc(SecretStore.K_DEVICE_ID, legacySession.deviceId))
            .putString(SecretStore.K_TOKEN, enc(SecretStore.K_TOKEN, legacySession.deviceToken))
            .putString(SecretStore.K_PUSH_KEY, enc(SecretStore.K_PUSH_KEY, legacySession.pushKey))
            .commit()

        val loaded = SecretStore(context)
        assertEquals(legacySession, loaded.current())

        val upgraded = legacySession.copy(relayName = "Upgraded Desktop")
        loaded.save(upgraded)
        assertEquals(1, prefs.all.size)
        assertTrue(prefs.contains(SecretStore.K_SESSION_V2))
        assertEquals(upgraded, SecretStore(context).current())
    }

    @Test
    fun chatRepositoryBatchesPersistEventAndFlushesOnTimerBatchSizeAndSettled() =
        runBlocking {
            val insertCalls = AtomicInteger(0)
            val countingChatDao =
                object : ChatDao by db.chat() {
                    override suspend fun insert(items: List<ChatRecordEntity>) {
                        insertCalls.incrementAndGet()
                        db.chat().insert(items)
                    }
                }
            val secrets = FakeSecretStore(context, testSession("https://relay.test", relayId = "r1"))
            val bus = MessageBus()
            val connection = RelayConnection(secrets, bus, testClient(), scope)
            val api = RelayApi(testClient(), secrets)
            val settingsRepo = SettingsRepository(context)
            val factory =
                NotificationFactory(
                    context,
                    NotificationChannels(context),
                    settingsRepo,
                    NotificationDeduper(db.dedup()),
                )
            val promptsRepo = PromptsRepository(context, db.prompts(), secrets, bus, connection, api, factory, scope)
            val repo =
                ChatRepository(
                    context = context,
                    chatDao = countingChatDao,
                    outboxDao = db.outbox(),
                    connection = connection,
                    bus = bus,
                    secrets = secrets,
                    prompts = promptsRepo,
                    api = api,
                    scope = scope,
                    agentDao = db.agents(),
                )

            fun event(
                seq: Long,
                type: String,
            ) = AgentEvent(
                agentId = "a1",
                epoch = "e1",
                seq = seq,
                e = JsonObject(mapOf("type" to JsonPrimitive(type))),
            )

            // 1. Five non-terminal events buffer in memory with 0 DB writes until 500 ms elapses.
            for (seq in 1L..5L) {
                repo.persistEventDirect("a1", event(seq, "tool_execution_start"))
            }
            assertEquals(0, insertCalls.get(), "Non-terminal events should batch instead of writing per event")
            assertEquals(0, db.chat().count("r1", "a1"))

            scheduler.advanceTimeBy(ChatRepository.EVENT_FLUSH_DELAY_MS)
            scheduler.runCurrent()
            assertEquals(1, insertCalls.get(), "5 buffered events must flush in a single DB insert")
            assertEquals(5, db.chat().count("r1", "a1"))

            // 2. Terminal event (agent_settled) flushes immediately along with prior buffered events.
            repo.persistEventDirect("a1", event(6L, "tool_execution_end"))
            assertEquals(1, insertCalls.get())
            repo.persistEventDirect("a1", event(7L, "agent_settled"))
            assertEquals(2, insertCalls.get(), "agent_settled must flush buffer immediately")
            assertEquals(7, db.chat().count("r1", "a1"))

            // 3. Buffer reaching 32 items flushes immediately in a single insert call.
            for (seq in 8L until 8L + ChatRepository.EVENT_BATCH_MAX_SIZE) {
                repo.persistEventDirect("a1", event(seq, "todo_update"))
            }
            assertEquals(3, insertCalls.get(), "32 buffered events must trigger a single immediate flush")
            assertEquals(7 + ChatRepository.EVENT_BATCH_MAX_SIZE, db.chat().count("r1", "a1"))

            // 4. loadInitialState flushes any buffered event before querying recent rows.
            repo.persistEventDirect("a1", event(100L, "tool_execution_start"))
            assertEquals(3, insertCalls.get())
            val state = repo.loadInitialStateDirect("a1")
            assertNotNull(state)
            assertEquals(4, insertCalls.get(), "loadInitialState must flush pending buffered events first")
            assertEquals(100L, state.lastSeq)
        }

    @Test
    fun agentsRepositoryApplyAgentsUpdateUsesBatchedGetByIdsInsteadOfNPlusOneGet() =
        runBlocking {
            val singleGetCalls = AtomicInteger(0)
            val batchGetCalls = AtomicInteger(0)
            val countingAgentDao =
                object : AgentDao by db.agents() {
                    override suspend fun get(
                        relayId: String,
                        agentId: String,
                    ): AgentEntity? {
                        singleGetCalls.incrementAndGet()
                        return db.agents().get(relayId, agentId)
                    }

                    override suspend fun getByIds(
                        relayId: String,
                        agentIds: List<String>,
                    ): List<AgentEntity> {
                        batchGetCalls.incrementAndGet()
                        return db.agents().getByIds(relayId, agentIds)
                    }
                }
            val secrets = FakeSecretStore(context, testSession("https://relay.test", relayId = "r1"))
            val bus = MessageBus()
            val connection = RelayConnection(secrets, bus, testClient(), scope)
            val repo =
                AgentsRepository(
                    dao = countingAgentDao,
                    secrets = secrets,
                    bus = bus,
                    connection = connection,
                    settings = SettingsRepository(context),
                    scope = scope,
                )

            val batch =
                (1..10).map { idx ->
                    AgentSummary(
                        agentId = "agent-$idx",
                        machineId = "m1",
                        machineName = "PC",
                        machineContext = "work",
                        cwd = "/tmp/$idx",
                        sessionName = "Session $idx",
                        status = "working",
                        online = true,
                        lastSeenAt = 1000L + idx,
                        todoCompleted = 1,
                        todoTotal = 3,
                        activeTask = "Task $idx",
                    )
                }

            repo.applyAgentsUpdate(AgentsUpdate(upsert = batch))
            assertEquals(0, singleGetCalls.get(), "applyAgentsUpdate must not call single-row dao.get in a loop")
            assertEquals(1, batchGetCalls.get(), "applyAgentsUpdate must batch lookup via dao.getByIds")

            // Second update with null telemetry while working must preserve existing telemetry using batched lookup.
            val telemetryLessUpdate =
                batch.map { it.copy(todoCompleted = 0, todoTotal = 0, activeTask = null, lastSeenAt = 2000L) }
            repo.applyAgentsUpdate(AgentsUpdate(upsert = telemetryLessUpdate))
            assertEquals(0, singleGetCalls.get())
            assertEquals(2, batchGetCalls.get())
            val saved = db.agents().get("r1", "agent-1")!!
            assertEquals(1, saved.todoCompleted)
            assertEquals(3, saved.todoTotal)
            assertEquals("Task 1", saved.activeTask)
        }

    @Test
    fun progressBodyThrottlesSlowUploadBy100MsInterval() {
        val total = 10L * 1024 * 1024 // 10 MiB
        val chunkBytes = 16_384 // ~0.15% per chunk (well under 5%)
        var fakeClockMs = 1_000L
        val reports = mutableListOf<Pair<Long, Long>>()
        val slowBody =
            object : RequestBody() {
                override fun contentType() = null

                override fun contentLength() = total

                override fun writeTo(sink: BufferedSink) {
                    val buf = ByteArray(chunkBytes)
                    var written = 0L
                    while (written < total) {
                        val toWrite = minOf(chunkBytes.toLong(), total - written).toInt()
                        sink.write(buf, 0, toWrite)
                        sink.flush()
                        written += toWrite
                        fakeClockMs += 50L // 2 chunks = 100 ms
                    }
                }
            }

        val body = ProgressBody(slowBody, onProgress = { sent, t -> reports += sent to t }, clockMs = { fakeClockMs })
        val sink = Buffer()
        body.writeTo(sink)

        assertEquals(total, sink.size)
        assertEquals(0L to total, reports.first())
        assertEquals(total to total, reports.last())
        // 640 chunks at 50 ms each -> emits ~320 time-throttled updates (half as many as chunks) rather than every chunk.
        assertTrue(reports.size in 300..340, "expected ~321 reports for 50 ms/chunk over 100 ms throttle, got ${reports.size}")
    }
}
