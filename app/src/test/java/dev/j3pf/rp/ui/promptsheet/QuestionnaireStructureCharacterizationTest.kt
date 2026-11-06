package dev.j3pf.rp.ui.promptsheet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.QuestionAnswer
import dev.j3pf.rp.protocol.QuestionDef
import dev.j3pf.rp.ui.theme.RpTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QA N6 structural characterization (CH39, rev 3): the questionnaire chrome advances pages
 * with `Siguiente` (progress label `Pregunta N de M · header`), requires an answer before
 * enabling it, and the last page answers `Answer.questionnaire([...])`. Green-first on
 * UNCHANGED code; exact strings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class QuestionnaireStructureCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private fun prompt() =
        Prompt(
            promptId = "q-1",
            agentId = "a-1",
            kind = PromptKind.QUESTIONNAIRE,
            category = PromptCategory.DIALOG,
            title = "Configuración",
            danger = Danger.MEDIUM,
            questions =
                listOf(
                    QuestionDef(
                        question = "¿Cuál es el entorno destino?",
                        header = "Despliegue",
                        options = listOf(PromptOption("st", "Staging"), PromptOption("prd", "Producción")),
                    ),
                    QuestionDef(
                        question = "¿Confirmas el despliegue?",
                        header = "Confirmación",
                        options = listOf(PromptOption("si", "Sí"), PromptOption("no", "No")),
                    ),
                ),
        )

    @Test
    fun `questionnaire advances pages only when each page is answered`() {
        val received = mutableListOf<Answer>()
        rule.setContent {
            RpTheme { PromptContent(prompt = prompt(), busy = false, onAnswer = { received += it }) }
        }

        rule.onNodeWithText("Pregunta 1 de 2 · Despliegue").assertIsDisplayed()
        rule.onNodeWithText("¿Cuál es el entorno destino?").assertIsDisplayed()
        rule.onNodeWithText("Siguiente").assertIsNotEnabled()
        rule.onNodeWithText("Staging").performClick()
        rule.onNodeWithText("Siguiente").assertIsEnabled()
        rule.onNodeWithText("Siguiente").performClick()

        rule.onNodeWithText("Pregunta 2 de 2 · Confirmación").assertIsDisplayed()
        rule.onNodeWithText("¿Confirmas el despliegue?").assertIsDisplayed()
        rule.onNodeWithText("Sí").performClick()
        rule.onNodeWithText("Enviar").performClick() // última página: envía el cuestionario

        assertEquals(
            listOf(Answer.questionnaire(listOf(QuestionAnswer(0, optionIds = listOf("st")), QuestionAnswer(1, optionIds = listOf("si"))))),
            received,
        )
    }
}
