package dev.j3pf.rp.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NavModelsTest {
    @Test
    fun `AppTab entries have expected labels and routes`() {
        assertEquals(4, AppTab.entries.size)
        assertEquals("Agentes", AppTab.AGENTS.label)
        assertEquals(AgentsRoute, AppTab.AGENTS.route)
        assertEquals("Red", AppTab.MESH.label)
        assertEquals(MeshRoute, AppTab.MESH.route)
        assertEquals("Pendientes", AppTab.PENDING.label)
        assertEquals(PendingRoute, AppTab.PENDING.route)
        assertEquals("Ajustes", AppTab.SETTINGS.label)
        assertEquals(SettingsRoute, AppTab.SETTINGS.route)
    }

    @Test
    fun `Routes instantiation and properties`() {
        assertNotNull(OnboardingRoute)
        assertNotNull(ScanRoute)
        assertNotNull(PermissionsRoute)
        assertNotNull(AgentsRoute)
        assertNotNull(MeshRoute)
        assertNotNull(PendingRoute)
        assertNotNull(SettingsRoute)
        assertNotNull(NewAgentRoute)
        assertNotNull(DevicesRoute)
        assertNotNull(MachinesRoute)
        assertNotNull(AddMachineRoute)
        assertNotNull(NotificationSettingsRoute)
        assertNotNull(SecuritySettingsRoute)
        assertNotNull(BatteryRoute)
        assertNotNull(AboutRoute)

        val pair = PairRoute(url = "https://example.com", code = "123456")
        assertEquals("https://example.com", pair.url)
        assertEquals("123456", pair.code)

        val chat = ChatRoute(agentId = "agent-42")
        assertEquals("agent-42", chat.agentId)
    }
}
