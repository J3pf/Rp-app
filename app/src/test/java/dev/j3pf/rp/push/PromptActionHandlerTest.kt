package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PushPromptBody
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

/** El receiver llama a la API; ok / 409 retiran la notificación, sin red la dejan y encolan WorkManager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptActionHandlerTest {
    private lateinit var ctx: Application
    private lateinit var server: MockWebServer
    private lateinit var nm: NotificationManager
    private lateinit var factory: NotificationFactory
    private lateinit var handler: PromptActionHandler
    private val notifId = NotificationFactory.notificationId("01PROMPT")

    @Before
    fun setUp() {
        runBlocking {
            ctx = ApplicationProvider.getApplicationContext()
            WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
            shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            server = MockWebServer().also { it.start() }
            nm = ctx.getSystemService(NotificationManager::class.java)
            factory = NotificationFactory(ctx, NotificationChannels(ctx), SettingsRepository(ctx))
            val secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
            val db = inMemoryDb(ctx)
            handler = PromptActionHandler(RelayApi(testClient(), secrets), factory, db.prompts(), db.outbox(), secrets)
            factory.showPrompt(
                PushPromptBody(
                    promptId = "01PROMPT",
                    agentId = "m:s",
                    agentLabel = "api",
                    machineId = "M1",
                    machineName = "PC",
                    kind = PromptKind.CONFIRM,
                    title = "¿Permitir?",
                    options = listOf(PromptOption("0", "Permitir"), PromptOption("2", "Denegar")),
                ),
            )
        }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun intent(
        kind: String,
        option: String? = null,
    ) = Intent(PromptActionReceiver.ACTION_ANSWER).apply {
        putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, "01PROMPT")
        putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, notifId)
        putExtra(PromptActionReceiver.EXTRA_AGENT_LABEL, "api")
        putExtra(PromptActionReceiver.EXTRA_ANSWER_KIND, kind)
        option?.let { putExtra(PromptActionReceiver.EXTRA_OPTION_ID, it) }
    }

    private fun current() = shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, notifId)

    @Test
    fun `permitir envia la respuesta y retira la notificacion`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            assertNotNull(current())
            handler.handle(ctx, intent(PromptActionReceiver.ANSWER_OPTION, "0"))
            val req = server.takeRequest()
            assertEquals("/v1/prompts/01PROMPT/answer", req.path)
            assertEquals("""{"answer":{"optionIds":["0"]}}""", req.body.readUtf8())
            assertNull(current())
        }
    }

    @Test
    fun `confirm yes envia confirmed true`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            handler.handle(ctx, intent(PromptActionReceiver.ANSWER_CONFIRM_YES))
            assertEquals("""{"answer":{"confirmed":true}}""", server.takeRequest().body.readUtf8())
        }
    }

    @Test
    fun `409 tambien retira la notificacion`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(409))
            handler.handle(ctx, intent(PromptActionReceiver.ANSWER_CONFIRM_NO))
            assertNull(current())
        }
    }

    @Test
    fun `sin red muestra reintento y encola WorkManager`() {
        runBlocking {
            server.shutdown()
            handler.handle(ctx, intent(PromptActionReceiver.ANSWER_CONFIRM_YES))
            val n = current()
            assertNotNull(n) // la notificación se queda, ahora como "se reintentará"
            assertEquals(ctx.getString(dev.j3pf.rp.R.string.notif_retrying), n.extras.getString(android.app.Notification.EXTRA_TEXT))
            val infos =
                androidx.work.WorkManager
                    .getInstance(ctx)
                    .getWorkInfosForUniqueWork("answer-01PROMPT")
                    .get()
            assertEquals(1, infos.size)
        }
    }

    @Test
    fun `responder agente con 401 conserva el mensaje en outbox y encola reintento`() {
        runBlocking {
            val db = inMemoryDb(ctx)
            val secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
            val localHandler = PromptActionHandler(RelayApi(testClient(), secrets), factory, db.prompts(), db.outbox(), secrets)
            server.enqueue(MockResponse().setResponseCode(401))

            val replyIntent =
                Intent(PromptActionReceiver.ACTION_REPLY_AGENT).apply {
                    putExtra(PromptActionReceiver.EXTRA_AGENT_ID, "m:s")
                    putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, 42)
                }
            val results = android.os.Bundle().apply { putCharSequence(PromptActionReceiver.KEY_TEXT, "hola desde notif") }
            androidx.core.app.RemoteInput.addResultsToIntent(
                arrayOf(
                    androidx.core.app.RemoteInput
                        .Builder(PromptActionReceiver.KEY_TEXT)
                        .build(),
                ),
                replyIntent,
                results,
            )

            localHandler.handle(ctx, replyIntent)
            val pending = db.outbox().all("r1")
            assertEquals(1, pending.size)
        }
    }
}
