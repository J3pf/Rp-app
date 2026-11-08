package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.platform.AndroidPushEnvelopeCrypto
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushNotify
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.platform.AndroidNetworkMonitor
import dev.j3pf.rp.util.stableId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Characterizes DirectPushService's private frame router without invoking Hilt-dependent onCreate().
 * Production is unchanged (T1; S5/S11/S15). Empty real repositories backed by an empty in-memory DB
 * supply the unused repository fields. The push.notify case omits pushId, so it asserts routing only;
 * the optional push.ack send requires a live socket and is deliberately not claimed here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectPushServiceRoutingTest {
    private lateinit var context: Application
    private lateinit var notificationManager: NotificationManager
    private lateinit var db: RpDatabase
    private lateinit var secrets: FakeSecretStore
    private lateinit var bus: MessageBus
    private lateinit var factory: NotificationFactory
    private lateinit var service: DirectPushService
    private lateinit var repositoryScope: CoroutineScope

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()

        db = inMemoryDb(context)
        secrets = FakeSecretStore(context, testSession("https://relay.example"))
        bus = MessageBus()
        repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(context)
        val channels = NotificationChannels(context)
        val deduper = NotificationDeduper(db.dedup())
        factory = NotificationFactory(context, channels, settings, deduper)
        val client = testClient()
        val router = PushRouter(factory, deduper, AndroidPushEnvelopeCrypto(), secrets, settings, db.prompts())
        val connection = RelayConnection(secrets, bus, client, repositoryScope)
        val machines = MachinesRepository(db.machines(), secrets, bus, RelayApi(client, secrets), channels, repositoryScope)
        val agents = AgentsRepository(db.agents(), secrets, bus, connection, settings, repositoryScope)

        // `get()` only obtains the instance; do not call onCreate(), which is the Hilt composition root.
        service = Robolectric.buildService(DirectPushService::class.java).get()
        inject("secrets", secrets)
        inject("bus", bus)
        inject("router", router)
        inject("channels", channels)
        inject("machines", machines)
        inject("agents", agents)
        inject("factory", factory)
        inject("baseClient", client)
        inject("networkMonitor", AndroidNetworkMonitor(context))
    }

    @After
    fun tearDown() {
        if (::repositoryScope.isInitialized) {
            repositoryScope.cancel()
            runBlocking { withTimeout(10_000) { repositoryScope.coroutineContext[Job]?.join() } }
        }
        if (::db.isInitialized) db.close()
    }

    private fun inject(name: String, dependency: Any) {
        service.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(service, dependency)
        }
    }

    private fun onText(envelope: Envelope) {
        service.javaClass.getDeclaredMethod("onText", String::class.java).apply {
            isAccessible = true
            invoke(service, envelope.encode())
        }
    }

    private suspend fun awaitNotification(tag: String, id: Int): Notification = withTimeout(5_000) {
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(notificationManager).getNotification(tag, id)?.let { return@withTimeout it }
            delay(10)
        }
        error("unreachable")
    }

    @Test
    fun `push notify routes prompt body into the tray`() = runBlocking {
        val body =
            PushPromptBody(
                promptId = "01SERVICEPROMPT",
                agentId = "machine:service-agent",
                agentLabel = "api",
                machineId = "M1",
                machineName = "PC",
                kind = dev.j3pf.rp.protocol.PromptKind.CONFIRM,
                category = dev.j3pf.rp.protocol.PromptCategory.PERMISSION,
                title = "¿Permitir?",
            )
        val bodyJson = RpJson.encodeToJsonElement(PushPromptBody.serializer(), body) as JsonObject
        onText(Envelope.of(MsgType.PUSH_NOTIFY, PushNotify(k = PushKind.PROMPT, body = bodyJson)))

        val posted = awaitNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId(body.promptId))
        assertEquals("¿Permitir?", posted.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(shadowOf(notificationManager).allNotifications.isNotEmpty())
    }

    @Test
    fun `prompt opened is published to the shared bus`() = runBlocking {
        val expected = Prompt(promptId = "01OPENED", agentId = "machine:agent")
        val published = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { bus.messages.first { it.t == MsgType.PROMPT_OPENED } }
        }
        onText(Envelope.of(MsgType.PROMPT_OPENED, expected))

        val actual = published.await()
        assertEquals(expected.promptId, actual.body<Prompt>().promptId)
    }

    @Test
    fun `malformed push notify body is ignored without posting a notification`() = runBlocking {
        onText(Envelope.of(MsgType.PUSH_NOTIFY, JsonObject(emptyMap())))
        shadowOf(Looper.getMainLooper()).idle()
        delay(50)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `prompt resolved is published to the shared bus`() = runBlocking {
        val expected = PromptResolved("01RESOLVED", "remote")
        val published = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { bus.messages.first { it.t == MsgType.PROMPT_RESOLVED } }
        }
        onText(Envelope.of(MsgType.PROMPT_RESOLVED, expected))

        assertEquals(expected, published.await().body<PromptResolved>())
    }

    @Test
    fun `agents update with working status shows a working notification`() = runBlocking {
        val agent =
            AgentSummary(
                agentId = "machine:working-agent",
                machineId = "M1",
                machineName = "PC",
                sessionName = "Prueba",
                status = AgentStatus.WORKING,
                activeTask = "Ejecutando pruebas",
            )
        onText(Envelope.of(MsgType.AGENTS_UPDATE, AgentsUpdate(upsert = listOf(agent))))

        val working = awaitNotification(NotificationFactory.WORKING_TAG, stableId("working:${agent.agentId}"))
        assertEquals("Ejecutando pruebas", working.extras.getString(Notification.EXTRA_TEXT))
        assertTrue((working.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }
}
