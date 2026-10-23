package dev.j3pf.rp.ui.promptsheet

import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PromptTool
import dev.j3pf.rp.protocol.QuestionAnswer
import dev.j3pf.rp.protocol.QuestionDef
import dev.j3pf.rp.protocol.RpJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptContentTest {
    @Test
    fun testPermissionPromptStructure() {
        val prompt =
            Prompt(
                promptId = "P1",
                agentId = "agent-1",
                kind = PromptKind.CONFIRM,
                category = PromptCategory.PERMISSION,
                title = "Bash Execution",
                danger = Danger.HIGH,
                options =
                    listOf(
                        PromptOption("allow", "Permitir"),
                        PromptOption("always", "Permitir siempre"),
                        PromptOption("deny", "Denegar"),
                    ),
                tool = PromptTool("bash", RpJson.parseToJsonElement("""{"command":"git status"}""")),
            )

        assertEquals("P1", prompt.promptId)
        assertEquals(PromptCategory.PERMISSION, prompt.category)
        assertEquals(Danger.HIGH, prompt.danger)
        assertEquals(3, prompt.options.size)
        assertNotNull(prompt.tool)
        assertEquals("bash", prompt.tool?.name)
    }

    @Test
    fun testQuestionnaireStructureAndAnswers() {
        val q1 =
            QuestionDef(
                question = "¿Cuál es el entorno destino?",
                header = "Despliegue",
                options =
                    listOf(
                        PromptOption("staging", "Staging", "Servidor de pruebas"),
                        PromptOption("prod", "Producción", "Servidor en vivo"),
                    ),
                multiSelect = false,
                allowFreeText = true,
            )

        val q2 =
            QuestionDef(
                question = "¿Qué servicios reiniciar?",
                header = "Servicios",
                options =
                    listOf(
                        PromptOption("api", "API Gateway"),
                        PromptOption("worker", "Background Worker"),
                    ),
                multiSelect = true,
                allowFreeText = false,
            )

        val prompt =
            Prompt(
                promptId = "Q1",
                agentId = "agent-1",
                kind = PromptKind.QUESTIONNAIRE,
                category = PromptCategory.QUESTION,
                title = "Cuestionario de despliegue",
                questions = listOf(q1, q2),
            )

        assertEquals(2, prompt.questions?.size)
        assertFalse(q1.multiSelect)
        assertTrue(q2.multiSelect)

        val answer =
            Answer.questionnaire(
                listOf(
                    QuestionAnswer(0, optionIds = listOf("staging")),
                    QuestionAnswer(1, optionIds = listOf("api", "worker")),
                ),
            )

        assertEquals(2, answer.answers?.size)
        assertEquals(listOf("staging"), answer.answers?.get(0)?.optionIds)
        assertEquals(listOf("api", "worker"), answer.answers?.get(1)?.optionIds)
    }

    @Test
    fun testAnswerConfirmCreation() {
        val allowed = Answer.confirm(true)
        assertTrue(allowed.confirmed == true)

        val denied = Answer.confirm(false)
        assertFalse(denied.confirmed == true)
    }
}
