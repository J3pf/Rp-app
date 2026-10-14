package dev.j3pf.rp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "machines", primaryKeys = ["relayId", "machineId"])
data class MachineEntity(
    val relayId: String,
    val machineId: String,
    val name: String,
    val context: String,
    val color: String,
    val icon: String?,
    val online: Boolean,
    val agentCount: Int,
    val openPromptCount: Int,
    val hostOnline: Boolean,
)

@Entity(
    tableName = "agents",
    primaryKeys = ["relayId", "agentId"],
    indices = [Index("relayId", "machineId"), Index("relayId", "online", "lastSeenAt")],
)
data class AgentEntity(
    val relayId: String,
    val agentId: String,
    val machineId: String,
    val machineName: String,
    val machineContext: String,
    val machineColor: String,
    val sessionName: String?,
    val cwd: String?,
    val gitBranch: String?,
    val model: String?,
    val status: String,
    val online: Boolean,
    val lastSeenAt: Long?,
    val lastMessagePreview: String?,
    val launcher: String?,
    val herdrPaneId: String?,
    val herdrTabId: String?,
    val herdrWorkspaceId: String?,
    val gentleMode: String?,
    val redacted: Boolean,
    val spawnedByHost: Boolean,
)

/** Agente + contador de prompts abiertos calculado desde la tabla `prompts`. */
data class AgentWithCounts(
    @androidx.room.Embedded val agent: AgentEntity,
    val openPrompts: Int,
)

object PromptState {
    const val OPEN = "open"
    const val RESOLVED = "resolved"
}

@Entity(
    tableName = "prompts",
    primaryKeys = ["relayId", "promptId"],
    indices = [
        Index("relayId", "agentId"),
        Index("relayId", "state"),
        Index("relayId", "agentId", "state"),
        Index("state", "resolvedAt"),
    ],
)
data class PromptEntity(
    val relayId: String,
    val promptId: String,
    val agentId: String,
    /** JSON del `Prompt`. */
    val json: String,
    val state: String,
    /** 0 low · 1 medium · 2 high, para ordenar. */
    val dangerRank: Int,
    val openedAt: Long,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
)

object RecordKind {
    const val ENTRY = "entry"
    const val EVENT = "event"
}

/** Historial del chat persistido: entradas normalizadas y eventos finales (message_end, tool_execution_*...). */
@Entity(tableName = "chat_records", indices = [Index("relayId", "agentId", "kind", "seq")])
data class ChatRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val relayId: String,
    val agentId: String,
    val kind: String,
    val epoch: String?,
    val seq: Long,
    val json: String,
)

/** Mensajes pendientes de enviar (offline); `clientMsgId` es la clave de idempotencia. */
@Entity(tableName = "outbox", indices = [Index("relayId", "createdAt")])
data class OutboxEntity(
    @PrimaryKey val clientMsgId: String,
    val relayId: String,
    val agentId: String,
    val json: String,
    val attempts: Int = 0,
    val createdAt: Long,
)

/** Deduplicación de notificaciones por `promptId`/`pushId` durante 24 h. */
@Entity(tableName = "notification_dedup", indices = [Index("seenAt")])
data class DedupEntity(
    @PrimaryKey val key: String,
    val seenAt: Long,
)
