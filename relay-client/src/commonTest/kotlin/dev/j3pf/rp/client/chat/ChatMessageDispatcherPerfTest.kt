package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.str
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessageDispatcherPerfTest {
    private fun deltaEvent(
        seq: Long,
        delta: String,
        contentIndex: Int = 0,
        kind: String = "text",
        epoch: String = "E1",
        toolCallId: String? = null,
        name: String? = null,
    ): AgentEvent =
        AgentEvent(
            agentId = "my-agent",
            epoch = epoch,
            seq = seq,
            e =
                buildJsonObject {
                    put("type", "message_delta")
                    put("contentIndex", contentIndex)
                    put("kind", kind)
                    put("delta", delta)
                    toolCallId?.let { put("toolCallId", it) }
                    name?.let { put("name", it) }
                },
        )

    @Test
    fun coalesceStreamBatchMergesConsecutiveSameIndexAndKindDeltasPreservingOrderAndSeq() {
        val batch = mutableListOf<Pair<AgentEvent, Long>>()
        for (seq in 2L..51L) {
            batch.add(deltaEvent(seq, "a", contentIndex = 0, kind = "text") to (1_000L + seq))
        }
        // Duplicate seq inside contiguous run must be dropped without splitting run.
        batch.add(deltaEvent(10L, "DUP", contentIndex = 0, kind = "text") to 1_052L)
        for (seq in 52L..61L) {
            batch.add(deltaEvent(seq, "t", contentIndex = 1, kind = "thinking") to (1_000L + seq))
        }
        batch.add(
            AgentEvent(
                agentId = "my-agent",
                epoch = "E1",
                seq = 62L,
                e =
                    buildJsonObject {
                        put("type", "tool_execution_update")
                        put("toolCallId", "tc1")
                        put("toolName", "bash")
                        put("partialResult", "running")
                    },
            ) to 1_062L,
        )
        batch.add(deltaEvent(63L, """{"command":""", contentIndex = 2, kind = "toolcall", toolCallId = "tc2") to 1_063L)
        batch.add(deltaEvent(64L, """"ls"}""", contentIndex = 2, kind = "toolcall", name = "bash") to 1_064L)
        // Gap seq 70 must stay separate so ChatReducer flags needsResync.
        batch.add(deltaEvent(70L, "!", contentIndex = 2, kind = "toolcall") to 1_070L)

        val steps = ChatMessageDispatcher.coalesceStreamBatch(initialEpoch = "E1", initialLastSeq = 1L, events = batch)

        assertEquals(5, steps.size)
        assertEquals("a".repeat(50), steps[0].event.e.str("delta"))
        assertEquals(51L, steps[0].event.seq)
        assertEquals(50L, steps[0].preAdvanceLastSeq)

        assertEquals("t".repeat(10), steps[1].event.e.str("delta"))
        assertEquals(61L, steps[1].event.seq)
        assertEquals(60L, steps[1].preAdvanceLastSeq)

        assertEquals("tool_execution_update", steps[2].event.e.str("type"))
        assertEquals(62L, steps[2].event.seq)
        assertNull(steps[2].preAdvanceLastSeq)

        assertEquals("""{"command":"ls"}""", steps[3].event.e.str("delta"))
        assertEquals("tc2", steps[3].event.e.str("toolCallId"))
        assertEquals("bash", steps[3].event.e.str("name"))
        assertEquals(64L, steps[3].event.seq)
        assertEquals(63L, steps[3].preAdvanceLastSeq)

        assertEquals("!", steps[4].event.e.str("delta"))
        assertEquals(70L, steps[4].event.seq)
        assertNull(steps[4].preAdvanceLastSeq)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dispatcherAppliesCoalescedMixedDeltaBurstWithoutFalseResync() =
        runTest {
            val state = MutableStateFlow(ChatState(epoch = "E1"))
            var uiUpdateCalls = 0
            var resyncCount = 0
            var clockMs = 2_000L

            val dispatcher =
                ChatMessageDispatcher(
                    agentId = "my-agent",
                    getState = { state.value },
                    updateState = { transform ->
                        uiUpdateCalls++
                        state.update(transform)
                    },
                    onHistory = { _: AgentHistory, _: Boolean -> },
                    onPersistEvent = {},
                    onResyncNeeded = { resyncCount++ },
                    onHistoryApplied = {},
                    onSyncingResolved = {},
                    now = { clockMs },
                    scope = this,
                    frameBatchMs = 32L,
                )

            // Leading delta emits immediately.
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, deltaEvent(1L, "H", contentIndex = 0, kind = "text")))
            assertEquals(1, uiUpdateCalls)

            // Burst of text + thinking + toolcall deltas within 32ms window.
            clockMs = 2_005L
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, deltaEvent(2L, "e", contentIndex = 0, kind = "text")))
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, deltaEvent(3L, "l", contentIndex = 0, kind = "text")))
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, deltaEvent(4L, "lo", contentIndex = 0, kind = "text")))
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, deltaEvent(5L, "plan ", contentIndex = 1, kind = "thinking")))
            dispatcher.dispatch(Envelope.of(MsgType.AGENT_EVENT, deltaEvent(6L, "step", contentIndex = 1, kind = "thinking")))
            dispatcher.dispatch(
                Envelope.of(
                    MsgType.AGENT_EVENT,
                    deltaEvent(7L, """{"path":""", contentIndex = 2, kind = "toolcall", toolCallId = "tc-1", name = "read"),
                ),
            )
            dispatcher.dispatch(
                Envelope.of(
                    MsgType.AGENT_EVENT,
                    deltaEvent(8L, """"a.kt"}""", contentIndex = 2, kind = "toolcall"),
                ),
            )

            assertEquals(1, uiUpdateCalls)
            clockMs = 2_040L
            advanceTimeBy(40L)
            runCurrent()

            assertEquals(2, uiUpdateCalls)
            assertEquals(0, resyncCount)
            assertFalse(state.value.needsResync)
            assertEquals(8L, state.value.lastSeq)
            val blocks = state.value.streaming!!.blocks
            assertEquals(Block.Text("Hello"), blocks[0])
            assertEquals(Block.Thinking("plan step"), blocks[1])
            val tool = blocks[2] as Block.ToolCall
            assertEquals("tc-1", tool.toolCallId)
            assertEquals("read", tool.name)
            assertEquals("""{"path":"a.kt"}""", tool.rawArgs)
            assertTrue((tool.args as? JsonObject)?.get("path") == JsonPrimitive("a.kt"))
        }
}
