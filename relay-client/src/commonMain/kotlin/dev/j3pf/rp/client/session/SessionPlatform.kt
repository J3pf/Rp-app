package dev.j3pf.rp.client.session

import dev.j3pf.rp.data.secure.Session

/** Native session storage, settings, and push/connection lifecycle operations. */
interface SessionPlatform {
    fun currentSession(): Session?

    fun newRelayId(): String

    fun saveSession(session: Session)

    /** Stop connection, cancel work, clear caches with old credentials, then remove channels. */
    suspend fun clearLocalSession()

    /** Clear native encrypted credentials, then wipe settings. */
    suspend fun clearCredentialsAndSettings()

    suspend fun currentToken(): String?

    fun deviceName(): String

    suspend fun directPush(): Boolean

    suspend fun setDirectPush(direct: Boolean)

    fun startDirectPush()

    fun stopDirectPush()

    fun startConnection()
}
