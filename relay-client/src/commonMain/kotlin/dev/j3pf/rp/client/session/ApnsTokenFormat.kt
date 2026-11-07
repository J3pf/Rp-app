package dev.j3pf.rp.client.session

/** Canonical APNs token encoding used by the iOS bridge and relay registration API. */
object ApnsTokenFormat {
    private const val HEX = "0123456789abcdef"

    /** Encodes token bytes as lowercase hexadecimal without separators. */
    fun hexLowercase(bytes: ByteArray): String =
        buildString(bytes.size * 2) {
            for (byte in bytes) {
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
}
