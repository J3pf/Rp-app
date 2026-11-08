package dev.j3pf.rp.data.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.arc4random_buf
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
private object IosSecureRandom : Random() {
    override fun nextBits(bitCount: Int): Int {
        if (bitCount == 0) return 0
        val bytes = nextBytes(4)
        val raw =
            ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        return raw ushr (32 - bitCount)
    }

    override fun nextBytes(
        array: ByteArray,
        fromIndex: Int,
        toIndex: Int,
    ): ByteArray {
        require(fromIndex in 0..array.size && toIndex in 0..array.size && fromIndex <= toIndex)
        val count = toIndex - fromIndex
        if (count == 0) return array
        array.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, count.toULong(), pinned.addressOf(fromIndex))
            if (status != errSecSuccess) {
                arc4random_buf(pinned.addressOf(fromIndex), count.toULong())
            }
        }
        return array
    }
}

actual fun platformSecureRandom(): Random = IosSecureRandom
