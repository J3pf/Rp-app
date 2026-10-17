package dev.j3pf.rp.ui.chat

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SlashCommandSuggestionsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `typing slash th suggests thinking command and clicking appends space`() {
        var currentText by mutableStateOf("/th")

        rule.setContent {
            RpTheme {
                Composer(
                    ui = ChatUiState(),
                    working = false,
                    text = currentText,
                    onText = { currentText = it },
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { Uri.EMPTY },
                    onSend = {},
                )
            }
        }

        rule.onNodeWithText("/thinking").assertIsDisplayed()
        rule.onNodeWithText("/thinking").performClick()
        rule.waitForIdle()

        assertEquals("/thinking ", currentText)
    }

    @Test
    fun `thinking command displays all supported levels and clicking sets command with level`() {
        var currentText by mutableStateOf("/thinking ")

        rule.setContent {
            RpTheme {
                Composer(
                    ui = ChatUiState(),
                    working = false,
                    text = currentText,
                    onText = { currentText = it },
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { Uri.EMPTY },
                    onSend = {},
                )
            }
        }

        val expectedLevels = listOf("off", "minimal", "low", "medium", "high", "xhigh", "max")
        for (level in expectedLevels) {
            rule.onNodeWithText(level).assertIsDisplayed()
        }

        rule.onNodeWithText("medium").performClick()
        rule.waitForIdle()

        assertEquals("/thinking medium", currentText)
    }

    @Test
    fun `thinking without trailing space also shows level suggestions`() {
        var currentText by mutableStateOf("/thinking")

        rule.setContent {
            RpTheme {
                Composer(
                    ui = ChatUiState(),
                    working = false,
                    text = currentText,
                    onText = { currentText = it },
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { Uri.EMPTY },
                    onSend = {},
                )
            }
        }

        rule.onNodeWithText("high").assertIsDisplayed()
        rule.onNodeWithText("high").performClick()
        rule.waitForIdle()

        assertEquals("/thinking high", currentText)
    }

    @Test
    fun `partial thinking level filters suggestions`() {
        var currentText by mutableStateOf("/thinking mi")

        rule.setContent {
            RpTheme {
                Composer(
                    ui = ChatUiState(),
                    working = false,
                    text = currentText,
                    onText = { currentText = it },
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { Uri.EMPTY },
                    onSend = {},
                )
            }
        }

        rule.onNodeWithText("minimal").assertIsDisplayed()
        rule.onNodeWithText("minimal").performClick()
        rule.waitForIdle()

        assertEquals("/thinking minimal", currentText)
    }

    @Test
    fun `model command suggestion appears and fills slash model with space`() {
        var currentText by mutableStateOf("/mod")

        rule.setContent {
            RpTheme {
                Composer(
                    ui = ChatUiState(),
                    working = false,
                    text = currentText,
                    onText = { currentText = it },
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { Uri.EMPTY },
                    onSend = {},
                )
            }
        }

        rule.onNodeWithText("/model").assertIsDisplayed()
        rule.onNodeWithText("/model").performClick()
        rule.waitForIdle()

        assertEquals("/model ", currentText)
    }

    @Test
    fun `uppercase slash command prefix still suggests commands and thinking levels`() {
        var currentText by mutableStateOf("/Thinking HI")

        rule.setContent {
            RpTheme {
                Composer(
                    ui = ChatUiState(),
                    working = false,
                    text = currentText,
                    onText = { currentText = it },
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { Uri.EMPTY },
                    onSend = {},
                )
            }
        }

        rule.onNodeWithText("high").assertIsDisplayed()
        rule.onNodeWithText("high").performClick()
        rule.waitForIdle()

        assertEquals("/thinking high", currentText)
    }
}
