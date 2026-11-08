package dev.j3pf.rp.ui.shared

import androidx.compose.ui.graphics.Color
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.ui.components.ALL_MACHINES
import dev.j3pf.rp.ui.nav.AgentsRoute
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.nav.ChatRoute
import dev.j3pf.rp.ui.nav.MeshRoute
import dev.j3pf.rp.ui.nav.OnboardingRoute
import dev.j3pf.rp.ui.nav.PendingRoute
import dev.j3pf.rp.ui.nav.SettingsRoute
import dev.j3pf.rp.ui.util.humanSize
import dev.j3pf.rp.ui.util.lastSegment
import dev.j3pf.rp.ui.util.parseColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
