package dev.j3pf.rp.ui.promptsheet

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.Ulid
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for the review consent surface (parity CH33; v0.6.0, danger: high).
 * Green-first on UNCHANGED code. Observable behavior pinned:
 *  - header `Consentimiento de revisión` + `Peligro alto`;
 *  - terminal body rendered verbatim (leading spaces/newlines intact) inside a NO-REFLOW
 *    surface (softWrap=false -> horizontal scroll axis in semantics above zero extent);
 *  - the provider's three options and their declared effects, IN ORDER (bounds-based);
 *  - the session-wide grant chip is bound to option id "2" ONLY (moving it to any other
 *    option fails) — chip bounds overlap exclusively the option-2 row;
 *  - dismiss = `Cancelar` -> `Answer.Cancelled`; choose option -> `Answer.option(id)`;
 *  - over the wire, {cancelled:true} is the exact serialized JSON of Answer.Cancelled
 *    (project Envelope codec, MsgType.PROMPT_ANSWER).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewConsentCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private val body =
        "View the staged diff?\nsave   - commit the reviewed files\n  discard- keep nothing (destructive)\n   export - save a local bundle"

    private fun reviewPrompt() =
        Prompt(
            promptId = "consent-1",
            agentId = "agent-1",
            kind = PromptKind.SELECT,
            category = PromptCategory.PERMISSION,
            title = "Review authority - start review?",
            danger = Danger.HIGH,
            message = body,
            options =
                listOf(
                    PromptOption("0", "Start review with 4 lenses", "Long independent review of the whole candidate"),
                    PromptOption("1", "Start review on a narrower slice", "Splits the candidate into smaller reviews"),
                    PromptOption("2", "Skip review for this candidate", "Extra outside view is skipped only for this candidate"),
                ),
        )

    private fun setContent(answers: (Answer) -> Unit = {}) {
        rule.setContent {
            RpTheme { PromptContent(prompt = reviewPrompt(), busy = false, onAnswer = answers) }
        }
    }

    // UNMERGED tree: merged button nodes swallow label/chip/description into one bound.
    private fun bounds(text: String): Rect =
        rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun bodyNode(): SemanticsNode = rule.onNodeWithText(body).fetchSemanticsNode()

    /** Área de solapamiento entre dos rects (0f cuando no se tocan). */
    private fun overlapArea(a: Rect, b: Rect): Float =
        (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f) *
            (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)

    @Test
    fun `renders header danger tag and verbatim body`() {
        setContent()

        rule.onNodeWithText("Consentimiento de revisión").assertIsDisplayed()
        rule.onNodeWithText("Peligro alto").assertIsDisplayed()
        rule.onNodeWithText(body).assertIsDisplayed()
    }

    @Test
    fun `body surface does not reflow and exposes a horizontal scroll axis instead`() {
        setContent()

        // softWrap=false + horizontalScroll: a reflowed Text carries no horizontal scroll
        // axis, so a reflow regression fails exactly here.
        val horizontal = bodyNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertNotNull("El cuerpo del consentimiento debe ser scrollable horizontal (sin reflow)", horizontal)
    }

    @Test
    fun `options and their declared effects appear in provider order`() {
        setContent()

        // Vertical bounds pin the ORDER: reshuffling options changes these comparisons.
        val l0 = bounds("Start review with 4 lenses")
        val l1 = bounds("Start review on a narrower slice")
        val l2 = bounds("Skip review for this candidate")
        val e0 = bounds("Long independent review of the whole candidate")
        val e1 = bounds("Splits the candidate into smaller reviews")
        val e2 = bounds("Extra outside view is skipped only for this candidate")
        assertTrue("Opción 0 arriba de la 1 y esta de la 2 (orden del proveedor)", l0.top < l1.top && l1.top < l2.top)
        // Each declared effect belongs to its own option's vertical slice (pairing, not order alone).
        assertTrue("Efecto 0 va con la opción 0", e0.top > l0.top && e0.top < l1.top)
        assertTrue("Efecto 1 va con la opción 1", e1.top > l1.top && e1.top < l2.top)
        assertTrue("Efecto 2 va con la opción 2", e2.top > l2.top)
    }

    @Test
    fun `session wide grant chip is bound to option id 2 only`() {
        setContent()

        val chip = bounds("Permiso para toda la sesión")
        val l2 = bounds("Skip review for this candidate")
        val e2 = bounds("Extra outside view is skipped only for this candidate")
        val l1 = bounds("Start review on a narrower slice")
        val l0 = bounds("Start review with 4 lenses")
        // The chip lives in the option-2 button (label row above the effect text); moving the
        // chip to any other option (or rendering one per option) breaks exactly this placement.
        assertTrue("El chip debe vivir en la fila de la opción 2", chip.top >= l2.top && chip.bottom <= e2.top)
        assertTrue("El chip no debe tocar la opción 1", overlapArea(chip, l1) == 0f)
        assertTrue("El chip no debe tocar la opción 0", overlapArea(chip, l0) == 0f)
    }

    @Test
    fun `dismissing with Cancelar answers Cancelled and nothing else`() {
        val answers = mutableListOf<Answer>()
        setContent { answers += it }

        rule.onNodeWithText("Cancelar").performClick()

        assertEquals(1, answers.size)
        assertEquals(Answer.Cancelled, answers.first())
    }

    @Test
    fun `choose option sends only that option id`() {
        val answers = mutableListOf<Answer>()
        setContent { answers += it }

        rule.onNodeWithText("Start review with 4 lenses").performClick()

        assertEquals(listOf(Answer.option("0")), answers)
    }

    @Test
    fun `wire serial of a cancelled answer is exactly cancelled_true`() {
        val envelope =
            Envelope.of(MsgType.PROMPT_ANSWER, PromptAnswerRequest("consent-1", Answer.Cancelled), id = Ulid.new())
        val json = envelope.encode()

        assertTrue("El JSON del cable debe llevar cancelled:true: $json", json.contains("\"cancelled\":true"))
        assertTrue("cancelled nunca puede ir null: $json", !json.contains("\"cancelled\":null"))
        assertTrue("Cancelar no elige opciones: $json", !json.contains("\"optionIds\""))
    }
}
