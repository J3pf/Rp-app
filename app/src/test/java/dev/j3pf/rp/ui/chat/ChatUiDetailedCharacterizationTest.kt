package dev.j3pf.rp.ui.chat

import androidx.compose.ui.test.assertCountEquals

import androidx.compose.ui.test.assertIsDisplayed

import androidx.compose.ui.test.junit4.v2.createComposeRule

import androidx.compose.ui.test.onAllNodesWithText

import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.runtime.Composable

import androidx.compose.ui.test.performClick

import androidx.compose.ui.text.font.FontWeight

import dev.j3pf.rp.chat.AssistantMsg

import dev.j3pf.rp.chat.ChatItem

import dev.j3pf.rp.chat.ChatState

import dev.j3pf.rp.chat.ToolRun

import dev.j3pf.rp.chat.ToolStatus

import dev.j3pf.rp.chat.UserMsg

import dev.j3pf.rp.protocol.AgentSummary

import dev.j3pf.rp.chat.Block

import dev.j3pf.rp.protocol.RpJson

import dev.j3pf.rp.ui.theme.RpTheme

import kotlin.test.assertTrue

import org.junit.Assert.assertEquals

import org.junit.Rule

import org.junit.Test

import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner

import org.robolectric.annotation.Config

/**

 * Rev-3 additions to the pure-UI characterization (audit findings 1-3): QA C1 reasoning

 * collapse (CH11), C3 móvil marker (CH12), C5 AUTO chip (CH23), header model/thinking label

 * (CH43), edit diff inside the tool card (CH34), and the density bounds mapping (CH45).

 * All green-first on UNCHANGED code; exact strings and observable effects only.

 */

@RunWith(RobolectricTestRunner::class)

@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")

class ChatUiDetailedCharacterizationTest {

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

    private fun setAssistant(assistant: AssistantMsg) {

        rule.setContent {

            RpTheme { MessageList(displayItems = listOf(assistant as ChatItem)) }

        }

    }

    private fun setMessages(items: List<ChatItem>, spacing: String = "normal", fontScale: Float = 1.0f) {

        rule.setContent {

            RpTheme { MessageList(displayItems = items, chatMessageSpacing = spacing, chatFontScale = fontScale) }

        }

    }

    private fun setChatContent(ui: ChatUiState, text: String = "") {

        rule.setContent {

            RpTheme {

                ChatContent(

                    ui = ui,

                    text = text,

                    onText = {},

                    onSend = {},

                    onChoice = {},

                    onWorkingDelivery = {},

                    onPicked = {},

                    onRemoveAttachment = {},

                    newCameraTarget = { android.net.Uri.EMPTY },

                )

            }

        }

    }

    // ──────────────── CH11 reasoning collapse (QA C1) ────────────────

    @Test

    fun `thinking block renders expanded by default with the exact label and collapses on tap`() {

        val assistant =

            AssistantMsg(

                key = "t1",

                blocks =

                    listOf(

                        Block.Thinking("estoy pensando el enfoque"),

                        Block.Text("respuesta visible"),

                    ),

            )

        setAssistant(assistant)

        rule.onNodeWithText("Razonamiento").assertIsDisplayed()

        rule.onNodeWithText("estoy pensando el enfoque").assertIsDisplayed()

        rule.onNodeWithText("Razonamiento").performClick()

        rule.onAllNodesWithText("estoy pensando el enfoque").assertCountEquals(0)

        rule.onNodeWithText("respuesta visible").assertIsDisplayed()

    }

    // ──────────────── CH12 móvil marker (QA C3) ────────────────

    @Test

    fun `from-mobile user message shows the exact movil marker and others do not`() {

        val mine = UserMsg(key = "u1", text = "desde el móvil", at = 1L, fromMobile = true)

        val theirs = UserMsg(key = "u2", text = "otro saludo", at = 2L)

        setMessages(listOf(mine, theirs))

        rule.onNodeWithText("desde el móvil").assertIsDisplayed()

        rule.onAllNodesWithText("móvil").assertCountEquals(1)

    }

    // ──────────────── CH23 AUTO chip (QA C5) ────────────────

    @Test

    fun `gentle auto agent shows the exact AUTO chip`() {

        setChatContent(ChatUiState(agent = idle))

        rule.onNodeWithText("AUTO · tus mensajes se envían como tecleados").assertIsDisplayed()

    }

    @Test

    fun `normal agent hides the AUTO chip`() {

        setChatContent(ChatUiState(agent = idle.copy(gentleMode = "normal")))

        rule.onAllNodesWithText("AUTO · tus mensajes se envían como tecleados").assertCountEquals(0)

    }

    // ──────────────── CH43 header model · thinking label ────────────────

    @Test

    fun `header subtitle joins folder model and thinking level`() {

        setChatContent(ChatUiState(agent = idle, chat = ChatState(model = "gpt", thinkingLevel = "medium")))

        rule.onNodeWithText("project · gpt · medium").assertIsDisplayed()

    }

    // ──────────────── CH34 edit diff inside the tool card ────────────────

    @Config(sdk = [34], qualifiers = "w891dp-h1400dp-xxhdpi")
    @Test

    fun `edit tool card shows the diff with minus and plus lines when expanded`() {

        val assistant =

            AssistantMsg(

                key = "e1",

                blocks =

                    listOf(

                        Block.ToolCall(

                            toolCallId = "e-1",

                            name = "edit",

                            args = RpJson.parseToJsonElement("""{"path":"MainActivity.kt","oldText":"old line","newText":"new line"}"""),

                        ),

                    ),

            )

        rule.setContent {

            RpTheme {

                ChatItemView(

                    item = assistant,

                    tools =

                        mapOf(

                            "e-1" to ToolRun(
                                toolCallId = "e-1",
                                name = "edit",
                                args = RpJson.parseToJsonElement("""{"path":"MainActivity.kt","oldText":"old line","newText":"new line"}"""),
                                status = ToolStatus.OK,
                            ),

                        ),

                    busyPromptId = null,

                    onAnswerPrompt = { _, _ -> },

                )

            }

        }

        rule.onNodeWithText("edit").assertIsDisplayed()

        rule.onNodeWithText("edit").performClick()

        rule.waitForIdle()
        rule.onAllNodesWithText("").fetchSemanticsNodes().forEach { n ->
            val list = n.config.get(androidx.compose.ui.semantics.SemanticsProperties.Text)
        }

        rule.onNodeWithText("- old line").assertIsDisplayed()

        rule.onNodeWithText("+ new line").assertIsDisplayed()

    }

    // ──────────────── CH45 density bounds mapping ────────────────

    @Test

    fun `message spacing modes are strictly increasing compact normal spacious`() {

        // Tres listas apiladas, cada una de altura fija; el orden de composición fija el índice.

        rule.setContent {

            RpTheme {

                androidx.compose.foundation.layout.Column {

                    androidx.compose.foundation.layout.Box(

                        modifier = androidx.compose.ui.Modifier

                            .fillMaxWidth()

                            .height(180.dp),

                    ) {

                        MessageList(

                            displayItems = listOf(

                                UserMsg(key = "a-c", text = "primero-c", at = 1L),

                                UserMsg(key = "b-c", text = "segundo-c", at = 2L),

                            ),

                            chatMessageSpacing = "compact",

                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),

                        )

                    }

                    androidx.compose.foundation.layout.Box(

                        modifier = androidx.compose.ui.Modifier

                            .fillMaxWidth()

                            .height(180.dp),

                    ) {

                        MessageList(

                            displayItems = listOf(

                                UserMsg(key = "a-n", text = "primero-n", at = 1L),

                                UserMsg(key = "b-n", text = "segundo-n", at = 2L),

                            ),

                            chatMessageSpacing = "normal",

                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),

                        )

                    }

                    androidx.compose.foundation.layout.Box(

                        modifier = androidx.compose.ui.Modifier

                            .fillMaxWidth()

                            .height(200.dp),

                    ) {

                        MessageList(

                            displayItems = listOf(

                                UserMsg(key = "a-s", text = "primero-s", at = 1L),

                                UserMsg(key = "b-s", text = "segundo-s", at = 2L),

                            ),

                            chatMessageSpacing = "spacious",

                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),

                        )

                    }

                }

            }

        }

        fun gap(tag: String): Float {

            val first = rule.onNodeWithText("primero-$tag").fetchSemanticsNode().boundsInRoot

            val second = rule.onNodeWithText("segundo-$tag").fetchSemanticsNode().boundsInRoot

            return second.top - first.bottom

        }

        val compact = gap("c")

        val normal = gap("n")

        val spacious = gap("s")

        assertTrue(compact < normal && normal < spacious, "densities map strictly: compact($compact) < normal($normal) < spacious($spacious)")

    }

}
