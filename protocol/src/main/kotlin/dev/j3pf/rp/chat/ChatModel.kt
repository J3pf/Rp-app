package dev.j3pf.rp.chat

import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.SubagentInfo
import dev.j3pf.rp.protocol.TodoItem
import kotlinx.serialization.json.JsonElement

/** Elementos de la conversación tal y como los pinta la UI. */
sealed interface ChatItem {
    val key: String
}

data class UserMsg(
    override val key: String,
    val text: String,
    val at: Long = 0,
    /** Mensaje enviado desde esta app (se muestra con 📱). */
    val fromMobile: Boolean = false,
    val imageCount: Int = 0,
    val failed: Boolean = false,
    val clientMsgId: String? = null,
) : ChatItem

sealed interface Block {
    data class Text(
        val text: String,
    ) : Block

    data class Thinking(
        val text: String,
    ) : Block

    /**
     * Llamada a herramienta; el estado/resultado vive en [ChatState.tools] bajo [toolCallId].
     * [rawArgs] acumula el JSON parcial mientras llegan deltas.
     */
    data class ToolCall(
        val toolCallId: String?,
        val name: String?,
        val args: JsonElement? = null,
        val rawArgs: String = "",
    ) : Block
}

data class AssistantMsg(
    override val key: String,
    val blocks: List<Block> = emptyList(),
    val streaming: Boolean = false,
    val at: Long = 0,
    val model: String? = null,
    val stopReason: String? = null,
    val error: String? = null,
) : ChatItem {
    val plainText: String get() = blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
}

enum class ToolStatus { RUNNING, OK, ERROR }

data class ToolRun(
    val toolCallId: String,
    val name: String,
    val args: JsonElement? = null,
    val status: ToolStatus = ToolStatus.RUNNING,
    val partial: String? = null,
    val result: String? = null,
    val isError: Boolean = false,
    val truncated: Boolean = false,
    val startedAt: Long = 0,
    val durationMs: Long? = null,
    /** Herramientas anidadas (p. ej. subagentes) si el protocolo las informa. */
    val children: List<ToolRun> = emptyList(),
    /** Telemetría enriquecida de subagente estilo gentle shell si aplica. */
    val subagent: SubagentInfo? = null,
)

/** Tarjeta de subagente incrustada en el chat estilo gentle shell. */
data class SubagentCardItem(
    override val key: String,
    val info: SubagentInfo,
) : ChatItem

/** Tarjeta de prompt incrustada en el chat (mismo contenido que la hoja de respuesta). */
data class PromptCard(
    override val key: String,
    val prompt: Prompt,
    val resolved: Boolean = false,
    val resolvedBy: String? = null,
    val answer: Answer? = null,
) : ChatItem

enum class NoteKind { COMPACTION, MODEL_CHANGE, SESSION_RENAMED, THINKING_LEVEL, BRANCH_SUMMARY, BASH, CUSTOM, INFO }

data class SystemNote(
    override val key: String,
    val kind: NoteKind,
    val text: String,
    val detail: String? = null,
) : ChatItem

data class TerminalWaitInfo(
    val kind: String?,
    val title: String?,
)

data class ChatState(
    val items: List<ChatItem> = emptyList(),
    val tools: Map<String, ToolRun> = emptyMap(),
    /** Mensaje del asistente en streaming; sólo en memoria, nunca se persiste. */
    val streaming: AssistantMsg? = null,
    val epoch: String? = null,
    val lastSeq: Long = 0,
    val working: Boolean = false,
    val terminalWait: TerminalWaitInfo? = null,
    /** Se detectó un hueco de `seq`: el repositorio debe volver a suscribirse con `sinceSeq`. */
    val needsResync: Boolean = false,
    val sessionName: String? = null,
    val model: String? = null,
    val thinkingLevel: String? = null,
    /** Textos enviados desde el móvil aún no vistos en el historial (para marcar 📱). */
    val localSent: Set<String> = emptySet(),
    /** Tareas y TODOs actuales del agente. */
    val todos: List<TodoItem> = emptyList(),
    /** Telemetría de subagentes indexados por taskId. */
    val subagents: Map<String, SubagentInfo> = emptyMap(),
) {
    /** Items + mensaje en streaming, listos para pintar. */
    val display: List<ChatItem> get() = if (streaming != null) items + streaming else items
}
