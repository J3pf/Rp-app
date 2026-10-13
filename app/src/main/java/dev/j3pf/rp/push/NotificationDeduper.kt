package dev.j3pf.rp.push

import dev.j3pf.rp.data.db.DedupDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Garantiza una sola notificación por `promptId`/`pushId` aunque lleguen por el canal directo y por FCM
 * (Room, ventana de 24 h). Ambos canales pasan por [PushRouter] → [NotificationFactory].
 */
@Singleton
class NotificationDeduper @Inject constructor(private val dao: DedupDao) {
    /** `true` si es la primera vez que se ve [key]. */
    suspend fun firstSeen(key: String, now: Long = System.currentTimeMillis()): Boolean = dao.firstSeen(key, now)

    /** Olvida la clave (p. ej. al cancelar un prompt, por si se reabre con el mismo id). */
    suspend fun forget(key: String) = dao.remove(key)
}
