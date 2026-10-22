package dev.j3pf.rp.client.ws

import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.Envelope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocketConcurrencyTest {
    @Test
    fun concurrentRepliesAndDisconnectDoNotRacePendingMap() =
        runBlocking {
            val sent = CountDownLatch(128)
            val client =
                RelayWsClient(
                    isConnected = { true },
                    sendText = {
                        sent.countDown()
                        true
                    },
                    newId = { error("explicit IDs") },
                    bus = RelayMessageBus(),
                )
            val requests = (0 until 128).map { id -> async(Dispatchers.Default) { client.request(Envelope(t = "request", id = "$id")) } }
            assertTrue(sent.await(5, TimeUnit.SECONDS))
            val pool = Executors.newFixedThreadPool(4)
            try {
                val futures =
                    (0 until 128).map { id ->
                        pool.submit { client.onText(Envelope(t = "reply", re = "$id").encode()) }
                    } + pool.submit { client.failPending(WsReply.NotConnected) }
                futures.forEach { it.get(5, TimeUnit.SECONDS) }
                requests.forEach {
                    val reply = it.await()
                    assertTrue(reply is WsReply.Ok || reply == WsReply.NotConnected)
                }
            } finally {
                pool.shutdownNow()
            }
        }

    @Test
    fun disconnectCompletionCanResumeRequestInline() =
        runBlocking {
            val client = RelayWsClient({ true }, { true }, { "id" }, RelayMessageBus())
            val requests =
                (0 until 10).map { id ->
                    async(
                        Dispatchers.Unconfined,
                        start = CoroutineStart.UNDISPATCHED,
                    ) { client.request(Envelope(t = "request", id = "$id")) }
                }
            client.failPending(WsReply.NotConnected)
            requests.forEach { assertEquals(WsReply.NotConnected, it.await()) }
        }

    @Test
    fun concurrentOpenAndStopAlwaysFinishDisconnected() =
        runBlocking {
            val pool = Executors.newFixedThreadPool(2)
            try {
                repeat(50) {
                    val driver = FakeSocketDriver()
                    val policy = ReconnectingSocketPolicy(this, driver, { "request" }, {}, {})
                    policy.start()
                    kotlinx.coroutines.yield()
                    val gate = CountDownLatch(1)
                    val open =
                        pool.submit {
                            gate.await(5, TimeUnit.SECONDS)
                            driver.listeners[0].onOpen(0)
                        }
                    val stop =
                        pool.submit {
                            gate.await(5, TimeUnit.SECONDS)
                            policy.stop()
                        }
                    gate.countDown()
                    open.get(5, TimeUnit.SECONDS)
                    stop.get(5, TimeUnit.SECONDS)
                    assertEquals(ConnState.Disconnected, policy.state.value)
                }
            } finally {
                pool.shutdownNow()
            }
        }
}
