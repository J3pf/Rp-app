package dev.j3pf.rp.data.repo

import dev.j3pf.rp.protocol.UserMessageAck

/** Resultado de enviar un mensaje al agente. */
sealed interface SendResult {
    data class Delivered(
        val ack: UserMessageAck?,
    ) : SendResult

    /** Sin red: queda en la bandeja de salida y WorkManager lo envía al volver la conexión. */
    data object Queued : SendResult

    data class Failed(
        val reason: String,
    ) : SendResult
}
