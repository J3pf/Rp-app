package dev.j3pf.rp.data.net

import dev.j3pf.rp.protocol.Envelope

/** Resultado de una petición con `id` sobre el WS. */
sealed interface WsReply {
    data class Ok(
        val envelope: Envelope,
    ) : WsReply

    data class Error(
        val code: String,
        val message: String,
    ) : WsReply

    data object Timeout : WsReply

    data object NotConnected : WsReply
}
