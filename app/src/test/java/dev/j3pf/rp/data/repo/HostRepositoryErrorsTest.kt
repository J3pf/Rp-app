package dev.j3pf.rp.data.repo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Verdict rev-3 fix (2), HO-01: HostRepository wrappers must surface relay errors as
 * `HostResult.Failed` (never crash, never unwrap) — exact strings from the current code:
 * a relay ERROR with a message surfaces the message; an EMPTY message keeps the bare code;
 * timeouts are "Tiempo de espera agotado"; a closed connection is "Sin conexión con el relay".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostRepositoryErrorsTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var repository: HostRepository
    private val requests = LinkedBlockingQueue<Envelope>()

    @Volatile private var responder: ((Envelope) -> Envelope?)? = null

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            server = MockWebServer().also { it.start() }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
            bus = MessageBus()
            connection = RelayConnection(context, secrets, bus, dev.j3pf.rp.testClient(), scope)
            repository = HostRepository(connection, bus, scope)
            repository.start()
        }

    @After
    fun tearDown() {
        if (::connection.isInitialized) connection.stop()
        if (::scope.isInitialized) scope.cancel()
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    private fun acceptSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        requests.put(env)
                        responder?.invoke(env)?.let { webSocket.send(it.encode()) }
                    }
                },
            ),
        )
    }

    private fun error(
        request: Envelope,
        code: String,
        message: String = "",
    ) = Envelope.of(MsgType.ERROR, ErrorBody(code, message), re = request.id)

    private suspend fun connect() {
        acceptSocket()
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
    }

    @Test
    fun notConnectedWrappersReturnFailedWithTheExactNoConnectionMessage() =
        runBlocking {
            // No connection.start(): request() answers NotConnected immediately.
            assertEquals("Sin conexión con el relay", (repository.listDirs("m1", "/") as HostResult.Failed).message)
            assertEquals("Sin conexión con el relay", (repository.herdrLayout("m1") as HostResult.Failed).message)
            assertEquals("Sin conexión con el relay", (repository.gitDiff("m1", "/repo") as HostResult.Failed).message)
            assertEquals("Sin conexión con el relay", (repository.gitDiscard("m1", "/repo", "f.txt") as HostResult.Failed).message)
        }

    @Test
    fun relayErrorSurfacesTheExactMessageAndEmptyMessageKeepsTheBareCode() =
        runBlocking {
            connect()
            responder = { request ->
                when (request.t) {
                    MsgType.HOST_GIT_DIFF -> error(request, "herdr_unavailable", message = "herdr no responde")
                    MsgType.HOST_GIT_DISCARD -> error(request, "git_locked")
                    else -> null // ignore any connection-level frames
                }
            }

            val diffFailed = repository.gitDiff("m1", "/repo") as HostResult.Failed
            assertEquals("herdr no responde", diffFailed.message)

            val discardFailed = repository.gitDiscard("m1", "/repo", "f.txt") as HostResult.Failed
            assertEquals("git_locked", discardFailed.message)
            assertTrue(discardFailed.message.isNotEmpty())
        }
}
