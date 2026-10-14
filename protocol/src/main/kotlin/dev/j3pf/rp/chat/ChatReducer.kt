package dev.j3pf.rp.chat

import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Entry
import dev.j3pf.rp.protocol.EntryKind
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.arr
import dev.j3pf.rp.protocol.bool
import dev.j3pf.rp.protocol.int
import dev.j3pf.rp.protocol.long
import dev.j3pf.rp.protocol.obj
import dev.j3pf.rp.protocol.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

/**
 * Reductor puro `reduce(state, event) → state` del chat (PLAN-APP §4.3).
 *
 * Reglas:
 *  - `seq` ≤ `lastSeq` (misma `epoch`) se ignora: duplicados y reentregas tras reconectar.
 *  - `seq` > `lastSeq + 1` marca [ChatState.needsResync] pero se aplica igualmente.
 *  - Un `epoch` nuevo reinicia el contador (cambio de sesión: `/new`, `/resume`, reload).
 *  - Los deltas actualizan sólo el mensaje en streaming (en memoria); `message_end` es autoritativo.
 *  - Los items se desduplican por clave (`rol:timestamp`), así el historial y los eventos pueden solaparse.
 */
object ChatReducer {
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    // ───────────────────────────── Entradas ─────────────────────────────

    /** Aplica un evento en vivo (o reproducido desde el historial). [now] se inyecta para mantener la pureza. */
    fun reduce(state: ChatState, event: AgentEvent, now: Long = 0L): ChatState {
        var s = state
        if (event.epoch != null && event.epoch != s.epoch) {
            s = s.copy(epoch = event.epoch, lastSeq = 0, streaming = null, working = false, needsResync = false)
        }
        if (event.seq in 1..s.lastSeq) return s // duplicado o antiguo
        if (event.seq > s.lastSeq + 1 && s.lastSeq > 0) s = s.copy(needsResync = true)
        if (event.seq > 0) s = s.copy(lastSeq = event.seq)
        return applyEvent(s, event.e, event.seq, now)
    }

    /** Sustituye los items por el historial normalizado (`agent.snapshot` / `agent.history.entries`). */
    fun applyEntries(state: ChatState, entries: List<Entry>, epoch: String? = state.epoch): ChatState {
        var s = state.copy(items = emptyList(), tools = emptyMap(), streaming = null, epoch = epoch ?: state.epoch)
        for (e in entries) s = applyEntry(s, e)
        return s.copy(needsResync = false)
    }

    /** `agent.history`: entradas + eventos posteriores. Los eventos con `seq` ≤ lastSeq se descartan. */
    fun applyHistory(state: ChatState, h: AgentHistory, now: Long = 0L): ChatState {
        var s = state
        val epochChanged = h.epoch != null && h.epoch != s.epoch
        if (h.entries.isNotEmpty() || epochChanged) {
            s = applyEntries(s, h.entries, h.epoch)
            s = s.copy(lastSeq = if (epochChanged) 0 else s.lastSeq)
        }
        for (ev in h.events.sortedBy { it.seq }) s = reduce(s, ev.copy(epoch = ev.epoch ?: h.epoch), now)
        h.liveFromSeq?.let { live -> if (live - 1 > s.lastSeq) s = s.copy(lastSeq = live - 1) }
        return s.copy(needsResync = false)
    }

    fun applyPromptOpened(state: ChatState, prompt: Prompt): ChatState {
        val key = "prompt:${prompt.promptId}"
        if (state.items.any { it.key == key }) return state
        return state.copy(items = state.items + PromptCard(key, prompt))
    }

    fun applyPromptResolved(state: ChatState, r: PromptResolved): ChatState {
        val key = "prompt:${r.promptId}"
        return state.copy(items = state.items.map {
            if (it.key == key && it is PromptCard) it.copy(resolved = true, resolvedBy = r.by, answer = r.answer) else it
        })
    }

    /** Registra un texto enviado desde el móvil para marcar luego el mensaje con 📱. */
    fun markLocalSent(state: ChatState, text: String): ChatState = state.copy(localSent = state.localSent + text.trim())

    // ───────────────────────────── Eventos ─────────────────────────────

    private fun applyEvent(s: ChatState, e: JsonObject, seq: Long, now: Long): ChatState =
        when (e.str("type")) {
            "agent_start" -> s.copy(working = true)
            "agent_settled" -> settle(s, aborted = e.bool("aborted") == true)
            "message_start" -> messageStart(s, e)
            "message_delta" -> messageDelta(s, e)
            "message_end" -> messageEnd(s, e.obj("message"), seq, now)
            "tool_execution_start" -> toolStart(s, e, now)
            "tool_execution_update" -> toolUpdate(s, e, now)
            "tool_execution_end" -> toolEnd(s, e, now)
            "session_info_changed" -> {
                val name = e.str("name") ?: e.obj("session")?.str("name")
                if (name == null) s else s.copy(
                    sessionName = name,
                    items = s.items + SystemNote("note:$seq:rename", NoteKind.SESSION_RENAMED, "Sesión renombrada: $name"),
                )
            }
            "model_select" -> {
                val label = modelLabel(e.obj("model")) ?: e.str("model")
                if (label == null) s else s.copy(
                    model = label,
                    items = s.items + SystemNote("note:$seq:model", NoteKind.MODEL_CHANGE, "Modelo: $label"),
                )
            }
            "thinking_level_select" -> {
                val level = e.str("level") ?: e.str("thinking")
                if (level == null) s else s.copy(
                    thinkingLevel = level,
                    items = s.items + SystemNote("note:$seq:thinking", NoteKind.THINKING_LEVEL, "Razonamiento: $level"),
                )
            }
            "session_compact" -> s.copy(
                items = s.items + SystemNote(
                    "note:$seq:compact", NoteKind.COMPACTION, "Contexto compactado",
                    detail = e.str("summary") ?: e.obj("entry")?.str("summary"),
                ),
            )
            "terminal_wait" -> {
                val active = e.bool("active") ?: true
                s.copy(terminalWait = if (active) TerminalWaitInfo(e.str("kind"), e.str("title")) else null)
            }
            else -> s // turn_start/turn_end y desconocidos: sin efecto
        }

    private fun settle(s: ChatState, aborted: Boolean): ChatState {
        val st = s.streaming
        val items = if (st != null && st.blocks.isNotEmpty()) {
            s.items + st.copy(streaming = false, stopReason = if (aborted) "aborted" else st.stopReason)
        } else s.items
        val tools = s.tools.mapValues { (_, t) ->
            if (t.status == ToolStatus.RUNNING) t.copy(status = ToolStatus.ERROR, isError = true, result = t.result ?: "Interrumpido") else t
        }
        return s.copy(working = false, streaming = null, items = items, tools = tools, terminalWait = null)
    }

    private fun messageStart(s: ChatState, e: JsonObject): ChatState {
        val msg = e.obj("message")
        return when (msg?.str("role")) {
            "assistant", null -> if (s.streaming == null) {
                s.copy(streaming = AssistantMsg(key = "stream", streaming = true, model = msg?.str("model")))
            } else s
            else -> s // los mensajes de usuario / toolResult se materializan en message_end
        }
    }

    private fun messageDelta(s: ChatState, e: JsonObject): ChatState {
        val idx = e.int("contentIndex") ?: 0
        if (idx < 0 || idx > 64) return s
        val kind = e.str("kind") ?: "text"
        val delta = e.str("delta") ?: return s
        val base = s.streaming ?: AssistantMsg(key = "stream", streaming = true)
        val blocks = base.blocks.toMutableList()
        while (blocks.size <= idx) blocks.add(Block.Text(""))
        blocks[idx] = when (kind) {
            "thinking" -> Block.Thinking(((blocks[idx] as? Block.Thinking)?.text ?: "") + delta)
            "toolcall" -> {
                val prev = blocks[idx] as? Block.ToolCall ?: Block.ToolCall(null, null)
                val raw = prev.rawArgs + delta
                prev.copy(
                    toolCallId = e.str("toolCallId") ?: prev.toolCallId,
                    name = e.str("name") ?: e.str("toolName") ?: prev.name,
                    rawArgs = raw,
                    args = parsePartial(raw) ?: prev.args,
                )
            }
            else -> Block.Text(((blocks[idx] as? Block.Text)?.text ?: "") + delta)
        }
        return s.copy(streaming = base.copy(blocks = blocks))
    }

    private fun messageEnd(s: ChatState, msg: JsonObject?, seq: Long, now: Long): ChatState {
        if (msg == null) return s
        return when (msg.str("role")) {
            "assistant" -> {
                val item = assistantFrom(msg, keyOf(msg, "assistant", "seq$seq"))
                s.copy(streaming = null, items = upsert(s.items, item), tools = withCalls(s.tools, item, now))
            }
            "user" -> userMessage(s, msg, "seq$seq")
            "toolResult" -> toolResultMessage(s, msg)
            else -> s
        }
    }

    // ─────────────────────── Herramientas ───────────────────────

    private fun toolStart(s: ChatState, e: JsonObject, now: Long): ChatState {
        val id = e.str("toolCallId") ?: return s
        val prev = s.tools[id]
        val run = (prev ?: ToolRun(id, e.str("toolName") ?: e.str("name") ?: "?", startedAt = now)).copy(
            name = e.str("toolName") ?: e.str("name") ?: prev?.name ?: "?",
            args = e["args"] ?: prev?.args,
            status = ToolStatus.RUNNING,
            startedAt = now,
        )
        return s.copy(tools = s.tools + (id to run))
    }

    private fun toolUpdate(s: ChatState, e: JsonObject, now: Long): ChatState {
        val id = e.str("toolCallId") ?: return s
        val prev = s.tools[id] ?: ToolRun(id, e.str("toolName") ?: "?", startedAt = now)
        val text = extractText(e["partialResult"]) ?: prev.partial
        return s.copy(tools = s.tools + (id to prev.copy(partial = text, args = e["args"] ?: prev.args)))
    }

    private fun toolEnd(s: ChatState, e: JsonObject, now: Long): ChatState {
        val id = e.str("toolCallId") ?: return s
        val prev = s.tools[id] ?: ToolRun(id, e.str("toolName") ?: "?", startedAt = now)
        val result = e["result"]
        val isError = e.bool("isError") ?: result.obj()?.bool("isError") ?: false
        val run = prev.copy(
            name = e.str("toolName") ?: prev.name,
            status = if (isError) ToolStatus.ERROR else ToolStatus.OK,
            isError = isError,
            result = extractText(result) ?: prev.result,
            truncated = result.obj()?.bool("truncated") ?: e.bool("truncated") ?: false,
            partial = null,
            durationMs = if (prev.startedAt > 0 && now >= prev.startedAt) now - prev.startedAt else prev.durationMs,
        )
        return s.copy(tools = s.tools + (id to run))
    }

    private fun toolResultMessage(s: ChatState, msg: JsonObject): ChatState {
        val id = msg.str("toolCallId") ?: return s
        val prev = s.tools[id]
        if (prev != null && prev.status != ToolStatus.RUNNING) return s
        val isError = msg.bool("isError") ?: false
        val run = (prev ?: ToolRun(id, msg.str("toolName") ?: "?")).copy(
            status = if (isError) ToolStatus.ERROR else ToolStatus.OK,
            isError = isError,
            result = extractText(msg["content"]),
            partial = null,
        )
        return s.copy(tools = s.tools + (id to run))
    }

    private fun withCalls(tools: Map<String, ToolRun>, item: AssistantMsg, now: Long): Map<String, ToolRun> {
        var t = tools
        for (b in item.blocks) {
            if (b is Block.ToolCall && b.toolCallId != null) {
                val prev = t[b.toolCallId]
                t = t + (b.toolCallId to (prev?.copy(args = b.args ?: prev.args, name = b.name ?: prev.name)
                    ?: ToolRun(b.toolCallId, b.name ?: "?", b.args, startedAt = now)))
            }
        }
        return t
    }

    // ─────────────────────── Historial ───────────────────────

    private fun applyEntry(s: ChatState, entry: Entry): ChatState {
        val msg = entry.msg ?: JsonObject(emptyMap())
        return when (entry.kind) {
            EntryKind.USER -> userMessage(s, msg, "eid${entry.eid}", entry.at)
            EntryKind.ASSISTANT -> {
                val item = assistantFrom(msg, keyOf(msg, "assistant", "eid${entry.eid}"), entry.at)
                s.copy(items = upsert(s.items, item), tools = withCalls(s.tools, item, 0))
            }
            EntryKind.TOOL_RESULT -> toolResultMessage(s, msg)
            EntryKind.BASH -> {
                val cmd = msg.str("command") ?: ""
                val out = msg.str("output") ?: extractText(msg["content"]) ?: ""
                s.copy(items = s.items + SystemNote("eid${entry.eid}", NoteKind.BASH, "\$ $cmd", detail = out.ifEmpty { null }))
            }
            EntryKind.COMPACTION -> s.copy(
                items = s.items + SystemNote("eid${entry.eid}", NoteKind.COMPACTION, "Contexto compactado", detail = msg.str("summary")),
            )
            EntryKind.BRANCH_SUMMARY -> s.copy(
                items = s.items + SystemNote("eid${entry.eid}", NoteKind.BRANCH_SUMMARY, "Resumen de rama", detail = msg.str("summary")),
            )
            EntryKind.MODEL_CHANGE -> {
                val label = modelLabel(msg.obj("model")) ?: msg.str("modelId") ?: msg.str("model")
                s.copy(
                    model = label ?: s.model,
                    items = s.items + SystemNote("eid${entry.eid}", NoteKind.MODEL_CHANGE, "Modelo: ${label ?: "?"}"),
                )
            }
            else -> {
                val text = msg.str("text") ?: extractText(msg["content"])
                if (text.isNullOrBlank()) s else s.copy(items = s.items + SystemNote("eid${entry.eid}", NoteKind.CUSTOM, text))
            }
        }
    }

    private fun userMessage(s: ChatState, msg: JsonObject, fallback: String, at: Long = 0): ChatState {
        val (text, images) = userContent(msg["content"])
        val mobile = msg.bool("rp") == true || msg.str("origin") in setOf("rp", "mobile") ||
            s.localSent.contains(text.trim())
        val item = UserMsg(
            key = keyOf(msg, "user", fallback),
            text = text,
            at = msg.long("timestamp") ?: at,
            fromMobile = mobile,
            imageCount = images,
        )
        if (s.items.any { it.key == item.key }) return s
        return s.copy(items = s.items + item, localSent = s.localSent - text.trim())
    }

    private fun assistantFrom(msg: JsonObject, key: String, at: Long = 0): AssistantMsg {
        val blocks = mutableListOf<Block>()
        val content = msg["content"]
        if (content is JsonPrimitive) {
            content.str()?.let { blocks.add(Block.Text(it)) }
        } else {
            for (b in content.arr().orEmpty()) {
                val o = b.obj() ?: continue
                when (o.str("type")) {
                    "text" -> blocks.add(Block.Text(o.str("text") ?: ""))
                    "thinking" -> blocks.add(Block.Thinking(o.str("thinking") ?: o.str("text") ?: ""))
                    "toolCall" -> blocks.add(
                        Block.ToolCall(o.str("id"), o.str("name"), o["arguments"] ?: o["args"]),
                    )
                }
            }
        }
        return AssistantMsg(
            key = key,
            blocks = blocks,
            at = msg.long("timestamp") ?: at,
            model = msg.str("model"),
            stopReason = msg.str("stopReason"),
            error = msg.str("errorMessage"),
        )
    }

    // ─────────────────────── Utilidades ───────────────────────

    private fun keyOf(msg: JsonObject, role: String, fallback: String): String {
        val ts = msg.long("timestamp")
        return if (ts != null) "$role:$ts" else "$role:$fallback"
    }

    private fun upsert(items: List<ChatItem>, item: ChatItem): List<ChatItem> {
        if (items.isEmpty()) return listOf(item)
        if (items.last().key == item.key) return items.toMutableList().also { it[items.lastIndex] = item }
        val i = items.indexOfFirst { it.key == item.key }
        return if (i >= 0) items.toMutableList().also { it[i] = item } else items + item
    }

    private fun modelLabel(model: JsonObject?): String? = model?.str("id") ?: model?.str("name")

    /** Texto y número de imágenes de `content` (cadena o lista de bloques). */
    fun userContent(content: JsonElement?): Pair<String, Int> {
        if (content is JsonPrimitive) return (content.str() ?: "") to 0
        var images = 0
        val text = content.arr().orEmpty().mapNotNull {
            val o = it.obj() ?: return@mapNotNull null
            when (o.str("type")) {
                "text" -> o.str("text")
                "image", "image_ref" -> { images++; null }
                else -> null
            }
        }.joinToString("\n")
        return text to images
    }

    /** Extrae texto de un resultado de herramienta: cadena, bloques `[{type:text,text}]` u objeto con `content`/`output`. */
    fun extractText(el: JsonElement?): String? = when (el) {
        null -> null
        is JsonPrimitive -> el.str()
        is kotlinx.serialization.json.JsonArray -> el.mapNotNull { b ->
            val o = b.obj()
            if (o != null) o.str("text") else b.str()
        }.joinToString("\n").ifEmpty { null }
        is JsonObject -> extractText(el["content"]) ?: el.str("text") ?: el.str("output") ?: el.str("stdout")
    }

    /** Intenta parsear JSON de argumentos que puede estar incompleto; devuelve `null` si no es válido todavía. */
    private fun parsePartial(raw: String): JsonElement? {
        if (raw.isBlank() || raw.length > 32_768) return null
        val trimmed = raw.trimEnd()
        val last = trimmed.lastOrNull()
        if (last == '}' || last == ']') {
            runCatching { lenient.parseToJsonElement(trimmed) }.getOrNull()?.let { return it }
        }
        return runCatching { lenient.parseToJsonElement(closeJson(raw)) }.getOrNull()
    }

    private fun closeJson(raw: String): String {
        val stack = CharArray(raw.length)
        var top = 0
        var inStr = false
        var esc = false
        for (i in 0 until raw.length) {
            val c = raw[i]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '{' -> stack[top++] = '}'
                '[' -> stack[top++] = ']'
                '}', ']' -> if (top > 0) top--
            }
        }
        val sb = StringBuilder(raw.length + top + 1).append(raw)
        if (inStr) sb.append('"')
        while (top > 0) sb.append(stack[--top])
        return sb.toString()
    }
}
