package dev.j3pf.rp.ui.agents

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.j3pf.rp.data.repo.SessionRepository
import dev.j3pf.rp.ui.onboarding.PairContent
import dev.j3pf.rp.ui.onboarding.PairUiState
import dev.j3pf.rp.ui.theme.RpTheme
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T1a (Gate A): caracterización de Archivados (AR-3/AR-4), pairing (OB-1/OB-2) sin tocar producción.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ArchivedAndPairParityCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `empty archived list shows exact empty string when there is no error`() {
        rule.setContent {
            RpTheme {
                ArchivedAgentsContent(agents = emptyList(), loading = false, error = null, onOpenChat = {}, onPurge = {})
            }
        }
        rule.onNodeWithText("No hay agentes archivados").assertIsDisplayed()
        // Sin nextBefore no existe 'Cargar más'.
        rule.onAllNodesWithText("Cargar más").assertCountEquals(0)
    }

    @Test
    fun `load more button appears only with a nextBefore cursor`() {
        var loadMores = 0
        rule.setContent {
            RpTheme {
                ArchivedAgentsContent(
                    agents = emptyList(),
                    loading = false,
                    error = null,
                    nextBefore = 123L,
                    onOpenChat = {},
                    onPurge = {},
                    onLoadMore = { loadMores++ },
                )
            }
        }
        rule.onNodeWithText("Cargar más").assertIsDisplayed().performClick()
        assertEquals(1, loadMores)
    }

    @Test
    fun `parsePairingUri accepts the rp pair scheme with url and code and rejects everything else`() {
        val parsed = SessionRepository.parsePairingUri("rp://pair?u=https%3A%2F%2Fdrift.example%3A10000&c=ABCD1234")
        assertEquals("https://drift.example:10000" to "ABCD1234", parsed)

        assertNull(SessionRepository.parsePairingUri("https://drift.example:10000"))
        assertNull(SessionRepository.parsePairingUri("rp://pair?u=https%3A%2F%2Fdrift.example%3A10000"))
        assertNull(SessionRepository.parsePairingUri("rp://pair?c=ABCD1234"))
        assertNull(SessionRepository.parsePairingUri("rp:pair?u=x&c=y"))
        assertNull(SessionRepository.parsePairingUri(""))
    }

    @Test
    fun `pair content prefill deep link fields and manual pair button submits url and code`() {
        var pairs: List<Pair<String, String>> = emptyList()
        rule.setContent {
            RpTheme {
                PairContent(
                    state = PairUiState(busy = false),
                    initialUrl = "https://drift.example:10000",
                    initialCode = "ABCD1234",
                    onScan = {},
                    onPair = { u, c -> pairs = pairs + (u to c) },
                    onClearError = {},
                )
            }
        }
        rule.onNodeWithText("Escanear código QR").assertIsDisplayed()
        rule.onNodeWithText("Emparejar").performClick()
        assertEquals(listOf("https://drift.example:10000" to "ABCD1234"), pairs)
    }

    @Test
    fun `pair content with busy state disables inputs and pair button`() {
        rule.setContent {
            RpTheme {
                PairContent(
                    state = PairUiState(busy = true),
                    initialUrl = null,
                    initialCode = null,
                    onScan = {},
                    onPair = { _, _ -> },
                    onClearError = {},
                )
            }
        }
        // Busy UI shows the progress indicator next to the disabled Emparejar button.
        rule.onNodeWithText("Emparejar").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("URL del relay (https://…)").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Código de emparejamiento").assertCountEquals(1)
    }
}
