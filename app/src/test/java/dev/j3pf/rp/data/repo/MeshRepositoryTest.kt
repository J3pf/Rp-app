package dev.j3pf.rp.data.repo

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshCommEvent
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MeshUnsubscribe
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var repository: MeshRepository
    private val sockets = LinkedBlockingQueue<WebSocket>()
    private val outgoing = LinkedBlockingQueue<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        bus = MessageBus()
        connection = RelayConnection(context, secrets, bus, testClient(), scope)
        repository = MeshRepository(connection, bus, RelayApi(testClient(), secrets), secrets, scope)
    }

    @After
    fun tearDown() {
        connection.stop()
        sockets.forEach { runCatching { it.close(1000, "test complete") } }
        scope.cancel()
        server.shutdown()
    }

    private fun acceptSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        sockets.add(webSocket)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        outgoing.add(text)
                    }
                },
            ),
        )
    }

    private suspend fun connect() {
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
    }

    private suspend fun awaitOutgoing(type: String): Envelope =
        withTimeout(5_000) {
            while (true) {
                val text = outgoing.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val envelope = Envelope.parse(text)
                if (envelope.t == type) return@withTimeout envelope
            }
            error("unreachable")
        }

    @Test
    fun `subscribe sends mesh subscribe envelope with selected machine`() =
        runBlocking {
            acceptSocket()
            connect()

            repository.subscribe("machine-1")

            val envelope = awaitOutgoing(MsgType.MESH_SUBSCRIBE)
            assertEquals("machine-1", envelope.body<dev.j3pf.rp.protocol.MeshSubscribe>().machineId)
            assertTrue(repository.isSubscribed)
            assertEquals("machine-1", repository.subscribedMachineId)
        }

    @Test
    fun `unsubscribe sends mesh unsubscribe envelope`() =
        runBlocking {
            acceptSocket()
            connect()

            repository.subscribe("machine-1")
            awaitOutgoing(MsgType.MESH_SUBSCRIBE)
            repository.unsubscribe()

            val envelope = awaitOutgoing(MsgType.MESH_UNSUBSCRIBE)
            envelope.body<MeshUnsubscribe>()
            assertFalse(repository.isSubscribed)
            assertEquals(null, repository.subscribedMachineId)
        }

    @Test
    fun `snapshot and update messages replace the exposed snapshot`() =
        runBlocking {
            val first = MeshSnapshot(generatedAt = 1)
            val second = MeshSnapshot(generatedAt = 2)

            bus.publish(Envelope.of(MsgType.MESH_SNAPSHOT, first))
            withTimeout(2_000) { repository.snapshot.first { it == first } }
            bus.publish(Envelope.of(MsgType.MESH_UPDATE, second))

            assertEquals(second, withTimeout(2_000) { repository.snapshot.first { it == second } })
        }

    @Test
    fun `mesh comm messages are emitted on the live event flow`() =
        runBlocking {
            val comm = MeshComm(commKey = "c1", fromName = "A", toName = "B", at = 42)

            repository.commEvents.test {
                bus.publish(Envelope.of(MsgType.MESH_COMM, MeshCommEvent(comm)))
                assertEquals(comm, withTimeout(2_000) { awaitItem() })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `active subscription is restored after reconnect`() =
        runBlocking {
            acceptSocket()
            acceptSocket()
            connect()
            repository.subscribe("machine-2")
            awaitOutgoing(MsgType.MESH_SUBSCRIBE)

            val firstSocket = sockets.poll(5, TimeUnit.SECONDS)!!
            firstSocket.close(1012, "relay restart")
            withTimeout(8_000) { while (server.requestCount < 2) kotlinx.coroutines.delay(25) }
            withTimeout(8_000) { connection.state.first { it == ConnState.Connected } }

            val resubscribe = awaitOutgoing(MsgType.MESH_SUBSCRIBE)
            assertEquals("machine-2", resubscribe.body<dev.j3pf.rp.protocol.MeshSubscribe>().machineId)
            assertTrue(repository.isSubscribed)
        }

    @Test
    fun `subscription is ref-counted by owner token and only unsubscribes when all owners leave`() =
        runBlocking {
            acceptSocket()
            connect()
            awaitOutgoing(MsgType.APP_HELLO)

            val ownerA = Any()
            val ownerB = Any()

            // Owner A subscribes
            repository.subscribe(owner = ownerA, machineId = "m1")
            val subEnv = awaitOutgoing(MsgType.MESH_SUBSCRIBE)
            assertEquals("m1", subEnv.body<dev.j3pf.rp.protocol.MeshSubscribe>().machineId)
            assertTrue(repository.isSubscribed)

            // Owner B subscribes
            repository.subscribe(owner = ownerB, machineId = "m1")
            assertTrue(repository.isSubscribed)

            // Owner A unsubscribes (e.g. stale VM disposed) - subscription MUST stay active!
            repository.unsubscribe(owner = ownerA)
            assertTrue(repository.isSubscribed, "La suscripción debe permanecer activa mientras Owner B siga activo")
            val remaining = outgoing.poll(200, TimeUnit.MILLISECONDS)
            val remainingEnv = remaining?.let { Envelope.parse(it) }
            assertEquals(null, remainingEnv?.takeIf { it.t == MsgType.MESH_UNSUBSCRIBE })

            // Owner B unsubscribes - now subscription is dropped
            repository.unsubscribe(owner = ownerB)
            val unsubEnv = awaitOutgoing(MsgType.MESH_UNSUBSCRIBE)
            unsubEnv.body<MeshUnsubscribe>()
            assertFalse(repository.isSubscribed)
        }

    @Test
    fun `decode error on mesh snapshot emits error message`() =
        runBlocking {
            val invalidSnapshotEnvelope =
                Envelope(
                    v = 1,
                    t = MsgType.MESH_SNAPSHOT,
                    ts = 1000L,
                    d = kotlinx.serialization.json.JsonPrimitive("invalid-not-an-object"),
                )

            repository.meshErrors.test {
                bus.publish(invalidSnapshotEnvelope)
                val error = awaitItem()
                assertTrue(error.contains("Error al decodificar la red de agentes"))
            }
        }
}
