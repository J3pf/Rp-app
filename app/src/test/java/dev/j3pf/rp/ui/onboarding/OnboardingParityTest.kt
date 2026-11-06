package dev.j3pf.rp.ui.onboarding

import android.Manifest
import android.app.Application
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SessionRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.push.PushTokenProvider
import dev.j3pf.rp.testClient
import dev.j3pf.rp.ui.theme.RpTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T1a Gate A: JVM characterization of OB-5/OB-6 without pretending to exercise OS permission dialogs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingParityTest {
    @get:Rule val rule = createComposeRule()

    private var scope: CoroutineScope? = null
    private var connection: RelayConnection? = null
    private var db: dev.j3pf.rp.data.db.RpDatabase? = null
    private var settingsFile: File? = null
    private lateinit var settings: SettingsRepository
    private lateinit var pairViewModel: PairViewModel
    private lateinit var context: Application
    private lateinit var mainDispatcher: kotlinx.coroutines.test.TestDispatcher

    @Before
    fun setUp() {
        mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        buildHarness()
    }

    @After
    fun tearDown() {
        connection?.stop()
        scope?.cancel()
        db?.close()
        settingsFile?.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `OB-5 permissions screen exposes notification and battery state and Continue persists completion`() =
        runBlocking {
            shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            val batteryExcluded = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
            var doneCalls = 0
            rule.setContent {
                RpTheme {
                    PermissionsScreen(
                        onDone = { doneCalls++ },
                        vm = pairViewModel,
                    )
                }
            }

            rule.onNodeWithText("Permitir notificaciones").assertIsDisplayed().assertIsEnabled()
            val batteryLabel = if (batteryExcluded) "Excluida de la optimización ✓" else "Excluir de la optimización de batería"
            rule.onNodeWithText(batteryLabel).assertIsDisplayed()
            rule.onNodeWithText("Continuar").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(1, doneCalls) }
            mainDispatcher.scheduler.advanceUntilIdle()
            withTimeout(5_000) { settings.settings.first { it.onboardingDone } }
            assertTrue(settings.current().onboardingDone)
        }

    @Test
    @Config(sdk = [32])
    fun `OB-6 below Android 13 notifications are marked granted without a permission prompt and skip persists completion`() =
        runBlocking {
            shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            var doneCalls = 0
            rule.setContent {
                RpTheme {
                    PermissionsScreen(
                        onDone = { doneCalls++ },
                        vm = pairViewModel,
                    )
                }
            }

            rule.onNodeWithText("Permiso concedido ✓").assertIsDisplayed().assertIsNotEnabled()
            rule.onNodeWithText("Ahora no").assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(1, doneCalls) }
            mainDispatcher.scheduler.advanceUntilIdle()
            withTimeout(5_000) { settings.settings.first { it.onboardingDone } }
            assertTrue(settings.current().onboardingDone)
        }

    private fun buildHarness() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, null)
        val bus = MessageBus()
        val client = testClient()
        val api = RelayApi(client, secrets)
        connection = RelayConnection(context, secrets, bus, client, scope!!)
        db = inMemoryDb(context)
        settingsFile = File.createTempFile("onboarding-parity", ".preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope!!, produceFile = { settingsFile!! }))
        val channels = NotificationChannels(context)
        val machines = MachinesRepository(db!!.machines(), secrets, bus, api, channels, scope!!)
        val agents = AgentsRepository(db!!.agents(), secrets, bus, connection!!, settings, scope!!)
        val prompts =
            PromptsRepository(
                context,
                db!!.prompts(),
                secrets,
                bus,
                connection!!,
                api,
                NotificationFactory(context, channels, settings, NotificationDeduper(db!!.dedup())),
                scope!!,
            )
        val chat = ChatRepository(context, db!!.chat(), db!!.outbox(), connection!!, bus, secrets, prompts, api, scope!!, db!!.agents())
        val sessionRepo =
            SessionRepository(
                context,
                api,
                secrets,
                settings,
                connection!!,
                machines,
                agents,
                chat,
                prompts,
                HostRepository(connection!!, bus, scope!!),
                MeshRepository(connection!!, bus, api, scope!!),
                db!!.dedup(),
                object : PushTokenProvider {
                    override suspend fun currentToken(): String? = null
                },
                channels,
            )
        pairViewModel = PairViewModel(sessionRepo, settings)
    }
}
