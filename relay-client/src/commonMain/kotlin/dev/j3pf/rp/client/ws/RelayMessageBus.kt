package dev.j3pf.rp.client.ws

import dev.j3pf.rp.protocol.Envelope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Shared by foreground correlation and native direct-push publication. */
class RelayMessageBus {
    private val mutableMessages = MutableSharedFlow<Envelope>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<Envelope> = mutableMessages.asSharedFlow()

    fun publish(env: Envelope) {
        mutableMessages.tryEmit(env)
    }
}
