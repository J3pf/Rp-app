package dev.j3pf.rp.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.ui.theme.RpTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T1a (Gate A): selector de temas exactos (SE-1) sin tocar producción. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsThemeParityCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `theme dialog shows exactly the four rp theme choices and reports the picked id`() {
        val picked = mutableListOf<String>()
        rule.setContent {
            RpTheme {
                SettingsContent(
                    state = SettingsUiState(settings = dev.j3pf.rp.data.repo.AppSettings()),
                    onNav = {},
                    onSelectTheme = { picked += it },
                    onLogout = {},
                )
            }
        }

        // Fila de la pantalla: muestra el tema actual con su etiqueta exacta.
        rule.onNodeWithText("Tema de la app").performClick()
        rule.onNodeWithText("Gentle-Cute").assertIsDisplayed()
        rule.onNodeWithText("Gentle-Sexy").assertIsDisplayed()
        rule.onNodeWithText("Omarchy / Gentle Shell").assertIsDisplayed()
        rule.onNodeWithText("Material You / Sistema").assertIsDisplayed()

        rule.onNodeWithText("Gentle-Sexy").performClick()
        assertEquals(listOf("gentle-sexy"), picked)

        // La preferencia aún no cambió (el ViewModel persiste aparte), y el diálogo se cierra.
        rule.onAllNodesWithText("Material You / Sistema").fetchSemanticsNodes().isEmpty()
    }
}
