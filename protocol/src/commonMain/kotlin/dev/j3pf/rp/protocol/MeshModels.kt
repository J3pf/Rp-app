package dev.j3pf.rp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = CommActionSerializer::class)
enum class CommAction(
    val raw: String,
) {
    SEND("send"),
    ASK("ask"),
    REPLY("reply"),
    HANDOVER("handover"),
    MESSAGE("message"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromRaw(value: String?): CommAction = entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

object CommActionSerializer : KSerializer<CommAction> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CommAction", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): CommAction =
        runCatching { CommAction.fromRaw(decoder.decodeString()) }.getOrDefault(CommAction.UNKNOWN)

    override fun serialize(
        encoder: Encoder,
        value: CommAction,
    ) {
        encoder.encodeString(value.raw)
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object MeshSubscribeSerializer : KSerializer<MeshSubscribe> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("dev.j3pf.rp.protocol.MeshSubscribe") {
            element<String?>("machineId")
        }

    override fun serialize(
        encoder: Encoder,
        value: MeshSubscribe,
    ) {
        if (encoder is JsonEncoder) {
            val jsonObject =
                buildJsonObject {
                    if (value.machineId != null || value.hasExplicitMachineId) {
                        put("machineId", value.machineId?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                }
            encoder.encodeJsonElement(jsonObject)
        } else {
            val composite = encoder.beginStructure(descriptor)
            if (value.machineId != null || value.hasExplicitMachineId) {
                composite.encodeNullableSerializableElement(descriptor, 0, String.serializer().nullable, value.machineId)
            }
            composite.endStructure(descriptor)
        }
    }

    override fun deserialize(decoder: Decoder): MeshSubscribe =
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement().jsonObject
            val hasExplicit = element.containsKey("machineId")
            val machineId = element["machineId"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            MeshSubscribe(machineId = machineId, hasExplicitMachineId = hasExplicit)
        } else {
            val composite = decoder.beginStructure(descriptor)
            var machineId: String? = null
            var hasExplicit = false
            loop@ while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> {
                        break@loop
                    }

                    0 -> {
                        machineId = composite.decodeNullableSerializableElement(descriptor, 0, String.serializer().nullable)
                        hasExplicit = true
                    }
                }
            }
            composite.endStructure(descriptor)
            MeshSubscribe(machineId = machineId, hasExplicitMachineId = hasExplicit)
        }
}

@Serializable(with = MeshSubscribeSerializer::class)
data class MeshSubscribe(
    val machineId: String? = null,
    val hasExplicitMachineId: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshSubscribe) return false
        return machineId == other.machineId
    }

    override fun hashCode(): Int = machineId?.hashCode() ?: 0
}

@Serializable
class MeshUnsubscribe

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

@Serializable(with = MeshSubagentTokensSerializer::class)
data class MeshSubagentTokens(
    val prompt: Long? = null,
    val completion: Long? = null,
    val total: Long? = null,
)

object MeshSubagentTokensSerializer : KSerializer<MeshSubagentTokens> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MeshSubagentTokens", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): MeshSubagentTokens {
        val jsonDecoder = decoder as? JsonDecoder ?: return MeshSubagentTokens()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                MeshSubagentTokens(total = element.lenientLong())
            }

            is JsonObject -> {
                MeshSubagentTokens(
                    prompt = element.lenientLong("prompt"),
                    completion = element.lenientLong("completion"),
                    total = element.lenientLong("total"),
                )
            }

            else -> {
                MeshSubagentTokens()
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: MeshSubagentTokens,
    ) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null) {
            val map =
                buildMap<String, JsonElement> {
                    value.prompt?.let { put("prompt", JsonPrimitive(it)) }
                    value.completion?.let { put("completion", JsonPrimitive(it)) }
                    value.total?.let { put("total", JsonPrimitive(it)) }
                }
            jsonEncoder.encodeJsonElement(JsonObject(map))
        } else {
            encoder.encodeString(value.total?.toString() ?: "")
        }
    }
}

@Serializable
data class MeshToolCall(
    val toolCallId: String,
    val name: String,
    val status: String? = null,
    @Serializable(with = LenientLongSerializer::class)
    val durationMs: Long? = null,
)

@Serializable
data class MeshSubagent(
    val taskId: String,
    val agent: String,
    val label: String? = null,
    val status: String,
    val model: String? = null,
    val thinking: String? = null,
    @Serializable(with = LenientLongSerializer::class)
    val startedAt: Long? = null,
    @Serializable(with = LenientLongSerializer::class)
    val endedAt: Long? = null,
    @Serializable(with = LenientLongSerializer::class)
    val durationMs: Long? = null,
    val turns: Int? = null,
    val toolCalls: Int? = null,
    val tokens: MeshSubagentTokens? = null,
    val cost: Double? = null,
    val error: String? = null,
    val result: JsonElement? = null,
    val tools: List<String>? = null,
)

object TolerantMeshSubagentListSerializer : TolerantListSerializer<MeshSubagent>({ MeshSubagent.serializer() })

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
    @Serializable(with = TolerantMeshSubagentListSerializer::class)
    val subagents: List<MeshSubagent> = emptyList(),
    @Serializable(with = LenientLongSerializer::class)
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
    @Serializable(with = LenientLongSerializer::class)
    val lastAt: Long? = null,
    val lastFrom: String? = null,
    val lastAction: String? = null,
    val lastPreview: String? = null,
    val pendingAsk: Boolean = false,
    val active: Boolean = false,
)

object TolerantMeshGroupListSerializer : TolerantListSerializer<MeshGroup>({ MeshGroup.serializer() })

object TolerantMeshNodeListSerializer : TolerantListSerializer<MeshNode>({ MeshNode.serializer() })

object TolerantMeshEdgeListSerializer : TolerantListSerializer<MeshEdge>({ MeshEdge.serializer() })

object TolerantMeshCommListSerializer : TolerantListSerializer<MeshComm>({ MeshComm.serializer() })

@Serializable
data class MeshSnapshot(
    val machineId: String? = null,
    @Serializable(with = LenientLongSerializer::class)
    val generatedAt: Long = 0L,
    @Serializable(with = TolerantMeshGroupListSerializer::class)
    val groups: List<MeshGroup> = emptyList(),
    @Serializable(with = TolerantMeshNodeListSerializer::class)
    val nodes: List<MeshNode> = emptyList(),
    @Serializable(with = TolerantMeshEdgeListSerializer::class)
    val edges: List<MeshEdge> = emptyList(),
)

@Serializable
data class MeshComm(
    val commKey: String,
    val fromAgentId: String? = null,
    val toAgentId: String? = null,
    val fromName: String,
    val toName: String,
    val action: CommAction = CommAction.UNKNOWN,
    val text: String? = null,
    val truncated: Boolean = false,
    @Serializable(with = LenientLongSerializer::class)
    val at: Long = 0L,
    val groupId: String? = null,
) {
    /** Helper for UI display when text is null (redacted / hidden) */
    val displayText: String get() = text ?: "Content hidden"
}

@Serializable
data class MeshCommEvent(
    val comm: MeshComm,
)

@Serializable
data class MeshConversationResponse(
    @Serializable(with = TolerantMeshCommListSerializer::class)
    val items: List<MeshComm> = emptyList(),
    @Serializable(with = LenientLongSerializer::class)
    val nextBefore: Long? = null,
)

@Serializable
data class MeshAgentCommsResponse(
    @Serializable(with = TolerantMeshCommListSerializer::class)
    val items: List<MeshComm> = emptyList(),
)
