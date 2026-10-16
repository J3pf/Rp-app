package dev.j3pf.rp.ui.mesh

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MeshScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `empty state explains how agents appear`() {
        val empty = MeshSnapshot()
        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = MeshUiState(isLoading = false, snapshot = empty),
                    onOpenChat = {},
                )
            }
        }

        rule.onNodeWithText("No agents found").assertIsDisplayed()
        rule.onNodeWithText("Open Pi with the rp plugin on your machines to see agents here.").assertIsDisplayed()
    }

    @Test
    fun `toggle to list view displays nodes accessibly`() {
        val nodes = listOf(node("orchestrator", "Orchestrator", "orchestrator"), node("worker", "Worker", "subordinate"))
        val snapshot = MeshSnapshot(nodes = nodes)
        val layout = MeshLayoutEngine().computeLayout(nodes, emptyList())
        val state = MeshUiState(isLoading = false, snapshot = snapshot, layoutResult = layout)
        val isList = mutableStateOf(false)

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = if (isList.value) state.copy(isListView = true) else state,
                    onOpenChat = {},
                    onToggleListView = { isList.value = !isList.value },
                )
            }
        }

        rule.onNodeWithText("List view").performClick()
        rule.onNodeWithText("Orchestrator").assertIsDisplayed()
        rule.onNodeWithText("Worker").assertIsDisplayed()
        rule.onNodeWithContentDescription("Orchestrator Orchestrator, idle").assertIsDisplayed()
    }

    @Test
    fun `tapping node opens node detail sheet`() {
        val agent = node("agent-1", "Review Agent", "solo")
        val snapshot = MeshSnapshot(nodes = listOf(agent))
        val layout = MeshLayoutEngine().computeLayout(snapshot.nodes, snapshot.edges)
        val state = mutableStateOf(MeshUiState(isLoading = false, snapshot = snapshot, layoutResult = layout))

        rule.setContent {
            RpTheme {
                MeshScreenContent(
                    uiState = state.value,
                    onOpenChat = {},
                    onNodeClick = { state.value = state.value.copy(nodeDetail = MeshNodeDetail(agent)) },
                )
            }
        }

        rule.onNodeWithContentDescription("Agent Review Agent, idle").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Review Agent").assertExists()
        rule.onNodeWithText("Open chat").assertExists()
    }

    @Test
    fun `disconnected banner shows stale data and retry action`() {
        val state = MeshUiState(isLoading = false, isConnected = false, snapshot = MeshSnapshot())
        rule.setContent {
            RpTheme {
                MeshScreenContent(uiState = state, onOpenChat = {}, onRetry = {})
            }
        }

        rule.onNodeWithText("Disconnected — showing stale data").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed()
    }

    private fun node(
        id: String,
        name: String,
        role: String,
    ) = MeshNode(
        agentId = id,
        machineId = "machine",
        machineName = "Test machine",
        name = name,
        status = "idle",
        online = true,
        role = role,
    )
}
