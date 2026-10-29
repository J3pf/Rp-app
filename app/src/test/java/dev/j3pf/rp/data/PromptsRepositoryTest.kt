package dev.j3pf.rp.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ConnState
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
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var conn: RelayConnection
    private lateinit var repo: PromptsRepository
    private val prompt =
        Prompt(
            promptId = "01PROMPT",
            agentId = "m:s",
            kind = PromptKind.SELECT,
            title = "¿Permitir?",
            options = listOf(PromptOption("0", "Permitir"), PromptOption("2", "Denegar")),
        )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
        server = MockWebServer().also { it.start() }
        testScheduler = TestCoroutineScheduler()
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        bus = MessageBus()
        val secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
        val api = RelayApi(testClient(), secrets)
        conn = RelayConnection(ctx, secrets, bus, testClient(), scope)
        val db = inMemoryDb(ctx)
        val factory = NotificationFactory(ctx, NotificationChannels(ctx), SettingsRepository(ctx), NotificationDeduper(db.dedup()))
        repo = PromptsRepository(ctx, db.prompts(), secrets, bus, conn, api, factory, scope)
        repo.start()
    }

    @After
    fun tearDown() {
        conn.stop()
        scope.cancel()
        runCatching { server.shutdown() }
    }

    private suspend fun publishOpened() {
        bus.publish(Envelope.of(MsgType.PROMPT_OPENED, prompt))
        repo.open.first { it.isNotEmpty() }
    }

    @Test
    fun `prompt abierto llega al bandeja y se cierra con prompt resolved`() {
        runTest(testScheduler) {
            publishOpened()
            assertEquals(
                "01PROMPT",
                repo.open
                    .first()
                    .single()
                    .promptId,
            )
            bus.publish(
                Envelope.of(
                    MsgType.PROMPT_RESOLVED,
                    dev.j3pf.rp.protocol
                        .PromptResolved("01PROMPT", "local"),
                ),
            )
            repo.open.first { it.isEmpty() }
        }
    }

    @Test
    fun `sin websocket responde por HTTP y 409 se trata como ya respondido`() {
        runTest(testScheduler) {
            publishOpened()
            server.enqueue(MockResponse().setResponseCode(409))
            val r = repo.answer("01PROMPT", Answer.option("0"))
            assertEquals(AnswerOutcome.AlreadyResolved, r)
            assertEquals("/v1/prompts/01PROMPT/answer", server.takeRequest().path)
            assertTrue(repo.open.first { it.isEmpty() }.isEmpty()) // se retira de la bandeja
        }
    }

    @Test
    fun `sin red se encola el reintento`() {
        runTest(testScheduler) {
            publishOpened()
            server.shutdown()
            assertEquals(AnswerOutcome.Queued, repo.answer("01PROMPT", Answer.option("0")))
            val infos =
                androidx.work.WorkManager
                    .getInstance(ApplicationProvider.getApplicationContext<Application>())
                    .getWorkInfosForUniqueWork("answer-01PROMPT")
                    .get()
            assertEquals(1, infos.size)
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, infos.single().state)
        }
    }

    @Test
    fun `con websocket responde por WS`() {
        runTest(testScheduler) {
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            val env = Envelope.parse(text)
                            if (env.t == MsgType.PROMPT_ANSWER) {
                                webSocket.send(
                                    Envelope.of(MsgType.PROMPT_ANSWER_RESULT, PromptAnswerResult(ok = true), re = env.id).encode(),
                                )
                            }
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
            conn.start()
            conn.state.first { it == ConnState.Connected }
            publishOpened()
            assertEquals(
                AnswerOutcome.Sent,
                withContext(Dispatchers.IO) { repo.answer("01PROMPT", Answer.option("0")) },
            )
            assertEquals(1, server.requestCount) // sólo el upgrade, ninguna petición HTTP
            assertNotNull(repo.get("01PROMPT"))
        }
    }
}
