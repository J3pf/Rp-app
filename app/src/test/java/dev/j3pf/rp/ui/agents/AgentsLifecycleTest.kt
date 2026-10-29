package dev.j3pf.rp.ui.agents

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.ui.theme.RpTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentsLifecycleTest {
    @get:Rule val rule = createComposeRule()

    private var server: MockWebServer? = null
    private var scope: CoroutineScope? = null
    private var connection: RelayConnection? = null
    private var db: dev.j3pf.rp.data.db.RpDatabase? = null
    private var settingsFile: File? = null
    private val requests = LinkedBlockingQueue<Envelope>()

    @After
    fun tearDown() {
        connection?.stop()
        scope?.cancel()
        db?.close()
        // MockWebServer asserts si el socket queda medio abierto al cerrar; la suite ya envuelve shutdown().
        runCatching { server?.shutdown() }
        settingsFile?.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `close confirmation has exact Spanish copy and confirming calls once`() {
        var confirms = 0
        rule.setContent {
            var show by remember { mutableStateOf(true) }
            RpTheme {
                if (show) {
                    AgentCloseConfirmationDialog(
                        onConfirm = {
                            confirms++
                            show = false
                        },
                        onDismiss = { show = false },
                    )
                }
            }
        }

        rule.onNodeWithText("Esto cerrará el pane en Herdr y detendrá el agente").assertIsDisplayed()
        rule.onNodeWithText("Cerrar agente").performClick()
        rule.runOnIdle { assertEquals(1, confirms) }
    }

    @Test
    fun `canceling close confirmation does not confirm`() {
        var confirms = 0
        var cancels = 0
        rule.setContent {
            var show by remember { mutableStateOf(true) }
            RpTheme {
                if (show) {
                    AgentCloseConfirmationDialog(
                        onConfirm = { confirms++ },
                        onDismiss = {
                            cancels++
                            show = false
                        },
                    )
                }
            }
        }

        rule.onNodeWithText("Cancelar").performClick()
        rule.runOnIdle {
            assertEquals(0, confirms)
            assertEquals(1, cancels)
        }
        assertTrue(rule.onAllNodesWithText("Esto cerrará el pane en Herdr y detendrá el agente").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `agents view model confirms close once and exposes a failed result`() {
        runBlocking {
            initializeViewModelHarness()
            // The harness seeds the repository with this same relay session.
            val agent = AgentSummary(agentId = "machine:agent-1", machineId = "machine", sessionName = "Agente de prueba")
            db!!.agents().upsert(listOf(agent.toEntity("r1")))
            val viewModel = lifecycleViewModel

            viewModel.forget(agent.agentId)
            assertEquals(agent.agentId, viewModel.lifecycleState.value.forgetAgentId)
            responder = { request -> Envelope.of(MsgType.ERROR, ErrorBody("internal", "relay failure"), re = request.id) }

            viewModel.confirmForget()
            val failure = withTimeout(5_000) { viewModel.lifecycleState.first { it.message != null } }
            assertEquals("No se pudo cerrar el agente", failure.message)
            assertEquals(MsgType.AGENT_CLOSE, awaitRequest().t)
            assertNotNull(db!!.agents().get("r1", agent.agentId))
        }
    }

    private var responder: ((Envelope) -> Envelope?)? = null

    private suspend fun initializeViewModelHarness() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server!!.url("/").toString()))
        val bus = MessageBus()
        connection = RelayConnection(context, secrets, bus, testClient(), scope!!)
        db = inMemoryDb(context)
        settingsFile = File.createTempFile("agents-lifecycle-test", ".preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope!!, produceFile = { settingsFile!! }))
        val api = RelayApi(testClient(), secrets)
        val channels = NotificationChannels(context)
        val machines = MachinesRepository(db!!.machines(), secrets, bus, api, channels, scope!!)
        val agents = AgentsRepository(db!!.agents(), secrets, bus, connection!!, settings, scope!!)
        val host = HostRepository(connection!!, bus, scope!!)
        server!!.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        if (env.t == MsgType.AGENT_CLOSE || env.t == MsgType.AGENT_ARCHIVED) {
                            requests.put(env)
                            responder?.invoke(env)?.let { webSocket.send(it.encode()) }
                        }
                    }
                },
            ),
        )
        agents.start()
        connection!!.start()
        runBlocking { withTimeout(5_000) { connection!!.state.first { it == ConnState.Connected } } }
        lifecycleViewModel = AgentsViewModel(machines, agents, settings, connection!!, host)
    }

    private lateinit var lifecycleViewModel: AgentsViewModel

    private suspend fun awaitRequest(type: String = MsgType.AGENT_CLOSE): Envelope =
        withTimeout(5_000) {
            while (true) {
                val request = requests.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (request.t == type) return@withTimeout request
            }
            error("No se recibió $type")
        }
}
