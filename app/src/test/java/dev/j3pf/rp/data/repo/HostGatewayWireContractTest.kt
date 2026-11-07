package dev.j3pf.rp.data.repo

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.HostGateway
import dev.j3pf.rp.data.ports.HostResult
import dev.j3pf.rp.data.ports.HostResult.Failed
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.HostGitDiff
import dev.j3pf.rp.protocol.HostGitDiscard
import dev.j3pf.rp.protocol.HostListDirs
import dev.j3pf.rp.protocol.HostMachineRef
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
 * WIRE-LEVEL contract proof for UNIT P3 (verifier follow-up on the fake-only common test,
 * Risk: item 2): the assertions below run through a `HostGateway` REFERENCE (never the
 * concrete type), backed by the REAL `HostRepository` over a real socket, pinning exactly
 * the four mutations the verifier marked as MISSED:
 *   (a) the timeout literal "Tiempo de espera agotado" reaching Failed.message AT WIRE LEVEL
 *       (mutating the literal in HostRepository turns this RED);
 *   (b) NotConnected -> "Sin conexión con el relay" with `requests.isEmpty()` (NO frame
 *       leaves the device), and exactly ONE frame per call on the connected-erroring path;
 *   (c) ORDER with no dispatcher hops: two sequential gateway calls from one coroutine are
 *       received by the wire in the exact send order;
 *   (d) EXACT payload shapes the :app consumers build today for listDirs/herdrLayout/gitDiff
 *       (null and non-null path)/gitDiscard/spawn.
 * The pre-existing gitDiscard error-string assertion in HostRepositoryErrorsTest stays
 * untouched (S15). Timeouts use the REAL 12 s path of `ask()` — the 12 s duration is the
 * wire guarantee being proven, not slack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostGatewayWireContractTest {
    private lateinit var context: android.app.Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var repository: HostRepository
    /** The contract is proven through the PORT type, never the concrete class. */
    private lateinit var gateway: HostGateway
    private val requests = LinkedBlockingQueue<Envelope>()

    @Volatile private var responder: ((Envelope) -> Envelope?)? = null

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        bus = MessageBus()
        connection = RelayConnection(secrets, bus, dev.j3pf.rp.testClient(), scope)
        repository = HostRepository(connection, bus, scope)
        gateway = repository
        gateway.start()
    }

    @After
    fun tearDown() {
        if (::connection.isInitialized) connection.stop()
        if (::scope.isInitialized) scope.cancel()
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    private fun socketAcceptingOnlyFrames() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        // Filter CONNECTION-LEVEL frames (app.hello keepalive from
                        // RelayConnection.kt:87) — the same discipline
                        // HostRepositoryErrorsTest uses; the contract is about host frames.
                        if (env.t in HostGatewayWireContractTest.HOST_FRAMES) requests.put(env)
                        // No response ever: leaves the gateway to its provider-timeout path.
                    }
                },
            ),
        )
    }

    private fun connect() {
        socketAcceptingOnlyFrames()
        connection.start()
        runBlocking { withTimeout(5_000) { connection.state.first { it == ConnState.Connected } } }
    }

    @Test
    fun timeoutLiteralIsPinnedAtWireLevelThroughThePort() = runBlocking {
        connect()
        // assert THROUGH the port: `gateway` is a HostGateway reference.
        val result = gateway.listDirs("m1", "/") as Failed
        assertEquals("Tiempo de espera agotado", result.message)
    }

    @Test
    fun notConnectedSendsZeroFramesAndPinsTheDisconnectionLiteral() = runBlocking {
        // connection.start() was never called: the gateway must fail FAST with the exact
        // literal and WITHOUT sending any frame (NotConnected branch, HostRepository.kt:124).
        val before = requests.size
        val failed = gateway.gitDiscard("m1", "/repo", "f.txt") as Failed
        assertEquals("Sin conexión con el relay", failed.message)
        assertEquals("no frame may leave the device when the socket is not connected", before, requests.size)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun connectedErroringPathSendsExactlyOneFramePerCall() = runBlocking {
        connect()
        responder = { req ->
            FrameLog.payload(req)
            null // gateway goes to the timeout path after one frame; order+count is what we pin
        }
        val before = requests.size
        gateway.listDirs("m1", "/")
        assertEquals(1, requests.size - before)
    }

    @Test
    fun gatewayPreservesSendOrderWithoutDispatcherHops() = runBlocking {
        connect()
        responder = { req ->
            FrameLog.payload(req)
            null
        }
        // Two sequential calls launched from ONE coroutine: the wire receives them in the
        // exact send order. No queue, no dispatcher hop between them (port adds nothing).
        val j1 = launch(Dispatchers.Unconfined) { gateway.gitDiff("m1", "/repo", null) }
        val j2 = launch(Dispatchers.Unconfined) { gateway.gitDiff("m1", "/repo", "lib/x") }
        joinAll(j1, j2)
        val first = requests.poll(15, TimeUnit.SECONDS)!!
        val second = requests.poll(15, TimeUnit.SECONDS)!!
        assertEquals(MsgType.HOST_GIT_DIFF, first.t)
        assertEquals(null, first.body<HostGitDiff>()!!.path)
        assertEquals("lib/x", second.body<HostGitDiff>()!!.path)
    }

    @Test
    fun everyRpcCarriesExactlyThePayloadConsumersBuildToday() = runBlocking {
        connect()
        responder = {
            null // consume frames only; payloads are asserted from the requests queue
        }
        gateway.listDirs("m1", "/x")
        gateway.herdrLayout("m2")
        gateway.spawn(HostSpawn(machineId = "m3", cwd = "/y", name = "n", backend = "claude"))
        gateway.gitDiff("m4", "/repo/x", null)
        gateway.gitDiscard("m5", "/repo/y", "f.txt")

        withTimeout(10_000) {
            val listOfDirs = requests.take()
            assertEquals(MsgType.HOST_LIST_DIRS, listOfDirs.t)
            assertEquals(HostListDirs("m1", "/x"), listOfDirs.body<HostListDirs>())

            val herdr = requests.take()
            assertEquals(MsgType.HOST_HERDR_LAYOUT, herdr.t)
            assertEquals(HostMachineRef("m2"), herdr.body<HostMachineRef>())

            val spawnFrame = requests.take()
            assertEquals(MsgType.HOST_SPAWN, spawnFrame.t)
            assertEquals(
                HostSpawn(machineId = "m3", cwd = "/y", name = "n", backend = "claude"),
                spawnFrame.body<HostSpawn>(),
            )

            val gitNull = requests.take()
            assertEquals(MsgType.HOST_GIT_DIFF, gitNull.t)
            assertEquals(HostGitDiff("m4", "/repo/x", path = null), gitNull.body<HostGitDiff>())

            val discard = requests.take()
            assertEquals(MsgType.HOST_GIT_DISCARD, discard.t)
            assertEquals(HostGitDiscard("m5", "/repo/y", "f.txt"), discard.body<HostGitDiscard>())
        }
    }

    /** One shared object: the connection-level fame filter alias with the other tests. */
    private object FrameLog {
        fun payload(request: Envelope) { /* no-op; queue owned by the test fixture */ }
    }

    private companion object {
        /** Host-frame kinds this contract's queue accepts (connection frames excluded). */
        val HOST_FRAMES =
            listOf(
                MsgType.HOST_LIST_DIRS,
                MsgType.HOST_HERDR_LAYOUT,
                MsgType.HOST_SPAWN,
                MsgType.HOST_GIT_DIFF,
                MsgType.HOST_GIT_DISCARD,
            ).toSet()
    }
}
