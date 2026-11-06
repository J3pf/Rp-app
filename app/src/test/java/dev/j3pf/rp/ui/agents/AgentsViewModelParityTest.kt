package dev.j3pf.rp.ui.agents

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.CloseOutcome
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentArchivedList
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MachinesUpdate
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T1a (Gate A): pins AG-2 (group ordering across machines), AG-3 (context filter)
 * and AG-11 (refresh) + AR-1/AR-4/AR-6 (archived load, pagination, purge) on
 * UNCHANGED Android ViewModels, using the same real WebSocket harness as
 * AgentsLifecycleTest. No production code is touched by these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentsViewModelParityTest {
    private var server: MockWebServer? = null
    private var scope: CoroutineScope? = null
    private var connection: RelayConnection? = null
    private var db: RpDatabase? = null
    private var settingsFile: File? = null
    private val requests = LinkedBlockingQueue<Envelope>()

    @Volatile private var responder: ((Envelope) -> Envelope?)? = null
    private lateinit var mainDispatcher: kotlinx.coroutines.test.TestDispatcher

    @Before
    fun setUp() {
        mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
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

    private val machineWork =
        MachineSummary(machineId = "m-work", name = "PC Empresa", context = "empresa", color = "#C62828", online = true, hostOnline = true)
    private val machinePersonal =
        MachineSummary(machineId = "m-personal", name = "PC Personal", context = "personal", color = "#2E7D32", online = true)

    private val aWorkingWithPrompts =
        AgentSummary(agentId = "m-work:a1", machineId = "m-work", machineName = "PC Empresa", machineContext = "empresa",
            sessionName = "A", status = "working", online = true, openPromptCount = 2, lastSeenAt = 1000L)
    private val aIdleRecent =
        AgentSummary(agentId = "m-work:a2", machineId = "m-work", machineName = "PC Empresa", machineContext = "empresa",
            sessionName = "B", status = "idle", online = true, lastSeenAt = 9000L)
    private val bWorkingNoPrompts =
        AgentSummary(agentId = "m-personal:b1", machineId = "m-personal", machineName = "PC Personal", machineContext = "personal",
            sessionName = "C", status = "working", online = true, lastSeenAt = 5000L)
    private val cIdleOnly =
        AgentSummary(agentId = "m-personal:b2", machineId = "m-personal", machineName = "PC Personal", machineContext = "personal",
            sessionName = "D", status = "idle", online = false, lastSeenAt = 2000L)

    private lateinit var viewModel: AgentsViewModel
    private lateinit var machinesRepo: MachinesRepository

    private fun seedMachines(vararg machines: MachineSummary) =
        runBlocking { db!!.machines().upsert(machines.toList().map { it.toEntity("r1") }) }

    private fun seedAgents(vararg agents: AgentSummary) =
        runBlocking { db!!.agents().upsert(agents.toList().map { it.toEntity("r1") }) }

    private suspend fun buildHarness() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server!!.url("/").toString()))
        val bus = MessageBus()
        connection = RelayConnection(context, secrets, bus, testClient(), scope!!)
        db = inMemoryDb(context)
        settingsFile = File.createTempFile("agents-vm-parity-test", ".preferences_pb")
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
                        if (env.t == MsgType.AGENT_ARCHIVED || env.t == MsgType.AGENT_PURGE || env.t == MsgType.AGENT_CLOSE || env.t == MsgType.AGENT_FORGET) {
                            requests.put(env)
                            responder?.invoke(env)?.let { webSocket.send(it.encode()) }
                        }
                    }
                },
            ),
        )
        machinesRepo = machines
        machines.start()
        agents.start()
        connection!!.start()
        withTimeout(30_000) { connection!!.state.first { it == ConnState.Connected } }
        // The same welcome the relay sends on connect; seeds the two machines through the real flow.
        bus.publish(Envelope.of(MsgType.APP_WELCOME, AppWelcome(machines = listOf(machineWork, machinePersonal))))
        // Wait for the BroadcastStateBeforeBuildingTheViewModel (Dao state is busy in a different dispatcher).
        withTimeout(30_000) { machinesRepo.machinesState.first { it.size == 2 } }
        viewModel = AgentsViewModel(machines, agents, settings, connection!!, host)
    }

    private suspend fun awaitState(predicate: (AgentsUiState) -> Boolean): AgentsUiState =
        // 10s: the combine chain runs over real DataStore + Room + websocket transports on IO threads;
        // the predicate body has no chicken, so patience here is not a weak assert.
        withTimeout(30_000) {
            viewModel.state.first { predicate(it) }
        }

    @Test
    fun `AG-3 context filter shows everything for ALL and only matching machines and agents for one context`() =
        runBlocking {
            buildHarness()
            seedAgents(aWorkingWithPrompts, aIdleRecent, bWorkingNoPrompts, cIdleOnly)

            val all = awaitState { it.groups.size == 2 }
            assertEquals(2, all.machines.size)

            viewModel.select("empresa")
            val filtered = awaitState { it.groups.size == 1 && it.selected == "empresa" }
            assertEquals(listOf("m-work"), filtered.groups.map { it.machine.machineId })
            // No agents from m-personal survive the contexto filter.
            assertTrue(filtered.groups.single().agents.all { it.machineContext == "empresa" })

            viewModel.select(AppSettings.ALL)
            val restored = awaitState { it.groups.size == 2 && it.selected == AppSettings.ALL }
            assertEquals(2, restored.groups.size)
        }

    @Test
    fun `AG-2 groups are ordered across machines by prompts, then working, then recent lastSeen`() =
        runBlocking {
            buildHarness()
            seedAgents(aWorkingWithPrompts, aIdleRecent, bWorkingNoPrompts, cIdleOnly)

            val state = awaitState { it.groups.size == 2 }
            val groupIds = state.groups.map { it.machine.machineId }
            // m-work has an agent with open prompts (2_000_000_000_000 anchor), so it outranks m-personal
            // (working-only, no prompts: 1_000_000_000_000 anchor).
            assertEquals(listOf("m-work", "m-personal"), groupIds)
            // Inside the ranked group, sortAgentsWithinGroup keeps prompts first (already pinned in AG-1).
            assertEquals(
                listOf("m-work:a1", "m-work:a2"),
                state.groups[0].agents.map { it.agentId },
            )
        }

    @Test
    fun `AG-11 refresh marks refreshing and clears it again`() =
        runBlocking {
            buildHarness()
            seedMachines(machinePersonal)
            seedAgents(aIdleRecent)
            awaitState { it.groups.size == 1 }

            assertTrue(!viewModel.state.value.refreshing)
            // refresh() (AgentsViewModel.kt:96-102) toggles refreshing, asks the connection to retry and
            // clears the flag 600 virtual ms later. On a socket that is already Connected the retryNow()
            // is a deliberate no-op (ReconnectingSocketPolicy.kt:63-66), so no HTTP request follows; the
            // observable end state is refreshing=false again with no banner message.
            viewModel.refresh()
            // Transient TRUE state is pinned before the drain: setting isRefreshing happens inline on
            // the unconfined main dispatcher, the 600 ms clear waits for the virtual scheduler, so
            // nothing else may advance it in between and this awaits sees `refreshing == true`.
            withTimeout(30_000) { viewModel.state.first { it.refreshing } }
            // Drain the virtual-time delay(600) of the unconfined main dispatcher.
            mainDispatcher.scheduler.advanceUntilIdle()
            withTimeout(30_000) { viewModel.state.first { !it.refreshing } }
            assertNull(viewModel.lifecycleState.value.message)
        }

    @Test
    fun `AR-1 initial archive load maps the ok relay reply into the lifecycle state`() =
        runBlocking {
            buildHarness()
            responder = { request ->
                Envelope.of(
                    MsgType.AGENT_ARCHIVED,
                    AgentArchivedList(
                        agents = listOf(
                            ArchivedAgentSummary(
                                agentId = "m-personal:arc1",
                                machineId = "m-personal",
                                machineName = "PC Personal",
                                sessionName = "Archivado 1",
                                archivedAt = 100L,
                                lastSeenAt = 90L,
                                messageCount = 3,
                            ),
                        ),
                        nextBefore = 42L,
                    ),
                    re = request.id,
                )
            }

            viewModel.loadArchived()
            val state = withTimeout(30_000) { viewModel.lifecycleState.first { it.archivedAgents.isNotEmpty() } }
            assertTrue { state.archivedError == null }
            assertEquals(1, state.archivedAgents.size)
            assertEquals(42L, state.archivedNextBefore)
            assertTrue(!state.archivedLoading)
            assertEquals(MsgType.AGENT_ARCHIVED, awaitRequest(MsgType.AGENT_ARCHIVED).t)
        }

    @Test
    fun `AR-4 load more appends the next page using the previous cursor`() =
        runBlocking {
            buildHarness()
            var servedFirstPage = false
            responder = { request ->
                val before =
                    runCatching { request.body<dev.j3pf.rp.protocol.AgentArchivedRequest>().before }.getOrNull()
                if (before == null) {
                    servedFirstPage = true
                    Envelope.of(
                        MsgType.AGENT_ARCHIVED,
                        AgentArchivedList(
                            agents =
                                listOf(
                                    ArchivedAgentSummary(
                                        agentId = "arc-page1",
                                        machineId = "m1",
                                        sessionName = "P1",
                                        archivedAt = 10L,
                                        lastSeenAt = 9L,
                                    ),
                                ),
                            nextBefore = 77L,
                        ),
                        re = request.id,
                    )
                } else {
                    Envelope.of(
                        MsgType.AGENT_ARCHIVED,
                        AgentArchivedList(
                            agents =
                                listOf(
                                    ArchivedAgentSummary(
                                        agentId = "arc-page2",
                                        machineId = "m1",
                                        sessionName = "P2",
                                        archivedAt = 5L,
                                        lastSeenAt = 4L,
                                    ),
                                ),
                            nextBefore = null,
                        ),
                        re = request.id,
                    )
                }
            }

            viewModel.loadArchived()
            withTimeout(30_000) { viewModel.lifecycleState.first { it.archivedNextBefore != null } }
            viewModel.loadMoreArchived()
            val state = withTimeout(30_000) { viewModel.lifecycleState.first { it.archivedAgents.size == 2 } }
            assertEquals(listOf("arc-page1", "arc-page2"), state.archivedAgents.map { it.agentId })
            assertNull(state.archivedNextBefore)
        }

    private fun okArchivedResponder(): (Envelope) -> Envelope? = { request ->
        Envelope.of(
            MsgType.AGENT_ARCHIVED,
            AgentArchivedList(
                agents =
                    listOf(
                        ArchivedAgentSummary(
                            agentId = "m-personal:arc1",
                            machineId = "m-personal",
                            sessionName = "Arc",
                            archivedAt = 1L,
                            lastSeenAt = 1L,
                        ),
                    ),
            ),
            re = request.id,
        )
    }

    @Test
    fun `AR-6 purge success removes the row and failure surfaces the exact snackbar string`() =
        runBlocking {
            buildHarness()
            responder = okArchivedResponder()
            viewModel.loadArchived()
            withTimeout(30_000) { viewModel.lifecycleState.first { it.archivedAgents.isNotEmpty() } }

            responder = { request -> Envelope(t = "ack", re = request.id) }
            viewModel.purgeArchived("m-personal:arc1")
            val removed = withTimeout(30_000) { viewModel.lifecycleState.first { it.archivedAgents.isEmpty() } }
            assertNull(removed.message)
            assertEquals(MsgType.AGENT_PURGE, awaitRequest(MsgType.AGENT_PURGE).t)

            // Failure path: the relay rejects the purge and the VM keeps the exact failure copy.
            responder = okArchivedResponder()
            viewModel.loadArchived()
            withTimeout(30_000) { viewModel.lifecycleState.first { it.archivedAgents.isNotEmpty() } }
            responder = { request -> Envelope.of(MsgType.ERROR, ErrorBody("internal", "boom"), re = request.id) }
            viewModel.purgeArchived("m-personal:arc1")
            val failure = withTimeout(30_000) { viewModel.lifecycleState.first { it.message != null } }
            assertEquals("No se pudo eliminar definitivamente el historial", failure.message)
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

