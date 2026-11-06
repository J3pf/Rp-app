package dev.j3pf.rp.ui.nav

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * T1a Gate A: pins the pure top-level tab mapping. A full RpRoot render was attempted, but typed-route
 * registration throws JVM IllegalAccessException for private ArchivedAgentsRoute (RpRoot.kt:73,255),
 * so the start/session-target branches are reported as a Gate A gap rather than copied into a fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RpRootParityTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `NV-2 bottom tabs select the four typed top level routes in order`() {
        val selected = mutableListOf<AppTab>()
        rule.setContent {
            RpTheme {
                AppBottomBar(
                    selectedTab = AppTab.AGENTS,
                    onSelectTab = { selected += it },
                )
            }
        }

        listOf("Agentes", "Red", "Pendientes", "Ajustes").forEach { label ->
            rule.onNodeWithText(label).performClick()
        }

        assertEquals(listOf(AppTab.AGENTS, AppTab.MESH, AppTab.PENDING, AppTab.SETTINGS), selected)
        assertEquals(
            listOf(AgentsRoute, MeshRoute, PendingRoute, SettingsRoute),
            AppTab.entries.map { it.route },
        )
    }
}
