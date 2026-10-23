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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
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
}
