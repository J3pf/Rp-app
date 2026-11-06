package dev.j3pf.rp.ui.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.HostGitDiffFile
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.ui.chat.DiffSheet as AppDiffSheet
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization for parity row CH29/CH31 partials: the Android `:app` wrapper
 * `dev.j3pf.rp.ui.chat.DiffSheet` must wire the SHARED sheet (dev.j3pf.rp.ui.diff.DiffSheet)
 * with the app's exact resources: title `R.string.diff_title` = "Revisión de cambios" and
 * discard `R.string.diff_discard` = "Descartar". The discard confirmation (S19) must survive
 * the wrapper untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DiffSheetWrapperTest {
    @get:Rule val rule = createComposeRule()

    private val filePath = "app/src/main/java/dev/j3pf/rp/ui/chat/ChatScreen.kt"
    private val confirmQuestion = "¿Seguro que deseas descartar los cambios en '$filePath'?"

    private val diff =
        HostGitDiffResult(
            branch = "main",
            files =
                listOf(
                    HostGitDiffFile(path = filePath, status = "M", additions = 12, deletions = 3, patch = "+padding"),
                ),
        )

    @Test
    fun `wrapper renders shared sheet with exact resource strings`() {
        rule.setContent {
            AppDiffSheet(
                diff = diff,
                selectedFile = filePath,
                isLoading = false,
                onSelectFile = {},
                onDiscardFile = {},
                onRequestAdjustment = {},
                onDismiss = {},
            )
        }

        rule.onNodeWithText("Revisión de cambios").assertIsDisplayed()
        rule.onNodeWithText("Descartar").assertIsDisplayed()
        rule.onNodeWithText("Pedir ajuste").assertIsDisplayed()
    }

    @Test
    fun `wrapper keeps the S19 discard confirmation and passes the exact path through`() {
        val discarded = AtomicInteger(0)
        val captured = mutableListOf<String>()
        rule.setContent {
            AppDiffSheet(
                diff = diff,
                selectedFile = filePath,
                isLoading = false,
                onSelectFile = {},
                onDiscardFile = {
                    discarded.incrementAndGet()
                    captured += it
                },
                onRequestAdjustment = {},
                onDismiss = {},
            )
        }

        rule.onNodeWithText("Descartar").performClick()
        val buttons = rule.onAllNodesWithText("Descartar")
        assertEquals(3, buttons.fetchSemanticsNodes().size)
        buttons[2].performClick()

        assertEquals("El wrapper no puede saltarse la confirmación (S19)", 1, discarded.get())
        assertEquals(listOf(filePath), captured)
        rule.onAllNodesWithText(confirmQuestion).assertCountEquals(0)
    }
}
