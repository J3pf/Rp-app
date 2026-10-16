package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatBottomAnchorTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `open chat lands on newest content with bottom visible`() {
        val messages =
            (1..20).map { i ->
                UserMsg(key = "u-$i", text = "Mensaje previo #$i", at = 1000L + i)
            } +
                listOf(
                    AssistantMsg(
                        key = "latest-msg",
                        blocks = listOf(Block.Text("Este es el mensaje más reciente y su fondo está visible.")),
                        at = 2000L,
                    ),
                )

        val uiState = ChatUiState(chat = ChatState(items = messages))

        rule.setContent {
            RpTheme {
                MessageList(
                    ui = uiState,
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _, _ -> },
                )
            }
        }

        // El mensaje más reciente debe ser visible inmediatamente al abrir
        rule.onNodeWithText("Este es el mensaje más reciente y su fondo está visible.").assertIsDisplayed()
    }

    @Test
    fun `tall streaming message keeps bottom visible`() {
        val tallHistory =
            (1..20).map { i ->
                Block.Text("Bloque de streaming previo #$i con contenido que llena la pantalla.")
            }
        val latestBlock = Block.Text("FINAL DEL STREAMING - Fondo visible")

        val streamingMsg =
            AssistantMsg(
                key = "streaming-1",
                blocks = tallHistory + latestBlock,
                streaming = true,
                at = 3000L,
            )

        val uiState = ChatUiState(chat = ChatState(streaming = streamingMsg))

        rule.setContent {
            RpTheme {
                MessageList(
                    ui = uiState,
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _, _ -> },
                )
            }
        }

        // El bloque final del mensaje en streaming debe ser visible al fondo de la pantalla
        rule.onNodeWithText("FINAL DEL STREAMING - Fondo visible").assertIsDisplayed()
    }

    @Test
    fun `scrolled up user sees jump to latest button with unread count`() {
        val initialMessages =
            (1..25).map { i ->
                UserMsg(key = "msg-$i", text = "Historial mensaje #$i", at = 1000L + i)
            }

        val chatState = mutableStateOf(ChatState(items = initialMessages))

        rule.setContent {
            RpTheme {
                MessageList(
                    ui = ChatUiState(chat = chatState.value),
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = null,
                    onAnswerPrompt = { _, _ -> },
                )
            }
        }

        // El usuario hace scroll hacia arriba en el historial (swipeDown desplaza la lista hacia arriba en reverseLayout)
        rule.onNodeWithText("Historial mensaje #25").assertIsDisplayed()
        rule.onNodeWithText("Historial mensaje #25").performTouchInput {
            swipeDown()
        }

        // Se agregan nuevos mensajes mientras el usuario está leyendo arriba
        val updatedMessages =
            initialMessages +
                listOf(
                    UserMsg(key = "new-1", text = "Nuevo mensaje en vivo 1", at = 5000L),
                    UserMsg(key = "new-2", text = "Nuevo mensaje en vivo 2", at = 5001L),
                )
        chatState.value = ChatState(items = updatedMessages)
        rule.waitForIdle()

        // El botón para saltar al más reciente debe aparecer con el contador de nuevos
        rule.onNodeWithText("Más recientes", substring = true).assertIsDisplayed()

        // Al pulsar el botón, vuelve a la parte inferior
        rule.onNodeWithText("Más recientes", substring = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Nuevo mensaje en vivo 2").assertIsDisplayed()
    }
}
