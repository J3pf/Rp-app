package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatReducer
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.TerminalWaitInfo
import dev.j3pf.rp.client.ws.RelayLock
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private val BATCHABLE_STREAM_EVENTS: Set<String> =
    setOf(
        "message_delta",
        "tool_execution_update",
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
    private val scope: CoroutineScope? = null,
    private val frameBatchMs: Long = 32L,
    private val delayMs: suspend (Long) -> Unit = { delay(it) },
) {
    private val batchLock = RelayLock()
    private var pendingStreamState: ChatState? = null
    private var flushJob: Job? = null
    private var lastUiEmitMs: Long = Long.MIN_VALUE / 2

    fun flushPending() {
        val buffered =
            batchLock.withLock {
                flushJob?.cancel()
                flushJob = null
                val s = pendingStreamState
                pendingStreamState = null
                if (s != null) lastUiEmitMs = now()
                s
            }
        if (buffered != null) {
            updateState { buffered }
            if (buffered.items.isNotEmpty() || buffered.streaming != null) onSyncingResolved()
        }
    }

    suspend fun dispatch(env: Envelope) {
        when (env.t) {
            MsgType.AGENT_HISTORY -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val h = runCatching { env.body<AgentHistory>() }.getOrNull() ?: return
                flushPending()
                val epochChanged = h.epoch != null && h.epoch != getState().epoch
                updateState { ChatReducer.applyHistory(it, h, now()) }
                onHistoryApplied()
                onHistory(h, epochChanged)
            }

            MsgType.AGENT_EVENT -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val ev = runCatching { env.body<AgentEvent>() }.getOrNull() ?: return
                val evType = ev.e.str("type")
                val batchScope = scope
                if (batchScope != null && frameBatchMs > 0L && evType in BATCHABLE_STREAM_EVENTS) {
                    val currentNow = now()
                    var emitImmediately: ChatState? = null
                    var triggerResync = false
                    batchLock.withLock {
                        val before = pendingStreamState ?: getState()
                        val after = ChatReducer.reduce(before, ev, currentNow)
                        if (after.needsResync && !before.needsResync) {
                            flushJob?.cancel()
                            flushJob = null
                            pendingStreamState = null
                            lastUiEmitMs = currentNow
                            emitImmediately = after
                            triggerResync = true
                        } else {
                            val elapsed = currentNow - lastUiEmitMs
                            if (elapsed >= frameBatchMs && flushJob == null) {
                                pendingStreamState = null
                                lastUiEmitMs = currentNow
                                emitImmediately = after
                            } else {
                                pendingStreamState = after
                                if (flushJob == null) {
                                    val waitMs = (frameBatchMs - elapsed).coerceIn(1L, frameBatchMs)
                                    flushJob =
                                        batchScope.launch {
                                            delayMs(waitMs)
                                            flushPending()
                                        }
                                }
                            }
                        }
                    }
                    emitImmediately?.let { s ->
                        updateState { s }
                        if (s.items.isNotEmpty() || s.streaming != null) onSyncingResolved()
                        if (triggerResync) onResyncNeeded()
                    }
                    return
                }

                flushPending()
                val before = getState()
                val after = ChatReducer.reduce(before, ev, now())
                updateState { after }
                if (after.items.isNotEmpty()) onSyncingResolved()
                if (after.lastSeq != before.lastSeq && evType in PERSISTED_EVENTS) {
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
                flushPending()
                updateState { ChatReducer.applyPromptResolved(it, r) }
            }

            MsgType.USER_MESSAGE_ACK -> {
                val ack = runCatching { env.body<UserMessageAck>() }.getOrNull() ?: return
                flushPending()
                updateState { ChatReducer.applyUserMessageAck(it, ack) }
            }

            MsgType.TERMINAL_WAIT -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val w = runCatching { env.body<TerminalWait>() }.getOrNull() ?: return
                flushPending()
                updateState { it.copy(terminalWait = if (w.active) TerminalWaitInfo(w.kind, w.title) else null) }
            }
        }
    }

    fun dispatchPrompt(p: Prompt) {
        flushPending()
        updateState { ChatReducer.applyPromptOpened(it, p) }
    }
}
