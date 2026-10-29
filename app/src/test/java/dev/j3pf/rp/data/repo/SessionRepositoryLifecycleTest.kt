package dev.j3pf.rp.data.repo

import android.app.Application
import android.app.NotificationManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.MachineEntity
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.push.PushTokenProvider
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRepositoryLifecycleTest {
    class WaitingWorker(
        context: android.content.Context,
        params: WorkerParameters,
    ) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var db: RpDatabase
    private lateinit var secrets: FakeSecretStore
    private lateinit var connection: RelayConnection
    private lateinit var settings: SettingsRepository
    private lateinit var repository: SessionRepository
    private lateinit var work: WorkManager
    private lateinit var settingsFile: File
    private var tokenReads = 0
    private var socket: WebSocket? = null

    @Before
    fun setUp(): Unit =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
            work = WorkManager.getInstance(context)
            server = MockWebServer().also { it.start() }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            db = inMemoryDb(context)
            secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
            val bus = MessageBus()
            val api = RelayApi(testClient(), secrets)
            connection = RelayConnection(context, secrets, bus, testClient(), scope)
            settingsFile = File.createTempFile("session-policy", ".preferences_pb").also { it.delete() }
            settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }))
            settings.setDirectPush(false)
            val channels = NotificationChannels(context)
            channels.ensureMachine("machine", "Old machine")
            val machines = MachinesRepository(db.machines(), secrets, bus, api, channels, scope)
            val agents = AgentsRepository(db.agents(), secrets, bus, connection, settings, scope)
            val prompts =
                PromptsRepository(
                    context,
                    db.prompts(),
                    secrets,
                    bus,
                    connection,
                    api,
                    NotificationFactory(context, channels, settings, NotificationDeduper(db.dedup())),
                    scope,
                )
            val chat = ChatRepository(context, db.chat(), db.outbox(), connection, bus, secrets, prompts, api, scope, db.agents())
            repository =
                SessionRepository(
                    context,
                    api,
                    secrets,
                    settings,
                    connection,
                    machines,
                    agents,
                    chat,
                    prompts,
                    HostRepository(connection, bus, scope),
                    MeshRepository(connection, bus, api, scope),
                    db.dedup(),
                    object : PushTokenProvider {
                        override suspend fun currentToken(): String {
                            tokenReads++
                            return "fcm-test"
                        }
                    },
                    channels,
                )
            db.machines().upsert(listOf(MachineEntity("r1", "machine", "Old machine", "personal", "blue", null, true, 1, 0, true)))
            work
                .enqueue(
                    OneTimeWorkRequestBuilder<WaitingWorker>().setInitialDelay(1, TimeUnit.DAYS).addTag("preserve-session").build(),
                ).result
                .get(5, TimeUnit.SECONDS)
        }

    @After
    fun tearDown() {
        connection.stop()
        socket?.close(1000, "test complete")
        scope.cancel()
        db.close()
        server.shutdown()
        settingsFile.delete()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun response(
        code: Int = 200,
        body: String = "{}",
    ) = MockResponse().setResponseCode(code).setBody(body)

    private suspend fun connectOldSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: okhttp3.Response,
                    ) {
                        socket = webSocket
                    }
                },
            ),
        )
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
        assertEquals("/ws/app?mode=foreground", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    private suspend fun assertOldState() {
        assertEquals("r1", secrets.current()?.relayId)
        assertEquals(1, db.machines().all("r1").size)
        assertEquals(1, context.getSystemService(NotificationManager::class.java).notificationChannelGroups.size)
        val jobs = work.getWorkInfosByTag("preserve-session").get(5, TimeUnit.SECONDS)
        assertEquals(1, jobs.size)
        assertFalse(jobs.single().state.isFinished)
        assertEquals(ConnState.Connected, connection.state.value)
    }

    @Test
    fun invalidInputsPreserveOldStorageWorkChannelsAndSocket() =
        runBlocking {
            connectOldSocket()
            assertEquals(
                PairResult.Failure("URL del relay no válida (debe ser https://…)"),
                repository.pair("http://evil.example", "ABCDEF"),
            )
            assertEquals(PairResult.Failure("El código es demasiado corto"), repository.pair(server.url("/").toString(), " a-b "))
            assertEquals(0, tokenReads)
            assertOldState()
            println("T3_NATIVE invalidInputs:oldSession,cache,work,channels,socket-preserved;token=0")
        }

    @Test
    fun healthFailuresPreserveOldStateAndDoNotFetchToken() =
        runBlocking {
            connectOldSocket()
            for ((reply, expected) in listOf(
                response(body = """{"ok":false}""") to "El relay no está listo",
                response(503) to "El relay respondió HTTP 503",
                response(body = "broken") to "No se pudo conectar con el relay: Respuesta inválida:",
            )) {
                server.enqueue(reply)
                val failure = assertIs<PairResult.Failure>(repository.pair(server.url("/").toString(), "ABCDEF"))
                if (expected.endsWith(
                        "Respuesta inválida:",
                    )
                ) {
                    assertTrue(failure.message.startsWith(expected))
                } else {
                    assertEquals(expected, failure.message)
                }
                assertEquals("/healthz", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                assertEquals(0, tokenReads)
                assertOldState()
            }
            println("T3_NATIVE healthFailures:notReady,http,network;oldState-preserved;token=0")
        }

    @Test
    fun pairFailuresUseNormalizedCodeAndNativeNameWithoutClearingOldState() =
        runBlocking {
            connectOldSocket()
            for ((code, message) in listOf(
                400 to "Código incorrecto o caducado",
                404 to "Código incorrecto o caducado",
                429 to "Demasiados intentos. Espera un minuto",
                500 to "El relay rechazó el emparejamiento (HTTP 500)",
                200 to "Error de red: Respuesta inválida:",
            )) {
                server.enqueue(response(body = """{"ok":true}"""))
                server.enqueue(response(code, "broken"))
                val failure = assertIs<PairResult.Failure>(repository.pair(server.url("/").toString(), " ab-cd ef "))
                if (code == 200) assertTrue(failure.message.startsWith(message)) else assertEquals(message, failure.message)
                assertEquals("/healthz", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/v1/pair/device", request.path)
                assertNull(request.getHeader("Authorization"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("\"code\":\"ABCDEF\""))
                assertTrue(body.contains("\"name\":\"${android.os.Build.MODEL ?: "Android"}\""))
                assertTrue(body.contains("\"fcmToken\":\"fcm-test\""))
                assertOldState()
            }
            assertEquals(5, tokenReads)
            println("T3_NATIVE pairFailures:400,404,429,500,network;normalizedCode,nativeName,fcm;oldState-preserved;token=5")
        }

    @Test
    fun registrationWithoutSessionDoesNotSendOrReadToken() =
        runBlocking {
            secrets.set(null)
            assertTrue(repository.registerFcmToken("changed"))
            assertEquals(0, server.requestCount)
            assertEquals(0, tokenReads)
            println("T3_NATIVE registerWithoutSession:true;requests=0;token=0")
        }

    @Test
    fun registrationReportsHttpFailureAndUsesCurrentCredentialsAndPreference() =
        runBlocking {
            server.enqueue(response(503))
            assertFalse(repository.registerFcmToken("changed"))
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("PUT", request.method)
            assertEquals("/v1/devices/me/push", request.path)
            assertEquals("Bearer tok-123", request.getHeader("Authorization"))
            assertEquals("""{"fcmToken":"changed","direct":false}""", request.body.readUtf8())
            assertEquals(0, tokenReads)
            println("T3_NATIVE registerToken:false;PUT push;currentAuth,direct=false,argumentToken;tokenReads=0")
        }

    @Test
    fun logoutRevokesBestEffortAndClearsOldRelayDataChannelsCredentialsAndSettings() =
        runBlocking {
            server.enqueue(response(503))
            settings.setSelectedContext("old-context")
            repository.logout()
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("DELETE", request.method)
            assertEquals("/v1/devices/01DEVICE", request.path)
            assertEquals("Bearer tok-123", request.getHeader("Authorization"))
            assertNull(secrets.current())
            assertTrue(db.machines().all("r1").isEmpty())
            assertTrue(context.getSystemService(NotificationManager::class.java).notificationChannelGroups.isEmpty())
            assertEquals(AppSettings(), settings.current())
            assertEquals(ConnState.Disconnected, connection.state.value)
            println("T3_NATIVE logout:DELETE oldDevice,oldAuth;503-ignored;cache,channels,credentials,settings-cleared;socket-stopped")
        }

    @Test
    fun logoutWithoutSessionDoesNotRevokeAndStillRemovesChannelsAndSettings() =
        runBlocking {
            secrets.set(null)
            settings.setSelectedContext("old-context")
            repository.logout()
            assertEquals(0, server.requestCount)
            assertTrue(context.getSystemService(NotificationManager::class.java).notificationChannelGroups.isEmpty())
            assertEquals(AppSettings(), settings.current())
            println("T3_NATIVE logoutWithoutSession:requests=0;channels,settings-cleared")
        }

    @Test
    fun applyPushIgnoresHttpFailureAfterPersistingPreference() =
        runBlocking {
            server.enqueue(response(503))
            repository.applyPushChannels(false)
            assertFalse(settings.current().directPush)
            assertEquals(1, tokenReads)
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("""{"fcmToken":"fcm-test","direct":false}""", request.body.readUtf8())
            println("T3_NATIVE applyPush:direct=false;tokenReads=1;503-ignored")
        }
}
