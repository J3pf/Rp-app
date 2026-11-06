package dev.j3pf.rp.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import dev.j3pf.rp.protocol.HostGitDiffFile
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.ui.diff.DiffSheet
import dev.j3pf.rp.ui.theme.RpTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for parity rows CH30/CH31 (diff discard confirmation, S19).
 * They MUST stay green on UNCHANGED Android code (T1); a RED result here is either a
 * wrong test or a recorded defect — never a production fix inside T1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DiffDiscardConfirmationTest {
    @get:Rule val rule = createComposeRule()

    private val diff =
        HostGitDiffResult(
            branch = "feat/demo",
            files =
                listOf(
                    HostGitDiffFile(
                        path = "protocol/src/commonMain/kotlin/dev/j3pf/rp/protocol/Models.kt",
                        status = "M",
                        additions = 4,
                        deletions = 2,
                        patch = "@@ -1,2 +1,4 @@\n+line",
                    ),
                ),
            totalAdditions = 4,
            totalDeletions = 2,
        )

    private fun renderSheet(discardCalls: AtomicInteger) {
        rule.setContent {
            RpTheme {
                DiffSheet(
                    diff = diff,
                    selectedFile = diff.files.first().path,
                    isLoading = false,
                    onSelectFile = {},
                    onDiscardFile = { discardCalls.incrementAndGet() },
                    onRequestAdjustment = {},
                    onDismiss = {},
                    title = "Cambios",
                    discardLabel = "Descartar",
                    requestAdjustmentLabel = "Pedir ajuste",
                )
            }
        }
    }

    @Test
    fun `tapping Descartar opens the confirmation dialog and does not discard yet`() {
        val discardCalls = AtomicInteger(0)
        renderSheet(discardCalls)

        rule.onNodeWithText("Descartar").performClick()

        // Confirmation dialog appears with its exact question text, and the file is untouched.
        rule
            .onNodeWithText("¿Seguro que deseas descartar los cambios en 'protocol/src/commonMain/kotlin/dev/j3pf/rp/protocol/Models.kt'?")
            .assertIsDisplayed()
        assertEquals("Sin confirmación no se llama a onDiscardFile (S19)", 0, discardCalls.get())
    }

    @Test
    fun `confirming the dialog discards exactly once with the file path`() {
        val discardCalls = AtomicInteger(0)
        renderSheet(discardCalls)

        rule.onNodeWithText("Descartar").performClick()
        // "Descartar" now matches three nodes: the sheet button, the dialog title, and the
        // dialog confirm button (title first, confirm button last in tree order).
        val discardButtons = rule.onAllNodesWithText("Descartar")
        assertEquals(3, discardButtons.fetchSemanticsNodes().size)
        discardButtons[2].performClick()

        assertEquals("Confirmar descarta exactamente una vez con el path", 1, discardCalls.get())
    }

    @Test
    fun `cancelling the dialog keeps the file intact`() {
        val discardCalls = AtomicInteger(0)
        renderSheet(discardCalls)

        rule.onNodeWithText("Descartar").performClick()
        rule.onNodeWithText("Cancelar").performClick()

        assertTrue("Cancelar no descarta", discardCalls.get() == 0)
    }
}
