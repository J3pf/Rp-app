package dev.j3pf.rp.client.session

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairDeviceResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SessionPolicyStoredEnvGuardTest {
    private class Platform : SessionPlatform {
        val pushRequests = mutableListOf<Unit>()

        override fun currentSession(): Session =
            Session("relay", "https://relay", "Relay", "device", "token", "key")

        override fun newRelayId(): String = "new-relay"

        override fun saveSession(session: Session) = Unit

        override suspend fun clearLocalSession() = Unit

        override suspend fun clearCredentialsAndSettings() = Unit

        override suspend fun currentToken(): String? = "fcm-token"

        override suspend fun currentApnsToken(): String? = "apns-token"

        override fun apnsEnvironment(): String? = "testing"

        override fun pushPlatform(): SessionPushPlatform = SessionPushPlatform.IOS

        override fun deviceName(): String = "Phone"

        override suspend fun directPush(): Boolean = false

        override suspend fun setDirectPush(direct: Boolean) = Unit

        override fun startDirectPush() = Unit

        override fun stopDirectPush() = Unit

        override fun startConnection() = Unit
    }

    @Test
    fun applyPushChannelsDoesNotSendWhenStoredApnsEnvironmentIsInvalid() =
        runTest {
            val platform = Platform()
            val policy =
                SessionPolicy(
                    normalizeUrl = { it },
                    healthz = { ApiResult.Ok(HealthResponse()) },
                    pairDevice = { _, _, _, _ ->
                        ApiResult.Ok(PairDeviceResponse("device", "token", "key"))
                    },
                    putPush = { _, _, _, _, _ ->
                        platform.pushRequests += Unit
                        ApiResult.Ok(OkResponse())
                    },
                    revokeDevice = { ApiResult.Ok(OkResponse()) },
                    platform = platform,
                )

            policy.applyPushChannels(false)

            assertTrue(platform.pushRequests.isEmpty())
        }
}
