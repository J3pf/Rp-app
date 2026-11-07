package dev.j3pf.rp.ui.promptsheet

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Characterizes the shared prompt-sheet chrome after the app's stateless sheet shell moves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PromptSheetChromeCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private fun setChrome(
        state: PromptSheetChromeState,
        errorMessage: String? = null,
        authMessage: String? = null,
    ) {
        rule.setContent {
            RpTheme {
                PromptSheetChrome(
                    onDismissRequest = {},
                    state = state,
                    errorMessage = errorMessage,
                    authMessage = authMessage,
                ) {
                    Text("Prompt body")
                }
            }
        }
    }

    @Test
    fun `already resolved status keeps its exact inline string`() {
        setChrome(PromptSheetChromeState.AlreadyResolved)

        rule.onNodeWithText("Ya respondido en otro lugar", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("Prompt body", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `missing prompt status keeps its exact inline string`() {
        setChrome(PromptSheetChromeState.PromptMissing)

        rule.onNodeWithText("Esta pregunta ya no está abierta", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("Prompt body", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `queued status message passes through unchanged`() {
        val queuedMessage = "Sin conexión · se enviará al volver la red"
        setChrome(PromptSheetChromeState.Queued(queuedMessage))

        rule.onNodeWithText(queuedMessage, useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("Prompt body", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `error and auth messages pass through unchanged alongside prompt content`() {
        val errorMessage = "Permission denied by relay"
        val authMessage = "Desbloqueo cancelado"
        setChrome(PromptSheetChromeState.Content, errorMessage, authMessage)

        rule.onNodeWithText("Prompt body", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(errorMessage, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(authMessage, useUnmergedTree = true).assertIsDisplayed()
    }
}
