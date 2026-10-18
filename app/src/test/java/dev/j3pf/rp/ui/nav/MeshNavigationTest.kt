package dev.j3pf.rp.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MeshNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `bottom bar displays Red tab and navigates between agents and mesh`() {
        lateinit var navController: NavHostController

        rule.setContent {
            RpTheme {
                navController = rememberNavController()
                Scaffold(
                    bottomBar = {
                        BottomBar(nav = navController, pending = 2)
                    },
                ) { padding ->
                    NavHost(navController, startDestination = AgentsRoute, modifier = Modifier.padding(padding)) {
                        composable<AgentsRoute> { Text("Agentes Content") }
                        composable<MeshRoute> { Text("Red de agentes") }
                        composable<PendingRoute> { Text("Pendientes Content") }
                        composable<SettingsRoute> { Text("Ajustes Content") }
                    }
                }
            }
        }

        rule.waitForIdle()

        // Bottom bar tabs are present
        rule.onNodeWithText("Agentes").assertExists()
        rule.onNodeWithText("Red").assertExists()
        rule.onNodeWithText("Pendientes").assertExists()
        rule.onNodeWithText("Ajustes").assertExists()

        // Initially at Agentes
        rule.onNodeWithText("Agentes Content").assertIsDisplayed()

        // Click Red tab
        rule.onNodeWithText("Red").performClick()
        rule.waitForIdle()

        // Navigated to Red
        assertEquals(true, navController.currentDestination?.hasRoute<MeshRoute>())
        rule.onNodeWithText("Red de agentes").assertIsDisplayed()

        // Return to Agentes
        rule.onNodeWithText("Agentes").performClick()
        rule.waitForIdle()

        assertEquals(true, navController.currentDestination?.hasRoute<AgentsRoute>())
        rule.onNodeWithText("Agentes Content").assertIsDisplayed()
    }
}
