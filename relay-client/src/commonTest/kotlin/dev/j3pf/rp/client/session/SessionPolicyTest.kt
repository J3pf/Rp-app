package dev.j3pf.rp.client.session

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.repo.PairResult
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairDeviceResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionPolicyTest {
    private class Fixture(
        initialSession: Boolean = true,
    ) : SessionPlatform {
        val old = Session("old", "https://old", "Old", "old-device", "old-token", "old-key")
        var session: Session? = old.takeIf { initialSession }
        val events = mutableListOf<String>()
        var work = 3
        var channels = 5
        var cacheRelayId: String? = old.relayId.takeIf { initialSession }
        var direct = true
        var fcm: String? = "fcm"
        var health: ApiResult<HealthResponse> = ApiResult.Ok(HealthResponse())
        var paired: ApiResult<PairDeviceResponse> = ApiResult.Ok(PairDeviceResponse("device", "token", "key"))
        var push: ApiResult<OkResponse> = ApiResult.Ok(OkResponse())
        var healthError: Throwable? = null
        var pairError: Throwable? = null
        var pushError: Throwable? = null
        var revokeError: Throwable? = null
        val policy =
            SessionPolicy(
                normalizeUrl = { raw ->
                    events += "normalize:$raw"
                    if (raw == "bad") null else raw.trim().trimEnd('/')
                },
                healthz = { url ->
                    events += "health:$url"
                    healthError?.let { throw it }
                    health
                },
                pairDevice = { url, code, name, token ->
                    events += "pair:$url:$code:$name:$token"
                    pairError?.let { throw it }
                    paired
                },
                putPush = { token, enabled ->
                    events += "push:${session?.relayId}:$token:$enabled"
                    pushError?.let { throw it }
                    push
                },
                revokeDevice = { id ->
                    events += "revoke:$id"
                    revokeError?.let { throw it }
                    ApiResult.Http(503, "ignored")
                },
                platform = this,
            )

        override fun currentSession(): Session? = session

        override fun newRelayId(): String {
            events += "newId"
            return "new"
        }

        override fun saveSession(session: Session) {
            events += "save:${session.relayId}"
            this.session = session
        }

        override suspend fun clearLocalSession() {
            events += "stopConnection"
            events += "cancelWork"
            work = 0
            events += "clearCache:${session?.relayId}"
            cacheRelayId = null
            events += "removeChannels"
            channels = 0
        }

        override suspend fun clearCredentialsAndSettings() {
            events += "clearCredentials"
            session = null
            events += "wipeSettings"
        }

        override suspend fun currentToken(): String? {
            events += "token"
            return fcm
        }

        override fun deviceName(): String = "Phone"

        override suspend fun directPush(): Boolean {
            events += "direct:$direct"
            return direct
        }

        override suspend fun setDirectPush(direct: Boolean) {
            events += "setDirect:$direct"
            this.direct = direct
        }

        override fun startDirectPush() {
            events += "startDirect"
        }

        override fun stopDirectPush() {
            events += "stopDirect"
        }

        override fun startConnection() {
            events += "startConnection"
        }

        fun assertUntouched() {
            assertEquals(old, session)
            assertEquals(3, work)
            assertEquals(5, channels)
            assertEquals("old", cacheRelayId)
            assertFalse(events.any { it.startsWith("stop") || it == "newId" || it.startsWith("save") || it == "cancelWork" })
        }
    }

    private suspend fun rejected(
        expected: String,
        rawUrl: String = "https://new",
        rawCode: String = "ABCDEF",
        configure: Fixture.() -> Unit = {},
    ): Fixture {
        val f = Fixture().apply(configure)
        assertEquals(PairResult.Failure(expected), f.policy.pair(rawUrl, rawCode))
        f.assertUntouched()
        println("T3_POLICY reject:$expected;events=${f.events};state=old;work=${f.work};channels=${f.channels}")
        return f
    }

    @Test
    fun badUrlDoesNotReadTokenOrCallHealth() =
        runTest {
            val f = rejected("URL del relay no válida (debe ser https://…)", rawUrl = "bad")
            assertEquals(listOf("normalize:bad"), f.events)
        }

    @Test
    fun shortNormalizedCodeDoesNotCallHealth() =
        runTest {
            val f = rejected("El código es demasiado corto", rawCode = " ab-c d ")
            assertEquals(listOf("normalize:https://new"), f.events)
        }

    @Test
    fun healthNotReadyDoesNotFetchToken() =
        runTest {
            val f = rejected("El relay no está listo") { health = ApiResult.Ok(HealthResponse(ok = false)) }
            assertEquals(listOf("normalize:https://new", "health:https://new"), f.events)
        }

    @Test
    fun healthHttpDoesNotFetchToken() =
        runTest {
            val f = rejected("El relay respondió HTTP 503") { health = ApiResult.Http(503, "body") }
            assertFalse("token" in f.events)
        }

    @Test
    fun healthNetworkDoesNotFetchToken() =
        runTest {
            val f = rejected("No se pudo conectar con el relay: offline") { health = ApiResult.Network(IllegalStateException("offline")) }
            assertFalse("token" in f.events)
        }

    @Test
    fun pair400MapsToIncorrectOrExpiredCode() =
        runTest { rejected("Código incorrecto o caducado") { paired = ApiResult.Http(400, "body") } }

    @Test
    fun pair404MapsToIncorrectOrExpiredCode() =
        runTest { rejected("Código incorrecto o caducado") { paired = ApiResult.Http(404, "body") } }

    @Test
    fun pair429MapsToTooManyAttempts() =
        runTest { rejected("Demasiados intentos. Espera un minuto") { paired = ApiResult.Http(429, "body") } }

    @Test
    fun otherPairHttpIncludesStatus() =
        runTest { rejected("El relay rechazó el emparejamiento (HTTP 500)") { paired = ApiResult.Http(500, "body") } }

    @Test
    fun pairNetworkIncludesMessage() = runTest { rejected("Error de red: null") { paired = ApiResult.Network(IllegalStateException()) } }

    @Test
    fun initialPairNormalizesCodeUsesUrlCallbackAndSkipsCleanup() =
        runTest {
            val f = Fixture(initialSession = false).apply { direct = false }
            assertEquals(PairResult.Success, f.policy.pair(" https://new/// ", " ab-cd ef "))
            assertEquals(
                listOf(
                    "normalize: https://new/// ",
                    "health:https://new",
                    "token",
                    "pair:https://new:ABCDEF:Phone:fcm",
                    "newId",
                    "save:new",
                    "direct:false",
                    "push:new:fcm:false",
                    "startConnection",
                ),
                f.events,
            )
            assertEquals(Session("new", "https://new", "new", "device", "token", "key"), f.session)
            println("T3_POLICY initialPair:${f.events};session=${f.session}")
        }

    @Test
    fun normalizationDoesNotAddAnAlphabetLengthOrInternalWhitespaceRestriction() =
        runTest {
            val f = Fixture(false)
            assertEquals(PairResult.Success, f.policy.pair("https://new", " ab\tcd-ef-1234 "))
            assertTrue("pair:https://new:AB\tCDEF1234:Phone:fcm" in f.events)
        }

    @Test
    fun replacementClearsOldCacheBeforeGeneratingAndSavingNewCredentials() =
        runTest {
            val f =
                Fixture().apply {
                    paired = ApiResult.Ok(PairDeviceResponse("device", "token", "key", "Named relay"))
                    push = ApiResult.Http(500, "ignored")
                }
            assertEquals(PairResult.Success, f.policy.pair("https://new", "ABCDEF"))
            assertEquals(
                listOf(
                    "normalize:https://new",
                    "health:https://new",
                    "token",
                    "pair:https://new:ABCDEF:Phone:fcm",
                    "stopConnection",
                    "cancelWork",
                    "clearCache:old",
                    "removeChannels",
                    "newId",
                    "save:new",
                    "direct:true",
                    "push:new:fcm:true",
                    "startDirect",
                    "startConnection",
                ),
                f.events,
            )
            assertEquals("Named relay", assertNotNull(f.session).relayName)
            assertEquals(0, f.work)
            assertEquals(0, f.channels)
            println("T3_POLICY replacement:${f.events};session=${f.session}")
        }

    @Test
    fun httpFallbackNameKeepsHttpPrefixAndNullFcmAndNetworkPushAreAccepted() =
        runTest {
            val f =
                Fixture(false).apply {
                    fcm = null
                    push = ApiResult.Network(IllegalStateException("offline"))
                }
            assertEquals(PairResult.Success, f.policy.pair("http://localhost", "ABCDEF"))
            assertEquals("http://localhost", assertNotNull(f.session).relayName)
            assertTrue("pair:http://localhost:ABCDEF:Phone:null" in f.events)
            assertEquals(listOf("startDirect", "startConnection"), f.events.takeLast(2))
        }

    @Test
    fun healthAndPairCancellationPropagateWithoutChangingOldSession() =
        runTest {
            for (atHealth in listOf(true, false)) {
                val f =
                    Fixture().apply {
                        if (atHealth) healthError = CancellationException("cancel") else pairError = CancellationException("cancel")
                    }
                assertFailsWith<CancellationException> { f.policy.pair("https://new", "ABCDEF") }
                f.assertUntouched()
            }
        }

    @Test
    fun pushCancellationAfterSavePropagatesWithoutStartingConnections() =
        runTest {
            val f = Fixture(false).apply { pushError = CancellationException("cancel") }
            assertFailsWith<CancellationException> { f.policy.pair("https://new", "ABCDEF") }
            assertEquals("new", assertNotNull(f.session).relayId)
            assertFalse("startConnection" in f.events)
            assertFalse("startDirect" in f.events)
        }

    @Test
    fun applyPushPersistsPreferenceBeforeTokenAndIgnoresHttpFailure() =
        runTest {
            val f = Fixture().apply { push = ApiResult.Http(503, "ignored") }
            f.policy.applyPushChannels(false)
            f.policy.applyPushChannels(true)
            assertEquals(
                listOf(
                    "setDirect:false",
                    "token",
                    "push:old:fcm:false",
                    "stopDirect",
                    "setDirect:true",
                    "token",
                    "push:old:fcm:true",
                    "startDirect",
                ),
                f.events,
            )
        }

    @Test
    fun registerTokenWithoutSessionIsSuccessfulNoOp() =
        runTest {
            val f = Fixture(false)
            assertTrue(f.policy.registerFcmToken("new-token"))
            assertTrue(f.events.isEmpty())
        }

    @Test
    fun registerTokenUsesArgumentAndCurrentPreferenceAndReportsFailures() =
        runTest {
            val f = Fixture().apply { direct = false }
            assertTrue(f.policy.registerFcmToken(null))
            f.push = ApiResult.Http(401, "denied")
            assertFalse(f.policy.registerFcmToken("changed"))
            f.push = ApiResult.Network(IllegalStateException())
            assertFalse(f.policy.registerFcmToken("changed"))
            assertEquals(
                listOf(
                    "direct:false",
                    "push:old:null:false",
                    "direct:false",
                    "push:old:changed:false",
                    "direct:false",
                    "push:old:changed:false",
                ),
                f.events,
            )
        }

    @Test
    fun logoutRevocationIsBestEffortThenStopsClearsAndWipesInOriginalOrder() =
        runTest {
            for (error in listOf(null, IllegalStateException("offline"), CancellationException("cancel"))) {
                val f = Fixture().apply { revokeError = error }
                f.policy.logout()
                assertEquals(
                    listOf(
                        "revoke:old-device",
                        "stopDirect",
                        "stopConnection",
                        "cancelWork",
                        "clearCache:old",
                        "removeChannels",
                        "clearCredentials",
                        "wipeSettings",
                    ),
                    f.events,
                )
                assertNull(f.session)
                println("T3_POLICY logout:${f.events};session=${f.session}")
            }
        }

    @Test
    fun logoutWithoutSessionStillCleansNativeStateButDoesNotRevoke() =
        runTest {
            val f = Fixture(false)
            f.policy.logout()
            assertEquals(
                listOf(
                    "stopDirect",
                    "stopConnection",
                    "cancelWork",
                    "clearCache:null",
                    "removeChannels",
                    "clearCredentials",
                    "wipeSettings",
                ),
                f.events,
            )
        }

    @Test
    fun sessionWsBaseRetainsExistingConversionBehavior() {
        val s = Fixture().old
        assertEquals("wss://relay/path", s.copy(relayUrl = "https://relay/path///").wsBase)
        assertEquals("ws://localhost", s.copy(relayUrl = "http://localhost/").wsBase)
        assertEquals("HTTPS://relay", s.copy(relayUrl = "HTTPS://relay/").wsBase)
    }
}
