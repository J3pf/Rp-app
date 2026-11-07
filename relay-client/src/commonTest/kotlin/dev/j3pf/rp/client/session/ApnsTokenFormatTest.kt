package dev.j3pf.rp.client.session

import kotlin.test.Test
import kotlin.test.assertEquals

class ApnsTokenFormatTest {
    @Test
    fun encodesBytesAsLowercaseHexWithoutSeparators() {
        assertEquals(
            "000fa0ff",
            ApnsTokenFormat.hexLowercase(byteArrayOf(0x00, 0x0f, 0xa0.toByte(), 0xff.toByte())),
        )
    }

    @Test
    fun encodesEmptyBytesAsAnEmptyString() {
        assertEquals("", ApnsTokenFormat.hexLowercase(byteArrayOf()))
    }
}
