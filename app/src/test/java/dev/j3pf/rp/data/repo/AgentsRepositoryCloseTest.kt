package dev.j3pf.rp.data.repo

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentArchivedList
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentsRepositoryCloseTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var repository: AgentsRepository
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private lateinit var settingsFile: File
    private val requests = LinkedBlockingQueue<Envelope>()

    @Volatile private var responder: ((Envelope) -> Envelope?)? = null
    private val agent = AgentSummary(agentId = "machine:agent-1", machineId = "machine", sessionName = "Agente de prueba")

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            server = MockWebServer().also { it.start() }
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
            bus = MessageBus()
            connection = RelayConnection(context, secrets, bus, testClient(), scope)
            db = inMemoryDb(context)
            settingsFile = File.createTempFile("agents-close-test", ".preferences_pb")
            val settings =
                SettingsRepository(
                    PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }),
                )
            repository = AgentsRepository(db.agents(), secrets, bus, connection, settings, scope)
            repository.start()
            acceptSocket()
            connection.start()
            withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
            db.agents().upsert(listOf(agent.toEntity("r1")))
        }

    @After
    fun tearDown() {
        if (::connection.isInitialized) connection.stop()
        if (::scope.isInitialized) scope.cancel()
        if (::db.isInitialized) db.close()
        // MockWebServer asserts if the socket is still half-open; el resto de la suite ya envuelve
        // shutdown() en runCatching por lo mismo.
        if (::server.isInitialized) runCatching { server.shutdown() }
        if (::settingsFile.isInitialized) settingsFile.delete()
    }

    private fun acceptSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = Envelope.parse(text)
                        if (env.t !in setOf(MsgType.AGENT_CLOSE, MsgType.AGENT_FORGET, MsgType.AGENT_ARCHIVED, MsgType.AGENT_PURGE)) return
                        requests.put(env)
                        responder?.invoke(env)?.let { webSocket.send(it.encode()) }
                    }
                },
            ),
        )
    }

    private fun ack(request: Envelope) = Envelope(t = "ack", re = request.id)

    private fun error(
        request: Envelope,
        code: String,
        message: String = "",
    ) = Envelope.of(MsgType.ERROR, ErrorBody(code, message), re = request.id)

    private fun awaitRequest(type: String): Envelope {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val env = requests.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (env.t == type) return env
        }
        error("No se recibió solicitud $type")
    }

    private suspend fun assertAgentExists() {
        assertTrue(
            db
                .agents()
                .observe("r1")
                .first { it.isNotEmpty() }
                .any { it.agent.agentId == agent.agentId },
        )
    }

    @Test
    fun `close accepted removes local agent and returns Closed`() =
        runBlocking {
            responder = { ack(it) }

            val result = repository.close(agent.agentId)
            assertIs<CloseOutcome.Closed>(result)
            assertEquals(MsgType.AGENT_CLOSE, awaitRequest(MsgType.AGENT_CLOSE).t)
            assertNull(db.agents().get("r1", agent.agentId))
        }

    @Test
    fun `unsupported close falls back to forget and returns Archived`() =
        runBlocking {
            responder = { request ->
                when (request.t) {
                    MsgType.AGENT_CLOSE -> error(request, "agent_close_unsupported", "No hay pane")
                    MsgType.AGENT_FORGET -> ack(request)
                    else -> null
                }
            }

            val result = repository.close(agent.agentId)
            assertIs<CloseOutcome.Archived>(result)
            val close = awaitRequest(MsgType.AGENT_CLOSE)
            val forget = awaitRequest(MsgType.AGENT_FORGET)
            assertEquals(AgentRef(agent.agentId), close.body<AgentRef>())
            assertEquals(AgentRef(agent.agentId), forget.body<AgentRef>())
            assertNull(db.agents().get("r1", agent.agentId))
        }

    @Test
    fun `a pane that is already gone counts as closed, not as an error`() =
        runBlocking {
            // El plugin rechaza cerrar un pane que ya no existe; el agente se ha ido igual, así que no
            // puede aparecer un banner de error por algo que salió bien.
            responder = { error(it, ErrorCodes.NOT_FOUND, "pane_not_found") }

            assertEquals(CloseOutcome.Closed, repository.close(agent.agentId))
            assertFalse(
                db
                    .agents()
                    .observe("r1")
                    .first { it.isEmpty() }
                    .isNotEmpty(),
            )
        }

    @Test
    fun `other close error fails with Spanish message and leaves local agent`() =
        runBlocking {
            responder = { error(it, "internal", "relay failure") }

            val result = repository.close(agent.agentId)
            val failure = assertIs<CloseOutcome.Failed>(result)
            assertTrue(failure.message.any { it in 'á'..'ú' } || failure.message.contains("No se pudo"))
            assertEquals(MsgType.AGENT_CLOSE, awaitRequest(MsgType.AGENT_CLOSE).t)
            assertAgentExists()
        }

    @Test
    fun `an agents update remove deletes the local agent`() =
        runBlocking {
            // Así llega la retirada cuando un pane de Herdr se cierra: el relay quita el agente y la app lo
            // refleja sin acción del usuario. `agent.closed` es un frame del puente al relay y no se recibe aquí.
            bus.publish(Envelope.of(MsgType.AGENTS_UPDATE, AgentsUpdate(remove = listOf(agent.agentId))))

            assertTrue(
                db
                    .agents()
                    .observe("r1")
                    .first { it.isEmpty() }
                    .isEmpty(),
            )
        }

    @Test
    fun `purge sends the exact agent id`() =
        runBlocking {
            responder = { ack(it) }

            assertTrue(repository.purge(agent.agentId))
            val request = awaitRequest(MsgType.AGENT_PURGE)
            assertEquals(AgentRef(agent.agentId), request.body<AgentRef>())
        }

    @Test
    fun `archived relay failure returns an explicit Spanish error`() =
        runBlocking {
            responder = { error(it, "unauthorized", "No autorizado") }

            val result = assertIs<ArchivedAgentsResult.Failed>(repository.archived())
            assertTrue(result.message.contains("archivados", ignoreCase = true))
            assertTrue(result.message.contains("cargar", ignoreCase = true))
            assertEquals(MsgType.AGENT_ARCHIVED, awaitRequest(MsgType.AGENT_ARCHIVED).t)
        }

    @Test
    fun `archived list decodes rows and pagination cursor`() =
        runBlocking {
            val archived =
                ArchivedAgentSummary(
                    agentId = "machine:archived",
                    machineId = "machine",
                    machineName = "PC principal",
                    sessionName = "Sesión archivada",
                    archivedAt = 100L,
                    lastSeenAt = 90L,
                    lastMessagePreview = "Último mensaje",
                    messageCount = 12,
                )
            responder = { request ->
                Envelope.of(MsgType.AGENT_ARCHIVED_LIST, AgentArchivedList(listOf(archived), nextBefore = 89L), re = request.id)
            }

            val result = repository.archived(machineId = "machine", before = 100L)
            val ok = assertIs<ArchivedAgentsResult.Ok>(result)
            assertEquals(listOf(archived), ok.agents)
            assertEquals(89L, ok.nextBefore)
            val request = awaitRequest(MsgType.AGENT_ARCHIVED)
            assertEquals("machine", request.body<dev.j3pf.rp.protocol.AgentArchivedRequest>().machineId)
            assertEquals(100L, request.body<dev.j3pf.rp.protocol.AgentArchivedRequest>().before)
        }
}
