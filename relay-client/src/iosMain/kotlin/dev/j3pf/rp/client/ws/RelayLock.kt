package dev.j3pf.rp.client.ws

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSRecursiveLock

@OptIn(ExperimentalForeignApi::class)
actual class RelayLock {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
