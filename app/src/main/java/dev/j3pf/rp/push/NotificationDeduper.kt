package dev.j3pf.rp.push

import dev.j3pf.rp.data.db.DedupDao
import dev.j3pf.rp.data.db.PromptState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Garantiza una sola notificación por `promptId`/`pushId` aunque lleguen por el canal directo y por FCM
 * (Room, ventana de 24 h). Ambos canales pasan por [PushRouter] → [NotificationFactory].
 */
@Singleton
class NotificationDeduper
    @Inject
    constructor(
        private val dao: DedupDao,
    ) {
        /** `true` si es la primera vez que se ve [key]. */
        suspend fun firstSeen(
            key: String,
            now: Long = System.currentTimeMillis(),
        ): Boolean = dao.firstSeen(key, now)

        /** Olvida la clave (p. ej. al asentar un prompt, para que un id reutilizado pueda notificarse otra vez). */
        suspend fun forget(key: String) = dao.remove(key)

        /**
         * Recuerda que este dispositivo asentó el prompt. Cubre el push que nunca llegó a persistirse
         * como fila: un replay inmediato no puede volver a la bandeja.
         */
        suspend fun markAnswered(
            promptId: String,
            now: Long = System.currentTimeMillis(),
        ) = dao.mark(answeredKey(promptId), now)

        /** `true` si el prompt se asentó aquí dentro de la ventana de replay. */
        suspend fun answeredRecently(
            promptId: String,
            now: Long = System.currentTimeMillis(),
        ): Boolean = dao.seenSince(answeredKey(promptId), PromptState.REPLAY_WINDOW_MS, now)

        companion object {
            /** Clave de deduplicación de la notificación de un prompt. */
            fun promptKey(promptId: String) = "prompt:$promptId"

            /** Marca de "ya respondido en este dispositivo". */
            fun answeredKey(promptId: String) = "answered:$promptId"
        }
    }
