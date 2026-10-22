package dev.j3pf.rp.client.ws

actual class RelayLock {
    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
