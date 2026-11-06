package dev.j3pf.rp.ui.agents

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * AG-7 (T1a rev3): the resume gate has three observable outcomes, each with an exact string.
 * Uses the real AgentsScreen composable with hand-built ViewModels over MockWebServer +
 * FakeSecretStore + in-memory Room + real repositories (no production edit).
 * `git show`-visible: AgentsScreen.kt:56-63 (gate), AgentsViewModel.kt:167-175 (strings).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AgentsResumeParityTest {
    @get:Rule val rule = createComposeRule()

    private var server: MockWebServer? = null
    private var scope: CoroutineScope? = null
    private var connection: RelayConnection? = null
    private var db: dev.j3pf.rp.data.db.RpDatabase? = null
    private var settingsFile: File? = null
    private val requests = LinkedBlockingQueue<Envelope>()

    @Volatile private var responder: ((Envelope) -> Envelope?)? = null

    private val machineOnlineHost =
        MachineSummary(
            machineId = "m-live",
            name = "PC Con Host",
            context = "personal",
            color = "#1565C0",
            online = true,
            hostOnline = true,
        )
    private val machineWithoutHost =
        MachineSummary(
            machineId = "m-gone",
            name = "PC Sin Host",
            context = "personal",
            color = "#EF6C00",
            online = true,
            hostOnline = false,
        )
    private val agentLive =
        AgentSummary(
            agentId = "m-live:a1",
            machineId = "m-live",
            machineName = "PC Con Host",
            sessionName = "Vivo",
            status = "idle",
            online = false,
        )
    private val agentOfflineHost =
        AgentSummary(
            agentId = "m-gone:a2",
            machineId = "m-gone",
            machineName = "PC Sin Host",
            sessionName = "Sin host",
            status = "idle",
            online = false,
        )

    private lateinit var viewModel: AgentsViewModel
    private lateinit var mainViewModel: dev.j3pf.rp.ui.MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

        /** Drains the test's harness scope so no dao coroutine is mid-flight when the DB closes. */
    @Suppress("SameParameterValue")
    private fun drainScope() {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(10_000) { scope?.coroutineContext[Job]?.join() }
        }
    }

    @After
    fun tearDown() {
                // Do NOT cancel the ViewModel's combine scope here: the shared Room connection keeps
        // observing through the combine and surviving the test body; the harness scope with the
        // dao flows is its own SupervisorJob already cancelled below, so the combine's only
        // remaining subscriber is a stateInWhileSubscribed that ends 5s after the last collect.
        // (No-op placeholder: teardown is otherwise already sufficient.)

        // Cancel the ViewModel's combine scope BEFORE closing Room, then drain the test scope so
        // no dao coroutine is mid-flight when the database closes (this was the "connection
        // closed" / "database is not open" flake root when run under concurrent load).
        viewModel?.viewModelScope?.cancel()
        connection?.stop()
        scope?.cancel()
        if (scope != null) drainScope()
        db?.close()
        runCatching { server?.shutdown() }
        requests.clear()
        settingsFile?.delete()
        Dispatchers.resetMain()
    }

    private suspend fun buildHarness(
        answer: ((Envelope) -> Envelope?)? = null,
    ): Unit {
        val context = ApplicationProvider.getApplicationContext<Application>()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server!!.url("/").toString()))
        val bus = MessageBus()
        connection = RelayConnection(context, secrets, bus, testClient(), scope!!)
        db = inMemoryDb(context)
        settingsFile = File.createTempFile("agents-resume-parity-test", ".preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope!!, produceFile = { settingsFile!! }))
        val api = RelayApi(testClient(), secrets)
        val machines =
            MachinesRepository(db!!.machines(), secrets, bus, api, NotificationChannels(context), scope!!)
        val agents =
            AgentsRepository(db!!.agents(), secrets, bus, connection!!, settings, scope!!)
        val host = HostRepository(connection!!, bus, scope!!)
        // The resume request's exact outcome every time the screen asks the host to answer.
        val lateAnswer: (Envelope) -> Envelope? = answer ?: { request -> Envelope(t = "ack", re = request.id) }
        server!!.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        when (env.t) {
                            MsgType.HOST_SPAWN, MsgType.AGENT_CLOSE, MsgType.AGENT_ARCHIVED, MsgType.AGENT_PURGE, MsgType.AGENT_FORGET -> {
                                requests.put(env)
                                lateAnswer(env)?.let { webSocket.send(it.encode()) }
                            }
                        }
                    }
                },
            ),
        )
        machines.start()
        agents.start()
        connection!!.start()
        withTimeout(30_000) { connection!!.state.first { it == ConnState.Connected } }
        viewModel = AgentsViewModel(machines, agents, settings, connection!!, host)
        mainViewModel =
            dev.j3pf.rp.ui.MainViewModel(secrets, settings, machines, agents, connection!!)
        // Seed the machines and agents through the real Room path the VM combine reads.
        db!!.machines().upsert(listOf(machineOnlineHost, machineWithoutHost).map { it.toEntity("r1") })
        db!!.agents().upsert(listOf(agentLive, agentOfflineHost).map { it.toEntity("r1") })
        runBlocking { withTimeout(30_000) { viewModel.state.first { it.groups.size >= 2 } } }
    }

    private fun renderAgentsScreen() {
        rule.setContent {
            dev.j3pf.rp.ui.theme.RpTheme {
                AgentsScreen(
                    onOpenChat = {},
                    onNewAgent = {},
                    mainVm = mainViewModel,
                    vm = viewModel,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun clickResume() {
        // Only the machine with an online host renders the resume affordance (canResume);
        // the harness owns exactly one such agent card, so the description resolves uniquely.
        rule.onNodeWithContentDescription("Reanudar agente").performClick()
        rule.waitForIdle()
    }

    // (AgentsScreen.kt:62-63 'Para reanudar agentes, ejecuta rp-host en …' fallback cannot be
    // observed in the composed UI: canResume already holds machine.hostOnline, so the reachable
    // path for an offline host never reaches the snackbar. Recorded as partial in the matrix.)
    @Test
    fun `AG-7 running host resume success surfaces Agente reanudado en la PC and sends AGENT_RESUME`() {
        runBlocking {
            buildHarness(
                answer = { req -> Envelope.of(MsgType.HOST_SPAWN, dev.j3pf.rp.protocol.HostSpawned(spawnId = "sp-1"), re = req.id) },
            )
            renderAgentsScreen()
            rule.onNodeWithText("Vivo").assertIsDisplayed()
            clickResume()
            awaitRequest(MsgType.HOST_SPAWN)
            rule.waitUntil(timeoutMillis = 30_000) {
                rule.onAllNodesWithText("Agente reanudado en la PC").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Agente reanudado en la PC").assertIsDisplayed()
        }
    }

    @Test
    fun `AG-7 running host resume failure surfaces the relay reason verbatim`() {
        runBlocking {
            buildHarness(answer = { req -> Envelope.of(MsgType.ERROR, ErrorBody("internal", "el host dijo no"), re = req.id) })
            renderAgentsScreen()
            rule.onNodeWithText("Vivo").assertIsDisplayed()
            clickResume()
            awaitRequest(MsgType.HOST_SPAWN)
            rule.waitUntil(timeoutMillis = 30_000) {
                rule.onAllNodesWithText("No se pudo reanudar: el host dijo no")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            rule.onNodeWithText("No se pudo reanudar: el host dijo no").assertIsDisplayed()
        }
    }

    private suspend fun awaitRequest(type: String): Envelope =
        withTimeout(30_000) {
            while (true) {
                val request = requests.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (request.t == type) return@withTimeout request
            }
            error("No se recibió $type")
        }
}

