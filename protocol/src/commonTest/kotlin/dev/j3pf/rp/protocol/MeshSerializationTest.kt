package dev.j3pf.rp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshSerializationTest {
    @Test
    fun `subscribe and unsubscribe encode and decode`() {
        val subscribe = MeshSubscribe(machineId = "machine-1")
        val encodedSubscribe = RpJson.encodeToString(MeshSubscribe.serializer(), subscribe)
        assertEquals("""{"machineId":"machine-1"}""", encodedSubscribe)
        assertEquals(subscribe, RpJson.decodeFromString(MeshSubscribe.serializer(), encodedSubscribe))

        val subscribeTodas = MeshSubscribe(machineId = null)
        val encodedSubscribeTodas = RpJson.encodeToString(MeshSubscribe.serializer(), subscribeTodas)
        assertEquals("""{"machineId":null}""", encodedSubscribeTodas)

        assertEquals("{}", RpJson.encodeToString(MeshUnsubscribe.serializer(), MeshUnsubscribe()))
        val decodedUnsubscribe = RpJson.decodeFromString(MeshUnsubscribe.serializer(), "{}")
        assertEquals("{}", RpJson.encodeToString(MeshUnsubscribe.serializer(), decodedUnsubscribe))
        assertEquals(MeshSubscribe(), RpJson.decodeFromString(MeshSubscribe.serializer(), "{}"))
    }

    @Test
    fun `mesh snapshot and nested models encode and decode`() {
        val snapshot =
            MeshSnapshot(
                machineId = "machine-1",
                generatedAt = 1_700_000_000_000,
                groups = listOf(MeshGroup("group-1", "Orchestrator", "agent-1", listOf("machine-1"), listOf("agent-1", "agent-2"), "cwd")),
                nodes =
                    listOf(
                        MeshNode(
                            agentId = "agent-1",
                            machineId = "machine-1",
                            machineName = "Workstation",
                            machineColor = "#123456",
                            name = "Orchestrator",
                            cwd = "/workspace",
                            gitBranch = "main",
                            model = "claude-sonnet",
                            status = "working",
                            activity = "Reviewing changes",
                            online = true,
                            role = "orchestrator",
                            groupId = "group-1",
                            todos = MeshTodos(total = 4, done = 1, inProgress = 1, blocked = 0, pending = 2, current = "Review"),
                            subagents =
                                listOf(
                                    MeshSubagent(
                                        taskId = "task-1",
                                        agent = "worker",
                                        label = "Tests",
                                        status = "completed",
                                        model = "claude-haiku",
                                        thinking = "careful",
                                        startedAt = 10,
                                        endedAt = 20,
                                        durationMs = 10,
                                        turns = 2,
                                        toolCalls = 1,
                                        tokens = MeshSubagentTokens(total = 300),
                                        cost = 0.02,
                                        error = null,
                                        result = null,
                                        tools = listOf("gradle"),
                                    ),
                                ),
                            lastCommAt = 30,
                            openPromptCount = 1,
                        ),
                    ),
                edges =
                    listOf(
                        MeshEdge("edge-1", "agent-1", "agent-2", 3, 2, 1, 40, "agent-1", "ask", "Need help", true, true),
                    ),
            )

        val encoded = RpJson.encodeToString(MeshSnapshot.serializer(), snapshot)
        assertEquals(snapshot, RpJson.decodeFromString(MeshSnapshot.serializer(), encoded))
    }

    @Test
    fun `comm and HTTP response models encode and decode`() {
        val comm = MeshComm("comm-1", "agent-1", "agent-2", "One", "Two", CommAction.ASK, "Can you help?", true, 123, "group-1")
        val event = MeshCommEvent(comm)
        val encodedEvent = RpJson.encodeToString(MeshCommEvent.serializer(), event)
        assertEquals(event, RpJson.decodeFromString(MeshCommEvent.serializer(), encodedEvent))

        val conversation = MeshConversationResponse(listOf(comm), nextBefore = 100)
        val encodedConversation = RpJson.encodeToString(MeshConversationResponse.serializer(), conversation)
        assertEquals(conversation, RpJson.decodeFromString(MeshConversationResponse.serializer(), encodedConversation))

        val agentComms = MeshAgentCommsResponse(listOf(comm))
        val encodedAgentComms = RpJson.encodeToString(MeshAgentCommsResponse.serializer(), agentComms)
        assertEquals(agentComms, RpJson.decodeFromString(MeshAgentCommsResponse.serializer(), encodedAgentComms))

        // Redacted text falls back to "Content hidden"
        val redacted = MeshComm("comm-2", "agent-1", "agent-2", "One", "Two", CommAction.SEND, text = null, truncated = false, at = 124)
        assertEquals("Content hidden", redacted.displayText)

        // Unknown action gracefully falls back to UNKNOWN
        val unknownActionJson = """{"commKey":"c3","fromName":"A","toName":"B","action":"teleport","at":125}"""
        val decodedUnknown = RpJson.decodeFromString(MeshComm.serializer(), unknownActionJson)
        assertEquals(CommAction.UNKNOWN, decodedUnknown.action)
    }

    @Test
    fun `unknown fields in every mesh record are ignored`() {
        val json =
            """
            {
                "machineId":"machine-1","generatedAt":1,
                "groups":[{"groupId":"g","label":"Group","extraGroup":true}],
                "nodes":[{
                  "agentId":"a","machineId":"m","machineName":"Machine","name":"Agent","status":"idle",
                  "extraNode":{"future":1},
                  "subagents":[{
                    "taskId":"t","agent":"worker","label":"Worker","status":"running","extraSubagent":2,
                    "tools":["shell"]
                  }]
                }],
                "edges":[{"edgeId":"e","a":"a","b":"b","extraEdge":false}],
                "extraSnapshot":"future"
            }
            """.trimIndent()
        val snapshot = RpJson.decodeFromString(MeshSnapshot.serializer(), json)
        assertEquals("Group", snapshot.groups.single().label)
        assertEquals(
            "Worker",
            snapshot.nodes
                .single()
                .subagents
                .single()
                .label,
        )
        assertEquals(
            listOf("shell"),
            snapshot.nodes
                .single()
                .subagents
                .single()
                .tools,
        )
        assertEquals("e", snapshot.edges.single().edgeId)

        val commJson =
            """{"commKey":"c","fromAgentId":"a","toAgentId":"b","fromName":"A","toName":"B","action":"message","extraComm":true}"""
        assertEquals("c", RpJson.decodeFromString(MeshComm.serializer(), commJson).commKey)
    }

    @Test
    fun `missing fields use defaults and nullable fields accept null`() {
        assertEquals(
            MeshSnapshot(),
            RpJson.decodeFromString(MeshSnapshot.serializer(), "{}"),
        )
        assertEquals(MeshConversationResponse(), RpJson.decodeFromString(MeshConversationResponse.serializer(), "{}"))
        assertEquals(MeshAgentCommsResponse(), RpJson.decodeFromString(MeshAgentCommsResponse.serializer(), "{}"))
        assertEquals(MeshGroup("g", "Group"), RpJson.decodeFromString(MeshGroup.serializer(), """{"groupId":"g","label":"Group"}"""))
        assertEquals(MeshTodos(), RpJson.decodeFromString(MeshTodos.serializer(), "{}"))
        assertEquals(
            MeshToolCall("tc", "shell"),
            RpJson.decodeFromString(MeshToolCall.serializer(), """{"toolCallId":"tc","name":"shell"}"""),
        )
        assertEquals(
            MeshSubagent("task", "agent", "Worker", "pending"),
            RpJson.decodeFromString(MeshSubagent.serializer(), """{"taskId":"task","agent":"agent","label":"Worker","status":"pending"}"""),
        )
        assertEquals(
            MeshNode("agent", "machine", "Machine", name = "Agent", status = "idle"),
            RpJson.decodeFromString(
                MeshNode.serializer(),
                """{"agentId":"agent","machineId":"machine","machineName":"Machine","name":"Agent","status":"idle"}""",
            ),
        )
        assertEquals(MeshEdge("edge", "a", "b"), RpJson.decodeFromString(MeshEdge.serializer(), """{"edgeId":"edge","a":"a","b":"b"}"""))

        val comm =
            RpJson.decodeFromString(
                MeshComm.serializer(),
                """{"commKey":"c","fromAgentId":"a","toAgentId":"b","fromName":"A","toName":"B","action":"send","text":null,"groupId":null}""",
            )
        assertNull(comm.text)
        assertNull(comm.groupId)
        assertFalse(comm.truncated)
        assertEquals(0L, comm.at)

        val node =
            RpJson.decodeFromString(
                MeshNode.serializer(),
                """{"agentId":"agent","machineId":"machine","machineName":"Machine","name":"Agent","status":"idle","cwd":null,"todos":null,"parentAgentId":null}""",
            )
        assertNull(node.cwd)
        assertNull(node.todos)
        assertNull(node.parentAgentId)
        assertTrue(node.subagents.isEmpty())
        assertEquals("#6750A4", node.machineColor)
        assertEquals("solo", node.role)
        assertEquals(0, node.openPromptCount)
    }

    @Test
    fun `explicit nulls are accepted for every nullable mesh property`() {
        val snapshot =
            RpJson.decodeFromString(
                MeshSnapshot.serializer(),
                """
                {
                  "machineId":null,
                  "groups":[{"groupId":"g","label":"Group","orchestratorAgentId":null}],
                  "nodes":[{
                    "agentId":"a","machineId":"m","machineName":"Machine","name":"Agent","status":"idle",
                    "cwd":null,"gitBranch":null,"model":null,"activity":null,"parentAgentId":null,"groupId":null,
                    "todos":{"current":null},
                    "subagents":[{
                      "taskId":"t","agent":"worker","label":"Worker","status":"pending","model":null,"thinking":null,
                      "startedAt":null,"endedAt":null,"durationMs":null,"turns":null,"toolCalls":null,"tokens":null,
                      "cost":null,"error":null,"result":null,"tools":null
                    }],
                    "lastCommAt":null
                  }],
                  "edges":[{"edgeId":"e","a":"a","b":"b","lastAt":null,"lastFrom":null,"lastAction":null,"lastPreview":null}]
                }
                """.trimIndent(),
            )
        assertNull(snapshot.machineId)
        assertNull(snapshot.groups.single().orchestratorAgentId)
        val node = snapshot.nodes.single()
        assertNull(node.cwd)
        assertNull(node.gitBranch)
        assertNull(node.model)
        assertNull(node.activity)
        assertNull(node.parentAgentId)
        assertNull(node.groupId)
        assertNull(node.todos!!.current)
        assertNull(node.lastCommAt)
        val subagent = node.subagents.single()
        assertNull(subagent.model)
        assertNull(subagent.thinking)
        assertNull(subagent.startedAt)
        assertNull(subagent.endedAt)
        assertNull(subagent.durationMs)
        assertNull(subagent.turns)
        assertNull(subagent.toolCalls)
        assertNull(subagent.tokens)
        assertNull(subagent.cost)
        assertNull(subagent.error)
        assertNull(subagent.result)
        assertNull(subagent.tools)
        val edge = snapshot.edges.single()
        assertNull(edge.lastAt)
        assertNull(edge.lastFrom)
        assertNull(edge.lastAction)
        assertNull(edge.lastPreview)

        assertNull(
            RpJson
                .decodeFromString(
                    MeshToolCall.serializer(),
                    """{"toolCallId":"tc","name":"shell","status":null,"durationMs":null}""",
                ).status,
        )
        assertNull(RpJson.decodeFromString(MeshConversationResponse.serializer(), """{"nextBefore":null}""").nextBefore)
        assertNull(
            RpJson
                .decodeFromString(
                    MeshComm.serializer(),
                    """{"commKey":"c","fromAgentId":"a","toAgentId":"b","fromName":"A","toName":"B","action":"send","text":null,"groupId":null}""",
                ).text,
        )
    }

    @Test
    fun `envelopes encode and decode mesh snapshots and comm events`() {
        val snapshot = MeshSnapshot(machineId = "machine-1", generatedAt = 12)
        val snapshotEnvelope = Envelope.of(MsgType.MESH_SNAPSHOT, snapshot, ts = 12)
        val parsedSnapshot = Envelope.parse(snapshotEnvelope.encode())
        assertEquals(MsgType.MESH_SNAPSHOT, parsedSnapshot.t)
        assertEquals(snapshot, parsedSnapshot.body<MeshSnapshot>())

        val event = MeshCommEvent(MeshComm("c", "a", "b", "A", "B", CommAction.REPLY))
        val commEnvelope = Envelope.of(MsgType.MESH_COMM, event, ts = 13)
        val parsedComm = Envelope.parse(commEnvelope.encode())
        assertEquals(MsgType.MESH_COMM, parsedComm.t)
        assertEquals(event, parsedComm.body<MeshCommEvent>())
    }
}
