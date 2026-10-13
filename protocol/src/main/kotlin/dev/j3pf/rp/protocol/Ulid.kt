package dev.j3pf.rp.protocol

import java.security.SecureRandom

/** Generador mínimo de ULID (26 caracteres Crockford base32), usado para `id` y `clientMsgId`. */
object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun new(timeMs: Long = System.currentTimeMillis()): String {
        val out = CharArray(26)
        var t = timeMs
        for (i in 9 downTo 0) {
            out[i] = ALPHABET[(t and 31).toInt()]
            t = t shr 5
        }
        val bytes = ByteArray(10).also(random::nextBytes)
        // 80 bits aleatorios → 16 caracteres de 5 bits
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
        return String(out)
    }

    fun isValid(s: String): Boolean = s.length == 26 && s.all { it in ALPHABET }
}
