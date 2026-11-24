package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.PromptCard
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Entry
import dev.j3pf.rp.protocol.EntryKind
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.ResolvedBy
import dev.j3pf.rp.protocol.TerminalWait
import dev.j3pf.rp.protocol.UserMessageAck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessageDispatcherTest {
    @Test
    fun ignoresMessagesTargetedToDifferentAgentId() =
        runTest {
            val state = MutableStateFlow(ChatState())
            var historyPersisted = false
            var eventPersisted = false

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState -> state.update(transform) },
                    onHistory = { _: AgentHistory, _: Boolean -> historyPersisted = true },
                    onPersistEvent = { eventPersisted = true },
                    onResyncNeeded = {},
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { 1000L },
                )

            // Envelope for other agent
            val otherHistory = AgentHistory(agentId = "other-agent", epoch = "E1")
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_HISTORY, otherHistory))
            assertFalse(historyPersisted)

            val otherEvent =
                AgentEvent(
                    seq = 1L,
                    epoch = "E1",
                    agentId = "other-agent",
                    e = JsonObject(mapOf("type" to JsonPrimitive("agent_start"))),
                )
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, otherEvent))
            assertFalse(eventPersisted)
        }

    @Test
    fun appliesHistoryDetectsEpochChangeClearsSyncingAndNotifiesPersistence() =
        runTest {
            val state = MutableStateFlow(ChatState(epoch = "E1"))
            var notifiedEpochChanged: Boolean? = null
            var historyAppliedCalled = false

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState -> state.update(transform) },
                    onHistory = { _: AgentHistory, epochChanged: Boolean -> notifiedEpochChanged = epochChanged },
                    onPersistEvent = {},
                    onResyncNeeded = {},
                    onHistoryApplied = { historyAppliedCalled = true },
                    onSyncingResolved = {},
                    now = { 1000L },
                )

            val history =
                AgentHistory(
                    agentId = "my-agent",
                    epoch = "E2",
                    entries =
                        listOf(
                            Entry(eid = "1", kind = EntryKind.USER, msg = JsonObject(mapOf("text" to JsonPrimitive("hi")))),
                        ),
                )

            dispatcher.dispatch(Envelope.of(MsgType.AGENT_HISTORY, history))

            assertTrue(historyAppliedCalled, "History application callback must be invoked")
            assertEquals(true, notifiedEpochChanged, "Must detect epoch changed from E1 to E2")
            assertEquals("E2", state.value.epoch)
        }

    @Test
    fun reducesEventFiltersPersistedEventsAndTriggersResyncWhenNeedsResyncTransitionsFalseToTrue() =
        runTest {
            val state = MutableStateFlow(ChatState(lastSeq = 1L, epoch = "E1"))
            val persistedEvents = mutableListOf<AgentEvent>()
            var resyncTriggered = false
            var syncingResolved = false

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState -> state.update(transform) },
                    onHistory = { _: AgentHistory, _: Boolean -> },
                    onPersistEvent = { persistedEvents.add(it) },
                    onResyncNeeded = { resyncTriggered = true },
                    onHistoryApplied = {},
                    onSyncingResolved = { syncingResolved = true },
                    now = { 1000L },
                )

            // Event with seq gap: lastSeq was 1, received seq 5 -> triggers needsResync!
            val gapEvent =
                AgentEvent(
                    seq = 5L,
                    epoch = "E1",
                    agentId = "my-agent",
                    e = JsonObject(mapOf("type" to JsonPrimitive("agent_start"))),
                )

            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, gapEvent))

            assertTrue(resyncTriggered, "Should trigger resync when needsResync transitions false -> true")
            assertEquals(1, persistedEvents.size, "agent_start is a persisted event")
        }

    @Test
    fun dispatchesPromptOpenedAndResolved() =
        runTest {
            val state = MutableStateFlow(ChatState())

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState -> state.update(transform) },
                    onHistory = { _: AgentHistory, _: Boolean -> },
                    onPersistEvent = {},
                    onResyncNeeded = {},
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { 1000L },
                )

            val prompt =
                Prompt(
                    promptId = "p-1",
                    agentId = "my-agent",
                    kind = "confirm",
                    title = "Confirm action",
                )
            dispatcher.dispatch(Envelope.of(MsgType.PROMPT_OPENED, prompt))
            val card =
                state.value.items
                    .filterIsInstance<PromptCard>()
                    .firstOrNull { it.prompt.promptId == "p-1" }
            assertNotNull(card)
            assertFalse(card.resolved)

            val resolved =
                PromptResolved(
                    promptId = "p-1",
                    by = ResolvedBy.LOCAL,
                )
            dispatcher.dispatch(Envelope.of(MsgType.PROMPT_RESOLVED, resolved))
            val resolvedCard =
                state.value.items
                    .filterIsInstance<PromptCard>()
                    .firstOrNull { it.prompt.promptId == "p-1" }
            assertNotNull(resolvedCard)
            assertTrue(resolvedCard.resolved)
        }

    @Test
    fun togglesTerminalWaitActiveAndInactive() =
        runTest {
            val state = MutableStateFlow(ChatState())

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState -> state.update(transform) },
                    onHistory = { _: AgentHistory, _: Boolean -> },
                    onPersistEvent = {},
                    onResyncNeeded = {},
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { 1000L },
                )

            dispatcher.dispatch(
                Envelope.of(
                    MsgType.TERMINAL_WAIT,
                    TerminalWait(agentId = "my-agent", active = true, kind = "bash", title = "Running build"),
                ),
            )
            assertNotNull(state.value.terminalWait)
            assertEquals("bash", state.value.terminalWait?.kind)
            assertEquals("Running build", state.value.terminalWait?.title)

            dispatcher.dispatch(
                Envelope.of(
                    MsgType.TERMINAL_WAIT,
                    TerminalWait(agentId = "my-agent", active = false, kind = "bash"),
                ),
            )
            assertNull(state.value.terminalWait)
        }

    @Test
    fun dispatchesUserMessageAck() =
        runTest {
            val state = MutableStateFlow(ChatState())

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState -> state.update(transform) },
                    onHistory = { _: AgentHistory, _: Boolean -> },
                    onPersistEvent = {},
                    onResyncNeeded = {},
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { 1000L },
                )

            val ack =
                UserMessageAck(
                    clientMsgId = "msg-1",
                    disposition = "accepted",
                )
            dispatcher.dispatch(Envelope.of(MsgType.USER_MESSAGE_ACK, ack))
            // applied without throwing
        }

    @Test
    fun coalescesBurstMessageDeltasWithinFrameWindowAndFlushesBeforeMessageEnd() =
        runTest {
            val state = MutableStateFlow(ChatState(epoch = "E1"))
            var uiUpdateCalls = 0
            var clockMs = 1_000L

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform: (ChatState) -> ChatState ->
                        uiUpdateCalls++
                        state.update(transform)
                    },
                    onHistory = { _: AgentHistory, _: Boolean -> },
                    onPersistEvent = {},
                    onResyncNeeded = {},
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { clockMs },
                    scope = this,
                    frameBatchMs = 32L,
                )

            fun deltaEnv(
                seq: Long,
                delta: String,
            ) = Envelope.of(
                MsgType.AGENT_EVENT,
                AgentEvent(
                    seq = seq,
                    epoch = "E1",
                    agentId = "my-agent",
                    e =
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("message_delta"),
                                "contentIndex" to JsonPrimitive(0),
                                "kind" to JsonPrimitive("text"),
                                "delta" to JsonPrimitive(delta),
                            ),
                        ),
                ),
            )

            // First delta emits immediately (leading edge = 0ms latency)
            dispatcher.dispatch(deltaEnv(1L, "Hola"))
            assertEquals(1, uiUpdateCalls)
            assertEquals("Hola", state.value.streaming?.plainText)

            // Subsequent deltas within 32ms are buffered without triggering UI updates
            clockMs = 1_005L
            dispatcher.dispatch(deltaEnv(2L, " "))
            clockMs = 1_010L
            dispatcher.dispatch(deltaEnv(3L, "mundo"))
            assertEquals(1, uiUpdateCalls, "Burst deltas within 32ms must not emit separate UI updates")

            // A non-delta event (message_end) flushes pending deltas immediately before applying
            clockMs = 1_015L
            val endEvent =
                AgentEvent(
                    seq = 4L,
                    epoch = "E1",
                    agentId = "my-agent",
                    e =
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("message_end"),
                                "message" to
                                    JsonObject(
                                        mapOf(
                                            "role" to JsonPrimitive("assistant"),
                                            "timestamp" to JsonPrimitive(1_015L),
                                            "content" to JsonPrimitive("Hola mundo!"),
                                        ),
                                    ),
                            ),
                        ),
                )
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, endEvent))
            assertNull(state.value.streaming)
            assertEquals(4L, state.value.lastSeq)
        }

    @Test
    fun concurrentFlushPendingAndMessageEndDoNotRaceOrTriggerFalseResync() =
        runTest {
            withContext(Dispatchers.Default) {
                repeat(40) { iteration ->
                    val state = MutableStateFlow(ChatState(epoch = "E1"))
                    var resyncCount = 0
                    var clockMs = 1_000L

                    val dispatcher =
                        ChatMessageDispatcher(
                            agentId = "my-agent",
                            getState = { state.value },
                            updateState = { transform -> state.update(transform) },
                            onHistory = { _, _ -> },
                            onPersistEvent = {},
                            onResyncNeeded = { resyncCount++ },
                            onHistoryApplied = {},
                            onSyncingResolved = {},
                            now = { clockMs },
                            scope = this,
                            frameBatchMs = 1_000L,
                        )

                    fun deltaEnv(seq: Long, delta: String) =
                        Envelope.of(
                            MsgType.AGENT_EVENT,
                            AgentEvent(
                                seq = seq,
                                epoch = "E1",
                                agentId = "my-agent",
                                e =
                                    JsonObject(
                                        mapOf(
                                            "type" to JsonPrimitive("message_delta"),
                                            "contentIndex" to JsonPrimitive(0),
                                            "kind" to JsonPrimitive("text"),
                                            "delta" to JsonPrimitive(delta),
                                        ),
                                    ),
                            ),
                        )

                    // Leading edge seq 1 emits immediately, seq 2..4 buffer
                    dispatcher.dispatch(deltaEnv(1L, "A"))
                    clockMs = 1_005L
                    dispatcher.dispatch(deltaEnv(2L, "B"))
                    dispatcher.dispatch(deltaEnv(3L, "C"))
                    dispatcher.dispatch(deltaEnv(4L, "D"))

                    val endEnv =
                        Envelope.of(
                            MsgType.AGENT_EVENT,
                            AgentEvent(
                                seq = 5L,
                                epoch = "E1",
                                agentId = "my-agent",
                                e =
                                    JsonObject(
                                        mapOf(
                                            "type" to JsonPrimitive("message_end"),
                                            "message" to
                                                JsonObject(
                                                    mapOf(
                                                        "role" to JsonPrimitive("assistant"),
                                                        "timestamp" to JsonPrimitive(2_000L + iteration),
                                                        "content" to JsonPrimitive("ABCD"),
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        )

                    // Race flushPending() against dispatch(message_end) on concurrent worker threads
                    val flushTask = async { dispatcher.flushPending() }
                    val endTask = async { dispatcher.dispatch(endEnv) }
                    awaitAll(flushTask, endTask)

                    assertEquals(0, resyncCount, "Concurrent flushPending + message_end must never cause false resync (iter $iteration)")
                    assertFalse(state.value.needsResync, "needsResync must remain false (iter $iteration)")
                    assertEquals(5L, state.value.lastSeq, "lastSeq must advance to 5 (iter $iteration)")
                    assertNull(state.value.streaming, "streaming must be cleared after message_end (iter $iteration)")
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun burstDeltasTrackSeqGapAndDuplicatesInO1WithoutQuadraticReplay() =
        runTest {
            val state = MutableStateFlow(ChatState(epoch = "E1"))
            var uiUpdateCalls = 0
            var resyncCount = 0
            var clockMs = 1_000L

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform ->
                        uiUpdateCalls++
                        state.update(transform)
                    },
                    onHistory = { _, _ -> },
                    onPersistEvent = {},
                    onResyncNeeded = { resyncCount++ },
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { clockMs },
                    scope = this,
                    frameBatchMs = 32L,
                )

            fun deltaEnv(seq: Long, delta: String, epoch: String = "E1") =
                Envelope.of(
                    MsgType.AGENT_EVENT,
                    AgentEvent(
                        seq = seq,
                        epoch = epoch,
                        agentId = "my-agent",
                        e =
                            JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("message_delta"),
                                    "contentIndex" to JsonPrimitive(0),
                                    "kind" to JsonPrimitive("text"),
                                    "delta" to JsonPrimitive(delta),
                                ),
                            ),
                    ),
                )

            // Leading delta
            dispatcher.dispatch(deltaEnv(1L, "0"))
            assertEquals(1, uiUpdateCalls)

            // 500 contiguous deltas inside window + duplicate seq 50
            clockMs = 1_005L
            for (seq in 2L..500L) {
                dispatcher.dispatch(deltaEnv(seq, "x"))
            }
            dispatcher.dispatch(deltaEnv(50L, "DUP"))
            assertEquals(1, uiUpdateCalls, "All 500 buffered deltas must coalesce into a single future flush")
            assertEquals(0, resyncCount, "Contiguous deltas + duplicate must not trigger resync")

            // Gap delta (seq 505 instead of 501) immediately flushes and triggers resync once
            dispatcher.dispatch(deltaEnv(505L, "!"))
            assertEquals(1, resyncCount, "Seq gap inside burst window must immediately trigger resync")
            assertEquals(2, uiUpdateCalls)
            assertEquals(505L, state.value.lastSeq)
            assertTrue(state.value.needsResync)
            assertEquals(501, state.value.streaming?.plainText?.length)

            // Let scheduled timer settle cleanly without extra updates
            advanceTimeBy(50L)
            runCurrent()
            assertEquals(2, uiUpdateCalls)
        }
}
