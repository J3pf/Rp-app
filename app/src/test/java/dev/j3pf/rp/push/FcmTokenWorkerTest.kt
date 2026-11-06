package dev.j3pf.rp.push

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SessionRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.push.PushTokenProvider
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

/** FCM-only worker characterization; the direct flavor has no FcmTokenWorker class to execute. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FcmTokenWorkerTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var db: RpDatabase
    private lateinit var secrets: SecretStore
    private lateinit var scope: CoroutineScope
    private lateinit var connection: RelayConnection
    private lateinit var settingsFile: File
    private lateinit var repository: SessionRepository
    private lateinit var workerClass: Class<out CoroutineWorker>

    @Before
    fun setUp() = runBlocking {
        val fcmWorker = runCatching { Class.forName("dev.j3pf.rp.push.FcmTokenWorker") }.getOrNull()
        assumeTrue("FcmTokenWorker is only present in the fcm flavor", fcmWorker != null)
        @Suppress("UNCHECKED_CAST")
        workerClass = fcmWorker!!.asSubclass(CoroutineWorker::class.java)

        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        server = MockWebServer().also { it.start() }
        db = inMemoryDb(context)
        secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val bus = MessageBus()
        val client = testClient()
        val api = RelayApi(client, secrets)
        connection = RelayConnection(context, secrets, bus, client, scope)
        settingsFile = File.createTempFile("fcm-token-worker", ".preferences_pb").also { it.delete() }
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }))
        settings.setDirectPush(false)
        val channels = NotificationChannels(context)
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
                    override suspend fun currentToken(): String? = "unused"
                },
                channels,
            )
    }

    @After
    fun tearDown() {
        if (!::context.isInitialized) return
        connection.stop()
        scope.cancel()
        db.close()
        server.shutdown()
        settingsFile.delete()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun worker(token: String): CoroutineWorker {
        @Suppress("UNCHECKED_CAST")
        val concreteWorkerClass = workerClass as Class<CoroutineWorker>
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    if (workerClassName != concreteWorkerClass.name) return null
                    val constructor =
                        concreteWorkerClass.getDeclaredConstructor(
                            Context::class.java,
                            WorkerParameters::class.java,
                            SessionRepository::class.java,
                        )
                    return constructor.newInstance(appContext, workerParameters, repository)
                }
            }
        return TestListenableWorkerBuilder
            .from(context, concreteWorkerClass)
            .setInputData(Data.Builder().putString("token", token).build())
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `successful token registration completes worker`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        assertEquals(ListenableWorker.Result.success(), worker("fcm-unit-token").doWork())

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/devices/me/push", request.path)
        assertEquals("""{"fcmToken":"fcm-unit-token","direct":false}""", request.body.readUtf8())
    }

    @Test
    fun `failed token registration requests WorkManager retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))

        assertEquals(ListenableWorker.Result.retry(), worker("fcm-unit-token").doWork())

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/devices/me/push", request.path)
        assertEquals(1, server.requestCount)
    }
}
