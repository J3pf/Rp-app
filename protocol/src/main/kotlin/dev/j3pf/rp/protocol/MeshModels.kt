package dev.j3pf.rp.protocol

import kotlinx.serialization.Serializable

@Serializable
data class MeshSubscribe(
    val machineId: String? = null,
)

@Serializable
class MeshUnsubscribe()

@Serializable
data class MeshGroup(
    val groupId: String,
    val label: String,
    val orchestratorAgentId: String? = null,
    val machineIds: List<String> = emptyList(),
    val memberAgentIds: List<String> = emptyList(),
    val confidence: String = "comms",
)

@Serializable
data class MeshTodos(
    val total: Int = 0,
    val done: Int = 0,
    val inProgress: Int = 0,
    val blocked: Int = 0,
    val pending: Int = 0,
    val current: String? = null,
)

@Serializable
data class MeshToolCall(
    val toolCallId: String,
    val name: String,
    val status: String? = null,
    val durationMs: Long? = null,
)

@Serializable
data class MeshSubagent(
    val taskId: String,
    val agent: String,
    val label: String,
    val status: String,
    val model: String? = null,
    val thinking: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val durationMs: Long? = null,
    val turns: Int? = null,
    val toolCalls: Int? = null,
    val tokens: Long? = null,
    val cost: Double? = null,
    val error: String? = null,
    val result: String? = null,
    val tools: List<MeshToolCall>? = null,
)

@Serializable
data class MeshNode(
    val agentId: String,
    val machineId: String,
    val machineName: String,
    val machineColor: String = "#6750A4",
    val name: String,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val model: String? = null,
    val status: String,
    val activity: String? = null,
    val online: Boolean = false,
    val role: String = "solo",
    val parentAgentId: String? = null,
    val groupId: String? = null,
    val todos: MeshTodos? = null,
    val subagents: List<MeshSubagent> = emptyList(),
    val lastCommAt: Long? = null,
    val openPromptCount: Int = 0,
)

@Serializable
data class MeshEdge(
    val edgeId: String,
    val a: String,
    val b: String,
    val count: Int = 0,
    val aToB: Int = 0,
    val bToA: Int = 0,
    val lastAt: Long? = null,
    val lastFrom: String? = null,
    val lastAction: String? = null,
    val lastPreview: String? = null,
    val pendingAsk: Boolean = false,
    val active: Boolean = false,
)

@Serializable
data class MeshSnapshot(
    val machineId: String? = null,
    val generatedAt: Long = 0L,
    val groups: List<MeshGroup> = emptyList(),
    val nodes: List<MeshNode> = emptyList(),
    val edges: List<MeshEdge> = emptyList(),
)

@Serializable
data class MeshComm(
    val commKey: String,
    val fromAgentId: String,
    val toAgentId: String,
    val fromName: String,
    val toName: String,
    val action: String,
    val text: String? = null,
    val truncated: Boolean = false,
    val at: Long = 0L,
    val groupId: String? = null,
)

@Serializable
data class MeshCommEvent(
    val comm: MeshComm,
)

@Serializable
data class MeshConversationResponse(
    val items: List<MeshComm> = emptyList(),
    val nextBefore: Long? = null,
)

@Serializable
data class MeshAgentCommsResponse(
    val items: List<MeshComm> = emptyList(),
)
