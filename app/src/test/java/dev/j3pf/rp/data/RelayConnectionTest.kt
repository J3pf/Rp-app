package dev.j3pf.rp.data

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayConnectionTest {
    private lateinit var server: MockWebServer
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var conn: RelayConnection
    private val serverSocket = LinkedBlockingQueue<WebSocket>()
    private val received = LinkedBlockingQueue<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        testScheduler = TestCoroutineScheduler()
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        bus = MessageBus()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        conn = RelayConnection(ctx, FakeSecretStore(ctx, testSession(server.url("/").toString())), bus, testClient(), scope)
    }

    @After
    fun tearDown() {
        conn.stop()
        while (true) {
            val ws = serverSocket.poll() ?: break
            runCatching { ws.close(1000, "bye") }
        }
        scope.cancel()
        server.shutdown()
    }

    private fun acceptSocket(onText: (WebSocket, String) -> Unit = { _, _ -> }) {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        serverSocket.add(webSocket)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        received.add(text)
                        onText(webSocket, text)
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
    }

    @Test
    fun `conecta con token, envia app hello y publica los mensajes en el bus`() {
        runBlocking {
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
    }

    @Test
    fun `una peticion se resuelve con la respuesta cuyo re coincide con el id`() {
        runBlocking {
            acceptSocket { ws, text ->
                val env = Envelope.parse(text)
                if (env.t == MsgType.PROMPT_ANSWER) {
                    ws.send(
                        Envelope
                            .of(
                                MsgType.PROMPT_ANSWER_RESULT,
                                PromptAnswerResult(ok = false, reason = "already_resolved"),
                                re = env.id,
                            ).encode(),
                    )
                }
            }
            conn.start()
            withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
            val reply = conn.request(Envelope.of(MsgType.PROMPT_ANSWER, PromptAnswerRequest("P1", Answer.confirm(true)), id = "01REQ"))
            assertIs<WsReply.Ok>(reply)
            assertTrue(reply.envelope.body<PromptAnswerResult>().alreadyResolved)
        }
    }

    @Test
    fun `error del relay se traduce a WsReply Error`() {
        runBlocking {
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
    }

    @Test
    fun `sin conexion la peticion falla rapido`() {
        runBlocking {
            assertEquals(WsReply.NotConnected, conn.request(Envelope(t = MsgType.AGENT_ABORT, id = "01Y")))
        }
    }

    @Test
    fun `cierre 4001 es fatal y no se reintenta`() {
        runBlocking {
            acceptSocket()
            conn.start()
            withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
            serverSocket.poll(5, TimeUnit.SECONDS)!!.close(4001, "revoked")
            withTimeout(5000) { while (conn.state.value !is ConnState.Fatal) kotlinx.coroutines.delay(20) }
            assertEquals(4001, (conn.state.value as ConnState.Fatal).code)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `reconecta tras un cierre normal del servidor`() {
        runBlocking {
            acceptSocket()
            acceptSocket()
            conn.start()
            withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
            serverSocket.poll(5, TimeUnit.SECONDS)!!.close(1012, "restart") // reinicio del relay: reconexión inmediata
            withTimeout(8000) { while (server.requestCount < 2) kotlinx.coroutines.delay(20) }
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `stop cancela peticiones pendientes de inmediato sin esperar timeout`() {
        runBlocking {
            acceptSocket()
            conn.start()
            withTimeout(5000) { while (!conn.isConnected) kotlinx.coroutines.delay(20) }
            val reqJob =
                async {
                    conn.request(Envelope(t = MsgType.USER_MESSAGE, id = "01STOP"), timeoutMs = 10_000)
                }
            kotlinx.coroutines.delay(100)
            conn.stop()
            val reply = withTimeout(1000) { reqJob.await() }
            assertEquals(WsReply.NotConnected, reply)
        }
    }

    @Test
    fun `HostRepository ignora hosts update malformado y limpia con clear`() {
        runBlocking {
            val hostRepo =
                dev.j3pf.rp.data.repo
                    .HostRepository(conn, bus, scope)
            hostRepo.start()
            kotlinx.coroutines.delay(50)
            bus.publish(Envelope(t = MsgType.HOSTS_UPDATE, d = kotlinx.serialization.json.JsonPrimitive("malformed")))
            bus.publish(
                Envelope.of(
                    MsgType.HOSTS_UPDATE,
                    dev.j3pf.rp.protocol.HostsUpdate(
                        upsert =
                            listOf(
                                dev.j3pf.rp.protocol
                                    .HostInfo(machineId = "M1", roots = listOf("/home")),
                            ),
                    ),
                ),
            )
            withTimeout(2000) { while (hostRepo.hosts.value.isEmpty()) kotlinx.coroutines.delay(20) }
            assertEquals(listOf("/home"), hostRepo.hosts.value["M1"]?.roots)
            hostRepo.clear()
            assertTrue(hostRepo.hosts.value.isEmpty())
        }
    }

    @Test
    fun `ChatRepository cuenta referencias de sesion por agentId`() {
        runBlocking {
            val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
            val db = dev.j3pf.rp.inMemoryDb(ctx)
            val secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
            val api =
                dev.j3pf.rp.data.net
                    .RelayApi(testClient(), secrets)
            val notifFactory =
                dev.j3pf.rp.push.NotificationFactory(
                    ctx,
                    dev.j3pf.rp.push
                        .NotificationChannels(ctx),
                    dev.j3pf.rp.data.repo
                        .SettingsRepository(ctx),
                )
            val promptsRepo =
                dev.j3pf.rp.data.repo
                    .PromptsRepository(ctx, db.prompts(), secrets, bus, conn, api, notifFactory, scope)
            val chatRepo =
                dev.j3pf.rp.data.repo
                    .ChatRepository(ctx, db.chat(), db.outbox(), conn, bus, secrets, promptsRepo, api, scope)

            val s1 = chatRepo.open("m:a")
            val s2 = chatRepo.open("m:a")
            assertTrue(s1 === s2)
            chatRepo.release("m:a")
            val s3 = chatRepo.open("m:a")
            assertTrue(s1 === s3)
            chatRepo.release("m:a")
            chatRepo.release("m:a")
            val s4 = chatRepo.open("m:a")
            assertTrue(s1 !== s4)
            chatRepo.clear()
        }
    }
}
