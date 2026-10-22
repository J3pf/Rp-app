package dev.j3pf.rp.data.net

/** Resultado de responder un prompt. */
sealed interface AnswerOutcome {
    data object Sent : AnswerOutcome

    /** 409: ya respondido en otro lugar. También cuenta como "listo" para quitar la notificación. */
    data object AlreadyResolved : AnswerOutcome

    /** Sin red / error temporal: se reintenta con WorkManager. */
    data object Queued : AnswerOutcome

    data class Failed(
        val message: String,
    ) : AnswerOutcome
}
