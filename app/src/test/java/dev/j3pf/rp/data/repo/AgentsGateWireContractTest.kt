package dev.j3pf.rp.data.repo

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.AgentsGate
import dev.j3pf.rp.data.ports.ArchivedAgentsResult
import dev.j3pf.rp.data.ports.CloseOutcome
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentArchivedList
import dev.j3pf.rp.protocol.AgentArchivedRequest
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wire-level contract proof for Unit P2 (`AgentsGate`): exercises the real
 * `AgentsRepository` exclusively through a port-typed `AgentsGate` reference
 * over a real WebSocket + in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentsGateWireContractTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var db: RpDatabase
    private lateinit var settingsFile: File
    private lateinit var gate: AgentsGate
    private val requests = LinkedBlockingQueue<Envelope>()

    @Volatile
    private var responder: ((Envelope) -> Envelope?)? = null

    private val agent =
        AgentSummary(
            agentId = "machine:agent-1",
            machineId = "machine",
            sessionName = "Agente P2",
        )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        bus = MessageBus()
        connection = RelayConnection(secrets, bus, testClient(), scope)
        db = inMemoryDb(context)
        settingsFile = File.createTempFile("agents-gate-wire-test", ".preferences_pb")
        val settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }),
            )
        val repository = AgentsRepository(db.agents(), secrets, bus, connection, settings, scope)
        gate = repository
        gate.start()
        db.agents().upsert(listOf(agent.toEntity("r1")))
    }

    @After
    fun tearDown() {
        if (::connection.isInitialized) connection.stop()
        if (::scope.isInitialized) {
            scope.cancel()
            runBlocking { withTimeout(10_000) { scope.coroutineContext[Job]?.join() } }
        }
        if (::db.isInitialized) db.close()
        if (::server.isInitialized) runCatching { server.shutdown() }
        if (::settingsFile.isInitialized) settingsFile.delete()
    }

    private suspend fun connect() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        if (env.t in AGENT_FRAMES) {
                            requests.put(env)
                            responder?.invoke(env)?.let { webSocket.send(it.encode()) }
                        }
                    }
                },
            ),
        )
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
    }

    @Test
    fun notConnectedAfterRealSocketSendsZeroAgentFramesAndPinsExactLiterals() = runBlocking {
        connect()
        connection.stop()

        val closeOutcome = assertIs<CloseOutcome.Failed>(gate.close(agent.agentId))
        assertEquals("Sin conexión con el relay", closeOutcome.message)

        val archivedOutcome = assertIs<ArchivedAgentsResult.Failed>(gate.archived("machine", 99L))
        assertEquals("Sin conexión para cargar los agentes archivados", archivedOutcome.message)

        assertFalse(gate.purge(agent.agentId))
        assertTrue(requests.isEmpty(), "Zero agent frames may leave the device when disconnected")
    }

    @Test
    fun closeAndArchivedAndPurgeWirePayloadsAndFallbackSemanticsThroughPort() = runBlocking {
        connect()
        assertEquals(agent, gate.observe(agent.agentId).first())

        // 1. Blank error message -> exact fallback literal without trailing colon
        responder = { req ->
            Envelope.of(MsgType.ERROR, ErrorBody(code = "internal", message = "   "), re = req.id)
        }
        val blankErr = assertIs<CloseOutcome.Failed>(gate.close(agent.agentId))
        assertEquals("No se pudo cerrar el agente", blankErr.message)
        val firstClose = requests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.AGENT_CLOSE, firstClose.t)
        assertEquals(AgentRef(agent.agentId), firstClose.body<AgentRef>())

        // 2. Non-blank error message -> includes relay reason verbatim
        responder = { req ->
            Envelope.of(MsgType.ERROR, ErrorBody(code = "busy", message = "pane ocupado"), re = req.id)
        }
        val reasonErr = assertIs<CloseOutcome.Failed>(gate.close(agent.agentId))
        assertEquals("No se pudo cerrar el agente: pane ocupado", reasonErr.message)
        requests.poll(5, TimeUnit.SECONDS)

        // 3. AGENT_CLOSE_UNSUPPORTED -> falls back to AGENT_FORGET -> Archived and deletes row
        responder = { req ->
            when (req.t) {
                MsgType.AGENT_CLOSE ->
                    Envelope.of(
                        MsgType.ERROR,
                        ErrorBody(code = ErrorCodes.AGENT_CLOSE_UNSUPPORTED, message = "no herdr"),
                        re = req.id,
                    )
                MsgType.AGENT_FORGET -> Envelope(t = "ack", re = req.id)
                else -> null
            }
        }
        assertEquals(CloseOutcome.Archived, gate.close(agent.agentId))
        val closeFrame = requests.poll(5, TimeUnit.SECONDS)!!
        val forgetFrame = requests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.AGENT_CLOSE, closeFrame.t)
        assertEquals(MsgType.AGENT_FORGET, forgetFrame.t)
        assertEquals(AgentRef(agent.agentId), forgetFrame.body<AgentRef>())
        assertTrue(gate.agents.first().isEmpty())

        // 4. archived(machineId, before) -> sends AgentArchivedRequest and decodes list + cursor
        val archivedItem =
            ArchivedAgentSummary(
                agentId = "machine:old",
                machineId = "machine",
                lastSeenAt = 90L,
                archivedAt = 100L,
            )
        responder = { req ->
            Envelope.of(
                MsgType.AGENT_ARCHIVED_LIST,
                AgentArchivedList(agents = listOf(archivedItem), nextBefore = 50L),
                re = req.id,
            )
        }
        val archivedOk = assertIs<ArchivedAgentsResult.Ok>(gate.archived("machine", 200L))
        val archivedReq = requests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.AGENT_ARCHIVED, archivedReq.t)
        assertEquals(AgentArchivedRequest(machineId = "machine", before = 200L), archivedReq.body<AgentArchivedRequest>())
        assertEquals(listOf(archivedItem), archivedOk.agents)
        assertEquals(50L, archivedOk.nextBefore)

        // 5. purge(agentId) -> sends AGENT_PURGE and returns true on ack
        responder = { req -> Envelope(t = "ack", re = req.id) }
        assertTrue(gate.purge("machine:old"))
        val purgeReq = requests.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MsgType.AGENT_PURGE, purgeReq.t)
        assertEquals(AgentRef("machine:old"), purgeReq.body<AgentRef>())
    }

    private companion object {
        val AGENT_FRAMES =
            setOf(
                MsgType.AGENT_CLOSE,
                MsgType.AGENT_FORGET,
                MsgType.AGENT_ARCHIVED,
                MsgType.AGENT_PURGE,
            )
    }
}
