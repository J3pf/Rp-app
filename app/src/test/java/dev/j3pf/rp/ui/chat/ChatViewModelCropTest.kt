package dev.j3pf.rp.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.platform.BiometricAuthorizerAndroid
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.util.BiometricGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatViewModelCropTest {
    private lateinit var app: Application
    private lateinit var server: MockWebServer
    private lateinit var connection: RelayConnection
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var vm: ChatViewModel

    private val uploadAttempts = AtomicInteger(0)
    private var uploadBlockLatch: CountDownLatch? = null
    private var uploadStartedLatch: CountDownLatch? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        AttachmentsRepository.clearCache(app)
        db = inMemoryDb(app)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server =
            MockWebServer().apply {
                dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            if (request.path?.contains("/blobs") == true) {
                                val n = uploadAttempts.incrementAndGet()
                                uploadStartedLatch?.countDown()
                                uploadBlockLatch?.await(5, TimeUnit.SECONDS)
                                return MockResponse()
                                    .setResponseCode(200)
                                    .setBody("""{"blobId":"blob-$n","size":123}""")
                            }
                            return MockResponse().setResponseCode(200).setBody("{}")
                        }
                    }
                start()
            }
        val session = testSession(server.url("/").toString().trimEnd('/'))
        val secrets = FakeSecretStore(app, session)
        val client = testClient()
        val api = RelayApi(client, secrets)
        val bus = MessageBus()
        connection = RelayConnection(secrets, bus, client, scope)
        val settingsRepo = SettingsRepository(app)
        val factory = NotificationFactory(app, NotificationChannels(app), settingsRepo, NotificationDeduper(db.dedup()))
        val prompts = PromptsRepository(app, db.prompts(), secrets, bus, connection, api, factory, scope)
        val chats =
            ChatRepository(
                context = app,
                chatDao = db.chat(),
                outboxDao = db.outbox(),
                connection = connection,
                bus = bus,
                secrets = secrets,
                prompts = prompts,
                api = api,
                scope = scope,
                agentDao = db.agents(),
            )
        val agentsRepo = AgentsRepository(db.agents(), secrets, bus, connection, settingsRepo, scope)
        val attachmentsRepo = AttachmentsRepository(app, api)
        val hostRepo = HostRepository(connection, bus, scope)

        vm =
            ChatViewModel(
                savedStateHandle = SavedStateHandle(mapOf("agentId" to "m1:s1")),
                chats = chats,
                agentsRepo = agentsRepo,
                connection = connection,
                attachmentsRepo = attachmentsRepo,
                settingsRepo = settingsRepo,
                hostRepo = hostRepo,
                gate = BiometricAuthorizerAndroid(BiometricGate(), app),
            )
    }

    @After
    fun tearDown() {
        uploadBlockLatch?.countDown()
        if (::vm.isInitialized) {
            vm.viewModelScope.cancel()
            runBlocking { withTimeout(5_000) { vm.viewModelScope.coroutineContext[Job]?.join() } }
        }
        runCatching { connection.stop() }
        if (::scope.isInitialized) {
            scope.cancel()
            runBlocking { withTimeout(5_000) { scope.coroutineContext[Job]?.join() } }
        }
        if (::db.isInitialized) runCatching { db.close() }
        runCatching { server.shutdown() }
        AttachmentsRepository.clearCache(app)
        Dispatchers.resetMain()
    }

    private fun writePng(
        name: String,
        w: Int = 200,
        h: Int = 200,
    ): File {
        val file = File(app.cacheDir, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }
        return file
    }

    @Test
    fun cropDuringInFlightUploadThenRemoveDoesNotLeakOrUploadDeletedAttachment() =
        runBlocking {
            val src = writePng("race-crop.png")
            uploadStartedLatch = CountDownLatch(1)
            uploadBlockLatch = CountDownLatch(1)

            vm.addAttachment(Uri.fromFile(src))

            // Wait until job1 is suspended inside upload
            assertTrue(uploadStartedLatch!!.await(5, TimeUnit.SECONDS))
            val stagedId =
                withTimeout(5_000) {
                    vm.state
                        .first { s -> s.attachments.singleOrNull()?.state == UploadState.UPLOADING }
                        .attachments
                        .single()
                        .id
                }

            // Trigger crop while job1 is uploading, then immediately remove the attachment
            vm.cropAttachment(stagedId, NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.8f))
            vm.removeAttachment(stagedId)

            // Release blocked upload and allow all coroutines to settle
            uploadBlockLatch?.countDown()
            delay(250)

            assertTrue(vm.state.value.attachments.isEmpty())
            // Only job1's cancelled upload attempt happened; deleted crop job2 never uploaded
            assertEquals(1, uploadAttempts.get())
            val remainingStaged = File(app.cacheDir, "attachments").listFiles().orEmpty()
            assertEquals(0, remainingStaged.size)
        }

    @Test
    fun cropAttachmentReplacesCleanlyAndPreservesDisplayName() =
        runBlocking {
            val src = writePng("clean-name.png", w = 400, h = 200)
            vm.addAttachment(Uri.fromFile(src))

            val firstDone =
                withTimeout(5_000) {
                    vm.state
                        .first { s -> s.attachments.singleOrNull()?.state == UploadState.DONE }
                        .attachments
                        .single()
                }
            assertEquals("clean-name.jpg", firstDone.name)

            vm.cropAttachment(firstDone.id, NormalizedCropRect(0.25f, 0.25f, 0.75f, 0.75f))

            val secondDone =
                withTimeout(5_000) {
                    vm.state
                        .first { s ->
                            val a = s.attachments.singleOrNull()
                            a != null && a.id != firstDone.id && a.state == UploadState.DONE
                        }.attachments
                        .single()
                }
            assertEquals("clean-name.jpg", secondDone.name)
            assertEquals(2, uploadAttempts.get())
            val remainingStaged = File(app.cacheDir, "attachments").listFiles().orEmpty()
            assertEquals(1, remainingStaged.size)
        }
}
