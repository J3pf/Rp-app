package dev.j3pf.rp.client.ws

import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RelayWsClientTest {
    private var connected = true
    private var acceptsSend = true
    private val sent = mutableListOf<Envelope>()
    private val bus = RelayMessageBus()
    private val client =
        RelayWsClient(
            isConnected = { connected },
            sendText = {
                sent.add(Envelope.parse(it))
                acceptsSend
            },
            newId = { "secure-native-id" },
            bus = bus,
        )

    @Test
    fun generatedAndExplicitIdsCorrelateAndRepliesAlsoReachBus() =
        runTest {
            val seen = mutableListOf<Envelope>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { bus.messages.collect { seen.add(it) } }
            for (id in listOf(null, "explicit")) {
                val request = async { client.request(Envelope(t = MsgType.USER_MESSAGE, id = id)) }
                runCurrent()
                val sentId = sent.last().id
                assertEquals(id ?: "secure-native-id", sentId)
                val reply = Envelope(t = "extension.reply", re = sentId)
                client.onText(reply.encode())
                runCurrent()
                assertEquals(WsReply.Ok(reply), request.await())
                assertEquals(reply, seen.last())
            }
        }

    @Test
    fun errorFallbackAndProtocolFiltering() =
        runTest {
            val seen = mutableListOf<Envelope>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { client.messages.collect { seen.add(it) } }
            val request = async { client.request(Envelope(t = "request", id = "r")) }
            runCurrent()
            client.onText("not json")
            client.onText("""{"v":999,"t":"error","re":"r"}""")
            runCurrent()
            assertTrue(seen.isEmpty())
            assertFalse(request.isCompleted)
            client.onText("""{"v":1,"t":"error","re":"r","d":"malformed"}""")
            assertEquals(WsReply.Error("internal", ""), request.await())
            client.onText("""{"v":1,"t":"extension.event"}""")
            runCurrent()
            assertEquals(listOf("error", "extension.event"), seen.map { it.t })
        }

    @Test
    fun timeoutSendFailureAndDisconnectedFastPath() =
        runTest {
            connected = false
            assertEquals(WsReply.NotConnected, client.request(Envelope(t = "request")))
            assertTrue(sent.isEmpty())
            connected = true
            acceptsSend = false
            assertEquals(WsReply.NotConnected, client.request(Envelope(t = "request")))
            acceptsSend = true
            val request = async { client.request(Envelope(t = "request"), timeoutMs = 100) }
            runCurrent()
            advanceTimeBy(99)
            assertFalse(request.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(WsReply.Timeout, request.await())
        }

    @Test
    fun disconnectFailsAllPendingAndAllowsReusingExplicitId() =
        runTest {
            val first = async { client.request(Envelope(t = "request", id = "one")) }
            val second = async { client.request(Envelope(t = "request", id = "two")) }
            runCurrent()
            client.failPending(WsReply.NotConnected)
            assertEquals(WsReply.NotConnected, first.await())
            assertEquals(WsReply.NotConnected, second.await())
            val next = async { client.request(Envelope(t = "request", id = "one")) }
            runCurrent()
            client.onText(Envelope(t = "reply", re = "one").encode())
            assertIs<WsReply.Ok>(next.await())
        }

    @Test
    fun duplicateExplicitIdsAndCancellationRetainExistingBehavior() =
        runTest {
            val first = async { client.request(Envelope(t = "request", id = "same"), timeoutMs = 100) }
            runCurrent()
            val second = async { client.request(Envelope(t = "request", id = "same")) }
            runCurrent()
            client.onText(Envelope(t = "reply", re = "same").encode())
            assertIs<WsReply.Ok>(second.await())
            advanceTimeBy(100)
            assertEquals(WsReply.Timeout, first.await())
            val cancelled = launch { client.request(Envelope(t = "request", id = "cancelled")) }
            runCurrent()
            cancelled.cancelAndJoin()
            // Cancellation still propagates; pending cleanup remains on disconnect, not a migration fix.
            client.failPending(WsReply.NotConnected)
            assertTrue(cancelled.isCancelled)
        }

    @Test
    fun foregroundAndDirectPublishOnSameDropOldestBus() =
        runTest {
            val seen = mutableListOf<String?>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { bus.messages.collect { seen.add(it.id) } }
            client.onText(Envelope(t = "foreground", id = "foreground").encode())
            runCurrent()
            bus.publish(Envelope(t = "direct", id = "direct"))
            runCurrent()
            repeat(513) { bus.publish(Envelope(t = "burst", id = "$it")) }
            runCurrent()
            assertEquals(listOf("foreground", "direct"), seen.take(2))
            assertEquals(514, seen.size)
            assertEquals("1", seen[2])
            assertEquals("512", seen.last())
        }
}
