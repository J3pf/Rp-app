package dev.j3pf.rp.ui.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.HostGitDiffFile
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.ui.theme.RpTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure-UI characterization of the chat top bar overflow menu, the /quit dialog and the
 * message-spacing / font-scale controls, with EXACT user-visible literals. The test drives
 * `ChatContent` (the same public entry point that hosted these literals before the M1
 * stateless-composer move and still delegates to them after it), so it is green on both the
 * pre-move and post-move trees and pins the strings against mutation while the code moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatTopBarLiteralsCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    private val online =
        AgentSummary(
            agentId = "m:t",
            machineId = "m",
            sessionName = "tarea",
            cwd = "/home/user/project",
            gitBranch = "main",
            status = "idle",
            online = true,
            launcher = "pi",
            gentleMode = "auto",
        )

    private fun setContent(
        ui: ChatUiState,
        onQuit: () -> Unit = {},
        onForget: () -> Unit = {},
        onToggleMute: () -> Unit = {},
        onFontScale: (Float) -> Unit = {},
        onSpacing: (String) -> Unit = {},
        onOpenDiff: () -> Unit = {},
        onAbort: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        rule.setContent {
            RpTheme {
                ChatContent(
                    ui = ui,
                    text = "",
                    onText = {},
                    onBack = onBack,
                    onAbort = onAbort,
                    onOpenDiff = onOpenDiff,
                    onToggleMute = onToggleMute,
                    onFontScaleChange = onFontScale,
                    onMessageSpacingChange = onSpacing,
                    onQuit = onQuit,
                    onForget = onForget,
                )
            }
        }
    }

    private fun openMenu() {
        rule.onNodeWithContentDescription("Menú").performClick()
        rule.waitForIdle()
    }

    // ───────────────────────── overflow menu literals ─────────────────────────

    @Test
    fun `online agent menus shows the exact top bar overflow literals`() {
        setContent(ChatUiState(agent = online))
        rule.onNodeWithContentDescription("Menú").assertExists()
        rule.onNodeWithContentDescription("Atrás").assertExists()
        openMenu()
        rule.onNodeWithText("Revisar cambios Git (Diff)").assertExists()
        rule.onNodeWithText("Letra (100%)").assertExists()
        rule.onNodeWithText("Espaciado de mensajes").assertExists()
        rule.onNodeWithText("Compacto").assertExists()
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithText("Amplio").assertExists()
        rule.onNodeWithText("Información").assertExists()
        rule.onNodeWithText("Silenciar notificaciones del agente").assertExists()
        rule.onNodeWithText("Cerrar agente (/quit)").assertExists()
    }

    @Test
    fun `muted agent menus shows the unmute literal`() {
        setContent(ChatUiState(agent = online, muted = true))
        openMenu()
        rule.onNodeWithText("Activar notificaciones del agente").assertExists()
    }

    @Test
    fun `offline agent menus shows forget and never quit`() {
        setContent(ChatUiState(agent = online.copy(online = false)))
        openMenu()
        rule.onNodeWithText("Olvidar agente").assertExists()
        rule.onAllNodesWithText("Cerrar agente (/quit)").assertCountEquals(0)
    }

    @Test
    fun `readOnly agent hides quit and forget items`() {
        setContent(
            ChatUiState(agent = online, readOnly = true),
            onForget = { error("forbidden") },
            onQuit = { error("forbidden") },
        )
        openMenu()
        rule.onAllNodesWithText("Cerrar agente (/quit)").assertCountEquals(0)
        rule.onAllNodesWithText("Olvidar agente").assertCountEquals(0)
    }

    // ───────────────────────── /quit dialog literals + behavior ─────────────────────────

    @Test
    fun `quit dialog closes the agent with the exact strings and confirm callback exactly once`() {
        var quits = 0
        setContent(ChatUiState(agent = online), onQuit = { quits++ })
        openMenu()
        rule.onNodeWithText("Cerrar agente (/quit)").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cerrar sesión de Pi").assertExists()
        rule.onNodeWithText("¿Deseas enviar /quit al agente para cerrar su proceso en la terminal de la PC?").assertExists()
        assertEquals(0, quits)
        rule.onNodeWithText("Cerrar agente").performClick()
        rule.waitForIdle()
        assertEquals(1, quits)
        rule.onAllNodesWithText("Cerrar sesión de Pi").assertCountEquals(0)
    }

    @Test
    fun `quit dialog cancel keeps the agent alive`() {
        var quits = 0
        setContent(ChatUiState(agent = online), onQuit = { quits++ })
        openMenu()
        rule.onNodeWithText("Cerrar agente (/quit)").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()
        assertEquals(0, quits)
        rule.onAllNodesWithText("Cerrar sesión de Pi").assertCountEquals(0)
    }

    @Test
    fun `forget menu item calls forget exactly once for offline agent`() {
        var forgets = 0
        setContent(ChatUiState(agent = online.copy(online = false)), onForget = { forgets++ })
        openMenu()
        rule.onNodeWithText("Olvidar agente").performClick()
        rule.waitForIdle()
        assertEquals(1, forgets)
    }

    // ───────────────────────── message spacing ids + font scale ─────────────────────────

    @Test
    fun `spacing chips report the exact shipped ids`() {
        var picked: String? = null
        setContent(ChatUiState(agent = online), onSpacing = { picked = it })
        openMenu()
        rule.onNodeWithText("Compacto").performClick()
        rule.waitForIdle()
        assertEquals("compact", picked)
        rule.onNodeWithText("Amplio").performClick()
        rule.waitForIdle()
        assertEquals("spacious", picked)
        rule.onNodeWithText("Normal").performClick()
        rule.waitForIdle()
        assertEquals("normal", picked)
    }

    @Test
    fun `font scale buttons report exact values at 100`() {
        var scale = 0f
        setContent(ChatUiState(agent = online, chatFontScale = 1.0f), onFontScale = { scale = it })
        openMenu()
        rule.onNodeWithText("Letra (100%)").assertExists()
        rule.onNodeWithText("A+").performClick()
        rule.waitForIdle()
        assertEquals(1.1f, scale)
        rule.onNodeWithText("A-").performClick()
        rule.waitForIdle()
        assertEquals(0.9f, scale)
    }

    @Test
    fun `font scale offcenter reset button reports exactly 1`() {
        var reset = 0f
        setContent(ChatUiState(agent = online, chatFontScale = 1.05f), onFontScale = { reset = it })
        openMenu()
        // The shipped label applies `(1.05f * 100).toInt()` in Float math: 1.05f ≈ 1.04999995,
        // so today's user-visible literal is "Letra (104%)" — pinned as-is (no production fix).
        rule.onNodeWithText("Letra (104%)").assertExists()
        rule.onNodeWithText("100%").performClick()
        rule.waitForIdle()
        assertEquals(1.0f, reset)
    }

    @Test
    fun `font scale A+ clamps at the 1 45 ceiling`() {
        var clamped = 0f
        setContent(ChatUiState(agent = online, chatFontScale = 1.40f), onFontScale = { clamped = it })
        openMenu()
        rule.onNodeWithText("A+").performClick()
        rule.waitForIdle()
        assertEquals(1.45f, clamped)
    }

    // ───────────────────────── cond. chrome: abort, diff chip, branch ─────────────────────────

    @Test
    fun `working agent shows interrupt and diff chip with exact literals`() {
        var opened = 0
        setContent(
            ChatUiState(
                agent = online.copy(status = "working"),
                diffResult =
                    HostGitDiffResult(
                        branch = "main",
                        files = listOf(HostGitDiffFile(path = "a.kt", status = "M")),
                    ),
            ),
            onOpenDiff = { opened++ },
        )
        rule.onNodeWithContentDescription("Interrumpir").assertExists()
        rule.onNodeWithText("Diff (1)").performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
        openMenu()
        rule.onNodeWithText("Revisar cambios Git (Diff)").performClick()
        rule.waitForIdle()
        assertEquals(2, opened)
    }

    @Test
    fun `branch chip shows branch with dirty marker and opens the diff`() {
        var opened = 0
        setContent(
            ChatUiState(
                agent = online.copy(gitBranch = "feature"),
                diffResult =
                    HostGitDiffResult(
                        branch = "feature",
                        files = listOf(HostGitDiffFile(path = "a.kt", status = "M")),
                    ),
            ),
            onOpenDiff = { opened++ },
        )
        rule.onNodeWithText("⎇ feature *").assertExists()
        rule.onNodeWithText("⎇ feature *").performClick()
        rule.waitForIdle()
        assertEquals(1, opened)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExists() = assertExists()
