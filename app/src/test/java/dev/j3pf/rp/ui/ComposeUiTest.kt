package dev.j3pf.rp.ui

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PromptTool
import dev.j3pf.rp.protocol.QuestionAnswer
import dev.j3pf.rp.protocol.QuestionDef
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.ui.agents.AgentCard
import dev.j3pf.rp.ui.chat.ChatUiState
import dev.j3pf.rp.ui.chat.Composer
import dev.j3pf.rp.ui.promptsheet.PromptContent
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ComposeUiTest {
    @get:Rule val rule = createComposeRule()

    private val working =
        AgentSummary(
            agentId = "m:a",
            machineId = "m",
            machineName = "PC Empresa",
            machineContext = "work",
            sessionName = "api-gateway",
            cwd = "C:\\proy\\api",
            gitBranch = "main",
            status = "working",
            online = true,
            launcher = "gentle-shell",
            herdr = HerdrInfo(paneId = "p3", workspaceId = "w2"),
            gentleMode = "auto",
            lastMessagePreview = "Compilando…",
        )

    @Test
    fun `tarjetas de agentes muestran estado, lanzador, herdr y modo gentle`() {
        var clicked: String? = null
        rule.setContent {
            RpTheme {
                androidx.compose.foundation.layout.Column {
                    AgentCard(working, onClick = { clicked = working.agentId }, onForget = {})
                    AgentCard(
                        working.copy(
                            agentId = "m:b",
                            sessionName = "docs",
                            status = "waiting_input",
                            openPromptCount = 2,
                            gentleMode = "normal",
                        ),
                        onClick = {},
                        onForget = {},
                    )
                    AgentCard(
                        working.copy(
                            agentId = "m:c",
                            sessionName = "viejo",
                            online = false,
                            lastSeenAt =
                                System.currentTimeMillis() - 3 * 3600_000L,
                            redacted = true,
                        ),
                        onClick = {},
                        onForget = {},
                    )
                }
            }
        }
        rule.onNodeWithText("api-gateway").assertIsDisplayed()
        rule.onNodeWithText("Trabajando").assertIsDisplayed()
        rule.onNodeWithText("Esperando respuesta").assertIsDisplayed()
        rule.onNodeWithText("Desconectado hace 3 h").assertIsDisplayed()
        rule.onAllNodesWithText("gentle-shell", substring = true).assertCountEquals(3)
        rule.onAllNodesWithText("herdr ws w2·pane p3").assertCountEquals(3)
        rule.onAllNodesWithText("AUTO").assertCountEquals(2)
        rule.onNodeWithText("contenido oculto").assertExists()
        rule.onNodeWithText("api-gateway").performClick()
        assertEquals("m:a", clicked)
    }

    private val permission =
        Prompt(
            promptId = "P1",
            agentId = "m:a",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.PERMISSION,
            title = "⚠ Comando peligroso",
            danger = Danger.HIGH,
            options = listOf(PromptOption("0", "Permitir"), PromptOption("1", "Permitir siempre (sesión)"), PromptOption("2", "Denegar")),
            tool = PromptTool("bash", RpJson.parseToJsonElement("""{"command":"rm -rf dist"}""")),
        )

    @Test
    fun `hoja de permisos muestra el comando y responde con la opcion`() {
        val answers = mutableListOf<Answer>()
        rule.setContent { RpTheme { PromptContent(permission, busy = false, onAnswer = { answers += it }) } }
        rule.onNodeWithText("rm -rf dist").assertIsDisplayed()
        rule.onNodeWithText("Peligro alto").assertIsDisplayed()
        rule.onNodeWithText("Permitir siempre (sesión)").assertIsDisplayed()
        rule.onNodeWithText("Permitir").performClick()
        rule.onNodeWithText("Permitir siempre (sesión)").performClick()
        rule.onNodeWithText("Denegar").performClick()
        assertEquals(listOf(Answer.option("0"), Answer.option("1"), Answer.option("2")), answers)
    }

    @Test
    fun `denegar con motivo envia la opcion y el texto`() {
        val answers = mutableListOf<Answer>()
        rule.setContent { RpTheme { PromptContent(permission, busy = false, onAnswer = { answers += it }) } }
        rule.onNodeWithText("Denegar con motivo…").performClick()
        rule.onNodeWithText("Motivo de la denegación").performTextInput("No toques dist")
        rule.onNodeWithText("Denegar").performClick()
        assertEquals(Answer(optionIds = listOf("2"), text = "No toques dist"), answers.single())
    }

    @Test
    fun `ocupado desactiva los botones`() {
        rule.setContent { RpTheme { PromptContent(permission, busy = true, onAnswer = {}) } }
        rule.onNodeWithText("Permitir").assertIsNotEnabled()
    }

    @Test
    fun `permiso con una sola opcion no falla al pulsar Denegar`() {
        val singleOptPermission = permission.copy(options = listOf(PromptOption("0", "Permitir")))
        val answers = mutableListOf<Answer>()
        rule.setContent { RpTheme { PromptContent(singleOptPermission, busy = false, onAnswer = { answers += it }) } }
        rule.onNodeWithText("Denegar").performClick()
        assertEquals(listOf(Answer(confirmed = false, text = null)), answers)
    }

    private val consentBody =
        "Review consent\n" +
            "  Value: this candidate touches stored data\n" +
            "  Risk:  medium\n" +
            "  Target: sha256:abc123\n" +
            "    Projection: workspace"

    private val reviewConsent =
        Prompt(
            promptId = "C1",
            agentId = "m:a",
            kind = PromptKind.SELECT,
            category = PromptCategory.PERMISSION,
            title = "Review consent: gentle-pi#42",
            danger = Danger.HIGH,
            message = consentBody,
            options =
                listOf(
                    PromptOption("0", "Review this change", "Effect: reviews this exact candidate"),
                    PromptOption("1", "Skip this time", "Effect: delivers this candidate without a review"),
                    PromptOption(
                        "2",
                        "Review and allow this session",
                        "Effect: Reviews this change and allows later reviews in this session and repository; revoke anytime.",
                    ),
                ),
        )

    @Test
    fun `consentimiento de revision pinta el cuerpo literal y los tres efectos`() {
        rule.setContent { RpTheme { PromptContent(reviewConsent, busy = false, onAnswer = {}) } }
        // El cuerpo es texto de terminal: si se reflowara o se truncara, este nodo no existiria.
        rule.onNodeWithText(consentBody).assertExists()
        rule.onNodeWithText("Effect: reviews this exact candidate").assertExists()
        rule.onNodeWithText("Effect: delivers this candidate without a review").assertExists()
        val sessionEffect =
            "Effect: Reviews this change and allows later reviews in this session and repository; revoke anytime."
        rule.onNodeWithText(sessionEffect).assertExists()
        // El titulo es de la app; el headline del proveedor no se convierte en titulo.
        rule.onNodeWithText("Review consent: gentle-pi#42").assertDoesNotExist()
    }

    @Test
    fun `consentimiento envia el token exacto de cada opcion`() {
        val answers = mutableListOf<Answer>()
        rule.setContent { RpTheme { PromptContent(reviewConsent, busy = false, onAnswer = { answers += it }) } }
        // Las tres, en orden: con PermissionBody la opcion "1" no existe como tal y la lista no cuadra.
        rule.onNodeWithText("Review this change").performClick()
        rule.onNodeWithText("Skip this time").performClick()
        rule.onNodeWithText("Review and allow this session").performClick()
        assertEquals(listOf(Answer.option("0"), Answer.option("1"), Answer.option("2")), answers)
    }

    @Test
    fun `descartar el consentimiento envia cancelled y no un rechazo`() {
        val answers = mutableListOf<Answer>()
        rule.setContent { RpTheme { PromptContent(reviewConsent, busy = false, onAnswer = { answers += it }) } }
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf(Answer.Cancelled), answers)
    }

    @Test
    fun `un permiso de herramienta no usa la superficie de consentimiento`() {
        rule.setContent { RpTheme { PromptContent(permission, busy = false, onAnswer = {}) } }
        rule.onNodeWithText("Denegar con motivo…").assertExists()
        rule.onNodeWithText("Cancelar").assertDoesNotExist()
    }

    @Test
    fun `cuestionario recorre las paginas y envia al final`() {
        val prompt =
            Prompt(
                promptId = "Q1",
                agentId = "m:a",
                kind = PromptKind.QUESTIONNAIRE,
                category = PromptCategory.QUESTION,
                title = "Preguntas",
                questions =
                    listOf(
                        QuestionDef(
                            "¿Qué base de datos?",
                            "DB",
                            options = listOf(PromptOption("0", "SQLite"), PromptOption("1", "Postgres")),
                        ),
                        QuestionDef(
                            "¿Extras?",
                            "Extras",
                            multiSelect = true,
                            options = listOf(PromptOption("0", "Tests"), PromptOption("1", "Docs")),
                        ),
                    ),
            )
        var answer: Answer? = null
        rule.setContent { RpTheme { PromptContent(prompt, busy = false, onAnswer = { answer = it }) } }
        rule.onNodeWithText("Pregunta 1 de 2 · DB").assertIsDisplayed()
        rule.onNodeWithText("Siguiente").assertIsNotEnabled()
        rule.onNodeWithText("Postgres").performClick()
        rule.onNodeWithText("Siguiente").assertIsEnabled().performClick()
        rule.onNodeWithText("Pregunta 2 de 2 · Extras").assertIsDisplayed()
        rule.onNodeWithText("Tests").performClick()
        rule.onNodeWithText("Docs").performClick()
        rule.onNodeWithText("Enviar").performClick()
        assertEquals(
            Answer.questionnaire(listOf(QuestionAnswer(0, optionIds = listOf("1")), QuestionAnswer(1, optionIds = listOf("0", "1")))),
            answer,
        )
    }

    @Test
    fun `compositor con adjuntos muestra progreso y permite quitar`() {
        var removed: String? = null
        var sent = false
        val ui =
            ChatUiState(
                attachments =
                    listOf(
                        AttachmentUi("a1", "foto.jpg", "image/jpeg", 2048, UploadState.UPLOADING, progress = 0.5f),
                        AttachmentUi("a2", "informe.pdf", "application/pdf", 4096, UploadState.DONE, blobId = "B2"),
                    ),
            )
        rule.setContent {
            RpTheme {
                Composer(
                    ui = ui,
                    working = true,
                    text = "hola",
                    onText = {},
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = { removed = it },
                    newCameraTarget = { Uri.EMPTY },
                    onSend = { sent = true },
                )
            }
        }
        rule.onNodeWithText("foto.jpg", substring = true).assertIsDisplayed()
        rule.onNodeWithText("informe.pdf", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Encolar (follow‑up)").assertIsDisplayed()
        rule.onNodeWithText("Interrumpir y dirigir (steer)").assertIsDisplayed()
        rule.onNodeWithText("Entrega: Automático").assertIsDisplayed()
        // Mientras se sube un adjunto no se puede enviar.
        rule.onNodeWithContentDescription("Enviar").assertIsNotEnabled()
    }
}
