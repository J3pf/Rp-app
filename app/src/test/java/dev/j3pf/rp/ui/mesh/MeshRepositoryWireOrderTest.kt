package dev.j3pf.rp.ui.mesh

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MeshSubscribe
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshRepositoryWireOrderTest {
    @Test
    fun `subscribe decided before unsubscribe is dropped when its send is stale`() = runBlocking {
        val fixture = Fixture(MsgType.MESH_SUBSCRIBE)
        try {
            fixture.startAndConnect()
            val owner = Any()
            fixture.armPause()
            val failure = CompletableFuture<Throwable?>()
            val subscribeThread =
                Thread({
                    try {
                        fixture.repository.subscribe(owner, MACHINE_X)
                        failure.complete(null)
                    } catch (error: Throwable) {
                        failure.complete(error)
                    }
                }, "mesh-wire-order-subscribe")
            subscribeThread.start()

            assertTrue(fixture.deliveryEntered.await(WAIT_SECONDS, TimeUnit.SECONDS), "SUBSCRIBE decision did not reach the send seam")
            fixture.repository.unsubscribe(owner)

            assertEquals(2L, fixture.repository.lastSentSeqForTest)
            assertEquals(MsgType.MESH_UNSUBSCRIBE, fixture.awaitWire(MsgType.MESH_UNSUBSCRIBE).t)
            assertEquals(listOf(MsgType.MESH_UNSUBSCRIBE), fixture.accepted.map { it.envelope.t })

            fixture.releaseDelivery.countDown()
            subscribeThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
            assertTrue(!subscribeThread.isAlive, "wedged subscribe thread did not finish")
            assertEquals(null, failure.get(WAIT_SECONDS, TimeUnit.SECONDS))
            assertEquals(2L, fixture.repository.lastSentSeqForTest)
            assertEquals(listOf(MsgType.MESH_UNSUBSCRIBE), fixture.accepted.map { it.envelope.t })
            assertEquals(MsgType.MESH_UNSUBSCRIBE, fixture.accepted.last().envelope.t)
        } finally {
            fixture.releaseDelivery.countDown()
            fixture.close()
        }
    }

    @Test
    fun `reconnect observer resubscribe cannot overtake a newer subscribe decision`() = runBlocking {
        val fixture = Fixture(MsgType.MESH_SUBSCRIBE, MACHINE_X)
        try {
            fixture.startAndConnect()
            val ownerX = Any()
            fixture.repository.subscribe(ownerX, MACHINE_X)
            assertEquals(MACHINE_X, fixture.awaitWire(MsgType.MESH_SUBSCRIBE).body<MeshSubscribe>().machineId)

            fixture.armPause()
            fixture.repository.handleConnectionState(ConnState.Disconnected)
            val observerJob = fixture.dispatchConnectedObserver()
            assertTrue(fixture.deliveryEntered.await(WAIT_SECONDS, TimeUnit.SECONDS), "reconnect observer did not wedge its SUBSCRIBE(X)")

            val ownerY = Any()
            fixture.repository.subscribe(ownerY, MACHINE_Y)
            val newerSubscribe = fixture.awaitWire(MsgType.MESH_SUBSCRIBE)
            assertEquals(MACHINE_Y, newerSubscribe.body<MeshSubscribe>().machineId)
            assertEquals(3L, fixture.repository.lastSentSeqForTest)
            assertEquals(listOf(1L, 3L), fixture.accepted.map { it.seq })

            fixture.releaseDelivery.countDown()
            withTimeout(TIMEOUT_MS) { observerJob.join() }
            assertEquals(3L, fixture.repository.lastSentSeqForTest)
            assertEquals(listOf(1L, 3L), fixture.accepted.map { it.seq })
            assertEquals(MACHINE_Y, fixture.accepted.last().envelope.body<MeshSubscribe>().machineId)
        } finally {
            fixture.releaseDelivery.countDown()
            fixture.close()
        }
    }

    private data class SentEnvelope(
        val seq: Long,
        val envelope: Envelope,
    )

    private class Fixture(
        private val pausedType: String,
        private val pausedMachineId: String? = null,
    ) : AutoCloseable {
        val server = MockWebServer().also { it.start() }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val bus = MessageBus()
        private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        private val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        val connection = RelayConnection(context, secrets, bus, testClient(), scope)
        val deliveryEntered = CountDownLatch(1)
        val releaseDelivery = CountDownLatch(1)
        private val pauseNextDelivery = AtomicBoolean(false)
        val accepted = CopyOnWriteArrayList<SentEnvelope>()
        val sockets = LinkedBlockingQueue<WebSocket>()
        private val incoming = LinkedBlockingQueue<Envelope>()
        val repository =
            object : MeshRepository(connection, bus, RelayApi(testClient(), secrets), scope) {
                override fun deliverEnvelope(
                    env: Envelope,
                    seq: Long,
                ): Boolean {
                    val isTarget =
                        env.t == pausedType &&
                            (pausedMachineId == null || env.body<MeshSubscribe>().machineId == pausedMachineId)
                    val paused = isTarget && pauseNextDelivery.compareAndSet(true, false)
                    if (paused) {
                        deliveryEntered.countDown()
                        check(releaseDelivery.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "timed out at the wire-order send seam" }
                    }
                    return super.deliverEnvelope(env, seq).also { delivered ->
                        if (delivered) accepted += SentEnvelope(seq, env)
                    }
                }
            }

        init {
            repeat(2) {
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
                                incoming.add(Envelope.parse(text))
                            }
                        },
                    ),
                )
            }
        }

        suspend fun startAndConnect() {
            connection.start()
            withTimeout(TIMEOUT_MS) { connection.state.first { it == ConnState.Connected } }
        }

        fun armPause() {
            check(pauseNextDelivery.compareAndSet(false, true)) { "wire-order pause was already armed" }
        }

        fun dispatchConnectedObserver() = scope.launch { repository.handleConnectionState(ConnState.Connected) }

        suspend fun awaitWire(type: String): Envelope =
            withTimeout(TIMEOUT_MS) {
                while (true) {
                    val envelope = incoming.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    if (envelope.t == type) return@withTimeout envelope
                }
                error("unreachable")
            }

        override fun close() {
            releaseDelivery.countDown()
            connection.stop()
            sockets.forEach { runCatching { it.close(1000, "test complete") } }
            scope.cancel()
            server.shutdown()
        }
    }

    private companion object {
        const val MACHINE_X = "machine-x"
        const val MACHINE_Y = "machine-y"
        const val WAIT_SECONDS = 10L
        const val TIMEOUT_MS = 10_000L
    }
}
