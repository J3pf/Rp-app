package dev.j3pf.rp.ui.promptsheet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for the review consent surface (parity CH33; v0.6.0, danger: high).
 * Green-first on UNCHANGED code. Observable behavior pinned:
 *  - terminal body rendered verbatim in monospace (no reflow),
 *  - the provider's three options with their declared effects, in order,
 *  - the session-wide grant label only on option id "2",
 *  - dismiss = Cancelar -> Answer.Cancelled ({cancelled:true} over the wire).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewConsentCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private fun reviewPrompt() =
        Prompt(
            promptId = "consent-1",
            agentId = "agent-1",
            kind = PromptKind.SELECT,
            category = PromptCategory.PERMISSION,
            title = "Review authority - start review?",
            danger = Danger.HIGH,
            message =
                "View the staged diff?\nsave   - commit the reviewed files\n  discard- keep nothing (destructive)\n   export - save a local bundle",
            options =
                listOf(
                    PromptOption("0", "Start review with 4 lenses", "Long independent review of the whole candidate"),
                    PromptOption("1", "Start review on a narrower slice", "Splits the candidate into smaller reviews"),
                    PromptOption("2", "Skip review for this candidate", "Extra outside view is skipped only for this candidate"),
                ),
        )

    @Test
    fun `renders header danger tag verbatim monospace body and options in order`() {
        val answers = mutableListOf<Answer>()
        rule.setContent {
            RpTheme { PromptContent(prompt = reviewPrompt(), busy = false, onAnswer = { answers += it }) }
        }

        rule.onNodeWithText("Consentimiento de revisión").assertIsDisplayed()
        rule.onNodeWithText("Peligro alto").assertIsDisplayed()
        // Verbatim body: leading spaces and newlines survive.
        rule
            .onNodeWithText(
                "View the staged diff?\nsave   - commit the reviewed files\n  discard- keep nothing (destructive)\n   export - save a local bundle",
            )
            .assertIsDisplayed()
        // Option labels in provider order.
        rule.onNodeWithText("Start review with 4 lenses").assertIsDisplayed()
        rule.onNodeWithText("Start review on a narrower slice").assertIsDisplayed()
        rule.onNodeWithText("Skip review for this candidate").assertIsDisplayed()
        // Declared effects of every option visible.
        rule.onNodeWithText("Long independent review of the whole candidate").assertIsDisplayed()
        rule.onNodeWithText("Splits the candidate into smaller reviews").assertIsDisplayed()
        rule.onNodeWithText("Extra outside view is skipped only for this candidate").assertIsDisplayed()
        assertTrue("Ninguna respuesta antes de que el usuario toque", answers.isEmpty())
    }

    @Test
    fun `session wide grant chip is labelled only on option 2`() {
        rule.setContent {
            RpTheme { PromptContent(prompt = reviewPrompt(), busy = false, onAnswer = {}) }
        }
        rule.onNodeWithText("Permiso para toda la sesión").assertIsDisplayed()
        // Exactly one session-wide chip (not one per option).
        assertEquals(1, rule.onAllNodesWithText("Permiso para toda la sesión").fetchSemanticsNodes().size)
    }

    @Test
    fun `dismissing with Cancelar answers Cancelled and changes nothing else`() {
        val answers = mutableListOf<Answer>()
        rule.setContent {
            RpTheme { PromptContent(prompt = reviewPrompt(), busy = false, onAnswer = { answers += it }) }
        }

        rule.onNodeWithText("Cancelar").performClick()

        assertEquals(1, answers.size)
        assertEquals(Answer.Cancelled, answers.first())
    }

    @Test
    fun `choose option sends only that option id`() {
        val answers = mutableListOf<Answer>()
        rule.setContent {
            RpTheme { PromptContent(prompt = reviewPrompt(), busy = false, onAnswer = { answers += it }) }
        }

        rule.onNodeWithText("Start review with 4 lenses").performClick()

        assertEquals(listOf(Answer.option("0")), answers)
    }
}
