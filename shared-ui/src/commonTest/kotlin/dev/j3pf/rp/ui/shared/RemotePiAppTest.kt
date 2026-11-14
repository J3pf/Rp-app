package dev.j3pf.rp.ui.shared

import androidx.compose.ui.graphics.Color
import dev.j3pf.rp.chat.PromptCard
import dev.j3pf.rp.client.http.RelayHttpRequest
import dev.j3pf.rp.client.http.RelayHttpResponse
import dev.j3pf.rp.client.http.RelayHttpTransport
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.data.settings.SettingsKeys
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshCommEvent
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.ui.chat.DeliveryChoice
import dev.j3pf.rp.ui.components.ALL_MACHINES
import dev.j3pf.rp.ui.nav.AgentsRoute
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.nav.ChatRoute
import dev.j3pf.rp.ui.nav.MeshRoute
import dev.j3pf.rp.ui.nav.OnboardingRoute
import dev.j3pf.rp.ui.nav.PendingRoute
import dev.j3pf.rp.ui.nav.SettingsRoute
import dev.j3pf.rp.ui.promptsheet.PromptSheetChromeState
import dev.j3pf.rp.ui.util.humanSize
import dev.j3pf.rp.ui.util.lastSegment
import dev.j3pf.rp.ui.util.parseColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemotePiAppTest {
    @Test
    fun testSharedUiFoundation() {
        assertEquals(AppSettings.ALL, ALL_MACHINES)
        assertEquals("512 B", humanSize(512))
        assertEquals("1.5 KB", humanSize(1536))
        assertEquals("2.0 MB", humanSize(2L * 1024L * 1024L))
        assertEquals("repo", lastSegment("/home/user/repo/"))
        val fallback = Color(0xFF123456)
        assertEquals(fallback, parseColor("not-a-hex", fallback = fallback))
    }

    @Test
    fun sharedAppControllerNavigatesFiveScreensProcessesWelcomeAndLogsOut() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking())
        val settings = KeyValueSettingsStore()
        val controller = createInMemorySocketController(scope, secrets, settings)

        // Starts on OnboardingRoute when unpaired
        assertEquals(OnboardingRoute, controller.currentRoute.value)

        // Saving a paired session transitions automatically to AgentsRoute
        secrets.save(
            Session(
                relayId = "0123456789abcdef",
                relayUrl = "https://relay.example.com",
                relayName = "Studio Mac",
                deviceId = "dev-1",
                deviceToken = "tok-1",
                pushKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            ),
        )
        assertEquals(AgentsRoute, controller.currentRoute.value)

        // Receive APP_WELCOME over WebSocket bus
        val welcome =
            AppWelcome(
                machines = listOf(MachineSummary(machineId = "m1", name = "MacBook", online = true, hostOnline = true)),
                agents = listOf(AgentSummary(agentId = "m1:a1", machineId = "m1", sessionName = "Unify KMP", online = true)),
                openPrompts = listOf(Prompt(promptId = "p1", agentId = "m1:a1", kind = PromptKind.CONFIRM, title = "Allow?")),
            )
        controller.wsClient.onText(Envelope.of(MsgType.APP_WELCOME, welcome).encode())

        assertEquals(1, controller.machines.value.size)
        assertEquals(1, controller.agents.value.size)
        assertEquals(1, controller.openPrompts.value.size)

        // Navigate across all 5 primary screens + Chat
        controller.selectTab(AppTab.MESH)
        assertEquals(MeshRoute, controller.currentRoute.value)

        controller.selectTab(AppTab.PENDING)
        assertEquals(PendingRoute, controller.currentRoute.value)

        controller.selectTab(AppTab.SETTINGS)
        assertEquals(SettingsRoute, controller.currentRoute.value)

        controller.selectTab(AppTab.AGENTS)
        assertEquals(AgentsRoute, controller.currentRoute.value)

        controller.navigateTo(ChatRoute("m1:a1"))
        assertEquals(ChatRoute("m1:a1"), controller.currentRoute.value)
        assertTrue(controller.navigateBack())
        assertEquals(AgentsRoute, controller.currentRoute.value)

        // Parse QR deep link
        assertEquals(
            "https://relay.example.com" to "ABCD12",
            SharedAppController.parsePairingQr("rp://pair?u=https%3A%2F%2Frelay.example.com&c=ABCD12"),
        )

        // Logout clears session and returns to OnboardingRoute
        controller.logout()
        assertNull(secrets.current())
        assertEquals(OnboardingRoute, controller.currentRoute.value)
    }

    @Test
    fun keyValueSettingsStoreNotifiesInitialMutedAgentsAndAppVersionIsCurrent() {
        assertEquals("0.9.1", SharedAppController.APP_VERSION)
        val backing = InMemorySecretKeyValueBacking()
        backing.putString(SettingsKeys.K_MUTED_AGENTS, "m1:a1,m1:a2")
        var syncedMuted: Set<String> = emptySet()
        val store = KeyValueSettingsStore(backing = backing, onMutedAgentsChanged = { syncedMuted = it })
        assertEquals(setOf("m1:a1", "m1:a2"), store.settings.value.mutedAgents)
        assertEquals(setOf("m1:a1", "m1:a2"), syncedMuted)
    }

    @Test
    fun controllerEnforcesBiometricGateResolvesInlinePromptsAndDecodesMeshComm() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val secrets = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking())
        val settings = KeyValueSettingsStore()
        secrets.save(
            Session(
                relayId = "0123456789abcdef",
                relayUrl = "https://relay.example.com",
                relayName = "Studio Mac",
                deviceId = "dev-1",
                deviceToken = "tok-1",
                pushKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            ),
        )

        var biometricResult = BiometricAuthResult.FAILED
        var biometricInvocations = 0
        var answerHttpStatus = 200
        val httpTransport =
            object : RelayHttpTransport {
                override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> =
                    if (request.url.endsWith("/answer")) {
                        ApiResult.Ok(RelayHttpResponse(answerHttpStatus, """{"ok":true}"""))
                    } else {
                        ApiResult.Ok(RelayHttpResponse(200, "{}"))
                    }
            }

        val controller =
            createInMemorySocketController(
                scope = scope,
                secrets = secrets,
                settingsStore = settings,
                httpTransport = httpTransport,
                biometricAuthorizer =
                    object : BiometricAuthorizer {
                        override suspend fun authenticate(reason: String): BiometricAuthResult {
                            biometricInvocations++
                            return biometricResult
                        }
                    },
            )

        val welcome =
            AppWelcome(
                machines =
                    listOf(
                        MachineSummary(machineId = "m1", name = "MacBook", online = true, hostOnline = true),
                        MachineSummary(machineId = "m-empty", name = "EmptyHost", online = true, hostOnline = true),
                    ),
                agents =
                    listOf(
                        AgentSummary(
                            agentId = "m1:a1",
                            machineId = "m1",
                            sessionName = "Interactive Agent",
                            online = true,
                            gentleMode = "auto",
                        ),
                        AgentSummary(
                            agentId = "m-orphan:a2",
                            machineId = "m-orphan",
                            machineName = "OrphanHost",
                            sessionName = "Orphan Agent",
                            online = true,
                        ),
                    ),
                openPrompts =
                    listOf(
                        Prompt(
                            promptId = "p-crit",
                            agentId = "m1:a1",
                            kind = PromptKind.CONFIRM,
                            category = PromptCategory.PERMISSION,
                            danger = Danger.HIGH,
                            title = "Delete production DB?",
                        ),
                    ),
            )
        controller.wsClient.onText(Envelope.of(MsgType.APP_WELCOME, welcome).encode())

        // Agents UI hides empty machines and synthesizes orphan machine sections
        val agentsUi =
            SharedAppController.buildAgentsUiState(
                machines = controller.machines.value,
                agents = controller.agents.value,
                selectedContext = AppSettings.ALL,
            )
        assertEquals(listOf("m1", "m-orphan"), agentsUi.groups.map { it.machine.machineId })

        // Missing prompt sets PromptSheetChromeState.PromptMissing
        controller.openPromptSheet("no-such-prompt")
        assertEquals(PromptSheetChromeState.PromptMissing, controller.promptSheetState.value)

        // Critical prompt is blocked when biometric authentication fails
        val chatSession = controller.chatSessions.open("m1:a1")
        controller.openPromptSheet("p-crit")
        assertEquals(PromptSheetChromeState.Content, controller.promptSheetState.value)
        controller.answerPrompt("p-crit", Answer.confirm(true))
        assertEquals(1, biometricInvocations)
        assertEquals(1, controller.openPrompts.value.size)

        // Interactive chat send is also gated by biometricConfirm when enabled
        var chatSent = false
        controller.sendChatMessage(
            session = chatSession,
            text = "rm -rf /",
            choice = DeliveryChoice.TYPED,
            workingDelivery = Delivery.AUTO,
            working = false,
            onSent = { chatSent = true },
        )
        assertFalse(chatSent)
        assertEquals(2, biometricInvocations)

        // Allowing biometrics lets critical prompt resolve and updates inline ChatSession PromptCard
        biometricResult = BiometricAuthResult.OK
        controller.answerPrompt("p-crit", Answer.confirm(true))
        assertTrue(controller.openPrompts.value.isEmpty())
        val promptCard =
            chatSession
                .state
                .value
                .items
                .filterIsInstance<PromptCard>()
                .first()
        assertTrue(promptCard.resolved)

        // Answering an already-resolved prompt transitions sheet to AlreadyResolved
        controller.wsClient.onText(
            Envelope
                .of(
                    MsgType.PROMPT_OPENED,
                    welcome.openPrompts.first().copy(promptId = "p-conflict"),
                ).encode(),
        )
        controller.openPromptSheet("p-conflict")
        answerHttpStatus = 409
        controller.answerPrompt("p-conflict", Answer.confirm(true))
        assertEquals(PromptSheetChromeState.AlreadyResolved, controller.promptSheetState.value)

        // MESH_COMM envelope decodes MeshCommEvent wrapper properly
        val commEvent =
            MeshCommEvent(
                comm =
                    MeshComm(
                        commKey = "mc-1",
                        fromAgentId = "m1:a1",
                        toAgentId = "m-orphan:a2",
                        fromName = "agent-a",
                        toName = "agent-b",
                        text = "hello mesh",
                        at = 1_700_000_000_000L,
                    ),
            )
        controller.wsClient.onText(Envelope.of(MsgType.MESH_COMM, commEvent).encode())
        val liveComms = controller.meshState.value.liveComms
        assertEquals(1, liveComms.size)
        assertEquals("mc-1", liveComms.first().commKey)
        assertEquals("hello mesh", liveComms.first().text)
    }
}
