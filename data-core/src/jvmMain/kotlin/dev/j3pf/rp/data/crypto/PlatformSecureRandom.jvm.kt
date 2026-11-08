package dev.j3pf.rp.data.crypto

import java.security.SecureRandom
import kotlin.random.Random

private object JvmSecureRandom : Random() {
    private val delegate = SecureRandom()

    override fun nextBits(bitCount: Int): Int {
        if (bitCount == 0) return 0
        return delegate.nextInt() ushr (32 - bitCount)
    }

    override fun nextBytes(
        array: ByteArray,
        fromIndex: Int,
        toIndex: Int,
    ): ByteArray {
        require(fromIndex in 0..array.size && toIndex in 0..array.size && fromIndex <= toIndex)
        val count = toIndex - fromIndex
        if (count == 0) return array
        if (fromIndex == 0 && toIndex == array.size) {
            delegate.nextBytes(array)
        } else {
            val tmp = ByteArray(count)
            delegate.nextBytes(tmp)
            tmp.copyInto(array, destinationOffset = fromIndex)
        }
        return array
    }
}

actual fun platformSecureRandom(): Random = JvmSecureRandom
