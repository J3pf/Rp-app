package dev.j3pf.rp.data.repo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.PromptSheetGate
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
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
 * Wire-level contract proof for Unit P5 (`PromptSheetGate`): exercises the real
 * `PromptsRepository` exclusively through a port-typed `PromptSheetGate` reference
 * over a real WebSocket/HTTP server + in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptSheetGateWireContractTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var db: RpDatabase
    private lateinit var gate: PromptSheetGate
    private val wsRequests = LinkedBlockingQueue<Envelope>()

    @Volatile
    private var responder: ((Envelope) -> Envelope?)? = null

    private val prompt =
        Prompt(
            promptId = "01PROMPT",
            agentId = "m1:a1",
            kind = PromptKind.CONFIRM,
            title = "¿Permitir bash?",
            options = listOf(PromptOption("0", "Permitir"), PromptOption("2", "Denegar")),
        )

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
        val factory =
            NotificationFactory(
                context,
                NotificationChannels(context),
                SettingsRepository(context),
                NotificationDeduper(db.dedup()),
            )
        val repo =
            PromptsRepository(
                context,
                db.prompts(),
                secrets,
                bus,
                connection,
                RelayApi(testClient(), secrets),
                factory,
                scope,
            )
        gate = repo
        gate.start()
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
                        if (env.t == MsgType.PROMPT_ANSWER) {
                            wsRequests.put(env)
                            responder?.invoke(env)?.let { webSocket.send(it.encode()) }
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
    fun cancelledAnswerSendsExactWireFlagAndResolvesLocalPromptThroughPort() = runBlocking {
        connectWs()
        bus.publish(Envelope.of(MsgType.PROMPT_OPENED, prompt))
        withTimeout(5_000) { gate.open.first { it.isNotEmpty() } }
        assertEquals(prompt.promptId, gate.observe(prompt.promptId).first()?.promptId)

        responder = { req ->
            Envelope.of(MsgType.PROMPT_ANSWER_RESULT, PromptAnswerResult(ok = true), re = req.id)
        }
        val outcome = gate.answer(prompt.promptId, Answer.Cancelled)
        assertEquals(AnswerOutcome.Sent, outcome)

        val wireEnv = wsRequests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.PROMPT_ANSWER, wireEnv.t)
        assertTrue(wireEnv.encode().contains("\"cancelled\":true"))
        assertEquals(PromptAnswerRequest(prompt.promptId, Answer.Cancelled), wireEnv.body<PromptAnswerRequest>())

        withTimeout(5_000) { gate.open.first { it.isEmpty() } }
        assertTrue(gate.observe(prompt.promptId).first() != null)
    }

    @Test
    fun conflictErrorOverWsAndHttpFallbackMapToAlreadyResolvedThroughPort() = runBlocking {
        connectWs()
        bus.publish(Envelope.of(MsgType.PROMPT_OPENED, prompt))
        withTimeout(5_000) { gate.open.first { it.isNotEmpty() } }

        responder = { req ->
            Envelope.of(
                MsgType.ERROR,
                ErrorBody(code = ErrorCodes.CONFLICT, message = "already answered"),
                re = req.id,
            )
        }
        assertEquals(AnswerOutcome.AlreadyResolved, gate.answer(prompt.promptId, Answer.option("0")))
        wsRequests.poll(5, TimeUnit.SECONDS)

        // When WS is stopped, answer() falls back to HTTP POST /v1/prompts/{id}/answer
        connection.stop()
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"already_resolved"}"""))
        assertEquals(AnswerOutcome.AlreadyResolved, gate.answer("02PROMPT", Answer.confirm(true)))
        val httpReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", httpReq.method)
        assertEquals("/v1/prompts/02PROMPT/answer", httpReq.path)
        assertTrue(wsRequests.isEmpty(), "Zero WS prompt.answer frames when disconnected")
    }
}
