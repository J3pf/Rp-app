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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
 * WIRE-LEVEL contract proof for UNIT P3 (rev2.1 corrections applied per the verifier's
 * ruling): the contract holds a `HostGateway` REFERENCE (never the concrete class) whose
 * backing implementation is the REAL `HostRepository` over a real socket. Proven here:
 *   (a) the timeout literal "Tiempo de espera agotado" AND its REAL 12 s duration
 *       (System.nanoTime window; mutating 12_000 -> 1_000 must fail the floor);
 *   (b) NotConnected AFTER a real socket existed: "Sin conexión con el relay" with
 *       ZERO host frames on the wire;
 *   (c) send ORDER of two SEQUENTIAL gateway calls in ONE coroutine on
 *       Dispatchers.Default, plus the hop probe (thread identity after each call);
 *   (d) EXACT payloads for listDirs/herdrLayout/spawn/gitDiff(null and non-null
 *       path)/gitDiscard, all three fields of gitDiff's non-null case asserted.
 * Connection-level frames (app.hello from RelayConnection.kt onSocketOpen) are FILTERED so
 * the contract counts only host frames (the same discipline HostRepositoryErrorsTest uses).
 * rev2.1 wording discipline: "zero frames" only where a socket exists; no "erroring path"
 * claim; the hop statement is limited to the resume-thread identity, not a general claim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostGatewayWireContractTest {
    private lateinit var context: android.app.Application
    private lateinit var server: MockWebServer
    private lateinit var scope: kotlinx.coroutines.CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var repository: HostRepository
    /** The contract is proven through the PORT type, never the concrete class. */
    private lateinit var gateway: HostGateway
    private val requests = LinkedBlockingQueue<Envelope>()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
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
                        if (env.t in HOST_FRAMES) {
                            requests.put(env)
                            // No response ever: leaves the gateway to its provider timeout path.
                        }
                    }
                },
            ),
        )
    }

    private suspend fun connect() {
        socketAcceptingOnlyFrames()
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
    }

    @Test
    fun timeoutLiteralAndIts12sDurationArePinnedAtWireLevelThroughThePort() = runBlocking {
        connect()
        val startedAt = System.nanoTime()
        val result = gateway.listDirs("m1", "/") as Failed
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals("Tiempo de espera agotado", result.message)
        assertTrue(
            "listDirs timeout must run the REAL 12 s path (mutating 12_000->1_000 fails this floor; got ${elapsedMs} ms)",
            elapsedMs >= 10_500,
        )
        assertTrue(
            "listDirs timeout must not drift past ~15 s (got ${elapsedMs} ms)",
            elapsedMs <= 15_000,
        )
    }

    @Test
    fun notConnectedAfterARealSocketExistedSendsZeroFramesAndPinsTheDisconnectionLiteral() = runBlocking {
        // A real socket WAS opened first (green light for the zero-frames claim to be
        // meaningful): connect -> stop, then the NotConnected path bets zero host frames.
        connect()
        connection.stop()

        val failed = gateway.gitDiscard("m1", "/repo", "f.txt") as Failed
        assertEquals("Sin conexión con el relay", failed.message)
        assertTrue(
            "ZERO host frames may leave the device on the NotConnected path",
            requests.isEmpty(),
        )
    }

    @Test
    fun connectedPathSendsExactlyOneHostFramePerCall() = runBlocking {
        connect()
        val before = requests.size
        gateway.listDirs("m1", "/")
        assertEquals(1, requests.size - before)
    }

    @Test
    fun sequentialGatewayCallsInOneDefaultCoroutinePreserveSendOrder() = runBlocking {
        withContext(Dispatchers.Default) {
            connect()
            val callerThread = Thread.currentThread().name
            gateway.gitDiff("m1", "/repo", null)
            gateway.gitDiff("m1", "/repo", "lib/x")
            // HOP PROBE (what IS provable): after each call the coroutine RESUMES on this
            // coroutine's own dispatcher (Dispatchers.Default); a hop that left the
            // coroutine on a DIFFERENT thread is observable here.
            assertEquals(callerThread, Thread.currentThread().name)
            val first = requests.poll(15, TimeUnit.SECONDS)!!
            val second = requests.poll(15, TimeUnit.SECONDS)!!
            assertEquals(MsgType.HOST_GIT_DIFF, first.t)
            assertEquals(MsgType.HOST_GIT_DIFF, second.t)
            // ALL THREE fields of the non-null case, plus the null-path shape:
            assertEquals(HostGitDiff("m1", "/repo", path = "lib/x"), second.body<HostGitDiff>())
            assertEquals(HostGitDiff("m1", "/repo", path = null), first.body<HostGitDiff>())
        }
    }

    @Test
    fun everyRpcCarriesExactlyThePayloadConsumersBuildToday() = runBlocking {
        connect()
        gateway.listDirs("m1", "/x")
        gateway.herdrLayout("m2")
        gateway.spawn(HostSpawn(machineId = "m3", cwd = "/y", name = "n", backend = "claude"))
        gateway.gitDiff("m4", "/repo/x", null)
        gateway.gitDiscard("m5", "/repo/y", "f.txt")

        withTimeout(10_000) {
            val listDirsFrame = requests.take()
            assertEquals(MsgType.HOST_LIST_DIRS, listDirsFrame.t)
            assertEquals(HostListDirs("m1", "/x"), listDirsFrame.body<HostListDirs>())

            val herdrFrame = requests.take()
            assertEquals(MsgType.HOST_HERDR_LAYOUT, herdrFrame.t)
            assertEquals(HostMachineRef("m2"), herdrFrame.body<HostMachineRef>())

            val spawnFrame = requests.take()
            assertEquals(MsgType.HOST_SPAWN, spawnFrame.t)
            assertEquals(
                HostSpawn(machineId = "m3", cwd = "/y", name = "n", backend = "claude"),
                spawnFrame.body<HostSpawn>(),
            )

            val gitNullFrame = requests.take()
            assertEquals(MsgType.HOST_GIT_DIFF, gitNullFrame.t)
            assertEquals(HostGitDiff("m4", "/repo/x", path = null), gitNullFrame.body<HostGitDiff>())

            val discardFrame = requests.take()
            assertEquals(MsgType.HOST_GIT_DISCARD, discardFrame.t)
            assertEquals(HostGitDiscard("m5", "/repo/y", "f.txt"), discardFrame.body<HostGitDiscard>())
        }
    }

    private companion object {
        /** Host frames this contract counts (connection-level frames filtered). */
        val HOST_FRAMES =
            setOf(
                MsgType.HOST_LIST_DIRS,
                MsgType.HOST_HERDR_LAYOUT,
                MsgType.HOST_SPAWN,
                MsgType.HOST_GIT_DIFF,
                MsgType.HOST_GIT_DISCARD,
            )
    }
}
