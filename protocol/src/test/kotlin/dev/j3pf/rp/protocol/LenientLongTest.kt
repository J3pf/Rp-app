package dev.j3pf.rp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LenientLongTest {
    @Serializable
    private data class Dummy(
        @Serializable(with = LenientLongSerializer::class)
        val value: Long,
        @Serializable(with = LenientLongSerializer::class)
        val optionalValue: Long? = null,
    )

    @Test
    fun `decodes integer numbers accurately`() {
        val json = """{"value": 1234567890123, "optionalValue": 42}"""
        val decoded = RpJson.decodeFromString(Dummy.serializer(), json)
        assertEquals(1234567890123L, decoded.value)
        assertEquals(42L, decoded.optionalValue)
    }

    @Test
    fun `decodes floating point numbers with round half-up`() {
        val json1 = """{"value": 47725510.340108, "optionalValue": 1.0}"""
        val decoded1 = RpJson.decodeFromString(Dummy.serializer(), json1)
        assertEquals(47725510L, decoded1.value)
        assertEquals(1L, decoded1.optionalValue)

        val json2 = """{"value": 1.5, "optionalValue": 2.4}"""
        val decoded2 = RpJson.decodeFromString(Dummy.serializer(), json2)
        assertEquals(2L, decoded2.value)
        assertEquals(2L, decoded2.optionalValue)

        val json3 = """{"value": 0.0, "optionalValue": null}"""
        val decoded3 = RpJson.decodeFromString(Dummy.serializer(), json3)
        assertEquals(0L, decoded3.value)
        assertNull(decoded3.optionalValue)
    }

    @Test
    fun `rejects strings booleans and non-number types`() {
        assertFailsWith<SerializationException> {
            RpJson.decodeFromString(Dummy.serializer(), """{"value": "12345"}""")
        }

        assertFailsWith<SerializationException> {
            RpJson.decodeFromString(Dummy.serializer(), """{"value": true}""")
        }

        assertFailsWith<SerializationException> {
            RpJson.decodeFromString(Dummy.serializer(), """{"value": {}}""")
        }

        assertFailsWith<SerializationException> {
            RpJson.decodeFromString(Dummy.serializer(), """{"value": []}""")
        }
    }

    @Test
    fun `decodes exact failing payload from real device`() {
        val subagentJson =
            """
            {
                "taskId": "task-alpha-1",
                "agent": "worker-1",
                "label": "Deep Worker",
                "status": "running",
                "model": "gpt-6-luna",
                "thinking": "xhigh",
                "startedAt": 47725510.340108,
                "tokens": 321595,
                "turns": 9
            }
            """.trimIndent()

        val subagent = RpJson.decodeFromString(MeshSubagent.serializer(), subagentJson)
        assertEquals("task-alpha-1", subagent.taskId)
        assertEquals("worker-1", subagent.agent)
        assertEquals("running", subagent.status)
        assertEquals("gpt-6-luna", subagent.model)
        assertEquals("xhigh", subagent.thinking)
        assertEquals(47725510L, subagent.startedAt)
        assertEquals(321595L, subagent.tokens?.total)
        assertEquals(9, subagent.turns)
    }
}
