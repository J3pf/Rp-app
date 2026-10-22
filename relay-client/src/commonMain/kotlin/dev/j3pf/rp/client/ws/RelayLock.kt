package dev.j3pf.rp.client.ws

/** Reentrant: socket callbacks and native connectivity events share this lock. */
expect class RelayLock() {
    fun <T> withLock(block: () -> T): T
}
