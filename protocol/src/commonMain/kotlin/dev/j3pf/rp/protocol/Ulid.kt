package dev.j3pf.rp.protocol

import kotlin.time.Clock

internal expect fun platformRandomBytes(count: Int): ByteArray

/** Minimum ULID generator (26 Crockford base32 characters) for `id` and `clientMsgId`. */
object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun new(timeMs: Long = Clock.System.now().toEpochMilliseconds()): String {
        val out = CharArray(26)
        var t = timeMs
        for (i in 9 downTo 0) {
            out[i] = ALPHABET[(t and 31).toInt()]
            t = t shr 5
        }
        val bytes = platformRandomBytes(10)
        // 80 random bits -> 16 5-bit characters
        var acc = 0L
        var bits = 0
        var idx = 10
        for (b in bytes) {
            acc = (acc shl 8) or (b.toLong() and 0xff)
            bits += 8
            while (bits >= 5 && idx < 26) {
                bits -= 5
                out[idx++] = ALPHABET[((acc shr bits) and 31).toInt()]
            }
        }
        return out.concatToString()
    }

    fun isValid(s: String): Boolean = s.length == 26 && s.all { it in ALPHABET }
}
