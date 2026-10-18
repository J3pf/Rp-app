package dev.j3pf.rp.protocol

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshFixturesRelayTest {
    private fun readFixture(fileName: String): String {
        val resource =
            checkNotNull(javaClass.getResource("/fixtures/relay/$fileName")) {
                "Fixture not found: /fixtures/relay/$fileName"
            }
        return File(resource.toURI()).readText()
    }

    @Test
    fun `decode appDown__mesh snapshot__completo valid json`() {
        val text = readFixture("appDown__mesh.snapshot__completo.valid.json")
        val envelope = Envelope.parse(text)
        assertEquals(MsgType.MESH_SNAPSHOT, envelope.t)
        assertEquals(1, envelope.v)
        assertEquals(1760342400000L, envelope.ts)

        val snapshot = envelope.body<MeshSnapshot>()
        assertNull(snapshot.machineId)
        assertEquals(1760342400000L, snapshot.generatedAt)

        // Groups
        assertEquals(1, snapshot.groups.size)
        val group = snapshot.groups.single()
        assertEquals("group-main", group.groupId)
        assertEquals("Release work", group.label)
        assertEquals("m-pc1:orchestrator", group.orchestratorAgentId)
        assertEquals(listOf("m-pc1", "m-pc2"), group.machineIds)
        assertEquals(listOf("m-pc1:orchestrator", "m-pc1:worker-a", "m-pc2:worker-b"), group.memberAgentIds)
        assertEquals("comms", group.confidence)

        // Nodes
        assertEquals(4, snapshot.nodes.size)
        val orch = snapshot.nodes.first { it.agentId == "m-pc1:orchestrator" }
        assertEquals("m-pc1", orch.machineId)
        assertEquals("PC principal", orch.machineName)
        assertEquals("#4F7DF3", orch.machineColor)
        assertEquals("orchestrator", orch.name)
        assertEquals("/workspace/project", orch.cwd)
        assertEquals("main", orch.gitBranch)
        assertEquals("anthropic/claude-sonnet", orch.model)
        assertEquals("working", orch.status)
        assertEquals("Reviewing changes", orch.activity)
        assertTrue(orch.online)
        assertEquals("orchestrator", orch.role)
        assertNull(orch.parentAgentId)
        assertEquals("group-main", orch.groupId)

        val todos = checkNotNull(orch.todos)
        assertEquals(3, todos.total)
        assertEquals(1, todos.done)
        assertEquals(1, todos.inProgress)
        assertEquals(0, todos.blocked)
        assertEquals(1, todos.pending)
        assertEquals("Review changes", todos.current)

        assertEquals(1, orch.subagents.size)
        val sub = orch.subagents.single()
        assertEquals("task-review", sub.taskId)
        assertEquals("reviewer", sub.agent)
        assertEquals("Review implementation", sub.label)
        assertEquals("running", sub.status)
        assertEquals("anthropic/claude-sonnet", sub.model)
        assertEquals("high", sub.thinking)
        assertEquals(1760342390000L, sub.startedAt)
        assertNull(sub.endedAt)
        assertEquals(2, sub.turns)
        assertEquals(4, sub.toolCalls)
        assertNotNull(sub.tokens)
        assertEquals(1200L, sub.tokens.prompt)
        assertEquals(350L, sub.tokens.completion)
        assertEquals(1550L, sub.tokens.total)
        assertEquals(0.015, sub.cost)
        assertEquals(listOf("read", "bash"), sub.tools)

        val solo = snapshot.nodes.first { it.agentId == "m-pc2:solo" }
        assertEquals("solo", solo.role)
        assertFalse(solo.online)
        assertNull(solo.todos)

        // Edges
        assertEquals(1, snapshot.edges.size)
        val edge = snapshot.edges.single()
        assertEquals("m-pc1:orchestrator|m-pc1:worker-a", edge.edgeId)
        assertEquals("m-pc1:orchestrator", edge.a)
        assertEquals("m-pc1:worker-a", edge.b)
        assertEquals(2, edge.count)
        assertEquals(1, edge.aToB)
        assertEquals(1, edge.bToA)
        assertEquals(1760342400000L, edge.lastAt)
        assertEquals("m-pc1:orchestrator", edge.lastFrom)
        assertEquals("ask", edge.lastAction)
        assertEquals("Please review the implementation", edge.lastPreview)
        assertFalse(edge.pendingAsk)
        assertTrue(edge.active)
    }

    @Test
    fun `decode appDown__mesh update__basico valid json`() {
        val text = readFixture("appDown__mesh.update__basico.valid.json")
        val envelope = Envelope.parse(text)
        assertEquals(MsgType.MESH_UPDATE, envelope.t)
        assertEquals(1760342401000L, envelope.ts)

        val update = envelope.body<MeshSnapshot>()
        assertEquals("m-pc1", update.machineId)
        assertEquals(1760342401000L, update.generatedAt)
        assertTrue(update.groups.isEmpty())
        assertTrue(update.nodes.isEmpty())
        assertTrue(update.edges.isEmpty())
    }

    @Test
    fun `decode appDown__mesh comm__basico valid json`() {
        val text = readFixture("appDown__mesh.comm__basico.valid.json")
        val envelope = Envelope.parse(text)
        assertEquals(MsgType.MESH_COMM, envelope.t)
        assertEquals(1760342402000L, envelope.ts)

        val event = envelope.body<MeshCommEvent>()
        val comm = event.comm
        assertEquals("comm-001", comm.commKey)
        assertEquals("m-pc1:orchestrator", comm.fromAgentId)
        assertEquals("m-pc1:worker-a", comm.toAgentId)
        assertEquals("orchestrator", comm.fromName)
        assertEquals("worker-a", comm.toName)
        assertEquals(CommAction.ASK, comm.action)
        assertEquals("Please review the implementation", comm.text)
        assertEquals("Please review the implementation", comm.displayText)
        assertFalse(comm.truncated)
        assertEquals(1760342402000L, comm.at)
        assertEquals("group-main", comm.groupId)
    }

    @Test
    fun `serialize and compare appUp__mesh subscribe__basico valid json`() {
        val text = readFixture("appUp__mesh.subscribe__basico.valid.json")
        val expectedEnv = Envelope.parse(text)
        val expectedSubscribe = expectedEnv.body<MeshSubscribe>()
        assertEquals("m-pc1", expectedSubscribe.machineId)

        val myEnv = Envelope.of(MsgType.MESH_SUBSCRIBE, MeshSubscribe(machineId = "m-pc1"), ts = expectedEnv.ts ?: 0L)
        val myDecoded = myEnv.body<MeshSubscribe>()
        assertEquals(expectedSubscribe, myDecoded)
    }

    @Test
    fun `serialize and compare appUp__mesh subscribe__todas valid json`() {
        val text = readFixture("appUp__mesh.subscribe__todas.valid.json")
        val expectedEnv = Envelope.parse(text)
        val expectedSubscribe = expectedEnv.body<MeshSubscribe>()
        assertNull(expectedSubscribe.machineId)

        val myEnv = Envelope.of(MsgType.MESH_SUBSCRIBE, MeshSubscribe(machineId = null), ts = expectedEnv.ts ?: 0L)
        val myDecoded = myEnv.body<MeshSubscribe>()
        assertEquals(expectedSubscribe, myDecoded)
    }

    @Test
    fun `serialize and compare appUp__mesh unsubscribe__vacio valid json`() {
        val text = readFixture("appUp__mesh.unsubscribe__vacio.valid.json")
        val expectedEnv = Envelope.parse(text)
        assertEquals(MsgType.MESH_UNSUBSCRIBE, expectedEnv.t)

        val myEnv = Envelope.of(MsgType.MESH_UNSUBSCRIBE, MeshUnsubscribe(), ts = expectedEnv.ts ?: 0L)
        assertEquals(expectedEnv.t, myEnv.t)
        assertEquals("{}", myEnv.d.toString())
    }

    @Test
    fun `decode mesh-conversation-response json`() {
        val text = readFixture("mesh-conversation-response.json")
        val response = RpJson.decodeFromString(MeshConversationResponse.serializer(), text)
        assertEquals(2, response.items.size)
        assertEquals(1760342402000L, response.nextBefore)

        val item0 = response.items[0]
        assertEquals("comm-002", item0.commKey)
        assertEquals("m-pc1:worker-a", item0.fromAgentId)
        assertEquals("m-pc1:orchestrator", item0.toAgentId)
        assertEquals("worker-a", item0.fromName)
        assertEquals("orchestrator", item0.toName)
        assertEquals(CommAction.REPLY, item0.action)
        assertEquals("Review complete, all tests are green.", item0.text)
        assertFalse(item0.truncated)
        assertEquals(1760342410000L, item0.at)
        assertEquals("group-main", item0.groupId)

        val item1 = response.items[1]
        assertEquals("comm-001", item1.commKey)
        assertEquals(CommAction.ASK, item1.action)
    }

    @Test
    fun `decode mesh-agent-comms-response json`() {
        val text = readFixture("mesh-agent-comms-response.json")
        val response = RpJson.decodeFromString(MeshAgentCommsResponse.serializer(), text)
        assertEquals(1, response.items.size)
        val item = response.items.single()
        assertEquals("comm-003", item.commKey)
        assertEquals("m-pc1:orchestrator", item.fromAgentId)
        assertEquals("m-pc1:worker-a", item.toAgentId)
        assertEquals(CommAction.ASK, item.action)
        assertEquals("Can you review this change?", item.text)
        assertFalse(item.truncated)
        assertEquals(1760342404000L, item.at)
        assertEquals("group-main", item.groupId)
    }
}
