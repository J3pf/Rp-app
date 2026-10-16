package dev.j3pf.rp.ui.mesh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshEdge
import dev.j3pf.rp.protocol.MeshGroup
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutEngine
import dev.j3pf.rp.ui.mesh.layout.MeshLayoutResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeshViewModel
    @Inject
    constructor(
        private val repository: MeshRepository,
        machines: MachinesRepository,
        private val settings: SettingsRepository,
        private val layoutEngine: MeshLayoutEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MeshUiState())
        val uiState: StateFlow<MeshUiState> = _uiState.asStateFlow()

        private var selectionsInitialized = false
        private var screenVisible = false
        private var hasSentSubscription = false
        private var lastSubscribedMachineId: String? = null
        private var viewportWidth = DEFAULT_VIEWPORT_SIZE
        private var viewportHeight = DEFAULT_VIEWPORT_SIZE
        private var nodeCommsJob: Job? = null
        private var conversationJob: Job? = null

        init {
            combine(
                repository.snapshot,
                machines.machinesState,
                settings.settings,
                repository.connState,
            ) { snapshot, machineList, appSettings, connectionState ->
                Inputs(snapshot, machineList, appSettings.selectedMeshMachine, appSettings.selectedMeshGroup, connectionState)
            }.onEach(::applyInputs).launchIn(viewModelScope)

            repository.commEvents
                .onEach(::onIncomingComm)
                .launchIn(viewModelScope)
        }

        fun onScreenVisible() {
            screenVisible = true
            subscribeForCurrentMachine()
        }

        fun onScreenInvisible() {
            screenVisible = false
            hasSentSubscription = false
            repository.unsubscribe()
        }

        fun selectMachine(machineId: String?) {
            val state = _uiState.value
            val selected = machineId?.takeIf { id -> state.machineChips.any { it.machineId == id } }
            _uiState.update { current ->
                current.copy(selectedMachineId = selected).withLayout(layoutEngine, viewportWidth, viewportHeight)
            }
            viewModelScope.launch { settings.setMeshSelectedMachine(selected) }
            subscribeForCurrentMachine()
        }

        fun selectGroup(groupId: String?) {
            val state = _uiState.value
            val selected = groupId?.takeIf { id -> state.groupChips.any { it.groupId == id } }
            _uiState.update { current ->
                current.copy(selectedGroupId = selected).withLayout(layoutEngine, viewportWidth, viewportHeight)
            }
            viewModelScope.launch { settings.setMeshSelectedGroup(selected) }
        }

        fun onNodeClick(agentId: String) {
            val node =
                _uiState.value.snapshot
                    ?.nodes
                    ?.firstOrNull { it.agentId == agentId } ?: return
            nodeCommsJob?.cancel()
            conversationJob?.cancel()
            _uiState.update {
                it.copy(
                    nodeDetail = MeshNodeDetail(node = node, isLoadingComms = true),
                    conversationDetail = null,
                )
            }
            nodeCommsJob =
                viewModelScope.launch {
                    val result = repository.getMeshAgentComms(agentId)
                    _uiState.update { state ->
                        val detail = state.nodeDetail?.takeIf { it.node.agentId == agentId } ?: return@update state
                        val comms = (result as? ApiResult.Ok)?.value?.items.orEmpty()
                        state.copy(nodeDetail = detail.copy(recentComms = comms.toList(), isLoadingComms = false))
                    }
                }
        }

        fun onEdgeClick(edgeId: String) {
            val snapshot = _uiState.value.snapshot ?: return
            val edge = snapshot.edges.firstOrNull { it.edgeId == edgeId } ?: return
            val nodes = snapshot.nodes.associateBy { it.agentId }
            val nodeA = nodes[edge.a]
            val nodeB = nodes[edge.b]
            nodeCommsJob?.cancel()
            conversationJob?.cancel()
            val detail =
                MeshConversationDetail(
                    agentA = edge.a,
                    agentB = edge.b,
                    agentAName = nodeA?.name ?: edge.a,
                    agentBName = nodeB?.name ?: edge.b,
                    isLoading = true,
                )
            _uiState.update { it.copy(nodeDetail = null, conversationDetail = detail) }
            conversationJob = viewModelScope.launch { loadConversationPage(detail, before = null, append = false) }
        }

        fun loadMoreConversation() {
            val detail = _uiState.value.conversationDetail ?: return
            val before = detail.nextBefore ?: return
            if (detail.isLoading || detail.isLoadingMore) return
            _uiState.update { state ->
                val current = state.conversationDetail ?: return@update state
                state.copy(conversationDetail = current.copy(isLoadingMore = true))
            }
            conversationJob?.cancel()
            conversationJob = viewModelScope.launch { loadConversationPage(detail, before, append = true) }
        }

        fun dismissNodeDetail() {
            nodeCommsJob?.cancel()
            nodeCommsJob = null
            _uiState.update { it.copy(nodeDetail = null) }
        }

        fun dismissConversationDetail() {
            conversationJob?.cancel()
            conversationJob = null
            _uiState.update { it.copy(conversationDetail = null) }
        }

        fun toggleListView() {
            _uiState.update { it.copy(isListView = !it.isListView) }
        }

        fun onViewportSizeChanged(
            width: Float,
            height: Float,
        ) {
            viewportWidth = width.coerceAtLeast(1f)
            viewportHeight = height.coerceAtLeast(1f)
            _uiState.update { it.withLayout(layoutEngine, viewportWidth, viewportHeight) }
        }

        private fun applyInputs(inputs: Inputs) {
            val previous = _uiState.value
            val machineChips = buildMachineChips(inputs.machineList, inputs.snapshot)
            val groupChips = buildGroupChips(inputs.snapshot)
            var machineId = if (selectionsInitialized) previous.selectedMachineId else inputs.selectedMachineId
            var groupId = if (selectionsInitialized) previous.selectedGroupId else inputs.selectedGroupId

            if (inputs.snapshot != null) {
                if (machineId != null && machineChips.none { it.machineId == machineId }) machineId = null
                if (groupId != null && groupChips.none { it.groupId == groupId }) groupId = null
                if (groupId == null && groupChips.isNotEmpty()) groupId = mostActiveGroup(inputs.snapshot, groupChips)
            }
            selectionsInitialized = true

            val updated =
                previous
                    .copy(
                        isLoading = inputs.snapshot == null && inputs.connectionState == ConnState.Connected,
                        isConnected = inputs.connectionState == ConnState.Connected,
                        snapshot = inputs.snapshot,
                        selectedMachineId = machineId,
                        selectedGroupId = groupId,
                        machineChips = machineChips,
                        groupChips = groupChips,
                    ).withLayout(layoutEngine, viewportWidth, viewportHeight)
            _uiState.value = updated

            if (inputs.selectedMachineId != machineId) {
                viewModelScope.launch { settings.setMeshSelectedMachine(machineId) }
            }
            if (inputs.selectedGroupId != groupId) {
                viewModelScope.launch { settings.setMeshSelectedGroup(groupId) }
            }
            if (screenVisible && (!hasSentSubscription || lastSubscribedMachineId != machineId)) {
                subscribeForCurrentMachine()
            }
        }

        private fun buildMachineChips(
            machines: List<MachineSummary>,
            snapshot: MeshSnapshot?,
        ): List<MeshMachineChip> {
            val nodeCounts =
                snapshot
                    ?.nodes
                    .orEmpty()
                    .groupingBy { it.machineId }
                    .eachCount()
            val byId = linkedMapOf<String, MeshMachineChip>()
            machines.forEach { machine ->
                byId[machine.machineId] =
                    MeshMachineChip(
                        machineId = machine.machineId,
                        name = machine.name,
                        color = machine.color,
                        icon = machine.icon,
                        agentCount = nodeCounts[machine.machineId] ?: machine.agentCount,
                    )
            }
            snapshot?.nodes.orEmpty().forEach { node ->
                if (node.machineId !in byId) {
                    byId[node.machineId] =
                        MeshMachineChip(
                            machineId = node.machineId,
                            name = node.machineName,
                            color = node.machineColor,
                            agentCount = nodeCounts[node.machineId] ?: 0,
                        )
                }
            }
            return listOf(MeshMachineChip(machineId = null, name = "All", color = "")) + byId.values
        }

        private fun buildGroupChips(snapshot: MeshSnapshot?): List<MeshGroupChip> {
            if (snapshot == null) return emptyList()
            val groups =
                snapshot.groups.map { group ->
                    MeshGroupChip(
                        groupId = group.groupId,
                        label = group.label,
                        memberCount = membersOf(snapshot, group.groupId).size,
                    )
                }
            val soloNodes = snapshot.nodes.filter(::isSolo)
            return groups +
                if (soloNodes.isNotEmpty()) {
                    listOf(MeshGroupChip(SOLO_GROUP_ID, "Solo", isSolo = true, memberCount = soloNodes.size))
                } else {
                    emptyList()
                }
        }

        private fun mostActiveGroup(
            snapshot: MeshSnapshot,
            chips: List<MeshGroupChip>,
        ): String? =
            chips
                .maxWithOrNull(
                    compareBy<MeshGroupChip> { activityFor(snapshot, it) }
                        .thenByDescending { it.groupId.orEmpty() },
                )?.groupId

        private fun activityFor(
            snapshot: MeshSnapshot,
            chip: MeshGroupChip,
        ): Long {
            val members = membersOf(snapshot, chip.groupId)
            val nodeActivity =
                snapshot.nodes
                    .filter { it.agentId in members }
                    .mapNotNull { it.lastCommAt }
                    .maxOrNull() ?: Long.MIN_VALUE
            val edgeActivity =
                snapshot.edges
                    .filter { it.a in members && it.b in members }
                    .mapNotNull { it.lastAt }
                    .maxOrNull() ?: Long.MIN_VALUE
            return maxOf(nodeActivity, edgeActivity)
        }

        private fun membersOf(
            snapshot: MeshSnapshot,
            groupId: String?,
        ): Set<String> {
            if (groupId == SOLO_GROUP_ID) return snapshot.nodes.filter(::isSolo).mapTo(mutableSetOf()) { it.agentId }
            val group = snapshot.groups.firstOrNull { it.groupId == groupId } ?: return emptySet()
            return snapshot.nodes
                .asSequence()
                .filter { it.groupId == groupId || it.agentId in group.memberAgentIds }
                .mapTo(mutableSetOf()) { it.agentId }
        }

        private fun isSolo(node: MeshNode): Boolean = node.role == "solo" || node.groupId == null

        private fun subscribeForCurrentMachine() {
            if (!screenVisible) return
            val machineId = _uiState.value.selectedMachineId
            if (hasSentSubscription && machineId == lastSubscribedMachineId) return
            repository.subscribe(machineId)
            hasSentSubscription = true
            lastSubscribedMachineId = machineId
        }

        private fun onIncomingComm(comm: MeshComm) {
            val expiresAt = System.currentTimeMillis() + BUBBLE_LIFETIME_MS
            _uiState.update { state ->
                state.copy(
                    liveComms = (listOf(comm) + state.liveComms.filterNot { it.commKey == comm.commKey }).take(MAX_LIVE_COMMS),
                    activeBubbles =
                        (listOf(LiveCommBubble(comm, expiresAt)) + state.activeBubbles)
                            .filter { it.expiresAtMs > System.currentTimeMillis() }
                            .take(MAX_ACTIVE_BUBBLES),
                )
            }
            viewModelScope.launch {
                delay(BUBBLE_LIFETIME_MS)
                val now = System.currentTimeMillis()
                _uiState.update { state -> state.copy(activeBubbles = state.activeBubbles.filter { it.expiresAtMs > now }) }
            }
        }

        private suspend fun loadConversationPage(
            detail: MeshConversationDetail,
            before: Long?,
            append: Boolean,
        ) {
            val result = repository.getMeshConversation(detail.agentA, detail.agentB, before, CONVERSATION_PAGE_SIZE)
            _uiState.update { state ->
                val current =
                    state.conversationDetail?.takeIf {
                        it.agentA == detail.agentA && it.agentB == detail.agentB
                    } ?: return@update state
                val response = (result as? ApiResult.Ok)?.value
                val items = response?.items.orEmpty()
                val merged =
                    if (append) {
                        (current.items + items).distinctBy { it.commKey }
                    } else {
                        items.distinctBy { it.commKey }
                    }
                state.copy(
                    conversationDetail =
                        current.copy(
                            items = merged.toList(),
                            nextBefore = response?.nextBefore,
                            isLoading = false,
                            isLoadingMore = false,
                        ),
                )
            }
        }

        private data class Inputs(
            val snapshot: MeshSnapshot?,
            val machineList: List<MachineSummary>,
            val selectedMachineId: String?,
            val selectedGroupId: String?,
            val connectionState: ConnState,
        )

        private fun MeshUiState.withLayout(
            layoutEngine: MeshLayoutEngine,
            width: Float,
            height: Float,
        ): MeshUiState {
            val mesh = snapshot ?: return copy(layoutResult = null)
            val filteredNodes =
                mesh.nodes.filter { node ->
                    val machineMatches = selectedMachineId == null || node.machineId == selectedMachineId
                    val groupMatches =
                        when (selectedGroupId) {
                            SOLO_GROUP_ID -> {
                                isSolo(node)
                            }

                            null -> {
                                true
                            }

                            else -> {
                                node.groupId == selectedGroupId ||
                                    mesh.groups
                                        .firstOrNull { it.groupId == selectedGroupId }
                                        ?.memberAgentIds
                                        ?.contains(node.agentId) == true
                            }
                        }
                    machineMatches && groupMatches
                }
            val visibleIds = filteredNodes.mapTo(mutableSetOf()) { it.agentId }
            val filteredEdges = mesh.edges.filter { it.a in visibleIds && it.b in visibleIds }
            val layout = layoutEngine.computeLayout(filteredNodes, filteredEdges, width, height, layoutResult)
            return copy(layoutResult = layout)
        }

        companion object {
            const val SOLO_GROUP_ID = "solo"
            private const val MAX_LIVE_COMMS = 20
            private const val MAX_ACTIVE_BUBBLES = 50
            private const val BUBBLE_LIFETIME_MS = 6_000L
            private const val CONVERSATION_PAGE_SIZE = 50
            private const val DEFAULT_VIEWPORT_SIZE = 800f
        }
    }
