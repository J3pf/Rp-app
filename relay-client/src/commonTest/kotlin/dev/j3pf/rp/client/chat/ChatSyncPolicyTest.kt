package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.AgentSubscribe
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSyncPolicyTest {
    @Test
    fun subscribesWithLastSeqAndEpochWhenPresent() =
        runTest {
            val requests = mutableListOf<Envelope>()
            val state = ChatState(lastSeq = 42L, epoch = "epoch-1")
            val isSyncing = MutableStateFlow(false)

            val policy =
                ChatSyncPolicy(
                    agentId = "agent-1",
                    scope = this,
                    requestWs = { env ->
                        requests.add(env)
                        WsReply.Ok(Envelope(t = "ok", re = env.id))
                    },
                    newId = { "test-id" },
                    getState = { state },
                    isSyncing = { isSyncing.value },
                    setSyncing = { isSyncing.value = it },
                )

            policy.subscribe()

            assertEquals(1, requests.size)
            val req = requests[0]
            assertEquals(MsgType.AGENT_SUBSCRIBE, req.t)
            val sub = req.body<AgentSubscribe>()
            assertEquals("agent-1", sub.agentId)
            assertEquals(42L, sub.sinceSeq)
            assertEquals("epoch-1", sub.epoch)
        }

    @Test
    fun retriesThreeTimesWithBackoffThenCancelsTimeoutAndSetsSyncingFalse() =
        runTest {
            var attempts = 0
            val delays = mutableListOf<Long>()
            val isSyncing = MutableStateFlow(true)

            val policy =
                ChatSyncPolicy(
                    agentId = "agent-1",
                    scope = this,
                    requestWs = {
                        attempts++
                        WsReply.Timeout
                    },
                    newId = { "id-$attempts" },
                    getState = { ChatState() },
                    isSyncing = { isSyncing.value },
                    setSyncing = { isSyncing.value = it },
                    delayMs = { delays.add(it) },
                )

            policy.subscribe()

            assertEquals(3, attempts, "Should attempt exactly 3 times")
            assertEquals(listOf(1500L, 3000L, 4500L), delays, "Should backoff with 1500ms * attempt")
            assertFalse(isSyncing.value, "Syncing should be false after all retries fail")
        }

    @Test
    fun immediateStopOnWsReplyErrorWithoutRetry() =
        runTest {
            var attempts = 0
            val isSyncing = MutableStateFlow(true)

            val policy =
                ChatSyncPolicy(
                    agentId = "agent-1",
                    scope = this,
                    requestWs = {
                        attempts++
                        WsReply.Error("not_found", "Agent does not exist")
                    },
                    newId = { "id-1" },
                    getState = { ChatState() },
                    isSyncing = { isSyncing.value },
                    setSyncing = { isSyncing.value = it },
                )

            policy.subscribe()

            assertEquals(1, attempts, "Must stop immediately on WsReply.Error without retrying")
            assertFalse(isSyncing.value, "Syncing should be false on error")
        }

    @Test
    fun starts6sSyncTimeoutWhenItemsWereEmptyOnOkReply() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(testDispatcher)
            val isSyncing = MutableStateFlow(false)

            val policy =
                ChatSyncPolicy(
                    agentId = "agent-1",
                    scope = scope,
                    requestWs = { env -> WsReply.Ok(Envelope(t = "ok", re = env.id)) },
                    newId = { "id-ok" },
                    getState = { ChatState() }, // items empty
                    isSyncing = { isSyncing.value },
                    setSyncing = { isSyncing.value = it },
                )

            scope.runTest {
                policy.subscribe()
                assertTrue(isSyncing.value, "Syncing should be true initially when items were empty")

                advanceTimeBy(4_000)
                assertTrue(isSyncing.value, "Syncing should still be true at 4s")

                advanceTimeBy(2_500)
                assertFalse(isSyncing.value, "Syncing should become false after 6s timeout")
            }
        }

    @Test
    fun historyReceivedCancelsSyncTimeoutAndClearsSyncing() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(testDispatcher)
            val isSyncing = MutableStateFlow(false)

            val policy =
                ChatSyncPolicy(
                    agentId = "agent-1",
                    scope = scope,
                    requestWs = { env -> WsReply.Ok(Envelope(t = "ok", re = env.id)) },
                    newId = { "id-ok" },
                    getState = { ChatState() },
                    isSyncing = { isSyncing.value },
                    setSyncing = { isSyncing.value = it },
                )

            scope.runTest {
                policy.subscribe()
                assertTrue(isSyncing.value)

                advanceTimeBy(2_000)
                policy.onHistoryReceived()

                assertFalse(isSyncing.value, "onHistoryReceived must clear syncing immediately")
                advanceTimeBy(10_000)
                assertFalse(isSyncing.value)
            }
        }

    @Test
    fun resubscribeCancelsStaleTimeoutTimer() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(testDispatcher)
            val isSyncing = MutableStateFlow(false)

            val policy =
                ChatSyncPolicy(
                    agentId = "agent-1",
                    scope = scope,
                    requestWs = { env -> WsReply.Ok(Envelope(t = "ok", re = env.id)) },
                    newId = { "id-ok" },
                    getState = { ChatState() },
                    isSyncing = { isSyncing.value },
                    setSyncing = { isSyncing.value = it },
                )

            scope.runTest {
                policy.subscribe()
                assertTrue(isSyncing.value)

                advanceTimeBy(4_000)
                assertTrue(isSyncing.value)

                // Re-subscribe at 4s: resets timer for another 6s
                policy.subscribe()
                assertTrue(isSyncing.value)

                advanceTimeBy(3_000)
                assertTrue(isSyncing.value, "Old timer must not clear syncing at total 7s")

                advanceTimeBy(3_500)
                assertFalse(isSyncing.value, "New timer should expire after its full 6s duration")
            }
        }
}
