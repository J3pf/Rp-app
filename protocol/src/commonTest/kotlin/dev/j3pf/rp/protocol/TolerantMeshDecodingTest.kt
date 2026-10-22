package dev.j3pf.rp.protocol

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TolerantMeshDecodingTest {
    @Test
    fun `tolerant snapshot decodes valid nodes and skips corrupt node`() {
        val json =
            """
            {
                "machineId": "m1",
                "generatedAt": 1700000000000,
                "nodes": [
                    {
                        "agentId": "good-1",
                        "machineId": "m1",
                        "machineName": "PC 1",
                        "name": "Good Node",
                        "status": "working"
                    },
                    {
                        "agentId": 12345,
                        "name": "Corrupt Node (agentId is number, not string)"
                    },
                    {
                        "agentId": "good-2",
                        "machineId": "m1",
                        "machineName": "PC 1",
                        "name": "Second Good Node",
                        "status": "idle"
                    }
                ]
            }
            """.trimIndent()

        val snapshot = RpJson.decodeFromString(MeshSnapshot.serializer(), json)
        assertEquals(2, snapshot.nodes.size)
        assertEquals("good-1", snapshot.nodes[0].agentId)
        assertEquals("good-2", snapshot.nodes[1].agentId)
    }

    @Test
    fun `tolerant node decodes valid subagents and skips corrupt subagent`() {
        val nodeJson =
            """
            {
                "agentId": "node-1",
                "machineId": "m1",
                "machineName": "PC 1",
                "name": "Orchestrator",
                "status": "working",
                "subagents": [
                    {
                        "taskId": "task-good",
                        "agent": "sub-1",
                        "status": "running",
                        "startedAt": 47725510.340108,
                        "tokens": 321595,
                        "turns": 9
                    },
                    {
                        "taskId": "task-corrupt",
                        "agent": "sub-corrupt",
                        "status": "running",
                        "turns": "non-integer-string-turns-should-fail-element"
                    },
                    {
                        "taskId": "task-good-2",
                        "agent": "sub-2",
                        "status": "done",
                        "startedAt": 1700000000000,
                        "endedAt": 1700000005000.5
                    }
                ]
            }
            """.trimIndent()

        val node = RpJson.decodeFromString(MeshNode.serializer(), nodeJson)
        assertEquals(2, node.subagents.size)
        assertEquals("task-good", node.subagents[0].taskId)
        assertEquals(47725510L, node.subagents[0].startedAt)
        assertEquals(321595L, node.subagents[0].tokens?.total)
        assertEquals("task-good-2", node.subagents[1].taskId)
        assertEquals(1700000005001L, node.subagents[1].endedAt)
    }

    @Test
    fun `unreadable top-level snapshot fails deserialization to trigger error state`() {
        assertFailsWith<SerializationException> {
            RpJson.decodeFromString(MeshSnapshot.serializer(), "\"not-an-object\"")
        }
    }
}
