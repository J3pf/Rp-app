package dev.j3pf.rp.ui.mesh

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshCommEvent
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshGroup
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Response
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var bus: MessageBus
    private lateinit var connection: RelayConnection
    private lateinit var repository: MeshRepository
    private lateinit var machines: MachinesRepository
    private lateinit var settings: SettingsRepository
    private lateinit var viewModel: MeshViewModel
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private val sockets = LinkedBlockingQueue<WebSocket>()
    private val outgoing = LinkedBlockingQueue<String>()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() =
        runBlocking {
            Dispatchers.setMain(testDispatcher)
            server = MockWebServer().also { it.start() }
            scope = CoroutineScope(SupervisorJob() + testDispatcher)
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
            bus = MessageBus()
            connection = RelayConnection(context, secrets, bus, testClient(), scope)
            val api = RelayApi(testClient(), secrets)
            repository = MeshRepository(connection, bus, api, secrets, scope)
            db = inMemoryDb(context)
            machines = MachinesRepository(db.machines(), secrets, bus, api, NotificationChannels(context), scope)
            val testDataStore =
                PreferenceDataStoreFactory.create(scope = scope, produceFile = {
                    java.io.File.createTempFile("mesh_test", ".preferences_pb")
                })
            settings = SettingsRepository(testDataStore)
            viewModel = MeshViewModel(repository, machines, settings, MeshLayoutEngine())
        }

    @After
    fun tearDown() {
        if (::connection.isInitialized) connection.stop()
        sockets.forEach { runCatching { it.close(1000, "test complete") } }
        if (::scope.isInitialized) scope.cancel()
        if (::db.isInitialized) db.close()
        if (::server.isInitialized) server.shutdown()
        testDispatcher.cancel()
        Dispatchers.resetMain()
    }

    private fun acceptSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        sockets.add(webSocket)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        outgoing.add(text)
                    }
                },
            ),
        )
    }

    private suspend fun connect() {
        connection.start()
        withTimeout(5_000) { connection.state.first { it == ConnState.Connected } }
    }

    private suspend fun awaitOutgoing(type: String): Envelope =
        withTimeout(5_000) {
            while (true) {
                val text = outgoing.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val envelope = Envelope.parse(text)
                if (envelope.t == type) return@withTimeout envelope
            }
            error("unreachable")
        }

    private fun node(
        id: String,
        machine: String,
        group: String?,
        role: String,
        lastCommAt: Long,
    ) = MeshNode(
        agentId = id,
        machineId = machine,
        machineName = "Machine $machine",
        machineColor = if (machine == "m1") "#112233" else "#445566",
        name = "Agent $id",
        status = "working",
        online = true,
        role = role,
        groupId = group,
        lastCommAt = lastCommAt,
    )

    private fun sampleSnapshot() =
        MeshSnapshot(
            generatedAt = 100,
            groups =
                listOf(
                    MeshGroup("g1", "Group One", memberAgentIds = listOf("a", "b")),
                    MeshGroup("g2", "Group Two", memberAgentIds = listOf("c", "d")),
                ),
            nodes =
                listOf(
                    node("a", "m1", "g1", "orchestrator", 10),
                    node("b", "m1", "g1", "subordinate", 20),
                    node("c", "m2", "g2", "orchestrator", 70),
                    node("d", "m2", "g2", "subordinate", 80),
                    node("solo", "m2", null, "solo", 30),
                ),
            edges =
                listOf(
                    MeshEdge("e1", "a", "b", lastAt = 25),
                    MeshEdge("e2", "c", "d", lastAt = 90),
                    MeshEdge("cross", "b", "c", lastAt = 95),
                ),
        )

    private suspend fun publish(snapshot: MeshSnapshot = sampleSnapshot()) {
        bus.publish(Envelope.of(MsgType.MESH_SNAPSHOT, snapshot))
        withTimeout(3_000) { viewModel.uiState.first { it.snapshot == snapshot } }
    }

    @Test
    fun `screen visibility subscribes and unsubscribes`() {
        runBlocking {
            acceptSocket()
            connect()

            viewModel.onScreenVisible()
            val subscribe = awaitOutgoing(MsgType.MESH_SUBSCRIBE)
            assertEquals(null, subscribe.body<dev.j3pf.rp.protocol.MeshSubscribe>().machineId)

            viewModel.onScreenInvisible()
            awaitOutgoing(MsgType.MESH_UNSUBSCRIBE)
        }
    }

    @Test
    fun `snapshot calculates layout machine and group chips and chooses most active group`() {
        runBlocking {
            publish()

            val state = viewModel.uiState.value
            assertEquals(setOf("c", "d"), state.layoutResult?.nodes?.keys)
            assertEquals(
                setOf("a", "b", "c", "d", "solo"),
                state.snapshot
                    ?.nodes
                    ?.map { it.agentId }
                    ?.toSet(),
            )
            assertEquals(listOf(null, "m1", "m2"), state.machineChips.map { it.machineId })
            assertEquals("", state.machineChips.first().name)
            assertEquals(5, state.machineChips.first().agentCount)
            assertEquals(listOf("g1", "g2", MeshViewModel.SOLO_GROUP_ID), state.groupChips.map { it.groupId })
            assertEquals("", state.groupChips.last().label)
            assertEquals("g2", state.selectedGroupId)
            assertTrue(state.groupChips.last().isSolo)
        }
    }

    @Test
    fun `preselection chooses group with highest recent node or edge activity`() {
        runBlocking {
            val snapshot =
                sampleSnapshot().copy(
                    nodes = sampleSnapshot().nodes.map { if (it.agentId == "a") it.copy(lastCommAt = 200) else it },
                )
            publish(snapshot)

            assertEquals("g1", viewModel.uiState.value.selectedGroupId)
            withTimeout(3_000) {
                while (settings.current().selectedMeshGroup != "g1") delay(20)
            }
            assertEquals("g1", settings.current().selectedMeshGroup)
        }
    }

    @Test
    fun `machine and group selections filter layout nodes and edges and persist`() {
        runBlocking {
            publish()

            viewModel.selectMachine("m1")
            viewModel.selectGroup("g1")
            withTimeout(3_000) {
                viewModel.uiState.first {
                    it.selectedMachineId == "m1" &&
                        it.selectedGroupId == "g1" &&
                        it.layoutResult?.nodes?.keys == setOf("a", "b")
                }
            }

            assertEquals(
                listOf("e1"),
                viewModel.uiState.value.layoutResult
                    ?.edges
                    ?.map { it.edge.edgeId },
            )
            withTimeout(3_000) {
                while (settings.current().selectedMeshMachine != "m1" || settings.current().selectedMeshGroup != "g1") delay(20)
            }
            assertEquals("m1", settings.current().selectedMeshMachine)
            assertEquals("g1", settings.current().selectedMeshGroup)

            viewModel.selectMachine(null)
            viewModel.selectGroup(MeshViewModel.SOLO_GROUP_ID)
            withTimeout(3_000) {
                viewModel.uiState.first {
                    it.selectedMachineId == null &&
                        it.selectedGroupId == MeshViewModel.SOLO_GROUP_ID &&
                        it.layoutResult?.nodes?.keys == setOf("solo")
                }
            }
            assertTrue(
                viewModel.uiState.value.layoutResult!!
                    .edges
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `incoming comm updates latest-first live strip and active bubbles`() {
        runBlocking {
            publish()
            repeat(21) { index ->
                val comm = MeshComm("c$index", fromName = "A", toName = "B", text = "message $index", at = index.toLong())
                bus.publish(Envelope.of(MsgType.MESH_COMM, MeshCommEvent(comm)))
            }
            withTimeout(3_000) { viewModel.uiState.first { it.liveComms.size == 20 && it.liveComms.first().commKey == "c20" } }

            assertEquals(
                "c20",
                viewModel.uiState.value.liveComms
                    .first()
                    .commKey,
            )
            assertEquals(20, viewModel.uiState.value.liveComms.size)
            assertEquals(21, viewModel.uiState.value.activeBubbles.size)
            assertTrue(
                viewModel.uiState.value.activeBubbles
                    .all { it.expiresAtMs > System.currentTimeMillis() },
            )
        }
    }

    @Test
    fun `clicking node fetches and displays its recent communications`() {
        runBlocking {
            publish()
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"commKey":"recent","fromAgentId":"a","toAgentId":"b","fromName":"Agent a","toName":"Agent b","action":"ask","text":"Question","at":44}]}""",
                ),
            )

            viewModel.onNodeClick("a")
            val detail = withTimeout(5_000) { viewModel.uiState.first { it.nodeDetail?.isLoadingComms == false } }.nodeDetail
            assertNotNull(detail)
            assertEquals("a", detail.node.agentId)
            assertEquals(listOf("recent"), detail.recentComms.map { it.commKey })
            assertEquals(null, viewModel.uiState.value.conversationDetail)
            viewModel.dismissNodeDetail()
            assertEquals(null, viewModel.uiState.value.nodeDetail)
        }
    }

    @Test
    fun `clicking edge loads conversation and pages older items with nextBefore`() {
        runBlocking {
            publish()
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"commKey":"new","fromAgentId":"a","toAgentId":"b","fromName":"Agent a","toName":"Agent b","action":"ask","at":90}],"nextBefore":50}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"commKey":"old","fromAgentId":"b","toAgentId":"a","fromName":"Agent b","toName":"Agent a","action":"reply","at":40}],"nextBefore":null}""",
                ),
            )

            viewModel.onEdgeClick("e1")
            withTimeout(5_000) { viewModel.uiState.first { it.conversationDetail?.isLoading == false } }
            assertEquals(
                "a",
                viewModel.uiState.value.conversationDetail
                    ?.agentA,
            )
            assertEquals(
                "b",
                viewModel.uiState.value.conversationDetail
                    ?.agentB,
            )
            assertEquals(
                50L,
                viewModel.uiState.value.conversationDetail
                    ?.nextBefore,
            )

            viewModel.loadMoreConversation()
            withTimeout(5_000) {
                viewModel.uiState.first {
                    it.conversationDetail?.items?.map(MeshComm::commKey) == listOf("new", "old") && !it.conversationDetail.isLoadingMore
                }
            }
            assertEquals(
                null,
                viewModel.uiState.value.conversationDetail
                    ?.nextBefore,
            )
            viewModel.dismissConversationDetail()
            assertEquals(null, viewModel.uiState.value.conversationDetail)
        }
    }

    @Test
    fun `accessibility list view toggle flips state`() {
        assertFalse(viewModel.uiState.value.isListView)
        viewModel.toggleListView()
        assertTrue(viewModel.uiState.value.isListView)
        viewModel.toggleListView()
        assertFalse(viewModel.uiState.value.isListView)
    }

    @Test
    fun `onOpenConversation opens conversation even when no edge exists and failed loadMore preserves nextBefore`() {
        runBlocking {
            publish()
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"commKey":"c1","fromAgentId":"a","toAgentId":"solo","fromName":"Agent a","toName":"Agent solo","action":"ask","at":90}],"nextBefore":50}""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(500))

            viewModel.onOpenConversation("a", "solo")
            withTimeout(5_000) { viewModel.uiState.first { it.conversationDetail?.isLoading == false } }
            assertEquals(
                "a",
                viewModel.uiState.value.conversationDetail
                    ?.agentA,
            )
            assertEquals(
                "solo",
                viewModel.uiState.value.conversationDetail
                    ?.agentB,
            )
            assertEquals(
                50L,
                viewModel.uiState.value.conversationDetail
                    ?.nextBefore,
            )

            viewModel.loadMoreConversation()
            withTimeout(5_000) { viewModel.uiState.first { it.conversationDetail?.isLoadingMore == false } }
            assertEquals(
                50L,
                viewModel.uiState.value.conversationDetail
                    ?.nextBefore,
            )
        }
    }
}
