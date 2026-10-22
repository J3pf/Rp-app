package dev.j3pf.rp.ui.mesh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MeshTodos
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.nav.AppBottomBar
import dev.j3pf.rp.ui.nav.AppTab
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshViewportRenderTest {
    @get:Rule
    val testWatchdog =
        dev.j3pf.rp.WatchdogTimeout
            .seconds(90)

    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `single agent fits inside viewport on 360dp phone`() {
        val node =
            MeshNode(
                agentId = "solo",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Agente Individual",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/app",
            )
        assertAllNodesInsideViewport(listOf(node), emptyList(), screenWidthDp = 360f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `four agents layout fits horizontally and vertically on 360dp phone`() {
        val (nodes, edges) = build4Agents()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 360f)
    }

    @Test
    @Config(qualifiers = "w800dp-h411dp-xhdpi")
    fun `four agents layout fits horizontally and vertically in landscape`() {
        val (nodes, edges) = build4Agents()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 800f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `twelve agents swarm fits horizontally and vertically on 360dp phone`() {
        val (nodes, edges) = build12Agents()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 360f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `multiroot layout fits horizontally and vertically on 360dp phone`() {
        val (nodes, edges) = buildMultiRoot()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 360f)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `multiroot layout fits horizontally and vertically on tablet`() {
        val (nodes, edges) = buildMultiRoot()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 800f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `deeptree layout fits horizontally and vertically on 360dp phone`() {
        val (nodes, edges) = buildDeepTree()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 360f)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun `deeptree layout fits horizontally and vertically on tablet`() {
        val (nodes, edges) = buildDeepTree()
        assertAllNodesInsideViewport(nodes, edges, screenWidthDp = 800f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `header actions do not wrap at font scale 1 point 3`() {
        assertHeaderActionsDoNotWrap(fontScale = 1.3f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `header actions do not wrap at font scale 2 point 0`() {
        assertHeaderActionsDoNotWrap(fontScale = 2.0f)
    }

    @Test
    fun `multiroot sibling roots have no connector edge between them`() {
        val (nodes, edges) = buildMultiRoot()
        val layout = MeshLayoutEngine().computeLayout(nodes, edges, width = 411f, height = 750f)
        val peerEdge = layout.edges.firstOrNull { it.edge.edgeId == "edge-peer" }
        assertEquals(null, peerEdge, "Sibling roots edge-peer must NOT be positioned as a tree connector")
    }

    private fun assertHeaderActionsDoNotWrap(fontScale: Float) {
        val (nodes, edges) = build4Agents()
        val layout = MeshLayoutEngine().computeLayout(nodes, edges, width = 360f, height = 700f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = nodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                RpTheme {
                    MeshScreenContent(uiState = uiState, onOpenChat = {})
                }
            }
        }
        rule.waitForIdle()

        // Recenter icon button is present with content description
        val recenterNode = rule.onNodeWithContentDescription("Recentrar organigrama")
        recenterNode.assertIsDisplayed()

        // Toggle list view button is present and text did not wrap to multiple lines
        val listViewButton = rule.onNodeWithText("Vista de lista")
        listViewButton.assertIsDisplayed()

        val textLayoutResults = mutableListOf<TextLayoutResult>()
        val action = listViewButton.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
        action?.action?.invoke(textLayoutResults)
        val result = textLayoutResults.firstOrNull()
        if (result != null) {
            assertEquals(1, result.lineCount, "Vista de lista should remain on 1 line at fontScale=$fontScale")
            assertFalse(result.didOverflowHeight, "Vista de lista should not overflow height")
        }
    }

    private fun assertAllNodesInsideViewport(
        nodes: List<MeshNode>,
        edges: List<MeshEdge>,
        screenWidthDp: Float,
    ) {
        val layout = MeshLayoutEngine().computeLayout(nodes, edges, width = screenWidthDp, height = 700f)
        val uiState =
            MeshUiState(
                isLoading = false,
                isConnected = true,
                snapshot = MeshSnapshot(nodes = nodes, edges = edges),
                layoutResult = layout,
                isListView = false,
            )

        rule.setContent {
            RpTheme {
                Scaffold(
                    bottomBar = { AppBottomBar(selectedTab = AppTab.MESH, pending = 0) },
                ) { padding: PaddingValues ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        MeshScreenContent(uiState = uiState, onOpenChat = {})
                    }
                }
            }
        }
        rule.waitForIdle()

        val headerBounds = rule.onNodeWithText("Red de agentes").fetchSemanticsNode().boundsInRoot
        val liveStripBounds = rule.onNodeWithText("En vivo").fetchSemanticsNode().boundsInRoot
        val screenWidthPx = with(rule.density) { screenWidthDp.dp.toPx() }

        nodes.forEach { node ->
            val parent = node.parentAgentId?.let { pid -> nodes.firstOrNull { it.agentId == pid } }
            val desc = formatMeshNodeContentDescription(node, nodes, parent)
            val semanticsNode = rule.onNodeWithContentDescription(desc).assertExists().fetchSemanticsNode()
            val bounds = semanticsNode.boundsInRoot

            assertTrue(
                bounds.left >= -1f,
                "Node '${node.name}' left (${bounds.left}) is clipped on the left of viewport",
            )
            assertTrue(
                bounds.right <= screenWidthPx + 1f,
                "Node '${node.name}' right (${bounds.right}) is clipped on the right of viewport ($screenWidthPx)",
            )
            assertTrue(
                bounds.top >= headerBounds.bottom - 1f,
                "Node '${node.name}' top (${bounds.top}) intersects header (${headerBounds.bottom})",
            )
            assertTrue(
                bounds.bottom <= liveStripBounds.top + 1f,
                "Node '${node.name}' bottom (${bounds.bottom}) intersects live strip (${liveStripBounds.top})",
            )
        }
    }

    private fun build4Agents(): Pair<List<MeshNode>, List<MeshEdge>> {
        val root =
            MeshNode(
                agentId = "orch-app",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Rp-app",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-app",
                todos = MeshTodos(total = 6, done = 3, inProgress = 1, blocked = 0, pending = 2),
            )
        val sub1 =
            MeshNode(
                agentId = "orch-relay",
                machineId = "m-2",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Rp-relay",
                role = "orchestrator",
                parentAgentId = "orch-app",
                status = "working",
                online = true,
                cwd = "/home/drift/Workspace/RP/Rp-relay",
                todos = MeshTodos(total = 4, done = 2, inProgress = 1, blocked = 0, pending = 1),
            )
        val sub2 =
            MeshNode(
                agentId = "worker-plugin",
                machineId = "m-2",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Worker Plugin",
                role = "subordinate",
                parentAgentId = "orch-app",
                status = "offline",
                online = false,
                cwd = "/home/drift/Workspace/RP/Rp-plugin",
            )
        val sub3 =
            MeshNode(
                agentId = "worker-rp",
                machineId = "m-2",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "RP",
                role = "subordinate",
                parentAgentId = "orch-app",
                status = "idle",
                online = true,
                cwd = "/home/drift/Workspace/RP",
            )
        val nodes = listOf(root, sub1, sub2, sub3)
        val edges =
            listOf(
                MeshEdge("edge-1", "orch-app", "orch-relay", count = 5, active = true),
                MeshEdge("edge-2", "orch-app", "worker-plugin", count = 1, active = false),
                MeshEdge("edge-3", "orch-app", "worker-rp", count = 2, active = false),
            )
        return nodes to edges
    }

    private fun build12Agents(): Pair<List<MeshNode>, List<MeshEdge>> {
        val root =
            MeshNode(
                agentId = "orch-root",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Orquestador Maestro",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/workspace",
                todos = MeshTodos(total = 12, done = 7, inProgress = 1, blocked = 0, pending = 4),
            )
        val subordinates =
            (1..11).map { idx ->
                MeshNode(
                    agentId = "sub-$idx",
                    machineId = if (idx % 2 == 0) "m-1" else "m-2",
                    machineName = if (idx % 2 == 0) "Omarchy" else "Croma",
                    machineColor = if (idx % 2 == 0) "#6750A4" else "#7AA2F7",
                    name = "Worker-$idx",
                    role = "subordinate",
                    parentAgentId = "orch-root",
                    status = if (idx in 2..5 || idx == 8) "working" else "idle",
                    online = idx != 11,
                    cwd = "/workspace/sub-$idx",
                )
            }
        val nodes = listOf(root) + subordinates
        val edges =
            subordinates.mapIndexed { idx, sub ->
                MeshEdge("edge-$idx", "orch-root", sub.agentId, count = idx + 1, active = sub.status == "working")
            }
        return nodes to edges
    }

    private fun buildMultiRoot(): Pair<List<MeshNode>, List<MeshEdge>> {
        val rootApp =
            MeshNode(
                agentId = "orch-app",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Orquestador App",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/workspace/app",
            )
        val workerUI =
            MeshNode(
                agentId = "worker-ui",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Worker UI",
                role = "subordinate",
                parentAgentId = "orch-app",
                status = "working",
                online = true,
                cwd = "/workspace/app/ui",
            )
        val rootRelay =
            MeshNode(
                agentId = "orch-relay",
                machineId = "m-2",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Orquestador Relay",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/workspace/relay",
            )
        val workerApi =
            MeshNode(
                agentId = "worker-api",
                machineId = "m-2",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Worker API",
                role = "subordinate",
                parentAgentId = "orch-relay",
                status = "idle",
                online = true,
                cwd = "/workspace/relay/api",
            )
        val soloNode =
            MeshNode(
                agentId = "solo-plugin",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Worker Plugin",
                role = "solo",
                status = "idle",
                online = true,
                cwd = "/workspace/plugin",
            )
        val nodes = listOf(rootApp, workerUI, rootRelay, workerApi, soloNode)
        val edges =
            listOf(
                MeshEdge("edge-app", "orch-app", "worker-ui", count = 3, active = true),
                MeshEdge("edge-relay", "orch-relay", "worker-api", count = 2, active = false),
                MeshEdge("edge-peer", "orch-app", "orch-relay", count = 12, active = true, lastAction = "ask"),
            )
        return nodes to edges
    }

    private fun buildDeepTree(): Pair<List<MeshNode>, List<MeshEdge>> {
        val ceo =
            MeshNode(
                agentId = "ceo",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "CEO Orquestador",
                role = "orchestrator",
                status = "working",
                online = true,
                cwd = "/workspace",
            )
        val dirInfra =
            MeshNode(
                agentId = "dir-infra",
                machineId = "m-2",
                machineName = "Croma",
                machineColor = "#7AA2F7",
                name = "Director Infraestructura",
                role = "orchestrator",
                parentAgentId = "ceo",
                status = "working",
                online = true,
                cwd = "/workspace/infra",
            )
        val dirProd =
            MeshNode(
                agentId = "dir-prod",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Director Producto",
                role = "orchestrator",
                parentAgentId = "ceo",
                status = "working",
                online = true,
                cwd = "/workspace/prod",
            )
        val teamLead =
            MeshNode(
                agentId = "lead-dev",
                machineId = "m-1",
                machineName = "Omarchy",
                machineColor = "#6750A4",
                name = "Líder Desarrollo",
                role = "subordinate",
                parentAgentId = "dir-prod",
                status = "working",
                online = true,
                cwd = "/workspace/dev",
            )
        val leaves =
            (1..4).map { i ->
                MeshNode(
                    agentId = "dev-leaf-$i",
                    machineId = "m-1",
                    machineName = "Omarchy",
                    machineColor = "#6750A4",
                    name = "Especialista $i",
                    role = "subordinate",
                    parentAgentId = "lead-dev",
                    status = if (i % 2 == 0) "working" else "idle",
                    online = true,
                    cwd = "/workspace/spec-$i",
                )
            }
        val nodes = listOf(ceo, dirInfra, dirProd, teamLead) + leaves
        val edges =
            listOf(
                MeshEdge("e1", "ceo", "dir-infra", count = 3, active = true),
                MeshEdge("e2", "ceo", "dir-prod", count = 4, active = true),
                MeshEdge("e3", "dir-prod", "lead-dev", count = 6, active = true),
            ) +
                leaves.mapIndexed { idx, leaf ->
                    MeshEdge("e-leaf-$idx", "lead-dev", leaf.agentId, count = idx + 1, active = leaf.status == "working")
                }
        return nodes to edges
    }
}
