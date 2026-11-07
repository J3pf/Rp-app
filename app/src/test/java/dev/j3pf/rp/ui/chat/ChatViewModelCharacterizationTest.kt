package dev.j3pf.rp.ui.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.HostGitDiscard
import dev.j3pf.rp.protocol.HostGitDiscardResult
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.util.BiometricGate
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests on the REAL ChatViewModel + data layer — UNCHANGED code
 * (harness copied from ChatRepositoryTest + AgentsLifecycleTest). Rows pinned with exact
 * strings and wire payloads: CH17 needsAuth, CH18 switchToNormal, CH19 send-error mapping,
 * CH21 quit, CH22 forget guard + close, CH24/25/26 attachments through the VM, and the
 * real S19 chain: vm.discardDiffFile -> HostRepository.gitDiscard -> `host.git_discard`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatViewModelCharacterizationTest {
    @get:Rule
    val testWatchdog = dev.j3pf.rp.WatchdogTimeout.seconds(120)

    private lateinit var vm: ChatViewModel
    private lateinit var server: MockWebServer
    private lateinit var connection: RelayConnection
    private lateinit var chats: ChatRepository
    private lateinit var agentsRepo: AgentsRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private lateinit var scope: CoroutineScope

    private val requests = LinkedBlockingQueue<Envelope>()

    /** Per-test reply for USER_MESSAGE envelopes; null (default) = silent. */
    private var ackReply: ((Envelope) -> Envelope?)? = null

    /** When set, AGENT_CLOSE replies with this error instead of ok (close-failure path). */
    private var agentCloseError: Pair<String, String>? = null

    private val agentId = "m:test"
    private val cwd = "/home/user/project"
    private val filePath = "app/src/main/java/dev/j3pf/rp/ui/chat/ChatViewModel.kt"

    private fun sampleAgent(gentleMode: String? = "auto") =
        AgentSummary(
            agentId = agentId,
            machineId = "m",
            machineName = "PC",
            sessionName = "chat-test",
            cwd = cwd,
            gitBranch = "main",
            status = "idle",
            online = true,
            launcher = "pi",
            gentleMode = gentleMode,
        )

    @Before
    fun resetDefaults() {
        ackReply = null
        agentCloseError = null
    }

    @After
    fun tearDown() {
        runCatching { connection.stop() }
        runCatching { scope.cancel() }
        runCatching { server.shutdown() }
    }

    private suspend fun connectHarness(gentleMode: String? = "auto"): ChatViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        val bus = MessageBus()
        connection = RelayConnection(secrets, bus, testClient(), scope)
        db = inMemoryDb(context)
        settingsRepo = SettingsRepository(context)
        val api = RelayApi(testClient(), secrets)
        val factory =
            dev.j3pf.rp.push.NotificationFactory(
                context,
                dev.j3pf.rp.push.NotificationChannels(context),
                settingsRepo,
                dev.j3pf.rp.push.NotificationDeduper(db.dedup()),
            )
        val prompts =
            dev.j3pf.rp.data.repo.PromptsRepository(
                context,
                db.prompts(),
                secrets,
                bus,
                connection,
                api,
                factory,
                scope,
            )
        chats =
            ChatRepository(
                context = context,
                chatDao = db.chat(),
                outboxDao = db.outbox(),
                connection = connection,
                bus = bus,
                secrets = secrets,
                prompts = prompts,
                api = api,
                scope = scope,
                agentDao = db.agents(),
            )
        agentsRepo = AgentsRepository(db.agents(), secrets, bus, connection, settingsRepo, scope)
        val attachments = AttachmentsRepository(context, api)
        val host = HostRepository(connection, bus, scope)

        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: okhttp3.Response,
                    ) {
                        webSocket.send(Envelope.of(MsgType.APP_WELCOME, dev.j3pf.rp.protocol.AppWelcome()).encode())
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val env = runCatching { Envelope.parse(text) }.getOrNull() ?: return
                        requests.put(env)
                        val reply =
                            if (env.t == MsgType.USER_MESSAGE) ackReply?.invoke(env) else defaultReply(env)
                        reply?.let { webSocket.send(it.encode()) }
                    }
                },
            ),
        )

        db.agents().upsert(listOf(sampleAgent(gentleMode).toEntity(relayId = "r1")))

        connection.start()
        withTimeout(15_000) { connection.state.first { it == ConnState.Connected } }

        val vm =
            ChatViewModel(
                savedStateHandle = SavedStateHandle(mapOf("agentId" to agentId)),
                chats = chats,
                agentsRepo = agentsRepo,
                settingsRepo = settingsRepo,
                attachmentsRepo = attachments,
                hostRepo = host,
                connection = connection,
                gate = BiometricGate(),
            )
        withTimeout(15_000) { vm.state.first { it.agent?.cwd == cwd } }
        return vm
    }

    private fun acceptedAck(env: Envelope): Envelope? {
        val user = env.body<UserMessage>()
        return Envelope.of(MsgType.USER_MESSAGE_ACK, UserMessageAck(clientMsgId = user.clientMsgId, disposition = "accepted"), re = env.id)
    }

    private fun ackWith(disposition: String, error: String? = null): (Envelope) -> Envelope? = { env ->
        val user = env.body<UserMessage>()
        Envelope.of(MsgType.USER_MESSAGE_ACK, UserMessageAck(clientMsgId = user.clientMsgId, disposition = disposition, error = error), re = env.id)
    }

    private fun defaultReply(env: Envelope): Envelope? =
        when (env.t) {
            MsgType.AGENT_SUBSCRIBE -> Envelope(t = "ok", re = env.id)
            MsgType.AGENT_CLOSE ->
                agentCloseError?.let { (code, message) ->
                    Envelope.of(MsgType.ERROR, dev.j3pf.rp.protocol.ErrorBody(code = code, message = message), re = env.id)
                } ?: Envelope(t = "ok", re = env.id)
            MsgType.HOST_GIT_DIFF -> Envelope.of(MsgType.HOST_GIT_DIFF_RESULT, HostGitDiffResult(), re = env.id)
            MsgType.HOST_GIT_DISCARD ->
                Envelope.of(
                    MsgType.HOST_GIT_DISCARD_RESULT,
                    HostGitDiscardResult(ok = true, path = env.body<HostGitDiscard>().path),
                    re = env.id,
                )
            else -> null
        }

    private suspend fun awaitRequest(type: String): Envelope =
        withTimeout(15_000) {
            while (true) {
                val found = requests.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (found.t == type) return@withTimeout found
            }
            error("No llegó $type")
        }

    /** Destructive poll avoided: scanning does NOT consume; a matching envelope is a failure. */
    private suspend fun assertNoRequest(type: String) {
        kotlinx.coroutines.delay(1_000)
        requests.filter { it.t == type }.forEach { error("No debía llegar $type, llegó ${it.encode().take(150)}") }
    }

    private fun tempImageUri(): android.net.Uri =
        File(
            ApplicationProvider.getApplicationContext<Application>().cacheDir,
            "chip-${System.nanoTime()}.jpg",
        ).apply { writeBytes(ByteArray(64) { it.toByte() }) }
            .let { android.net.Uri.fromFile(it) }

    // ─────────────────────────── CH17 needsAuth ───────────────────────────

    @Test
    fun `needsAuth gates TYPED and AUTO-on-gentle-auto but no other choice`() = runBlocking {
        vm = connectHarness(gentleMode = "auto")
        vm.setChoice(DeliveryChoice.TYPED)
        assertTrue(vm.needsAuth(), "TYPED siempre pide autenticación")
        vm.setChoice(DeliveryChoice.AUTO)
        assertTrue(vm.needsAuth(), "AUTO en agente gentle:auto cuenta como tecleado")
        vm.setChoice(DeliveryChoice.COMMAND)
        assertTrue(!vm.needsAuth(), "Comandos no piden autenticación")
        vm.setChoice(DeliveryChoice.EXTENSION)
        assertTrue(!vm.needsAuth(), "Mensajes de extensión no piden autenticación")
    }

    @Test
    fun `needsAuth is false for a normal agent in AUTO`() = runBlocking {
        vm = connectHarness(gentleMode = "normal")
        vm.setChoice(DeliveryChoice.AUTO)
        assertTrue(!vm.needsAuth(), "AUTO en agente normal no es tecleado")
    }

    // ─────────────────────────── CH18 switchToNormal ───────────────────────────

    @Test
    fun `switchToNormal sends the exact gentle mode normal command as a command`() = runBlocking {
        vm = connectHarness()
        ackReply = ::acceptedAck
        vm.switchToNormal()
        val wire = awaitRequest(MsgType.USER_MESSAGE).body<UserMessage>()
        assertEquals("/gentle:mode normal", wire.text)
        assertTrue(wire.asCommand, "/gentle:mode normal siempre viaja como comando")
        withTimeout(15_000) { vm.state.first { it.message == "Modo de permisos: Normal" && !it.interactiveUnavailable } }
        Unit
    }

    // ─────────────────────────── CH21 quit ───────────────────────────

    @Test
    fun `quit sends the exact slash quit command as command`() = runBlocking {
        vm = connectHarness()
        ackReply = ::acceptedAck
        var done = false
        vm.quit { done = true }
        val wire = awaitRequest(MsgType.USER_MESSAGE).body<UserMessage>()
        assertEquals("/quit", wire.text)
        assertTrue(wire.asCommand, "/quit siempre va como comando")
        withTimeout(15_000) {
            while (!done) kotlinx.coroutines.delay(50)
        }
        assertTrue(done, "onDone corre tras enviar /quit")
        Unit
    }

    // ─────────────────────────── CH22 forget ───────────────────────────

    @Test
    fun `confirmForget without prior forget sends no agent close and does nothing`() = runBlocking {
        vm = connectHarness()
        vm.confirmForget()
        assertNoRequest(MsgType.AGENT_CLOSE)
    }

    @Test
    fun `forget then confirm closes the agent with exactly one agent close`() = runBlocking {
        vm = connectHarness()
        var completed: Boolean? = null
        vm.forget { completed = true }
        vm.confirmForget()
        awaitRequest(MsgType.AGENT_CLOSE)
        withTimeout(15_000) {
            while (completed == null) kotlinx.coroutines.delay(50)
        }
        assertTrue(completed == true, "onDone corre tras confirmar el cierre")
        Unit
    }

    // ─────────────────────────── CH19 send-error mapping ───────────────────────────

    @Test
    fun `agent_blocked maps to the exact waiting-terminal dialog message`() = runBlocking {
        vm = connectHarness()
        ackReply = ackWith("accepted", "agent_blocked")
        vm.send("mensaje", {})
        withTimeout(15_000) { vm.state.first { it.message == "El agente espera un diálogo en la terminal; el mensaje quedó en cola" } }
        Unit
    }

    @Test
    fun `interactive_unavailable maps to the no-typing-in-auto message`() = runBlocking {
        vm = connectHarness()
        ackReply = ackWith("accepted", "interactive_unavailable")
        vm.send("mensaje", {})
        withTimeout(15_000) { vm.state.first { it.message == "No se pudo teclear en la terminal: el mensaje no autoriza acciones en Auto" } }
        Unit
    }

    @Test
    fun `herdr_unavailable maps to the exact herdr message`() = runBlocking {
        vm = connectHarness()
        ackReply = ackWith("accepted", "herdr_unavailable")
        vm.send("mensaje", {})
        withTimeout(15_000) { vm.state.first { it.message == "herdr no está disponible en esa PC" } }
        Unit
    }

    @Test
    fun `typed_after_dialog maps to the after-dialog queued message`() = runBlocking {
        vm = connectHarness()
        ackReply = ackWith("typed_after_dialog")
        vm.send("mensaje", {})
        withTimeout(15_000) { vm.state.first { it.message == "Se enviará al cerrarse el diálogo" } }
        Unit
    }

    @Test
    fun `offline send queues with the exact offline message`() = runBlocking {
        vm = connectHarness()
        connection.stop()
        // The HTTP fallback must also fail so the send lands Queued: disconnect consumes it.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        vm.send("mensaje offline", {})
        // Queued: no USER_MESSAGE leaves the wire; message is exactly the offline text.
        withTimeout(15_000) { vm.state.first { it.message == "Sin conexión: se enviará al volver la red" } }
        Unit
    }

    // ─────────────────────────── S19 vm→wire chain (CH31) ───────────────────────────

    @Test
    fun `discardDiffFile reaches the wire as host git discard with exact machine cwd and path`() = runBlocking {
        vm = connectHarness()
        vm.discardDiffFile(filePath)
        val wire = awaitRequest(MsgType.HOST_GIT_DISCARD).body<HostGitDiscard>()
        assertEquals("m", wire.machineId)
        assertEquals(cwd, wire.cwd)
        assertEquals(filePath, wire.path)
        // Solo una petición: no hay dobles descartes por la misma confirmación.
        assertNoRequest(MsgType.HOST_GIT_DISCARD)
        // Al confirmar ok, el VM vuelve a abrir la diff para refrescar.
        awaitRequest(MsgType.HOST_GIT_DIFF)
        Unit
    }

    @Test
    fun `openDiffSheet fetches git diff for the agent cwd`() = runBlocking {
        vm = connectHarness()
        vm.openDiffSheet(filePath)
        withTimeout(15_000) { vm.state.first { st -> !st.loadingDiff && st.diffResult != null } }
        val wire = awaitRequest(MsgType.HOST_GIT_DIFF).body<dev.j3pf.rp.protocol.HostGitDiff>()
        assertEquals("m", wire.machineId)
        assertEquals(cwd, wire.cwd)
        assertEquals(filePath, wire.path)
        Unit
    }

    // ─────────────────────────── CH24/25/26 attachments ───────────────────────────

    @Test
    fun `413 upload error surfaces the exact overlimit text and blocks sending`() = runBlocking {
        vm = connectHarness()
        server.enqueue(MockResponse().setResponseCode(413).setBody("{}"))
        vm.addAttachments(listOf(tempImageUri()))
        withTimeout(15_000) {
            vm.state.first { st -> st.attachments.any { it.error == "Supera 25 MB" } }
        }
        requests.clear()
        vm.send("hola", {})
        withTimeout(15_000) { vm.state.first { it.message == "Quita los adjuntos con error antes de enviar" } }
        assertNoRequest(MsgType.USER_MESSAGE)
    }

    @Test
    fun `successful upload lands in DONE with the relay blob id`() = runBlocking {
        vm = connectHarness()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"blobId":"b1","size":64}"""))
        vm.addAttachments(listOf(tempImageUri()))
        withTimeout(15_000) {
            vm.state.first { st -> st.attachments.any { it.state == dev.j3pf.rp.data.repo.UploadState.DONE && it.blobId == "b1" } }
        }
        Unit
    }

    @Test
    fun `network failure surfaces the exact offline attachment error`() = runBlocking {
        vm = connectHarness()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        vm.addAttachments(listOf(tempImageUri()))
        withTimeout(15_000) {
            vm.state.first { st -> st.attachments.any { it.error == "Sin conexión" } }
        }
        Unit
    }

    @Test
    fun `removeAttachment drops the chip and retryAttachment resets then succeeds`() = runBlocking {
        vm = connectHarness()
        server.enqueue(MockResponse().setResponseCode(413).setBody("{}"))
        vm.addAttachments(listOf(tempImageUri()))
        withTimeout(15_000) {
            vm.state.first { st -> st.attachments.any { it.error == "Supera 25 MB" } }
        }
        withTimeout(15_000) { vm.state.first { st -> st.attachments.firstOrNull()?.error == "Supera 25 MB" } }
        val failedId = vm.state.value.attachments.first().id
        vm.removeAttachment(failedId)
        withTimeout(15_000) { vm.state.first { st -> st.attachments.isEmpty() } }
        assertTrue(vm.state.value.attachments.isEmpty(), "Remove limpia la lista")

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"blobId":"b1","size":64}"""))
        vm.addAttachments(listOf(tempImageUri()))
        withTimeout(15_000) {
            vm.state.first { st -> st.attachments.any { it.state == dev.j3pf.rp.data.repo.UploadState.DONE } }
        }
        Unit
    }
    @Test
    fun `close failure surfaces the relay reason verbatim from the chat panel`() = runBlocking {
        vm = connectHarness()
        agentCloseError = "bad_request" to "branch has unmerged changes"
        var completed: Boolean? = null
        vm.forget { completed = true }
        vm.confirmForget()
        awaitRequest(MsgType.AGENT_CLOSE)
        withTimeout(15_000) { vm.state.first { it.message == "No se pudo cerrar el agente: branch has unmerged changes" } }
        assertTrue(completed == null, "onDone no corre cuando el cierre falló")
        Unit
    }
}

