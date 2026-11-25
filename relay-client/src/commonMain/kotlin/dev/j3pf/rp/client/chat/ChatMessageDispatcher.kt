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
import dev.j3pf.rp.protocol.int
import dev.j3pf.rp.protocol.obj
import dev.j3pf.rp.protocol.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    private val pendingStreamEvents = mutableListOf<Pair<AgentEvent, Long>>()
    private var flushJob: Job? = null
    private var lastUiEmitMs: Long = Long.MIN_VALUE / 2
    private var bufferedEpoch: String? = null
    private var bufferedLastSeq: Long = 0L
    private var bufferedNeedsResync: Boolean = false
    private var hasBufferedTracking: Boolean = false

    private fun applyBatchEvents(events: List<Pair<AgentEvent, Long>>): ChatState {
        var latest = getState()
        updateState { current ->
            var s = current
            val steps = coalesceStreamBatch(s.epoch, s.lastSeq, events)
            for (step in steps) {
                val base = step.preAdvanceLastSeq?.let { s.copy(lastSeq = it) } ?: s
                s = ChatReducer.reduce(base, step.event, step.timestamp)
            }
            latest = s
            s
        }
        return latest
    }

    /** Must be called while holding [batchLock]. */
    private fun flushPendingLocked(): ChatState? {
        flushJob?.cancel()
        flushJob = null
        hasBufferedTracking = false
        if (pendingStreamEvents.isEmpty()) return null
        val copy = pendingStreamEvents.toList()
        pendingStreamEvents.clear()
        lastUiEmitMs = now()
        return applyBatchEvents(copy)
    }

    fun flushPending() {
        val applied = batchLock.withLock { flushPendingLocked() }
        if (applied != null && (applied.items.isNotEmpty() || applied.streaming != null)) {
            onSyncingResolved()
        }
    }

    suspend fun dispatch(env: Envelope) {
        when (env.t) {
            MsgType.AGENT_HISTORY -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val h = runCatching { env.body<AgentHistory>() }.getOrNull() ?: return
                val currentNow = now()
                val epochChanged =
                    batchLock.withLock {
                        flushPendingLocked()
                        val changed = h.epoch != null && h.epoch != getState().epoch
                        updateState { ChatReducer.applyHistory(it, h, currentNow) }
                        changed
                    }
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
                    var appliedState: ChatState? = null
                    var triggerResync = false
                    batchLock.withLock {
                        val base = getState()
                        var epoch = if (hasBufferedTracking) bufferedEpoch else base.epoch
                        var lastSeq = if (hasBufferedTracking) bufferedLastSeq else base.lastSeq
                        var needsResync = if (hasBufferedTracking) bufferedNeedsResync else base.needsResync

                        val prevNeedsResync = needsResync
                        if (ev.epoch != null && ev.epoch != epoch) {
                            epoch = ev.epoch
                            lastSeq = 0L
                            needsResync = false
                        }
                        val isDuplicate = ev.seq in 1..lastSeq
                        if (!isDuplicate) {
                            if (ev.seq > lastSeq + 1 && lastSeq > 0) {
                                needsResync = true
                            }
                            if (ev.seq > 0) {
                                lastSeq = ev.seq
                            }
                        }
                        val resyncNow = needsResync && !prevNeedsResync
                        val elapsed = currentNow - lastUiEmitMs

                        if (resyncNow || (elapsed >= frameBatchMs && flushJob == null)) {
                            flushJob?.cancel()
                            flushJob = null
                            val events = pendingStreamEvents.toList() + (ev to currentNow)
                            pendingStreamEvents.clear()
                            hasBufferedTracking = false
                            lastUiEmitMs = currentNow
                            appliedState = applyBatchEvents(events)
                            triggerResync = resyncNow
                        } else {
                            pendingStreamEvents.add(ev to currentNow)
                            bufferedEpoch = epoch
                            bufferedLastSeq = lastSeq
                            bufferedNeedsResync = needsResync
                            hasBufferedTracking = true
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
                    appliedState?.let { s ->
                        if (s.items.isNotEmpty() || s.streaming != null) onSyncingResolved()
                        if (triggerResync) onResyncNeeded()
                    }
                    return
                }

                val currentNow = now()
                var flushedState: ChatState? = null
                var before = ChatState()
                var after = before
                batchLock.withLock {
                    flushedState = flushPendingLocked()
                    before = getState()
                    after = before
                    updateState { current ->
                        before = current
                        after = ChatReducer.reduce(current, ev, currentNow)
                        after
                    }
                }
                val flushedResolved = flushedState?.let { it.items.isNotEmpty() || it.streaming != null } == true
                if (flushedResolved || after.items.isNotEmpty()) onSyncingResolved()
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
                val flushed =
                    batchLock.withLock {
                        val applied = flushPendingLocked()
                        updateState { ChatReducer.applyPromptResolved(it, r) }
                        applied
                    }
                if (flushed != null && (flushed.items.isNotEmpty() || flushed.streaming != null)) onSyncingResolved()
            }

            MsgType.USER_MESSAGE_ACK -> {
                val ack = runCatching { env.body<UserMessageAck>() }.getOrNull() ?: return
                val flushed =
                    batchLock.withLock {
                        val applied = flushPendingLocked()
                        updateState { ChatReducer.applyUserMessageAck(it, ack) }
                        applied
                    }
                if (flushed != null && (flushed.items.isNotEmpty() || flushed.streaming != null)) onSyncingResolved()
            }

            MsgType.TERMINAL_WAIT -> {
                if (env.d.obj()?.str("agentId") != agentId) return
                val w = runCatching { env.body<TerminalWait>() }.getOrNull() ?: return
                val flushed =
                    batchLock.withLock {
                        val applied = flushPendingLocked()
                        updateState { it.copy(terminalWait = if (w.active) TerminalWaitInfo(w.kind, w.title) else null) }
                        applied
                    }
                if (flushed != null && (flushed.items.isNotEmpty() || flushed.streaming != null)) onSyncingResolved()
            }
        }
    }

    fun dispatchPrompt(p: Prompt) {
        val flushed =
            batchLock.withLock {
                val applied = flushPendingLocked()
                updateState { ChatReducer.applyPromptOpened(it, p) }
                applied
            }
        if (flushed != null && (flushed.items.isNotEmpty() || flushed.streaming != null)) onSyncingResolved()
    }

    internal data class CoalescedBatchStep(
        val event: AgentEvent,
        val timestamp: Long,
        val preAdvanceLastSeq: Long? = null,
    )

    internal companion object {
        fun coalesceStreamBatch(
            initialEpoch: String?,
            initialLastSeq: Long,
            events: List<Pair<AgentEvent, Long>>,
        ): List<CoalescedBatchStep> {
            if (events.isEmpty()) return emptyList()
            var epoch = initialEpoch
            var lastSeq = initialLastSeq
            val out = ArrayList<CoalescedBatchStep>(events.size)
            var i = 0
            while (i < events.size) {
                val (ev0, ts0) = events[i]
                val e0 = ev0.e
                val ev0Type = e0.str("type")
                val ev0Delta = if (ev0Type == "message_delta") e0.str("delta") else null

                val ev0ChangesEpoch = ev0.epoch != null && ev0.epoch != epoch
                if (ev0ChangesEpoch) {
                    epoch = ev0.epoch
                    lastSeq = 0L
                }

                if (ev0.seq in 1..lastSeq) {
                    i++
                    continue
                }

                val ev0Gap = ev0.seq > lastSeq + 1L && lastSeq > 0L
                if (ev0.seq > 0L) {
                    lastSeq = ev0.seq
                }

                if (ev0Delta == null) {
                    out.add(CoalescedBatchStep(ev0, ts0))
                    i++
                    continue
                }

                val contentIndex = e0.int("contentIndex") ?: 0
                val kind = e0.str("kind") ?: "text"
                var sb: StringBuilder? = null
                var lastTs = ts0
                var runLastSeq = ev0.seq
                var latestToolCallId = e0.str("toolCallId")
                var latestName = e0.str("name")
                var latestToolName = e0.str("toolName")
                var mergedCount = 1

                var j = i + 1
                while (j < events.size) {
                    val (evNext, tsNext) = events[j]
                    val eNext = evNext.e
                    if (evNext.epoch != null && evNext.epoch != epoch) break
                    if (evNext.seq in 1..lastSeq) {
                        j++
                        continue
                    }
                    if (eNext.str("type") != "message_delta") break
                    val nextDelta = eNext.str("delta") ?: break
                    val nextIdx = eNext.int("contentIndex") ?: 0
                    val nextKind = eNext.str("kind") ?: "text"
                    if (nextIdx != contentIndex || nextKind != kind) break
                    if (evNext.seq > lastSeq + 1L && lastSeq > 0L) break

                    val builder =
                        sb ?: StringBuilder(ev0Delta.length + nextDelta.length)
                            .append(ev0Delta)
                            .also { sb = it }
                    builder.append(nextDelta)
                    if (evNext.seq > 0L) {
                        lastSeq = evNext.seq
                        runLastSeq = evNext.seq
                    }
                    lastTs = tsNext
                    eNext.str("toolCallId")?.let { latestToolCallId = it }
                    eNext.str("name")?.let { latestName = it }
                    eNext.str("toolName")?.let { latestToolName = it }
                    mergedCount++
                    j++
                }

                if (mergedCount == 1) {
                    out.add(CoalescedBatchStep(ev0, ts0))
                } else {
                    val mergedFields = LinkedHashMap<String, JsonElement>(e0.size + 1)
                    mergedFields.putAll(e0)
                    mergedFields["delta"] = JsonPrimitive(sb!!.toString())
                    latestToolCallId?.let { mergedFields["toolCallId"] = JsonPrimitive(it) }
                    latestName?.let { mergedFields["name"] = JsonPrimitive(it) }
                    latestToolName?.let { mergedFields["toolName"] = JsonPrimitive(it) }
                    val mergedEvent = ev0.copy(seq = runLastSeq, e = JsonObject(mergedFields))
                    val preAdvance = if (!ev0Gap && !ev0ChangesEpoch && runLastSeq > 0L) runLastSeq - 1L else null
                    out.add(CoalescedBatchStep(mergedEvent, lastTs, preAdvanceLastSeq = preAdvance))
                }
                i = j
            }
            return out
        }
    }
}
