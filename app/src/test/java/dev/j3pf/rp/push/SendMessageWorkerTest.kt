package dev.j3pf.rp.push

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.OutboxEntity
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Characterizes outbox delivery, clientMsgId preservation, and retry semantics on unchanged production. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendMessageWorkerTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var db: RpDatabase
    private lateinit var secrets: SecretStore
    private lateinit var channels: NotificationChannels
    private lateinit var chat: ChatRepository
    private lateinit var scope: CoroutineScope

    private val clientMsgId = "01MSG"
    private val relayId = "r1"
    private val agentId = "machine:worker-agent"
    private val message = UserMessage(agentId = agentId, clientMsgId = clientMsgId, text = "hola", deviceId = "01DEVICE")
    private val outboxRow by lazy {
        OutboxEntity(
            clientMsgId = clientMsgId,
            relayId = relayId,
            agentId = agentId,
            json = RpJson.encodeToString(UserMessage.serializer(), message),
            createdAt = 1L,
        )
    }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        server = MockWebServer().also { it.start() }
        db = inMemoryDb(context)
        secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        channels = NotificationChannels(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val bus = MessageBus()
        val client = testClient()
        val api = RelayApi(client, secrets)
        val connection = RelayConnection(secrets, bus, client, scope)
        val settings = SettingsRepository(context)
        val notificationFactory = NotificationFactory(context, channels, settings, NotificationDeduper(db.dedup()))
        val prompts = PromptsRepository(context, db.prompts(), secrets, bus, connection, api, notificationFactory, scope)
        chat = ChatRepository(context, db.chat(), db.outbox(), connection, bus, secrets, prompts, api, scope, db.agents())
    }

    @After
    fun tearDown() {
        if (::scope.isInitialized) scope.cancel()
        if (::db.isInitialized) db.close()
        if (::server.isInitialized) server.shutdown()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun worker(attempt: Int = 0): SendMessageWorker {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? =
                    if (workerClassName == SendMessageWorker::class.java.name) {
                        SendMessageWorker(appContext, workerParameters, chat, channels)
                    } else {
                        null
                    }
            }
        return TestListenableWorkerBuilder
            .from(context, SendMessageWorker::class.java)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
    }

    private suspend fun seedOutbox() = db.outbox().put(outboxRow)

    private fun takeMessageId() =
        RpJson.decodeFromString(UserMessage.serializer(), server.takeRequest().body.readUtf8()).clientMsgId

    @Test
    fun `200 deletes the sent clientMsgId and a second empty-outbox run sends nothing`() = runBlocking {
        seedOutbox()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(null, db.outbox().get(clientMsgId))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/agents/machine:worker-agent/messages", request.path)
        assertEquals(clientMsgId, RpJson.decodeFromString(UserMessage.serializer(), request.body.readUtf8()).clientMsgId)

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(null, db.outbox().get(clientMsgId))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `500 retains the same outbox row and retries before attempt limit then fails`() = runBlocking {
        seedOutbox()
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        assertEquals(ListenableWorker.Result.retry(), worker(attempt = 0).doWork())
        assertEquals(outboxRow, db.outbox().get(clientMsgId))
        assertEquals(ListenableWorker.Result.failure(), worker(attempt = 8).doWork())
        assertEquals(outboxRow, db.outbox().get(clientMsgId))
        assertEquals(listOf("01MSG", "01MSG"), listOf(takeMessageId(), takeMessageId()))
        assertEquals(2, server.requestCount)
    }
}
