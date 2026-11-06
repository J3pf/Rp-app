package dev.j3pf.rp.ui.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.HostGitDiffFile
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.ui.diff.DiffSheet
import dev.j3pf.rp.ui.theme.RpTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization tests for parity rows CH30/CH31 (diff discard confirmation, S19).
 * Green-first on UNCHANGED Android code; every assertion pins exact strings and the
 * observable effect (discard reaches onDiscardFile only through the confirmation dialog,
 * exactly once, with the exact path; cancel/dismiss changes nothing and closes the dialog).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DiffDiscardConfirmationTest {
    @get:Rule val rule = createComposeRule()

    private val filePath = "protocol/src/commonMain/kotlin/dev/j3pf/rp/protocol/Models.kt"
    private val confirmQuestion = "¿Seguro que deseas descartar los cambios en '$filePath'?"

    private val diff =
        HostGitDiffResult(
            branch = "feat/demo",
            files =
                listOf(
                    HostGitDiffFile(path = filePath, status = "M", additions = 4, deletions = 2, patch = "@@ -1,2 +1,4 @@\n+line"),
                ),
            totalAdditions = 4,
            totalDeletions = 2,
        )

    private class DiscardCapture {
        val discardedPaths = mutableListOf<String>()

        fun onDiscardFile(path: String) {
            discardedPaths += path
        }
    }

    private fun renderSheet(discard: DiscardCapture) {
        rule.setContent {
            RpTheme {
                DiffSheet(
                    diff = diff,
                    selectedFile = diff.files.first().path,
                    isLoading = false,
                    onSelectFile = {},
                    onDiscardFile = discard::onDiscardFile,
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
        val discard = DiscardCapture()
        renderSheet(discard)

        rule.onNodeWithText("Descartar").performClick()

        // Confirmation dialog appears with its exact question text, and the file is untouched.
        rule.onNodeWithText(confirmQuestion).assertIsDisplayed()
        assertEquals("Sin confirmación no se llama a onDiscardFile (S19)", 0, discard.discardedPaths.size)
    }

    @Test
    fun `confirming discards exactly once with the exact path and closes the dialog`() {
        val discard = DiscardCapture()
        renderSheet(discard)

        rule.onNodeWithText("Descartar").performClick()
        // "Descartar" now matches three nodes: the sheet button, the dialog title, and the
        // dialog confirm button (title first, confirm button last in tree order).
        val discardButtons = rule.onAllNodesWithText("Descartar")
        assertEquals(3, discardButtons.fetchSemanticsNodes().size)
        discardButtons[2].performClick()

        assertEquals(1, discard.discardedPaths.size)
        assertEquals("Confirmar pasa solo el path del archivo descartado", listOf(filePath), discard.discardedPaths)
        // Dialog is closed after answering: the question text no longer exists.
        rule.onAllNodesWithText(confirmQuestion).assertCountEquals(0)
    }

    @Test
    fun `cancelling keeps the file intact and closes the dialog`() {
        val discard = DiscardCapture()
        renderSheet(discard)

        rule.onNodeWithText("Descartar").performClick()
        rule.onNodeWithText("Cancelar").performClick()

        assertFalse("Cancelar no descarta", discard.discardedPaths.isNotEmpty())
        rule.onAllNodesWithText(confirmQuestion).assertCountEquals(0)
    }

}
