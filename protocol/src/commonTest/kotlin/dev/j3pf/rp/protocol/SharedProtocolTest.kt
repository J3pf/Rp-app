package dev.j3pf.rp.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class SharedProtocolTest {
    @Test
    fun `numeric helpers preserve rounding ties and long boundaries`() {
        val cases =
            mapOf(
                "-1.5" to -1L,
                "-0.5" to 0L,
                "0.5" to 1L,
                "1.5" to 2L,
                "-1.5000000000000002" to -2L,
                "0.49999999999999994" to 0L,
                "9223372036854775807" to Long.MAX_VALUE,
                "-9223372036854775808" to Long.MIN_VALUE,
                "1e30" to Long.MAX_VALUE,
                "-1e30" to Long.MIN_VALUE,
            )
        for ((raw, expected) in cases) {
            val element = RpJson.parseToJsonElement(raw)
            assertEquals(expected, element.lenientLong(), raw)
            assertEquals(expected, RpJson.decodeFromString(LenientLongSerializer, raw), raw)
        }
    }

    @Test
    fun `envelope factories preserve wire shape and epoch millisecond timestamps`() {
        val explicit = Envelope.of("probe", JsonPrimitive("body"), id = "id", re = "parent", ts = 42L)
        assertEquals("""{"v":1,"t":"probe","id":"id","re":"parent","ts":42,"d":"body"}""", explicit.encode())
        assertEquals(explicit, Envelope.parse(explicit.encode()))

        val before = Clock.System.now().toEpochMilliseconds()
        val withBody = Envelope.of("probe", JsonPrimitive("body"))
        val empty = Envelope.empty("probe", id = "id")
        val after = Clock.System.now().toEpochMilliseconds()
        assertTrue(assertNotNull(withBody.ts) in before..after)
        assertTrue(assertNotNull(empty.ts) in before..after)
        assertEquals("id", empty.id)
        assertEquals("{}", empty.d.toString())
    }
}
