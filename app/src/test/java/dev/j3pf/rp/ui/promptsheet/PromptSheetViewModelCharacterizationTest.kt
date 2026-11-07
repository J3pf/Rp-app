package dev.j3pf.rp.ui.promptsheet

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.util.BiometricGate
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization of PromptSheetViewModel (CH34) on UNCHANGED code. Verdicts on observable
 * behavior: requiresAuth matrix (settings.requireUnlockToApprove × prompt category/danger),
 * already resolved → AnswerUi.AlreadyResolved then reset() → Idle, and the wire payload of
 * a Cancelled answer (`"cancelled":true`) captured from the real PROMPT_ANSWER envelope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptSheetViewModelCharacterizationTest {
    @get:Rule
    val testWatchdog = dev.j3pf.rp.WatchdogTimeout.seconds(120)

    private lateinit var vm: PromptSheetViewModel
    private lateinit var server: MockWebServer
    private lateinit var connection: RelayConnection
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private lateinit var scope: CoroutineScope

    private val requests = LinkedBlockingQueue<Envelope>()

    private fun permissionPrompt() =
        dev.j3pf.rp.protocol.Prompt(
            promptId = "p-1",
            agentId = "a-1",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.PERMISSION,
            title = "Bash",
            danger = Danger.HIGH,
        )

    private fun dialogPrompt() =
        dev.j3pf.rp.protocol.Prompt(
            promptId = "p-2",
            agentId = "a-1",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.DIALOG,
            title = "Pregunta",
            danger = Danger.MEDIUM,
        )

    @Before
    fun reset() = Unit

    @After
    fun tearDown() {
        runCatching { connection.stop() }
        runCatching { scope.cancel() }
        runCatching { server.shutdown() }
    }

    private suspend fun connectHarness(): PromptSheetViewModel {
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
                        when (env.t) {
                            MsgType.PROMPT_ANSWER ->
                                webSocket
                                    .send(
                                        Envelope
                                            .of(
                                                MsgType.PROMPT_ANSWER_RESULT,
                                                PromptAnswerResult(ok = false, reason = "already_resolved"),
                                                re = env.id,
                                            ).encode(),
                                    )
                        }
                    }
                },
            ),
        )

        connection.start()
        withTimeout(15_000) { connection.state.first { it == ConnState.Connected } }
        return PromptSheetViewModel(prompts, settingsRepo, BiometricGate())
    }

    // ──────────────────────── requiresAuth matrix ────────────────────────

    @Test
    fun `requiresAuth follows requireUnlockToApprove for permission and danger high`() = runBlocking {
        vm = connectHarness()
        // Default settings: requireUnlockToApprove = true.
        assertTrue(settingsRepo.settings.first().requireUnlockToApprove)
        assertTrue(vm.requiresAuth(permissionPrompt()), "Approbación crítica pide desbloqueo")
        // A HIGH dialog also requires unlock even outside PERMISSION.
        assertTrue(
            vm.requiresAuth(permissionPrompt().copy(category = PromptCategory.DIALOG)),
            "danger high siempre pide desbloqueo",
        )
        assertFalse(vm.requiresAuth(dialogPrompt()), "Diálogo de riesgo medio no pide desbloqueo")

        settingsRepo.setRequireUnlock(false)
        withTimeout(15_000) { vm.settings.first { !it.requireUnlockToApprove } }
        assertFalse(vm.requiresAuth(permissionPrompt()), "Con el ajuste apagado, tampoco")
        Unit
    }

    // ──────────────────────── already resolved + wire ────────────────────────

    @Test
    fun `answer of an already resolved prompt lands as AlreadyResolved and reset clears it`() = runBlocking {
        vm = connectHarness()
        vm.answer("p-1", Answer.Cancelled)

        withTimeout(15_000) { vm.status.first { it == AnswerUi.AlreadyResolved } }
        vm.reset()
        assertEquals(AnswerUi.Idle, vm.status.value)

        var wire: Envelope? = null
        while (wire == null) {
            wire = requests.poll(200, TimeUnit.MILLISECONDS).takeIf { it?.t == MsgType.PROMPT_ANSWER }
        }
        assertEquals(MsgType.PROMPT_ANSWER, wire!!.t)
        assertTrue(
            wire.encode().contains("\"cancelled\":true") || wire.d?.toString()?.contains("cancelled") == true,
            "El JSON del cable lleva cancelled:true",
        )
        Unit
    }
}
