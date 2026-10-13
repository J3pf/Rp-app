package dev.j3pf.rp.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var conn: RelayConnection
    private lateinit var repo: PromptsRepository
    private val prompt = Prompt(
        promptId = "01PROMPT", agentId = "m:s", kind = PromptKind.SELECT, title = "¿Permitir?",
        options = listOf(PromptOption("0", "Permitir"), PromptOption("2", "Denegar")),
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        bus = MessageBus()
        val secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
        val api = RelayApi(testClient(), secrets)
        conn = RelayConnection(ctx, secrets, bus, testClient(), scope)
        val db = inMemoryDb(ctx)
        val factory = NotificationFactory(ctx, NotificationChannels(ctx), SettingsRepository(ctx))
        repo = PromptsRepository(ctx, db.prompts(), secrets, bus, conn, api, factory, scope)
        repo.start()
    }

    @After
    fun tearDown() {
        conn.stop()
        scope.cancel()
        server.shutdown()
    }

    private suspend fun publishOpened() {
        bus.publish(Envelope.of(MsgType.PROMPT_OPENED, prompt))
        withTimeout(5000) { while (repo.open.first().isEmpty()) delay(20) }
    }

    @Test
    fun `prompt abierto llega al bandeja y se cierra con prompt resolved`() {
        runBlocking {
            publishOpened()
            assertEquals("01PROMPT", repo.open.first().single().promptId)
            bus.publish(Envelope.of(MsgType.PROMPT_RESOLVED, dev.j3pf.rp.protocol.PromptResolved("01PROMPT", "local")))
            withTimeout(5000) { while (repo.open.first().isNotEmpty()) delay(20) }
        }
    }

    @Test
    fun `sin websocket responde por HTTP y 409 se trata como ya respondido`() {
        runBlocking {
            publishOpened()
            server.enqueue(MockResponse().setResponseCode(409))
            val r = repo.answer("01PROMPT", Answer.option("0"))
            assertEquals(AnswerOutcome.AlreadyResolved, r)
            assertEquals("/v1/prompts/01PROMPT/answer", server.takeRequest().path)
            assertTrue(repo.open.first().isEmpty()) // se retira de la bandeja
        }
    }

    @Test
    fun `sin red se encola el reintento`() {
        runBlocking {
            publishOpened()
            server.shutdown()
            assertEquals(AnswerOutcome.Queued, repo.answer("01PROMPT", Answer.option("0")))
            val infos = androidx.work.WorkManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
                .getWorkInfosForUniqueWork("answer-01PROMPT").get()
            assertEquals(1, infos.size)
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, infos.single().state)
        }
    }

    @Test
    fun `con websocket responde por WS`() {
        runBlocking {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val env = Envelope.parse(text)
                    if (env.t == MsgType.PROMPT_ANSWER) {
                        webSocket.send(Envelope.of(MsgType.PROMPT_ANSWER_RESULT, PromptAnswerResult(ok = true), re = env.id).encode())
                    }
                }
            }))
            conn.start()
            withTimeout(5000) { while (!conn.isConnected) delay(20) }
            publishOpened()
            assertEquals(AnswerOutcome.Sent, repo.answer("01PROMPT", Answer.option("0")))
            assertEquals(1, server.requestCount) // sólo el upgrade, ninguna petición HTTP
            assertNotNull(repo.get("01PROMPT"))
        }
    }
}
