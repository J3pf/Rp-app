package dev.j3pf.rp.push

/**
 * Abstracción del token FCM. El sabor `direct` devuelve siempre `null` (sin Google); el sabor `fcm` lo
 * obtiene de Firebase si hay `google-services.json`.
 */
interface PushTokenProvider {
    suspend fun currentToken(): String?
}
