package dev.j3pf.rp.client.session

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairDeviceResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplePushRegistrationTest {
    private class RegistrationPlatform : SessionPlatform {
        override fun currentSession(): Session? = Session("relay", "https://relay", "Relay", "device", "token", "key")

        override fun newRelayId(): String = "new-relay"

        override fun saveSession(session: Session) = Unit

        override suspend fun clearLocalSession() = Unit

        override suspend fun clearCredentialsAndSettings() = Unit

        override suspend fun currentToken(): String? = "fcm-token"

        override fun deviceName(): String = "Phone"

        override suspend fun directPush(): Boolean = false

        override suspend fun setDirectPush(direct: Boolean) = Unit

        override fun startDirectPush() = Unit

        override fun stopDirectPush() = Unit

        override fun startConnection() = Unit

        override fun pushPlatform(): SessionPushPlatform = SessionPushPlatform.IOS
    }

    @Test
    fun missingSessionReturnsFalse() =
        runTest {
            val registration = ApplePushRegistration { null }

            assertFalse(registration.register("token", "sandbox"))
        }

    @Test
    fun delegatesCanonicalTokenAndEnvironmentVerbatim() =
        runTest {
            val requests = mutableListOf<List<String?>>()
            val policy =
                SessionPolicy(
                    normalizeUrl = { it },
                    healthz = { ApiResult.Ok(HealthResponse()) },
                    pairDevice = { _, _, _, _ -> ApiResult.Ok(PairDeviceResponse("device", "token", "key")) },
                    putPush = { fcmToken, direct, platform, apnsToken, environment ->
                        requests += listOf(fcmToken, direct.toString(), platform, apnsToken, environment)
                        ApiResult.Ok(OkResponse())
                    },
                    revokeDevice = { ApiResult.Ok(OkResponse()) },
                    platform = RegistrationPlatform(),
                )
            val registration = ApplePushRegistration { policy }

            val registered = registration.register("aabb00ff", "sandbox")
            assertTrue(registered)
            assertEquals(
                listOf(listOf<String?>("fcm-token", "false", "ios", "aabb00ff", "sandbox")),
                requests,
            )
        }
}
