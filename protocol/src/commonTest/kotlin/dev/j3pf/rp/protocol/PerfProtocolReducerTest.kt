package dev.j3pf.rp.protocol

import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatReducer
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.NoteKind
import dev.j3pf.rp.chat.SubagentCardItem
import dev.j3pf.rp.chat.SystemNote
import dev.j3pf.rp.chat.ToolStatus
import dev.j3pf.rp.chat.UserMsg
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PerfProtocolReducerTest {
    @Test
    fun appStateEnvelopeMatchesRelayFixtureContractAndRejectsMissingScreenOn() {
        assertEquals("app.state", MsgType.APP_STATE)

        val validFixture =
            """{"v":1,"t":"app.state","ts":1760342400000,"d":{"screenOn":false}}"""
        val parsed = Envelope.parse(validFixture)
        assertEquals(MsgType.APP_STATE, parsed.t)
        val decoded = parsed.body<AppState>()
        assertFalse(decoded.screenOn)

        val encoded = Envelope.of(MsgType.APP_STATE, AppState(screenOn = true), ts = 1760342400000L).encode()
        val roundtrip = Envelope.parse(encoded)
        assertEquals(MsgType.APP_STATE, roundtrip.t)
        assertTrue(roundtrip.body<AppState>().screenOn)

        val invalidFixture =
            """{"v":1,"t":"app.state","ts":1760342400000,"d":{}}"""
        val invalidEnv = Envelope.parse(invalidFixture)
        assertEquals(MsgType.APP_STATE, invalidEnv.t)
        assertFailsWith<SerializationException> {
            invalidEnv.body<AppState>()
        }
    }

    @Test
    fun toolcallDeltaSkipsPartialParseOver4096BytesAndRetainsPriorArgsUntilToolStart() {
        fun toolDelta(
            seq: Long,
            delta: String,
        ) = AgentEvent(
            agentId = "a1",
            epoch = "E1",
            seq = seq,
            e =
                buildJsonObject {
                    put("type", "message_delta")
                    put("contentIndex", 0)
                    put("kind", "toolcall")
                    put("toolCallId", "tc-big")
                    put("name", "write")
                    put("delta", delta)
                },
        )

        var state =
            ChatReducer.reduce(
                ChatState(),
                toolDelta(1L, """{"path":"src/Big.kt","content":"prefix"""),
            )
        val initialCall = state.streaming!!.blocks[0] as Block.ToolCall
        val initialArgs = assertNotNull(initialCall.args)
        assertEquals("src/Big.kt", initialArgs.obj()?.str("path"))

        // Exceed 4096 chars with valid closing JSON that adds a second field ("extra").
        // Because raw.length > 4096, parsePartial must be skipped and prev.args retained.
        val largePadding = "a".repeat(4_200)
        state =
            ChatReducer.reduce(
                state,
                toolDelta(2L, """$largePadding","extra":"after-limit"}"""),
            )
        val bigCall = state.streaming!!.blocks[0] as Block.ToolCall
        assertTrue(bigCall.rawArgs.length > 4_096)
        assertEquals(initialArgs, bigCall.args)
        assertEquals(null, bigCall.args?.obj()?.str("extra"))

        // tool_execution_start delivers the authoritative final args.
        val finalArgs =
            buildJsonObject {
                put("path", "src/Big.kt")
                put("extra", "after-limit")
            }
        state =
            ChatReducer.reduce(
                state,
                AgentEvent(
                    agentId = "a1",
                    epoch = "E1",
                    seq = 3L,
                    e =
                        buildJsonObject {
                            put("type", "tool_execution_start")
                            put("toolCallId", "tc-big")
                            put("toolName", "write")
                            put("args", finalArgs)
                        },
                ),
                now = 500L,
            )
        assertEquals("after-limit", state.tools["tc-big"]?.args?.obj()?.str("extra"))
        assertEquals(ToolStatus.RUNNING, state.tools["tc-big"]?.status)
    }

    @Test
    fun applyEntriesUsesLinearAccumulatorsWithCorrectOrderDedupPendingAndSubagents() {
        val pendingLocal =
            UserMsg(
                key = "local:pending-1",
                text = "still unsent",
                at = 9_999L,
                fromMobile = true,
                clientMsgId = "pending-1",
            )
        val subagent =
            SubagentInfo(
                taskId = "tc-10",
                agent = "worker",
                status = "running",
            )
        val baseState =
            ChatState(
                epoch = "E1",
                items = listOf(pendingLocal),
                subagents = mapOf("tc-10" to subagent),
                localSent = setOf("still unsent", "user-0"),
            )

        val entries = mutableListOf<Entry>()
        for (i in 0 until 80) {
            entries.add(
                Entry(
                    eid = "u$i",
                    kind = EntryKind.USER,
                    at = (i * 10 + 1).toLong(),
                    msg =
                        buildJsonObject {
                            put("timestamp", (i * 10 + 1).toLong())
                            put("content", "user-$i")
                        },
                ),
            )
            val toolId = "tc-$i"
            entries.add(
                Entry(
                    eid = "a$i",
                    kind = EntryKind.ASSISTANT,
                    at = (i * 10 + 2).toLong(),
                    msg =
                        RpJson.parseToJsonElement(
                            """
                            {
                                "role": "assistant",
                                "timestamp": ${i * 10 + 2},
                                "content": [
                                    {"type": "text", "text": "reply-$i"},
                                    {"type": "toolCall", "id": "$toolId", "name": "bash", "arguments": {"command": "echo $i"}}
                                ],
                                "usage": {"totalTokens": ${(i + 1) * 100}, "contextWindow": 200000}
                            }
                            """.trimIndent(),
                        ) as JsonObject,
                ),
            )
            entries.add(
                Entry(
                    eid = "tr$i",
                    kind = EntryKind.TOOL_RESULT,
                    at = (i * 10 + 3).toLong(),
                    msg =
                        buildJsonObject {
                            put("toolCallId", toolId)
                            put("toolName", "bash")
                            put("content", "out-$i")
                            put("isError", false)
                        },
                ),
            )
        }
        entries.add(
            Entry(
                eid = "m1",
                kind = EntryKind.MODEL_CHANGE,
                at = 9_000L,
                msg = buildJsonObject { put("modelId", "claude-sonnet-4") },
            ),
        )
        entries.add(
            Entry(
                eid = "b1",
                kind = EntryKind.BASH,
                at = 9_001L,
                msg =
                    buildJsonObject {
                        put("command", "ls")
                        put("output", "a.kt")
                    },
            ),
        )
        entries.add(
            Entry(
                eid = "c1",
                kind = EntryKind.COMPACTION,
                at = 9_002L,
                msg = buildJsonObject { put("summary", "compacted") },
            ),
        )
        // Duplicate assistant timestamp should upsert in-place rather than append a duplicate.
        entries.add(
            Entry(
                eid = "a0-dup",
                kind = EntryKind.ASSISTANT,
                at = 2L,
                msg =
                    buildJsonObject {
                        put("role", "assistant")
                        put("timestamp", 2L)
                        put("content", JsonPrimitive("reply-0-updated"))
                    },
            ),
        )

        val result = ChatReducer.applyEntries(baseState, entries, epoch = "E1")

        assertFalse(result.needsResync)
        assertEquals("claude-sonnet-4", result.model)
        assertEquals(8_000, result.contextTokens)
        assertEquals(200_000, result.contextWindow)
        assertEquals(80, result.tools.size)
        assertEquals(ToolStatus.OK, result.tools["tc-0"]?.status)
        assertEquals("out-0", result.tools["tc-0"]?.result)
        assertEquals(subagent, result.tools["tc-10"]?.subagent)

        // Expected items: 80 users + 80 assistants + 3 system notes + 1 preserved pending + 1 subagent card = 165
        assertEquals(165, result.items.size)
        assertTrue((result.items[0] as UserMsg).fromMobile)
        assertEquals("reply-0-updated", (result.items[1] as AssistantMsg).plainText)
        assertEquals(NoteKind.COMPACTION, (result.items[162] as SystemNote).kind)
        assertEquals("local:pending-1", result.items[163].key)
        assertEquals(SubagentCardItem("subagent:tc-10", subagent), result.items[164])
        assertEquals(setOf("still unsent"), result.localSent)
    }
}
