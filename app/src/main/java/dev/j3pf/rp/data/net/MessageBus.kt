package dev.j3pf.rp.data.net

import dev.j3pf.rp.client.ws.RelayMessageBus
import dev.j3pf.rp.protocol.Envelope
import kotlinx.coroutines.flow.SharedFlow
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
        internal val shared = RelayMessageBus()
        val messages: SharedFlow<Envelope> = shared.messages

        fun publish(env: Envelope) {
            shared.publish(env)
        }
    }
