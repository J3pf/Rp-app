package dev.j3pf.rp.data.repo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.ChatSession
import dev.j3pf.rp.data.ports.ChatSessionGate
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSubscribe
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire-level contract proof for Unit P1 (`ChatSessionGate` + `ChatSession`): exercises the real
 * `ChatRepository` and `ChatRepository.ChatSession` exclusively through port-typed references
 * over a real WebSocket/HTTP server + in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatSessionGateWireContractTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var db: RpDatabase
    private lateinit var gate: ChatSessionGate
    private val wsRequests = LinkedBlockingQueue<Envelope>()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        bus = MessageBus()
        connection = RelayConnection(secrets, bus, testClient(), scope)
        db = inMemoryDb(context)
        val api = RelayApi(testClient(), secrets)
        val factory =
            NotificationFactory(
                context,
                NotificationChannels(context),
                SettingsRepository(context),
                NotificationDeduper(db.dedup()),
            )
        val prompts =
            PromptsRepository(
                context,
                db.prompts(),
                secrets,
                bus,
                connection,
                api,
                factory,
                scope,
            )
        gate =
            ChatRepository(
                context = context,
                chatDao = db.chat(),
                outboxDao = db.outbox(),
                connection = connection,
                bus = bus,
                secrets = secrets,
                prompts = prompts,
                api = api,
                scope = scope,
                agentDao = db.agents(),
            )
    }

    @After
    fun tearDown() {
        if (::connection.isInitialized) connection.stop()
        if (::scope.isInitialized) {
            scope.cancel()
            runBlocking { withTimeout(10_000) { scope.coroutineContext[Job]?.join() } }
        }
        if (::db.isInitialized) db.close()
        if (::server.isInitialized) runCatching { server.shutdown() }
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private suspend fun connectWs() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        if (env.t != MsgType.APP_HELLO) {
                            wsRequests.put(env)
                        }
                        if (env.t == MsgType.USER_MESSAGE) {
                            val req = env.body<UserMessage>()
                            val ack = Envelope.of(MsgType.USER_MESSAGE_ACK, UserMessageAck(req.clientMsgId, disposition = "sent"), re = env.id)
                            webSocket.send(ack.encode())
                        }
                    }
                },
            ),
        )
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
        server.takeRequest(5, TimeUnit.SECONDS)
    }

    @Test
    fun openSubscribeSendAndAbortFlowOverWireThroughPort() = runBlocking {
        connectWs()
        val session: ChatSession = gate.open("m1:a1")
        assertEquals("m1:a1", session.agentId)

        val subEnv = wsRequests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.AGENT_SUBSCRIBE, subEnv.t)
        assertEquals("m1:a1", subEnv.body<AgentSubscribe>().agentId)

        val sendRes = session.send("hola mundo", clientMsgId = "msg-1")
        assertTrue(sendRes is SendResult.Delivered)
        val msgEnv = wsRequests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.USER_MESSAGE, msgEnv.t)
        assertEquals("hola mundo", msgEnv.body<UserMessage>().text)

        session.abort()
        val abortEnv = wsRequests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.AGENT_ABORT, abortEnv.t)
        assertEquals("m1:a1", abortEnv.body<AgentRef>().agentId)

        gate.release("m1:a1")
    }

    @Test
    fun offlineQueueFlushOutboxAndClearWorkThroughPort() = runBlocking {
        val session: ChatSession = gate.open("m1:a1")
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val queued = session.send("offline msg", clientMsgId = "msg-offline-1")
        assertEquals(SendResult.Queued, queued)

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"clientMsgId":"msg-offline-1","disposition":"sent"}"""))
        assertTrue(gate.flushOutbox())
        // Drain initial failing HTTP attempt + successful flush attempt
        server.takeRequest(5, TimeUnit.SECONDS)
        val flushReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", flushReq.method)
        assertEquals("/v1/agents/m1:a1/messages", flushReq.path)
        assertTrue(flushReq.body.readUtf8().contains("offline msg"))

        gate.clear()
        assertTrue(db.outbox().all("relay-test").isEmpty())
    }
}
