package dev.j3pf.rp.data.repo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var repoScope: CoroutineScope
    private lateinit var ioScope: CoroutineScope
    private lateinit var connection: RelayConnection
    private lateinit var bus: MessageBus
    private lateinit var repository: ChatRepository

    private val sockets = LinkedBlockingQueue<WebSocket>()
    private val outgoing = LinkedBlockingQueue<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        testScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        bus = MessageBus()
        connection = RelayConnection(context, secrets, bus, testClient(), repoScope)
        val db = inMemoryDb(context)
        val settingsRepo = SettingsRepository(context)
        val factory =
            dev.j3pf.rp.push
                .NotificationFactory(
                    context,
                    dev.j3pf.rp.push
                        .NotificationChannels(context),
                    settingsRepo,
                )
        val promptsRepo =
            PromptsRepository(
                context,
                db.prompts(),
                secrets,
                bus,
                connection,
                RelayApi(testClient(), secrets),
                factory,
                repoScope,
            )
        repository =
            ChatRepository(
                context = context,
                chatDao = db.chat(),
                outboxDao = db.outbox(),
                connection = connection,
                bus = bus,
                secrets = secrets,
                prompts = promptsRepo,
                api = RelayApi(testClient(), secrets),
                scope = repoScope,
                agentDao = db.agents(),
            )
    }

    @After
    fun tearDown() {
        connection.stop()
        sockets.forEach { runCatching { it.close(1000, "done") } }
        repoScope.cancel()
        server.shutdown()
    }

    private fun acceptSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: okhttp3.Response,
                    ) {
                        sockets.add(webSocket)
                        webSocket.send(Envelope.of(MsgType.APP_WELCOME, dev.j3pf.rp.protocol.AppWelcome()).encode())
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        outgoing.add(text)
                        val env = runCatching { Envelope.parse(text) }.getOrNull()
                        if (env?.t == MsgType.AGENT_SUBSCRIBE) {
                            webSocket.send(Envelope(t = "ok", re = env.id).encode())
                            val msgJson =
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "content" to
                                            kotlinx.serialization.json.JsonArray(
                                                listOf(
                                                    kotlinx.serialization.json.JsonObject(
                                                        mapOf(
                                                            "type" to kotlinx.serialization.json.JsonPrimitive("text"),
                                                            "text" to kotlinx.serialization.json.JsonPrimitive("hello"),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                    ),
                                )
                            val history =
                                AgentHistory(
                                    agentId = "test-agent",
                                    epoch = "E1",
                                    entries = listOf(dev.j3pf.rp.protocol.Entry(eid = "1", kind = dev.j3pf.rp.protocol.EntryKind.USER, msg = msgJson)),
                                )
                            webSocket.send(Envelope.of(MsgType.AGENT_HISTORY, history).encode())
                        }
                    }
                },
            ),
        )
    }

    private suspend fun connect() {
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
    }

    @Test
    fun `resync cancels previous timeout timer so stale timer cannot clear syncing during newer resync`() =
        runBlocking {
            acceptSocket()
            connect()

            val session = repository.open("test-agent")

            // Wait for subscription exchange
            session.subscribe()
            assertTrue(session.syncing.value)

            // Advance 4 seconds in virtual time (less than 6s timeout)
            testScheduler.advanceTimeBy(4_000)
            assertTrue(session.syncing.value, "Syncing should still be true at 4s")

            // Re-subscribe (newer resync) cancels previous 6s timer and starts fresh 6s timer
            session.subscribe()
            assertTrue(session.syncing.value, "Syncing remains true on new subscribe")

            // Advance 3 more seconds in virtual time (total 7s from first, 3s from second)
            testScheduler.advanceTimeBy(3_000)
            // If stale timer wasn't cancelled, it would have fired at 6s and cleared syncing!
            assertTrue(session.syncing.value, "Stale timer must NOT have cleared syncing at 7s total")

            // Advance another 3.5 seconds (6.5s from second subscribe)
            testScheduler.advanceTimeBy(3_500)
            assertFalse(session.syncing.value, "New timer expires after its full 6s duration")
        }

    @Test
    fun `agent history clears syncing immediately and cancels timeout timer`() =
        runBlocking {
            acceptSocket()
            connect()

            val session = repository.open("test-agent")

            // Await syncing becoming false when history arrives
            withTimeout(5_000) {
                session.syncing.first { !it }
            }
            assertFalse(session.syncing.value, "Syncing should be false once history arrives")
        }
}
