package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Characterizes AnswerWorker retry, persistence, and notification effects without changing production. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnswerWorkerTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var db: RpDatabase
    private lateinit var secrets: SecretStore
    private lateinit var channels: NotificationChannels
    private lateinit var factory: NotificationFactory
    private lateinit var notificationManager: NotificationManager

    private val promptId = "01ANSWER"
    private val agentId = "machine:answer-agent"
    private val answer = Answer.confirm(true)

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        server = MockWebServer().also { it.start() }
        db = inMemoryDb(context)
        secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        channels = NotificationChannels(context)
        notificationManager = context.getSystemService(NotificationManager::class.java)
        factory = NotificationFactory(context, channels, SettingsRepository(context), NotificationDeduper(db.dedup()))

        db.prompts().upsert(
            listOf(
                Prompt(promptId = promptId, agentId = agentId, kind = PromptKind.CONFIRM)
                    .toEntity("r1", System.currentTimeMillis()),
            ),
        )
        factory.showPrompt(
            PushPromptBody(
                promptId = promptId,
                agentId = agentId,
                agentLabel = "answer-agent",
                machineId = "M1",
                machineName = "PC",
                kind = PromptKind.CONFIRM,
                title = "¿Permitir?",
                options = listOf(PromptOption("yes", "Sí")),
            ),
        )
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (::server.isInitialized) server.shutdown()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun inputData(): Data =
        Data
            .Builder()
            .putString(AnswerWorker.KEY_PROMPT_ID, promptId)
            .putString(AnswerWorker.KEY_ANSWER, RpJson.encodeToString(Answer.serializer(), answer))
            .build()

    private fun worker(
        data: Data = inputData(),
        attempt: Int = 0,
    ): AnswerWorker {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? =
                    if (workerClassName == AnswerWorker::class.java.name) {
                        AnswerWorker(
                            appContext,
                            workerParameters,
                            RelayApi(testClient(), secrets),
                            db.prompts(),
                            secrets,
                            this@AnswerWorkerTest.factory,
                            channels,
                        )
                    } else {
                        null
                    }
            }
        return TestListenableWorkerBuilder
            .from(context, AnswerWorker::class.java)
            .setInputData(data)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(factory)
            .build()
    }

    private fun promptNotification() =
        shadowOf(notificationManager).getNotification(
            NotificationFactory.PROMPT_TAG,
            NotificationFactory.notificationId(promptId),
        )

    @Test
    fun `200 resolves the remote prompt and removes its notification`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        val row = db.prompts().get("r1", promptId)
        assertEquals("resolved", row?.state)
        assertEquals("remote", row?.resolvedBy)
        assertNull(promptNotification())
        assertEquals("/v1/prompts/$promptId/answer", server.takeRequest().path)
    }

    @Test
    fun `500 retries before attempt limit and fails at the limit without resolving prompt`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        assertEquals(ListenableWorker.Result.retry(), worker(attempt = 0).doWork())
        assertEquals(ListenableWorker.Result.failure(), worker(attempt = AnswerWorker.MAX_ATTEMPTS).doWork())

        assertEquals("open", db.prompts().get("r1", promptId)?.state)
        assertEquals(null, db.prompts().get("r1", promptId)?.resolvedBy)
        assertEquals(2, server.requestCount)
        assertNotNull(promptNotification())
        Unit
    }

    @Test
    fun `403 fails without resolving prompt or removing notification`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))

        assertEquals(ListenableWorker.Result.failure(), worker().doWork())

        assertEquals("open", db.prompts().get("r1", promptId)?.state)
        assertEquals(null, db.prompts().get("r1", promptId)?.resolvedBy)
        assertNotNull(promptNotification())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `enqueue answer captures connected network constraint`() = runBlocking {
        PromptsRepository.enqueueAnswer(context, promptId, answer)

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("answer-$promptId").get()
        assertEquals(1, workInfos.size)
        assertEquals(NetworkType.CONNECTED, workInfos.single().constraints.requiredNetworkType)
    }

    @Test
    fun `missing input fails without a request or prompt side effects`() = runBlocking {
        assertEquals(ListenableWorker.Result.failure(), worker(Data.EMPTY).doWork())

        assertEquals("open", db.prompts().get("r1", promptId)?.state)
        assertEquals(null, db.prompts().get("r1", promptId)?.resolvedBy)
        assertNotNull(promptNotification())
        assertEquals(0, server.requestCount)
    }
}
