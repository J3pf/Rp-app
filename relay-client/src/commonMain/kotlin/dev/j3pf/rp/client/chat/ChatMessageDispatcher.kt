package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatReducer
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.TerminalWaitInfo
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.TerminalWait
import dev.j3pf.rp.protocol.UserMessageAck
import dev.j3pf.rp.protocol.obj
import dev.j3pf.rp.protocol.str

val PERSISTED_EVENTS: Set<String> =
    setOf(
        "agent_start",
        "agent_settled",
        "message_end",
        "tool_execution_start",
        "tool_execution_end",
        "session_info_changed",
        "model_select",
        "thinking_level_select",
        "session_compact",
        "todo_update",
        "subagent_update",
    )

class ChatMessageDispatcher(
    private val agentId: String,
    private val getState: () -> ChatState,
    private val updateState: ((ChatState) -> ChatState) -> Unit,
    private val onHistory: suspend (AgentHistory, Boolean) -> Unit,
    private val onPersistEvent: suspend (AgentEvent) -> Unit,
    private val onResyncNeeded: () -> Unit,
    private val onHistoryApplied: () -> Unit,
    private val onSyncingResolved: () -> Unit,
    private val now: () -> Long,
) {
    suspend fun dispatch(env: Envelope) {
        when (env.t) {
            MsgType.AGENT_HISTORY -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val h = runCatching { env.body<AgentHistory>() }.getOrNull() ?: return
                val epochChanged = h.epoch != null && h.epoch != getState().epoch
                updateState { ChatReducer.applyHistory(it, h, now()) }
                onHistoryApplied()
                onHistory(h, epochChanged)
            }

            MsgType.AGENT_EVENT -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val ev = runCatching { env.body<AgentEvent>() }.getOrNull() ?: return
                val before = getState()
                val after = ChatReducer.reduce(before, ev, now())
                updateState { after }
                if (after.items.isNotEmpty()) onSyncingResolved()
                if (after.lastSeq != before.lastSeq && ev.e.str("type") in PERSISTED_EVENTS) {
                    onPersistEvent(ev)
                }
                if (after.needsResync && !before.needsResync) {
                    onResyncNeeded()
                }
            }

            MsgType.PROMPT_OPENED -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val p = runCatching { env.body<Prompt>() }.getOrNull() ?: return
                dispatchPrompt(p)
            }

            MsgType.PROMPT_RESOLVED -> {
                val r = runCatching { env.body<PromptResolved>() }.getOrNull() ?: return
                updateState { ChatReducer.applyPromptResolved(it, r) }
            }

            MsgType.USER_MESSAGE_ACK -> {
                val ack = runCatching { env.body<UserMessageAck>() }.getOrNull() ?: return
                updateState { ChatReducer.applyUserMessageAck(it, ack) }
            }

            MsgType.TERMINAL_WAIT -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val w = runCatching { env.body<TerminalWait>() }.getOrNull() ?: return
                updateState { it.copy(terminalWait = if (w.active) TerminalWaitInfo(w.kind, w.title) else null) }
            }
        }
    }

    fun dispatchPrompt(p: Prompt) {
        updateState { ChatReducer.applyPromptOpened(it, p) }
    }
}
