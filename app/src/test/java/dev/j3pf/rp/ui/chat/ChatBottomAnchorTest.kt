package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatItem
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatBottomAnchorTest {
    @get:Rule val rule = createComposeRule()

    private val sampleAgent =
        AgentSummary(
            agentId = "m:test",
            machineId = "m",
            sessionName = "refactor-session",
            status = "working",
            online = true,
        )

    @Test
    fun `open chat lands on newest content with bottom visible above composer`() {
        val messages: List<ChatItem> =
            (1..20).map { i ->
                UserMsg(key = "u-$i", text = "Mensaje histórico #$i", at = 1000L + i) as ChatItem
            } +
                listOf(
                    AssistantMsg(
                        key = "latest-msg",
                        blocks =
                            listOf(
                                Block.Text("Línea 1 del mensaje más reciente."),
                                Block.Text("Línea final del mensaje abierto que debe quedar visible."),
                            ),
                        at = 2000L,
                    ),
                )

        val uiState = ChatUiState(agent = sampleAgent, chat = ChatState(items = messages))

        rule.setContent {
            RpTheme {
                ChatContent(
                    ui = uiState,
                    text = "",
                    onText = {},
                    onBack = {},
                    onSend = {},
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { android.net.Uri.EMPTY },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val composerTop =
            rule
                .onNodeWithContentDescription("Interrumpir")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val lastLineBounds =
            rule
                .onNodeWithText(
                    "Línea final del mensaje abierto que debe quedar visible.",
                ).fetchSemanticsNode()
                .boundsInRoot

        assertTrue("El fondo del último elemento debe ser > 0", lastLineBounds.bottom > 0f)
        assertTrue(
            "El fondo del último elemento (${lastLineBounds.bottom}) debe ser <= composerTop ($composerTop)",
            lastLineBounds.bottom <= composerTop,
        )
    }

    @Test
    fun `tall streaming message keeps bottom visible above composer across appends`() {
        val lineCount = 65
        val lines = (1..lineCount).map { "Línea de streaming progresivo #$it con texto extenso para forzar altura." }
        val initialText = lines.joinToString("\n\n")

        var stateHolder by
            mutableStateOf(
                ChatUiState(
                    agent = sampleAgent,
                    chat =
                        ChatState(
                            streaming =
                                AssistantMsg(
                                    key = "streaming-active",
                                    blocks = listOf(Block.Text(initialText)),
                                    streaming = true,
                                ),
                        ),
                ),
            )

        rule.setContent {
            RpTheme {
                ChatContent(
                    ui = stateHolder,
                    text = "",
                    onText = {},
                    onBack = {},
                    onSend = {},
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { android.net.Uri.EMPTY },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val composerTop =
            rule
                .onNodeWithContentDescription("Interrumpir")
                .fetchSemanticsNode()
                .boundsInRoot.top

        // Comprobar que la última línea del texto largo inicial es visible sobre el composer
        val lastLineNode = rule.onNodeWithText("Línea de streaming progresivo #$lineCount", substring = true)
        val initialLastBounds = lastLineNode.fetchSemanticsNode().boundsInRoot
        assertTrue("La última línea inicial debe ser visible (> 0)", initialLastBounds.bottom > 0f)
        assertTrue(
            "La última línea inicial (${initialLastBounds.bottom}) debe quedar por encima del composer ($composerTop)",
            initialLastBounds.bottom <= composerTop,
        )

        // Anexar más texto en streaming progresivo
        val appendedText = initialText + "\n\nLínea 66 TEXTO ANEXADO EN VIVO FINAL"
        stateHolder =
            ChatUiState(
                agent = sampleAgent,
                chat =
                    ChatState(
                        streaming =
                            AssistantMsg(
                                key = "streaming-active",
                                blocks = listOf(Block.Text(appendedText)),
                                streaming = true,
                            ),
                    ),
            )
        rule.waitForIdle()
        rule.waitUntil(5000L) {
            rule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Línea 66 TEXTO ANEXADO EN VIVO FINAL"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Re-verificar que la nueva línea final sigue visible sobre el composer sin desplazamiento manual
        val appendedLastNode = rule.onNodeWithText("Línea 66 TEXTO ANEXADO EN VIVO FINAL")
        val appendedBounds = appendedLastNode.fetchSemanticsNode().boundsInRoot
        assertTrue("El texto anexado debe ser visible (> 0)", appendedBounds.bottom > 0f)
        assertTrue(
            "El texto anexado (${appendedBounds.bottom}) debe quedar sobre el composer ($composerTop)",
            appendedBounds.bottom <= composerTop,
        )
    }

    @Test
    fun `two consecutive chat items bounds do not overlap`() {
        val userText = "Pregunta del usuario sobre el diseño"
        val assistantText = "Respuesta detallada con bloque de código y explicación técnica."
        val messages =
            listOf(
                UserMsg(key = "u-overlap", text = userText, at = 1000L),
                AssistantMsg(
                    key = "a-overlap",
                    blocks =
                        listOf(
                            Block.Thinking("Pensando sobre la estructura..."),
                            Block.Text(assistantText),
                        ),
                    at = 2000L,
                ),
            )

        val uiState = ChatUiState(agent = sampleAgent, chat = ChatState(items = messages))

        rule.setContent {
            RpTheme {
                ChatContent(
                    ui = uiState,
                    text = "",
                    onText = {},
                    onBack = {},
                    onSend = {},
                    onChoice = {},
                    onWorkingDelivery = {},
                    onPicked = {},
                    onRemoveAttachment = {},
                    newCameraTarget = { android.net.Uri.EMPTY },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val userBounds = rule.onNodeWithText(userText).fetchSemanticsNode().boundsInRoot
        val assistantBounds = rule.onNodeWithText(assistantText).fetchSemanticsNode().boundsInRoot

        // En la lista de mensajes, el mensaje de usuario anterior se dibuja arriba del asistente
        assertTrue(
            "El fondo del mensaje de usuario (${userBounds.bottom}) debe ser menor o igual que la cima del asistente (${assistantBounds.top}) sin solapamiento",
            userBounds.bottom <= assistantBounds.top,
        )
    }

    @Test
    fun `scrolled up user sees jump to latest button with unread count`() {
        val initialMessages: List<ChatItem> =
            (1..40).map { i ->
                UserMsg(key = "msg-$i", text = "Historial mensaje #$i", at = 1000L + i) as ChatItem
            }

        var currentChatState by mutableStateOf(ChatState(items = initialMessages))

        rule.setContent {
            RpTheme {
                MessageList(
                    ui = ChatUiState(chat = currentChatState),
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _: dev.j3pf.rp.protocol.Prompt, _: dev.j3pf.rp.protocol.Answer -> },
                )
            }
        }

        // El usuario hace scroll hacia arriba en el historial
        rule.onNodeWithText("Historial mensaje #40").assertIsDisplayed()
        rule.onRoot().performTouchInput {
            swipeDown()
        }

        // Se agregan nuevos mensajes mientras el usuario está leyendo arriba
        val newItems: List<ChatItem> =
            listOf(
                UserMsg(key = "new-1", text = "Nuevo mensaje en vivo 1", at = 5000L),
                UserMsg(key = "new-2", text = "Nuevo mensaje en vivo 2", at = 5001L),
            )
        val updatedMessages =
            buildList<ChatItem> {
                addAll(initialMessages)
                addAll(newItems)
            }
        currentChatState = ChatState(items = updatedMessages)
        rule.waitForIdle()

        // El botón para saltar al más reciente debe aparecer con el contador de nuevos
        rule.onNodeWithText("Más recientes (2)").assertIsDisplayed()

        // Al pulsar el botón, vuelve a la parte inferior
        rule.onNodeWithText("Más recientes (2)").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Nuevo mensaje en vivo 2").assertIsDisplayed()
    }

    @Test
    fun `bottom anchored user auto-scrolls when multiple new messages arrive at once without showing jump pill`() {
        val initialMessages: List<ChatItem> =
            (1..40).map { i ->
                UserMsg(key = "msg-$i", text = "Historial mensaje #$i", at = 1000L + i) as ChatItem
            }

        val idleAgent = sampleAgent.copy(status = "done")
        var currentAgent by mutableStateOf(idleAgent)
        var currentChatState by mutableStateOf(ChatState(items = initialMessages))
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState

        rule.setContent {
            listState = androidx.compose.foundation.lazy.rememberLazyListState()
            RpTheme {
                MessageList(
                    ui = ChatUiState(agent = currentAgent, chat = currentChatState),
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _: dev.j3pf.rp.protocol.Prompt, _: dev.j3pf.rp.protocol.Answer -> },
                    listState = listState,
                )
            }
        }

        rule.onNodeWithText("Historial mensaje #40").assertIsDisplayed()
        // El usuario sigue en la zona inferior (offset <= 48px) mientras el chat estaba en "done"
        rule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(0, 24)
            }
        }
        rule.waitForIdle()
        assertTrue(
            "Con offset=24px en el fondo, el botón 'Más recientes' aún no debe mostrarse",
            rule.onAllNodesWithText("Más recientes", substring = true).fetchSemanticsNodes().isEmpty(),
        )

        // El agente pasa a working (inserta working-indicator en index 0) y llegan 3 elementos nuevos de golpe
        currentAgent = sampleAgent
        val batchMessages: List<ChatItem> =
            listOf(
                UserMsg(key = "batch-1", text = "si adelante", at = 6000L),
                AssistantMsg(
                    key = "batch-2",
                    blocks = listOf(Block.Thinking("Preparando comando en background...")),
                    at = 6001L,
                ),
                AssistantMsg(
                    key = "batch-3",
                    blocks = listOf(Block.Text("Lancé en background el comando y ya quedó corriendo.")),
                    at = 6002L,
                ),
            )
        currentChatState = ChatState(items = initialMessages + batchMessages)
        rule.waitForIdle()
        rule.waitUntil(5000L) {
            rule
                .onAllNodesWithText("Lancé en background el comando y ya quedó corriendo.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        rule.onNodeWithText("Lancé en background el comando y ya quedó corriendo.").assertIsDisplayed()
        assertTrue(
            "No debe aparecer el botón 'Más recientes' cuando el usuario ya estaba abajo",
            rule.onAllNodesWithText("Más recientes", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `failed user message shows error indicator and retry action that invokes callback`() {
        val failedMsg =
            UserMsg(
                key = "local:failed-1",
                text = "Mensaje que falló",
                failed = true,
                clientMsgId = "failed-1",
            )
        var retriedMsg: UserMsg? = null

        rule.setContent {
            RpTheme {
                MessageList(
                    ui = ChatUiState(chat = ChatState(items = listOf(failedMsg))),
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _: dev.j3pf.rp.protocol.Prompt, _: dev.j3pf.rp.protocol.Answer -> },
                    onRetryUserMessage = { retriedMsg = it },
                )
            }
        }

        // Visible failed indicator & message text
        rule.onNodeWithText("Error al enviar").assertIsDisplayed()
        rule.onNodeWithText("Mensaje que falló").assertIsDisplayed()

        // Visible retry action
        rule.onNodeWithText("Reintentar").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        rule.waitForIdle()
        assertEquals(failedMsg, retriedMsg)
    }

    @Test
    fun `jump pill click stays pinned to bottom when streaming delta arrives mid-scroll`() {
        val initialMessages: List<ChatItem> =
            (1..60).map { i ->
                UserMsg(key = "msg-$i", text = "History message #$i", at = 1000L + i) as ChatItem
            }

        var currentChatState by mutableStateOf(ChatState(items = initialMessages))
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState

        rule.setContent {
            listState = androidx.compose.foundation.lazy.rememberLazyListState()
            RpTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    MessageList(
                        ui = ChatUiState(chat = currentChatState),
                        modifier = Modifier.fillMaxSize(),
                        busyPromptId = null,
                        onAnswerPrompt = { _: dev.j3pf.rp.protocol.Prompt, _: dev.j3pf.rp.protocol.Answer -> },
                        listState = listState,
                    )
                }
            }
        }

        rule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(30)
            }
        }
        rule.waitForIdle()

        currentChatState =
            ChatState(
                items = initialMessages,
                streaming =
                    AssistantMsg(
                        key = "stream-1",
                        blocks = listOf(Block.Text("Streaming chunk 1")),
                        streaming = true,
                    ),
            )
        rule.waitForIdle()
        rule.onNodeWithText("Más recientes (1)").assertIsDisplayed()

        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("Más recientes (1)").performClick()
            repeat(2) {
                rule.mainClock.advanceTimeByFrame()
            }

            // Streaming text delta arrives while animateScrollToItem(0) is still mid-flight.
            currentChatState =
                ChatState(
                    items = initialMessages,
                    streaming =
                        AssistantMsg(
                            key = "stream-1",
                            blocks = listOf(Block.Text("Streaming chunk 1 + delta 2")),
                            streaming = true,
                        ),
                )
            repeat(5) {
                rule.mainClock.advanceTimeByFrame()
                assertTrue(
                    "Jump pill must stay hidden while animating back to bottom",
                    rule.onAllNodesWithText("Más recientes", substring = true).fetchSemanticsNodes().isEmpty(),
                )
            }
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
        } finally {
            rule.mainClock.autoAdvance = true
        }

        rule.onNodeWithText("Streaming chunk 1 + delta 2").assertIsDisplayed()
        assertTrue(
            "Jump pill must remain hidden after reaching bottom",
            rule.onAllNodesWithText("Más recientes", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `prepending older history at bottom does not show phantom unread count on later scroll up`() {
        val initialMessages: List<ChatItem> =
            (10..50).map { i ->
                UserMsg(key = "msg-$i", text = "History message #$i", at = 1000L + i) as ChatItem
            }

        var currentChatState by mutableStateOf(ChatState(items = initialMessages))

        rule.setContent {
            RpTheme {
                MessageList(
                    ui = ChatUiState(chat = currentChatState),
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _: dev.j3pf.rp.protocol.Prompt, _: dev.j3pf.rp.protocol.Answer -> },
                )
            }
        }

        rule.onNodeWithText("History message #50").assertIsDisplayed()

        val olderHistory: List<ChatItem> =
            (1..5).map { i ->
                UserMsg(key = "old-$i", text = "Older message #$i", at = 100L + i) as ChatItem
            }
        currentChatState = ChatState(items = olderHistory + initialMessages)
        rule.waitForIdle()

        rule.onRoot().performTouchInput {
            swipeDown()
        }
        rule.waitForIdle()

        rule.onNodeWithText("Más recientes").assertIsDisplayed()
        assertTrue(
            "Must not display phantom unread count from prepended older history",
            rule.onAllNodesWithText("Más recientes (5)").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `clearing empty search filter keeps hoisted scrolled position and jump pill`() {
        val initialMessages: List<ChatItem> =
            (1..40).map { i ->
                UserMsg(key = "msg-$i", text = "History message #$i", at = 1000L + i) as ChatItem
            }

        var searchQuery by mutableStateOf("")
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState

        rule.setContent {
            listState = androidx.compose.foundation.lazy.rememberLazyListState()
            RpTheme {
                MessageList(
                    ui = ChatUiState(chat = ChatState(items = initialMessages)),
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    searchQuery = searchQuery,
                    onAnswerPrompt = { _: dev.j3pf.rp.protocol.Prompt, _: dev.j3pf.rp.protocol.Answer -> },
                    listState = listState,
                )
            }
        }

        rule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(20)
            }
        }
        rule.waitForIdle()
        val scrolledIndexBeforeFilter = listState.firstVisibleItemIndex
        assertTrue("Expected list to stay scrolled up before filtering", scrolledIndexBeforeFilter > 0)
        rule.onNodeWithText("Más recientes").assertIsDisplayed()

        searchQuery = "no-match-anywhere"
        rule.waitForIdle()
        rule.onNodeWithText("Sin coincidencias").assertIsDisplayed()

        searchQuery = ""
        rule.waitForIdle()
        assertEquals(scrolledIndexBeforeFilter, listState.firstVisibleItemIndex)
        rule.onNodeWithText("Más recientes").assertIsDisplayed()
    }
}
