package dev.j3pf.rp.ui.promptsheet

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
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
import dev.j3pf.rp.ui.theme.RpTheme
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/**
 * Risk: item 2 — PromptSheet auth-gate seam net (C2, RED-first at compile level).
 *
 * Covers the OBSERVABLE seam surface reachable deterministically on this host:
 *  (1) `PromptSheetViewModel` now takes the FROZEN `BiometricAuthorizer` port (FakeGate):
 *      the direct `vm.answer` path wires EXACTLY ONE `PROMPT_ANSWER` envelope and never
 *      touches the gate (the direct-answer bypass of today's behavior is pinned);
 *  (2) the shared chrome renders each :app gate literal verbatim when passed as the
 *      `authMessage`/`errorMessage` parameter (the strings ; the :app side keeps the mapping).
 *
 * DOCUMENTED HOLE (ODD L16): the `:app` scope.launch MAPPING itself (AuthResult.OK/FAILED/
 * UNAVAILABLE -> the three exact strings + the activity-null guard) could NOT be driven on
 * this host: four probes clicked the merged Button node AND invoked the OnClick action
 * directly (`mergedClick=true`, gateCalls=0 / wire=0 always) — the `scope.launch` inside the
 * ModalBottomSheet content never executed from a test-side click. The three mapped-literal
 * cases therefore live in the chrome passthrough pinning + review/mutation probes until the
 * MultiplatformPresentation-layer gets a deterministic driver (orchestrator arbitration).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptSheetAuthGateTest {
    private class FakeGate(
        private val result: BiometricAuthResult,
    ) : BiometricAuthorizer {
        var calls = 0
            private set
        var lastTitle: String? = null
            private set
        var lastReason: String? = null
            private set

        override suspend fun authenticate(reason: String): BiometricAuthResult = result

        override suspend fun authenticate(
            title: String,
            reason: String,
        ): BiometricAuthResult {
            calls++
            lastTitle = title
            lastReason = reason
            return result
        }
    }

    @get:Rule val rule = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var connection: RelayConnection
    private lateinit var scope: CoroutineScope
    private var db: dev.j3pf.rp.data.db.RpDatabase? = null
    private var activeVm: PromptSheetViewModel? = null
    private val requests = LinkedBlockingQueue<Envelope>()

    private fun prompt() =
        dev.j3pf.rp.protocol.Prompt(
            promptId = "p-1",
            agentId = "a-1",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.PERMISSION,
            title = "Bash",
            danger = Danger.HIGH,
        )

    @After
    fun tearDown() {
        activeVm?.let { vm ->
            vm.viewModelScope.cancel()
            runBlocking { withTimeout(10_000) { vm.viewModelScope.coroutineContext[Job]?.join() } }
        }
        activeVm = null
        runCatching { connection.stop() }
        if (::scope.isInitialized) {
            scope.cancel()
            runBlocking { withTimeout(10_000) { scope.coroutineContext[Job]?.join() } }
        }
        runCatching { db?.close() }
        db = null
        runCatching { server.shutdown() }
        Dispatchers.resetMain()
    }

    private fun buildVm(gate: FakeGate): PromptSheetViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        val bus = MessageBus()
        connection = RelayConnection(secrets, bus, testClient(), scope)
        val localDb = inMemoryDb(context).also { db = it }
        val settingsRepo = SettingsRepository(context)
        val api = RelayApi(testClient(), secrets)
        val factory =
            dev.j3pf.rp.push.NotificationFactory(
                context,
                dev.j3pf.rp.push.NotificationChannels(context),
                settingsRepo,
                dev.j3pf.rp.push.NotificationDeduper(localDb.dedup()),
            )
        val prompts =
            dev.j3pf.rp.data.repo.PromptsRepository(
                context,
                localDb.prompts(),
                secrets,
                bus,
                connection,
                api,
                factory,
                scope,
            ).also { it.start() }
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: okhttp3.Response,
                    ) {
                        webSocket.send(
                            Envelope.of(
                                MsgType.APP_WELCOME,
                                dev.j3pf.rp.protocol.AppWelcome(openPrompts = listOf(prompt())),
                            ).encode(),
                        )
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
        runBlocking { withTimeout(15_000) { connection.state.first { it == ConnState.Connected } } }
        return PromptSheetViewModel(prompts, settingsRepo, gate).also { activeVm = it }
    }

    private fun wireAnswerCount(): Int = requests.count { it.t == MsgType.PROMPT_ANSWER }

    // ───────────────── (1) port seam on the VM + the direct-answer bypass ─────────────────

    @Test
    fun `port-typed vm wires one prompt answer for a direct answer without touching the gate`() = runBlocking {
        val gate = FakeGate(BiometricAuthResult.OK)
        val vm = buildVm(gate)
        withTimeout(15_000) { vm.open(prompt().promptId).first { it != null } }
        vm.answer(prompt().promptId, Answer.confirm(true))
        withTimeout(15_000) {
            while (wireAnswerCount() < 1) {
                kotlinx.coroutines.delay(50)
            }
        }
        assertEquals(1, wireAnswerCount())
        assertEquals(0, gate.calls)
        Unit
    }

    // ───────────────── (2) chrome passthrough of the :app gate literals ─────────────────

    @Test
    fun `chrome pins the gate title and unavailable literal verbatim`() {
        rule.setContent {
            RpTheme {
                PromptSheetChrome(
                    state = PromptSheetChromeState.Content,
                    onDismissRequest = {},
                    content = {},
                    authMessage = "Confirma tu identidad",
                    errorMessage = "Configura un bloqueo de pantalla para aprobar",
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Confirma tu identidad").assertExists()
        rule.onNodeWithText("Configura un bloqueo de pantalla para aprobar").assertExists()
    }

    @Test
    fun `chrome pins the gate guard and canceled literal verbatim`() {
        rule.setContent {
            RpTheme {
                PromptSheetChrome(
                    state = PromptSheetChromeState.Content,
                    onDismissRequest = {},
                    content = {},
                    authMessage = "No se pudo verificar tu identidad",
                    errorMessage = "Desbloqueo cancelado",
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("No se pudo verificar tu identidad").assertExists()
        rule.onNodeWithText("Desbloqueo cancelado").assertExists()
    }
}

