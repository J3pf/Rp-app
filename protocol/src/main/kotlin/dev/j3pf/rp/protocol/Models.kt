package dev.j3pf.rp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class MachineSummary(
    val machineId: String,
    val name: String,
    val context: String = "personal",
    val color: String = "#6750A4",
    val icon: String? = null,
    val online: Boolean = false,
    val agentCount: Int = 0,
    val openPromptCount: Int = 0,
    val hostOnline: Boolean = false,
)

@Serializable
data class HerdrInfo(
    val paneId: String? = null,
    val tabId: String? = null,
    val workspaceId: String? = null,
    val session: String? = null,
)

@Serializable
data class ModelInfo(val provider: String? = null, val id: String? = null) {
    val label: String get() = id ?: provider ?: ""
}

object AgentStatus {
    const val IDLE = "idle"
    const val WORKING = "working"
    const val WAITING_INPUT = "waiting_input"
    const val COMPACTING = "compacting"
    const val ERROR = "error"
}

@Serializable
data class AgentSummary(
    val agentId: String,
    val machineId: String,
    val machineName: String = "",
    val machineContext: String = "personal",
    val machineColor: String = "#6750A4",
    val sessionName: String? = null,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val model: ModelInfo? = null,
    val status: String = AgentStatus.IDLE,
    val online: Boolean = false,
    val lastSeenAt: Long? = null,
    val openPromptCount: Int = 0,
    val lastMessagePreview: String? = null,
    val launcher: String? = null,
    val herdr: HerdrInfo? = null,
    val gentleMode: String? = null,
    val redacted: Boolean = false,
    val spawnedByHost: Boolean = false,
)

@Serializable
data class PromptOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val preview: String? = null,
)

@Serializable
data class QuestionDef(
    val question: String,
    val header: String? = null,
    val multiSelect: Boolean = false,
    val allowFreeText: Boolean = false,
    val options: List<PromptOption> = emptyList(),
)

@Serializable
data class PromptSource(
    val extension: String? = null,
    val tool: String? = null,
    val subagent: Boolean = false,
)

@Serializable
data class PromptTool(val name: String, val args: JsonElement? = null)

object PromptKind {
    const val CONFIRM = "confirm"
    const val SELECT = "select"
    const val INPUT = "input"
    const val QUESTIONNAIRE = "questionnaire"
}

object PromptCategory {
    const val PERMISSION = "permission"
    const val QUESTION = "question"
    const val DIALOG = "dialog"
}

object Danger {
    const val LOW = "low"
    const val MEDIUM = "medium"
    const val HIGH = "high"

    fun rank(d: String?): Int = when (d) { HIGH -> 2; MEDIUM -> 1; else -> 0 }
}

/** Cuerpo de `prompt.open` / `prompt.opened` y elementos de `openPrompts`. */
@Serializable
data class Prompt(
    val promptId: String,
    val agentId: String,
    val kind: String = PromptKind.SELECT,
    val category: String = PromptCategory.DIALOG,
    val title: String = "",
    val message: String? = null,
    val options: List<PromptOption> = emptyList(),
    val allowFreeText: Boolean = false,
    val placeholder: String? = null,
    val questions: List<QuestionDef>? = null,
    val source: PromptSource? = null,
    val tool: PromptTool? = null,
    val danger: String = Danger.LOW,
    val expiresAt: Long? = null,
    val openedAt: Long? = null,
)

/** Respuesta a un prompt (PROTOCOLO §5). Sólo se rellena el campo que corresponde. */
@Serializable
data class QuestionAnswer(
    val questionIndex: Int,
    val optionIds: List<String>? = null,
    val text: String? = null,
)

@Serializable
data class Answer(
    val optionIds: List<String>? = null,
    val confirmed: Boolean? = null,
    val text: String? = null,
    val cancelled: Boolean? = null,
    val answers: List<QuestionAnswer>? = null,
) {
    companion object {
        fun option(id: String) = Answer(optionIds = listOf(id))
        fun confirm(yes: Boolean) = Answer(confirmed = yes)
        fun text(t: String) = Answer(text = t)
        val Cancelled = Answer(cancelled = true)
        fun questionnaire(items: List<QuestionAnswer>) = Answer(answers = items)
    }
}

object ResolvedBy {
    const val LOCAL = "local"
    const val REMOTE = "remote"
    const val TIMEOUT = "timeout"
    const val CANCELLED = "cancelled"
}

@Serializable
data class PromptResolved(
    val promptId: String,
    val by: String = ResolvedBy.LOCAL,
    val answer: Answer? = null,
    val byDeviceId: String? = null,
)

@Serializable
data class Entry(
    val eid: String,
    val kind: String,
    val at: Long = 0,
    val msg: JsonObject? = null,
)

object EntryKind {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL_RESULT = "tool_result"
    const val BASH = "bash"
    const val CUSTOM = "custom"
    const val COMPACTION = "compaction"
    const val BRANCH_SUMMARY = "branch_summary"
    const val MODEL_CHANGE = "model_change"
}

/** Cuerpo de `agent.event` (relay → app). */
@Serializable
data class AgentEvent(
    val agentId: String,
    val epoch: String? = null,
    val seq: Long = 0,
    val e: JsonObject,
)

@Serializable
data class AgentHistory(
    val agentId: String,
    val epoch: String? = null,
    val entries: List<Entry> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val liveFromSeq: Long? = null,
)

@Serializable
data class AppHello(
    val deviceId: String,
    val app: AppInfo,
    val subscriptions: String = "all",
)

@Serializable
data class AppInfo(val version: String)

@Serializable
data class AppWelcome(
    val machines: List<MachineSummary> = emptyList(),
    val agents: List<AgentSummary> = emptyList(),
    val openPrompts: List<Prompt> = emptyList(),
)

@Serializable
data class MachinesUpdate(
    val upsert: List<MachineSummary> = emptyList(),
    val remove: List<String> = emptyList(),
)

@Serializable
data class AgentsUpdate(
    val upsert: List<AgentSummary> = emptyList(),
    val remove: List<String> = emptyList(),
)

@Serializable
data class AgentSubscribe(val agentId: String, val sinceSeq: Long? = null, val epoch: String? = null)

@Serializable
data class AgentRef(val agentId: String)

object Delivery {
    const val AUTO = "auto"
    const val STEER = "steer"
    const val FOLLOW_UP = "followUp"
}

object DeliveryMethod {
    const val AUTO = "auto"
    const val API = "api"
    const val INTERACTIVE = "interactive"
    const val HERDR = "herdr"
}

@Serializable
data class AttachmentRef(
    val blobId: String,
    val name: String,
    val mime: String,
    val size: Long,
)

@Serializable
data class UserMessage(
    val agentId: String,
    val clientMsgId: String,
    val text: String,
    val attachments: List<AttachmentRef> = emptyList(),
    val delivery: String = Delivery.AUTO,
    val method: String = DeliveryMethod.AUTO,
    val asCommand: Boolean = false,
    val deviceId: String? = null,
)

@Serializable
data class UserMessageAck(
    val clientMsgId: String,
    val disposition: String,
    val method: String? = null,
    val error: String? = null,
) {
    val isFailure: Boolean get() = disposition == "rejected"
}

@Serializable
data class PromptAnswerRequest(val promptId: String, val answer: Answer)

@Serializable
data class PromptAnswerResult(val ok: Boolean, val reason: String? = null) {
    val alreadyResolved: Boolean get() = reason == "already_resolved"
}

@Serializable
data class TerminalWait(
    val agentId: String,
    val kind: String? = null,
    val title: String? = null,
    val active: Boolean = true,
)

/** `push.notify` del canal directo: mismo cuerpo que el FCM descifrado. */
@Serializable
data class PushNotify(
    val k: String,
    val pushId: String? = null,
    val body: JsonObject,
)

@Serializable
data class PushAck(val pushId: String)

object PushKind {
    const val PROMPT = "prompt"
    const val CANCEL = "cancel"
    const val SETTLED = "settled"
    const val ERROR = "error"
    const val TERMINAL_WAIT = "terminal_wait"
}

/** Cuerpo descifrado de un push `k=prompt` (PROTOCOLO §8.1). */
@Serializable
data class PushPromptBody(
    val promptId: String,
    val agentId: String,
    val agentLabel: String = "",
    val machineId: String = "",
    val machineName: String = "",
    val machineContext: String = "personal",
    val kind: String = PromptKind.SELECT,
    val category: String = PromptCategory.DIALOG,
    val title: String = "",
    val message: String? = null,
    val options: List<PromptOption> = emptyList(),
    val allowFreeText: Boolean = false,
    val danger: String = Danger.LOW,
    val expiresAt: Long? = null,
)

@Serializable
data class PushCancelBody(val promptId: String)

@Serializable
data class PushSettledBody(
    val agentId: String,
    val agentLabel: String = "",
    val preview: String? = null,
    val machineId: String = "",
    val machineName: String = "",
    val machineContext: String = "personal",
    val message: String? = null,
)
