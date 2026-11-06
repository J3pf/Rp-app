package dev.j3pf.rp.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
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
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.DevicesResponse
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MachinesResponse
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairCodeResponse
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.NotificationDeduper
import dev.j3pf.rp.push.NotificationFactory
import dev.j3pf.rp.push.PushTokenProvider
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.ui.theme.RpTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T1a Gate A: SE-5..SE-12 characterization of existing settings behavior; no production edits. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsActionsParityTest {
    @get:Rule val rule = createComposeRule()

    private var server: MockWebServer? = null
    private var scope: CoroutineScope? = null
    private var connection: RelayConnection? = null
    private var db: RpDatabase? = null
    private var settingsFile: File? = null
    private var settingsRepo: SettingsRepository? = null
    private var viewModel: SettingsViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        connection?.stop()
        scope?.cancel()
        db?.close()
        runCatching { server?.shutdown() }
        settingsFile?.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `SE-5 device load failure and revoke success or failure preserve exact messages and local list`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            enqueue("{}", code = 500)
            viewModel!!.loadDevices().join()
            assertEquals("No se pudo cargar la lista de dispositivos", viewModel!!.message.value)
            assertTrue(viewModel!!.devices.value.isEmpty())
            assertEquals("GET", request().method)
            assertEquals("/v1/devices", requestPathLast)

            viewModel!!.consumeMessage()
            enqueue(
                json(
                    DevicesResponse.serializer(),
                    DevicesResponse(
                        devices =
                            listOf(
                                DeviceInfo(deviceId = "01DEVICE", name = "Este teléfono"),
                                DeviceInfo(deviceId = "dev-other", name = "Otro teléfono"),
                                DeviceInfo(deviceId = "dev-revoked", name = "Revocado", revoked = true),
                            ),
                    ),
                ),
            )
            viewModel!!.loadDevices().join()
            assertEquals(listOf("01DEVICE", "dev-other"), viewModel!!.devices.value.map { it.deviceId })
            assertTrue(viewModel!!.devices.value.first().current)
            assertEquals("GET", request().method)

            enqueue("{}")
            viewModel!!.revokeDevice("dev-other").join()
            assertEquals("Dispositivo revocado", viewModel!!.message.value)
            assertEquals(listOf("01DEVICE"), viewModel!!.devices.value.map { it.deviceId })
            assertEquals("DELETE", request().method)
            assertEquals("/v1/devices/dev-other", requestPathLast)

            enqueue("{}", code = 500)
            viewModel!!.revokeDevice("dev-current").join()
            assertEquals("No se pudo revocar", viewModel!!.message.value)
            assertEquals(listOf("01DEVICE"), viewModel!!.devices.value.map { it.deviceId })
            assertEquals("DELETE", request().method)
        }

    @Test
    fun `SE-6 edit machine trims optional fields and reports relay success and failure`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            val updated = MachineSummary(machineId = "m:1", name = "PC nueva", context = "personal", color = "#123456")
            enqueue(json(MachineSummary.serializer(), updated))
            viewModel!!.editMachine("m:1", "  PC nueva  ", "   ", "#123456").join()
            assertEquals("PC actualizada", viewModel!!.message.value)
            val successRequest = request()
            assertEquals("PATCH", successRequest.method)
            assertEquals("/v1/machines/m:1", requestPathLast)
            assertEquals(
                MachinePatch(name = "PC nueva", context = null, color = "#123456"),
                RpJson.decodeFromString(MachinePatch.serializer(), successRequest.body.readUtf8()),
            )

            enqueue("{}", code = 500)
            viewModel!!.editMachine("m:1", "PC fallida", "empresa", "#654321").join()
            assertEquals("No se pudo guardar", viewModel!!.message.value)
            assertEquals("PATCH", request().method)
        }

    @Test
    fun `SE-7 machines screen refreshes on entry and revoke reports both outcomes`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            val initial = MachineSummary(machineId = "m:1", name = "PC inicial")
            enqueue(json(MachinesResponse.serializer(), MachinesResponse(listOf(initial))))
            rule.setContent { RpTheme { MachinesScreen(onBack = {}, vm = viewModel!!) } }
            val refresh = request()
            assertEquals("GET", refresh.method)
            assertEquals("/v1/machines", requestPathLast)
            kotlinx.coroutines.withTimeout(5_000) {
                while (db!!.machines().all("r1").none { it.machineId == "m:1" }) kotlinx.coroutines.delay(10)
            }

            enqueue("{}")
            viewModel!!.revokeMachine("m:1").join()
            assertEquals("PC revocada", viewModel!!.message.value)
            assertTrue(db!!.machines().all("r1").isEmpty())
            assertEquals("DELETE", request().method)

            enqueue("{}", code = 500)
            viewModel!!.revokeMachine("m:1").join()
            assertEquals("No se pudo revocar", viewModel!!.message.value)
            assertEquals("DELETE", request().method)
        }

    @Test
    fun `SE-8 machine code success is stored and a failed renewal clears it with exact message`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            enqueue(json(PairCodeResponse.serializer(), PairCodeResponse(code = "PAIR-123")))
            viewModel!!.createMachineCode().join()
            assertEquals("PAIR-123", viewModel!!.pairCode.value)
            assertEquals("POST", request().method)
            assertEquals("/v1/pair/codes", requestPathLast)

            enqueue("{}", code = 500)
            viewModel!!.createMachineCode().join()
            assertNull(viewModel!!.pairCode.value)
            assertEquals("No se pudo generar el código", viewModel!!.message.value)
            assertEquals("POST", request().method)
        }

    @Test
    fun `SE-9 notification preferences persist locally and are sent to the relay`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            settingsRepo!!.setAgentMuted("agent:1", true)
            viewModel!!.unmuteAgent("agent:1").join()
            assertFalse("agent:1" in settingsRepo!!.current().mutedAgents)

            enqueue("{}")
            viewModel!!.setCriticalAlways(false).join()
            var req = request()
            assertEquals("PUT", req.method)
            assertEquals("/v1/devices/me/prefs", requestPathLast)
            var prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertEquals(false, prefs.categories["criticalAlways"])
            assertFalse(settingsRepo!!.current().criticalAlways)

            enqueue("{}")
            viewModel!!.setMachineMute("m1", true).join()
            req = request()
            prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertTrue(prefs.machines["m1"]?.mute == true)

            enqueue("{}")
            viewModel!!.setMachineCategory("m1", "approvals", false).join()
            req = request()
            prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertEquals(false, prefs.machines["m1"]?.categories?.get("approvals"))

            enqueue("{}")
            viewModel!!.setQuietHours("m1", "22:00", "08:00", "").join()
            req = request()
            prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertEquals(dev.j3pf.rp.protocol.QuietHours("22:00", "08:00"), prefs.machines["m1"]?.quietHours)

            enqueue("{}")
            viewModel!!.setQuietHours("m1", "", "", "").join()
            req = request()
            prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertNull(prefs.machines["m1"]?.quietHours)

            enqueue("{}")
            viewModel!!.setCategory("terminal", true).join()
            req = request()
            prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertEquals(true, prefs.categories["terminal"])
        }

    @Test
    fun `SE-10 test notification reports exact success and failure messages`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            enqueue(json(OkResponse.serializer(), OkResponse()))
            viewModel!!.sendTestNotification().join()
            assertEquals("Notificación de prueba enviada", viewModel!!.message.value)
            assertEquals("POST", request().method)
            assertEquals("/v1/devices/me/push-test", requestPathLast)

            enqueue("{}", code = 500)
            viewModel!!.sendTestNotification().join()
            assertEquals("No se pudo enviar la notificación de prueba", viewModel!!.message.value)
            assertEquals("POST", request().method)
        }

    @Test
    fun `SE-11 relay preference save failure exposes the exact error and submitted preferences`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            enqueue("{}", code = 500)
            viewModel!!.setCategory("approvals", false).join()
            assertEquals("No se pudieron guardar las preferencias en el relay", viewModel!!.message.value)
            val req = request()
            assertEquals("PUT", req.method)
            assertEquals("/v1/devices/me/prefs", requestPathLast)
            val prefs = RpJson.decodeFromString(NotifyPrefs.serializer(), req.body.readUtf8())
            assertEquals(false, prefs.categories["approvals"])
            assertEquals(true, prefs.categories["criticalAlways"])
        }

    @Test
    fun `SE-12 security and chat display preferences persist through SettingsRepository`() =
        kotlinx.coroutines.runBlocking {
            buildHarness()
            viewModel!!.setRequireUnlock(false).join()
            viewModel!!.setAppLock(true).join()
            viewModel!!.setFlagSecure(true).join()
            viewModel!!.setChatFontScale(1.30f).join()
            viewModel!!.setChatMessageSpacing("spacious").join()

            val persisted = settingsRepo!!.current()
            assertFalse(persisted.requireUnlockToApprove)
            assertTrue(persisted.appLock)
            assertTrue(persisted.flagSecure)
            assertEquals(1.30f, persisted.chatFontScale)
            assertEquals("spacious", persisted.chatMessageSpacing)
        }

    @Test
    fun `chat font and message spacing labels and choices retain exact Spanish copy`() {
        assertEquals("Pequeño (85%)", fontScaleLabel(0.85f))
        assertEquals("Normal (100%)", fontScaleLabel(1.00f))
        assertEquals("Grande (115%)", fontScaleLabel(1.15f))
        assertEquals("Extra grande (130%)", fontScaleLabel(1.30f))
        assertEquals("Compacto", messageSpacingLabel("compact"))
        assertEquals("Normal", messageSpacingLabel("normal"))
        assertEquals("Amplio", messageSpacingLabel("spacious"))

        val scales = mutableListOf<Float>()
        val spacings = mutableListOf<String>()
        rule.setContent {
            RpTheme {
                SettingsContent(
                    state = SettingsUiState(settings = dev.j3pf.rp.data.repo.AppSettings(chatFontScale = 0.96f)),
                    onNav = {},
                    onSelectTheme = {},
                    onLogout = {},
                    onSelectFontScale = scales::add,
                    onSelectMessageSpacing = spacings::add,
                )
            }
        }

        rule.onNodeWithText("Tamaño de letra en el chat").performClick()
        listOf("Pequeño (85%)", "Normal (100%)", "Grande (115%)", "Extra grande (130%)")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Extra grande (130%)").performClick()
        assertEquals(listOf(1.30f), scales)

        rule.onNodeWithText("Espaciado de mensajes en el chat").performClick()
        listOf("Compacto (más mensajes en pantalla)", "Normal (equilibrado)", "Amplio (mayor separación)")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Amplio (mayor separación)").performClick()
        assertEquals(listOf("spacious"), spacings)
    }

    private data class Harness(
        val context: Application,
        val api: RelayApi,
        val secrets: FakeSecretStore,
        val channels: NotificationChannels,
        val sessionRepo: SessionRepository,
    )

    private fun buildHarness(): Harness {
        val context = ApplicationProvider.getApplicationContext<Application>()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server!!.url("/").toString()))
        val bus = MessageBus()
        val client = testClient()
        val api = RelayApi(client, secrets)
        connection = RelayConnection(context, secrets, bus, client, scope!!)
        db = inMemoryDb(context)
        settingsFile = File.createTempFile("settings-actions-parity", ".preferences_pb")
        val settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope!!, produceFile = { settingsFile!! }),
            )
        settingsRepo = settings
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
        viewModel = SettingsViewModel(context, secrets, settings, machines, agents, sessionRepo, api, channels, connection!!)
        return Harness(context, api, secrets, channels, sessionRepo)
    }

    private fun enqueue(body: String, code: Int = 200) {
        server!!.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private var requestPathLast: String? = null

    private fun request(): okhttp3.mockwebserver.RecordedRequest {
        val req = server!!.takeRequest(5, TimeUnit.SECONDS) ?: error("El relay no recibió la solicitud esperada")
        requestPathLast = req.path
        return req
    }

    private fun <T> json(serializer: kotlinx.serialization.SerializationStrategy<T>, value: T): String =
        RpJson.encodeToString(serializer, value)
}
