package dev.j3pf.rp.protocol

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.arc4random_buf

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    if (bytes.isNotEmpty()) {
        bytes.usePinned { arc4random_buf(it.addressOf(0), count.toULong()) }
    }
    return bytes
}
