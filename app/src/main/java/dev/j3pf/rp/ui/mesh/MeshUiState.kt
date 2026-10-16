package dev.j3pf.rp.ui.mesh

import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult

data class MeshMachineChip(
    val machineId: String?,
    val name: String,
    val color: String,
    val icon: String? = null,
    val agentCount: Int = 0,
)

data class MeshGroupChip(
    val groupId: String?,
    val label: String,
    val isSolo: Boolean = false,
    val memberCount: Int = 0,
)

data class MeshNodeDetail(
    val node: MeshNode,
    val recentComms: List<MeshComm> = emptyList(),
    val isLoadingComms: Boolean = false,
)

data class MeshConversationDetail(
    val agentA: String,
    val agentB: String,
    val agentAName: String,
    val agentBName: String,
    val items: List<MeshComm> = emptyList(),
    val nextBefore: Long? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
)

data class LiveCommBubble(
    val comm: MeshComm,
    val expiresAtMs: Long,
)

data class MeshUiState(
    val isLoading: Boolean = true,
    val isConnected: Boolean = true,
    val snapshot: MeshSnapshot? = null,
    val layoutResult: MeshLayoutResult? = null,
    val selectedMachineId: String? = null,
    val selectedGroupId: String? = null,
    val machineChips: List<MeshMachineChip> = emptyList(),
    val groupChips: List<MeshGroupChip> = emptyList(),
    val liveComms: List<MeshComm> = emptyList(),
    val activeBubbles: List<LiveCommBubble> = emptyList(),
    val isListView: Boolean = false,
    val nodeDetail: MeshNodeDetail? = null,
    val conversationDetail: MeshConversationDetail? = null,
) {
    val isEmpty: Boolean get() = snapshot != null && snapshot.nodes.isEmpty()
}
