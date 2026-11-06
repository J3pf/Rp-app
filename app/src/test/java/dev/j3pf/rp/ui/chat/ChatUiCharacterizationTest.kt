package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.ChatItem
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.SubagentCardItem
import dev.j3pf.rp.chat.TerminalWaitInfo
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.SubagentInfo
import dev.j3pf.rp.chat.ToolRun
import dev.j3pf.rp.chat.ToolStatus
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.ui.chat.AttachmentUploadState
import dev.j3pf.rp.ui.chat.AttachmentChip
import dev.j3pf.rp.ui.chat.MessageList
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpTheme
import dev.j3pf.rp.ui.util.agentTitle as sharedAgentTitle
import dev.j3pf.rp.util.agentTitle as appAgentTitle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure-UI characterization of the chat slice on unchanged code (rows CH6, CH7, CH8, CH9,
 * CH10, CH15, CH16, CH20, CH27, CH28, CH37). Exact strings and observable callbacks only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatUiCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private val idle =
        AgentSummary(
            agentId = "m:test",
            machineId = "m",
            sessionName = "tarea",
            cwd = "/home/user/project",
            gitBranch = "main",
            status = "idle",
            online = true,
            launcher = "pi",
            gentleMode = "auto",
        )

    // ───────────────────────── CH6/CH7 empty states ─────────────────────────

    @Test
    fun `empty chat while syncing shows the exact syncing strings`() {
        rule.setContent {
            RpTheme {
                MessageList(displayItems = emptyList(), syncingHistory = true)
            }
        }
        rule.onNodeWithText("Sincronizando historial…").assertIsDisplayed()
        rule.onNodeWithText("Descargando los mensajes recientes de la sesión.").assertIsDisplayed()
    }

    @Test
    fun `fully empty chat shows the exact placeholder strings`() {
        rule.setContent { RpTheme { MessageList(displayItems = emptyList()) } }
        rule.onNodeWithText("Sin mensajes todavía").assertIsDisplayed()
        rule.onNodeWithText("Escribe abajo para hablar con este agente.").assertIsDisplayed()
        rule.onAllNodesWithText("Sincronizando historial…").assertCountEquals(0)
    }

    // ───────────────────────── CH8/CH9 tools ─────────────────────────

    @Test
    fun `tool card expands on tap and truncates result behind the exact ver más toggle`() {
        val longResult = (1..200).joinToString("\n") { "linea-$it que agota el presupuesto de filas" }
        val assistant =
            AssistantMsg(key = "a1", blocks = listOf(Block.ToolCall(toolCallId = "t1", name = "bash")))
        val tools =
            mapOf(
                "t1" to ToolRun(toolCallId = "t1", name = "bash", status = ToolStatus.OK, result = longResult, truncated = true),
            )
        rule.setContent {
            RpTheme {
                ChatItemView(item = assistant, tools = tools, busyPromptId = null, onAnswerPrompt = { _, _ -> })
            }
        }
        // Cabecera colapsada: nombre de la herramienta y estado OK, sin resultado todavía.
        rule.onNodeWithText("bash").assertIsDisplayed()
        rule.onAllNodesWithText("ver más").assertCountEquals(0)
        // Expandir la tarjeta (click profundo en el header) muestra el resultado y el toggle.
        rule.onNodeWithText("bash").performClick()
        rule.onNodeWithText("ver más").assertIsDisplayed()
        rule.onNodeWithText("ver más").performClick()
        rule.onNodeWithText("ver menos").assertIsDisplayed()
        // El inicio del resultado es visible dentro del bloque truncado del relay.
        rule.onNodeWithText("linea-1 que agota el presupuesto de filas", substring = true).assertIsDisplayed()
        rule.onNodeWithText("(resultado truncado por el relay)").assertIsDisplayed()
        rule.onNodeWithText("ver menos").performClick()
        rule.onNodeWithText("ver más").assertIsDisplayed()
    }

    @Test
    fun `inline tool call hides the duplicate subagent card`() {
        val tool =
            ToolRun(
                toolCallId = "sub-1",
                name = "explore",
                status = ToolStatus.OK,
                subagent = SubagentInfo(taskId = "sub-1", agent = "explore", status = "done"),
            )
        val assistant =
            AssistantMsg(
                key = "a2",
                blocks =
                    listOf(
                        Block.Text("revisión con subagente"),
                        Block.ToolCall(toolCallId = "sub-1", name = "explore"),
                    ),
            )
        val card = SubagentCardItem(key = "card-sub-1", info = tool.subagent!!)
        rule.setContent {
            RpTheme {
                MessageList(
                    displayItems = listOf(assistant as ChatItem, card),
                    tools = mapOf("sub-1" to tool),
                )
            }
        }
        rule.onAllNodesWithText("explore").assertCountEquals(1)
        rule.onNodeWithText("revisión con subagente").assertIsDisplayed()
    }

    @Test
    fun `reduced motion still renders the streaming message statically`() {
        val assistant = AssistantMsg(key = "s1", blocks = listOf(Block.Text("Escribiendo…")), streaming = true)
        rule.setContent {
            RpTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    ChatItemView(item = assistant, tools = emptyMap(), busyPromptId = null, onAnswerPrompt = { _, _ -> })
                }
            }
        }
        rule.onNodeWithText("Escribiendo…").assertIsDisplayed()
    }

    // ───────────────────────── CH15/CH16 composer ─────────────────────────

    private fun setChatContent(ui: ChatUiState, text: String = "/", onChoice: (DeliveryChoice) -> Unit = {}, onSteer: (String) -> Unit = {}, onOpenDiff: () -> Unit = {}) {
        rule.setContent {
            RpTheme {
                ChatContent(
                    ui = ui,
                    text = text,
                    onText = {},
                    onSend = {},
                    onChoice = onChoice,
                    onWorkingDelivery = onSteer,
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { android.net.Uri.EMPTY },
                    onOpenDiff = onOpenDiff,
                )
            }
        }
    }

    @Test
    fun `composer with slash text shows the launcher command chip`() {
        setChatContent(ChatUiState(agent = idle), text = "/")
        rule.onNodeWithText("⚡ Comando Pi (/)").assertIsDisplayed()
    }

    @Test
    fun `herdr agent shows only the command chip and never the delivery chip`() {
        val herdrAgent = idle.copy(agentId = "m:h", herdr = HerdrInfo(paneId = "p", workspaceId = "w"))
        setChatContent(ChatUiState(agent = herdrAgent), text = "/")
        rule.onNodeWithText("⚡ Comando Pi (/)").assertIsDisplayed()
        rule.onAllNodesWithText("Entrega: Automático").assertCountEquals(0)
    }

    @Test
    fun `working agent surfaces queue and steer chips with exact labels`() {
        var steer: String? = null
        setChatContent(
            ChatUiState(agent = idle.copy(status = "working"), workingDelivery = dev.j3pf.rp.protocol.Delivery.FOLLOW_UP),
            text = "",
            onSteer = { steer = it },
        )
        rule.onNodeWithText("Encolar (follow‑up)").assertIsDisplayed()
        rule.onNodeWithText("Interrumpir y dirigir (steer)").assertIsDisplayed()
        rule.onNodeWithText("Encolar (follow‑up)").performClick()
        assertEquals(dev.j3pf.rp.protocol.Delivery.FOLLOW_UP, steer)
    }

    // ───────────────────────── CH20 banner ─────────────────────────

    @Test
    fun `terminal wait banner renders exact text with dialog title`() {
        setChatContent(
            ChatUiState(agent = idle, chat = ChatState(terminalWait = TerminalWaitInfo(kind = "input", title = "git push"))),
            text = "",
        )
        rule.onNodeWithText("Esperando en la terminal (diálogo no remoto): git push").assertIsDisplayed()
    }

    // ───────────────────────── CH27 attachment chips ─────────────────────────

    @Test
    fun `attachment chips surface name state error and exact failure text`() {
        rule.setContent {
            RpTheme {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttachmentChip(
                        name = "informe.pdf",
                        size = 1024L,
                        state = AttachmentUploadState.DONE,
                        progress = 1f,
                        error = null,
                        onRemove = {},
                        onRetry = {},
                    )
                    AttachmentChip(
                        name = "video.mov",
                        size = 999L,
                        state = AttachmentUploadState.ERROR,
                        progress = 0f,
                        error = "Supera 25 MB",
                        onRemove = {},
                        onRetry = {},
                    )
                }
            }
        }
        rule.onNodeWithText("informe.pdf", substring = true).assertIsDisplayed()
        rule.onNodeWithText("video.mov", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Supera 25 MB").assertIsDisplayed()
    }

    // ───────────────────────── CH28 branch chip ─────────────────────────

    @Test
    fun `branch chip shows the dirty marker and opens the diff sheet`() {
        var opened = false
        setChatContent(
            ChatUiState(
                agent = idle,
                diffResult = HostGitDiffResult(branch = "main", files = listOf(dev.j3pf.rp.protocol.HostGitDiffFile(path = "x.kt", status = "M"))),
            ),
            text = "",
            onOpenDiff = { opened = true },
        )
        rule.onNodeWithText("⎇ main *").assertIsDisplayed()
        rule.onNodeWithText("⎇ main *").performClick()
        assertTrue(opened, "La chip de rama abre la hoja de diff")
    }

    // ───────────────────────── CH37 header sanitization ─────────────────────────

    @Test
    fun `agent titles resolve from session name, cwd basename or 8-char id`() {
        assertEquals("tarea", appAgentTitle("tarea", "/home/user/proyecto", "m:abcdefghij"))
        assertEquals("proyecto", appAgentTitle(null, "/home/user/proyecto", "m:abcdefghij"))
        assertEquals("abcdefgh", appAgentTitle(null, null, "m:abcdefghijk"))
        assertTrue(!sharedAgentTitle(null, null, "m:abcdefghijk").contains("-"), "No hay UUID crudo en títulos")
    }
}
