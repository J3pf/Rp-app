package dev.j3pf.rp.data

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayConnectionTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var conn: RelayConnection
    private val serverSocket = LinkedBlockingQueue<WebSocket>()
    private val received = LinkedBlockingQueue<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        bus = MessageBus()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        conn = RelayConnection(ctx, FakeSecretStore(ctx, testSession(server.url("/").toString())), bus, testClient(), scope)
    }

    @After
    fun tearDown() {
        conn.stop()
        scope.cancel()
        server.shutdown()
    }

    private fun acceptSocket(onText: (WebSocket, String) -> Unit = { _, _ -> }) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { serverSocket.add(webSocket) }
            override fun onMessage(webSocket: WebSocket, text: String) { received.add(text); onText(webSocket, text) }
        }))
    }

    @Test
    fun `conecta con token, envia app hello y publica los mensajes en el bus`() = runBlocking {
        acceptSocket()
        conn.start()
        val req = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(req)
        assertEquals("/ws/app?mode=foreground", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
        val hello = Envelope.parse(received.poll(5, TimeUnit.SECONDS)!!)
        assertEquals(MsgType.APP_HELLO, hello.t)

        bus.messages.test(timeout = kotlin.time.Duration.parse("5s")) {
            serverSocket.poll(5, TimeUnit.SECONDS)!!.send(Envelope.of(MsgType.APP_WELCOME, AppWelcome()).encode())
            val env = awaitItem()
            assertEquals(MsgType.APP_WELCOME, env.t)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una peticion se resuelve con la respuesta cuyo re coincide con el id`() = runBlocking {
        acceptSocket { ws, text ->
            val env = Envelope.parse(text)
            if (env.t == MsgType.PROMPT_ANSWER) {
                ws.send(Envelope.of(MsgType.PROMPT_ANSWER_RESULT, PromptAnswerResult(ok = false, reason = "already_resolved"), re = env.id).encode())
            }
        }
        conn.start()
        withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
        val reply = conn.request(Envelope.of(MsgType.PROMPT_ANSWER, PromptAnswerRequest("P1", Answer.confirm(true)), id = "01REQ"))
        assertIs<WsReply.Ok>(reply)
        assertTrue(reply.envelope.body<PromptAnswerResult>().alreadyResolved)
    }

    @Test
    fun `error del relay se traduce a WsReply Error`() = runBlocking {
        acceptSocket { ws, text ->
            val env = Envelope.parse(text)
            if (env.t == MsgType.USER_MESSAGE) {
                ws.send("""{"v":1,"t":"error","re":"${env.id}","d":{"code":"agent_offline","message":"offline"}}""")
            }
        }
        conn.start()
        withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
        val reply = conn.request(Envelope(t = MsgType.USER_MESSAGE, id = "01X"))
        assertIs<WsReply.Error>(reply)
        assertEquals("agent_offline", reply.code)
    }

    @Test
    fun `sin conexion la peticion falla rapido`() = runBlocking {
        assertEquals(WsReply.NotConnected, conn.request(Envelope(t = MsgType.AGENT_ABORT, id = "01Y")))
    }

    @Test
    fun `cierre 4001 es fatal y no se reintenta`() = runBlocking {
        acceptSocket()
        conn.start()
        withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
        serverSocket.poll(5, TimeUnit.SECONDS)!!.close(4001, "revoked")
        withTimeout(5000) { while (conn.state.value !is ConnState.Fatal) kotlinx.coroutines.delay(20) }
        assertEquals(4001, (conn.state.value as ConnState.Fatal).code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `reconecta tras un cierre normal del servidor`() = runBlocking {
        acceptSocket()
        acceptSocket()
        conn.start()
        withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
        serverSocket.poll(5, TimeUnit.SECONDS)!!.close(1012, "restart") // reinicio del relay: reconexión inmediata
        withTimeout(8000) { while (server.requestCount < 2) kotlinx.coroutines.delay(20) }
        assertEquals(2, server.requestCount)
    }
}
