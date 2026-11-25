package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.R
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.platform.AndroidNetworkMonitor
import dev.j3pf.rp.platform.AndroidPushEnvelopeCrypto
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.AppState
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.util.stableId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectPushPerfTest {
    private lateinit var context: Application
    private lateinit var notificationManager: NotificationManager
    private lateinit var db: RpDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repositoryScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        db = inMemoryDb(context)
        settings = SettingsRepository(context)
        repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        repositoryScope.cancel()
        runBlocking { withTimeout(10_000) { repositoryScope.coroutineContext[Job]?.join() } }
        db.close()
    }

    @Test
    fun directSocketClientDisablesClientSidePingInterval() {
        val base = OkHttpClient.Builder().pingInterval(45, TimeUnit.SECONDS).build()
        val directClient = DirectPushService.createDirectSocketClient(base)
        assertEquals(0, directClient.pingIntervalMillis)
    }

    @Test
    fun encodeAppStateProducesRelayContractFrame() {
        val off = Envelope.parse(DirectPushService.encodeAppState(screenOn = false))
        assertEquals(MsgType.APP_STATE, off.t)
        assertFalse(off.body<AppState>().screenOn)

        val on = Envelope.parse(DirectPushService.encodeAppState(screenOn = true))
        assertEquals(MsgType.APP_STATE, on.t)
        assertTrue(on.body<AppState>().screenOn)
    }

    @Test
    fun showWorkingThrottlesCosmeticUpdatesFor3SecondsAndFlushesStructuralChangesImmediately() =
        runBlocking {
            var nowMs = 50_000L
            val factory =
                NotificationFactory(
                    context = context,
                    channels = NotificationChannels(context),
                    settings = settings,
                    deduper = NotificationDeduper(db.dedup()),
                    nowMs = { nowMs },
                    workingThrottleMs = 3_000L,
                )
            val agent =
                AgentSummary(
                    agentId = "m:perf-1",
                    machineId = "M1",
                    machineName = "PC",
                    sessionName = "RemotePi · Perf",
                    status = AgentStatus.WORKING,
                    activeTask = "Step 1",
                )
            val notifId = stableId("working:${agent.agentId}")

            // First render posts immediately.
            factory.showWorking(agent)
            val first = assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))
            assertEquals("Step 1", first.extras.getString(Notification.EXTRA_TITLE))

            // Rapid cosmetic updates within 3,000 ms are throttled (same Notification instance).
            nowMs += 500L
            factory.showWorking(agent.copy(activeTask = "Step 2"))
            nowMs += 1_500L
            factory.showWorking(agent.copy(activeTask = "Step 3", lastMessagePreview = "preview 3"))
            assertSame(first, shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))

            // Once >= 3,000 ms elapses, cosmetic update flushes.
            nowMs += 1_000L
            factory.showWorking(agent.copy(activeTask = "Step 4"))
            val flushed = assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))
            assertNotSame(first, flushed)
            assertEquals("Step 4", flushed.extras.getString(Notification.EXTRA_TITLE))

            // Status change (WORKING -> COMPACTING) flushes immediately even at +10 ms.
            nowMs += 10L
            factory.showWorking(agent.copy(status = AgentStatus.COMPACTING, activeTask = "Compacting context"))
            val compacted = assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))
            assertNotSame(flushed, compacted)
            assertEquals("Compacting context", compacted.extras.getString(Notification.EXTRA_TITLE))

            // Adding a second working agent flushes immediately even at +10 ms.
            nowMs += 10L
            val secondAgent = agent.copy(agentId = "m:perf-2", sessionName = "Other · Task", activeTask = "Building")
            factory.showWorking(secondAgent)
            val unifiedId = stableId("working:${secondAgent.agentId}")
            val unified = assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, unifiedId))
            assertTrue(unified.extras.getString(Notification.EXTRA_TITLE)!!.contains("2 agentes activos"))
            assertNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))

            // Cancelling second agent immediately re-renders remaining agent.
            nowMs += 10L
            factory.cancelWorking(secondAgent.agentId)
            val restored = assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))
            assertEquals("Compacting context", restored.extras.getString(Notification.EXTRA_TITLE))
            assertNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, unifiedId))

            factory.cancelWorking(agent.agentId)
            assertNull(shadowOf(notificationManager).getNotification(NotificationFactory.WORKING_TAG, notifId))
        }

    @Test
    fun showWorkingDoesNotPruneOrphanSummariesViaActiveNotificationsScan() =
        runBlocking {
            val channels = NotificationChannels(context)
            channels.ensureServiceChannel()
            val factory =
                NotificationFactory(
                    context = context,
                    channels = channels,
                    settings = settings,
                    deduper = NotificationDeduper(db.dedup()),
                    nowMs = { 10_000L },
                )

            // Post an orphan summary directly into NotificationManager.
            val orphanSummaryId = stableId("summary:orphan-agent")
            val orphanSummary =
                NotificationCompat
                    .Builder(context, NotificationChannels.SERVICE_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle("Orphan")
                    .setGroup("rp.agent.orphan-agent")
                    .setGroupSummary(true)
                    .build()
            notificationManager.notify(NotificationFactory.SUMMARY_TAG, orphanSummaryId, orphanSummary)
            assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.SUMMARY_TAG, orphanSummaryId))

            // showWorking must NOT invoke pruneOrphanSummaries() (which scanned nm.activeNotifications).
            factory.showWorking(
                AgentSummary(
                    agentId = "m:no-prune",
                    machineId = "M1",
                    machineName = "PC",
                    sessionName = "Repo",
                    status = AgentStatus.WORKING,
                    activeTask = "Working",
                ),
            )
            assertNotNull(shadowOf(notificationManager).getNotification(NotificationFactory.SUMMARY_TAG, orphanSummaryId))

            // Cancel path still prunes orphan summaries when needed.
            factory.cancelPrompt("01NONEXISTENT")
            assertNull(shadowOf(notificationManager).getNotification(NotificationFactory.SUMMARY_TAG, orphanSummaryId))
        }

    @Test
    fun agentsUpdateOnTextDelegatesWorkingNotificationsSolelyToRoomObserver(): Unit =
        runBlocking {
            val secrets = FakeSecretStore(context, testSession("https://relay.example"))
            val bus = MessageBus()
            val channels = NotificationChannels(context)
            val deduper = NotificationDeduper(db.dedup())
            val factory = NotificationFactory(context, channels, settings, deduper)
            val client = testClient()
            val router = PushRouter(factory, deduper, AndroidPushEnvelopeCrypto(), secrets, settings, db.prompts())
            val connection = RelayConnection(secrets, bus, client, repositoryScope)
            val machines = MachinesRepository(db.machines(), secrets, bus, RelayApi(client, secrets), channels, repositoryScope)
            val agents = AgentsRepository(db.agents(), secrets, bus, connection, settings, repositoryScope)

            val service = Robolectric.buildService(DirectPushService::class.java).get()
            fun inject(name: String, dep: Any) {
                service.javaClass.getDeclaredField(name).apply {
                    isAccessible = true
                    set(service, dep)
                }
            }
            inject("secrets", secrets)
            inject("bus", bus)
            inject("router", router)
            inject("channels", channels)
            inject("machines", machines)
            inject("agents", agents)
            inject("factory", factory)
            inject("baseClient", client)
            inject("networkMonitor", AndroidNetworkMonitor(context))

            val workingAgent =
                AgentSummary(
                    agentId = "m:single-driver",
                    machineId = "M1",
                    machineName = "PC",
                    sessionName = "SingleDriver",
                    status = AgentStatus.WORKING,
                    activeTask = "Testing",
                )
            // Invoke onText WITHOUT starting the Room observer: onText must persist to Room
            // and must NOT directly call factory.showWorking().
            service.javaClass.getDeclaredMethod("onText", String::class.java).apply {
                isAccessible = true
                invoke(service, Envelope.of(MsgType.AGENTS_UPDATE, AgentsUpdate(upsert = listOf(workingAgent))).encode())
            }

            withTimeout(5_000) {
                while (agents.agents.first().none { it.agentId == workingAgent.agentId }) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(factory.hasWorkingAgents())
            assertNull(
                shadowOf(notificationManager).getNotification(
                    NotificationFactory.WORKING_TAG,
                    stableId("working:${workingAgent.agentId}"),
                ),
            )

            // Once the single Room observer starts, it drives the working notification cleanly.
            val observerJob = service.startWorkingAgentsObserver()
            try {
                withTimeout(5_000) {
                    while (!factory.hasWorkingAgents()) {
                        shadowOf(Looper.getMainLooper()).idle()
                        delay(10)
                    }
                }
                assertNotNull(
                    shadowOf(notificationManager).getNotification(
                        NotificationFactory.WORKING_TAG,
                        stableId("working:${workingAgent.agentId}"),
                    ),
                )
            } finally {
                observerJob.cancel()
            }
        }
}
