package dev.j3pf.rp.client.session

import dev.j3pf.rp.data.secure.Session

/** Platform owning the push token used by the relay. */
enum class SessionPushPlatform(
    val wireValue: String,
) {
    ANDROID("android"),
    IOS("ios"),
}

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

    /** Null keeps the relay's APNs token; an empty string explicitly clears it. */
    suspend fun currentApnsToken(): String? = null

    /** Null keeps the relay's APNs environment; an empty string explicitly clears it. */
    fun apnsEnvironment(): String? = null

    /** Existing platforms default to Android to preserve their current registration behavior. */
    fun pushPlatform(): SessionPushPlatform = SessionPushPlatform.ANDROID

    fun deviceName(): String

    suspend fun directPush(): Boolean

    suspend fun setDirectPush(direct: Boolean)

    fun startDirectPush()

    fun stopDirectPush()

    fun startConnection()
}
