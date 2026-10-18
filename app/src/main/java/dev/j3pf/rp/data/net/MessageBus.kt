package dev.j3pf.rp.data.net

import dev.j3pf.rp.protocol.Envelope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bus único de mensajes del relay. Tanto la conexión de primer plano como la del servicio directo publican aquí;
 * los repositorios aplican los mensajes de forma idempotente (upserts), así que recibir `prompt.opened` por los dos
 * canales no duplica nada.
 */
@Singleton
class MessageBus
    @Inject
    constructor() {
        private val _messages = MutableSharedFlow<Envelope>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val messages: SharedFlow<Envelope> = _messages.asSharedFlow()

        fun publish(env: Envelope) {
            _messages.tryEmit(env)
        }
    }
