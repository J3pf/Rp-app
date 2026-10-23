package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.AgentSubscribe
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Subscription & resync policy for an agent session. */
class ChatSyncPolicy(
    private val agentId: String,
    private val scope: CoroutineScope,
    private val requestWs: suspend (Envelope) -> WsReply,
    private val newId: () -> String,
    private val getState: () -> ChatState,
    private val isSyncing: () -> Boolean,
    private val setSyncing: (Boolean) -> Unit,
    private val delayMs: suspend (Long) -> Unit = { delay(it) },
) {
    private var syncTimeoutJob: Job? = null

    suspend fun subscribe() {
        val st = getState()
        if (st.items.isEmpty()) setSyncing(true)
        cancelSyncTimeout()
        val req =
            Envelope.of(
                MsgType.AGENT_SUBSCRIBE,
                AgentSubscribe(agentId, st.lastSeq.takeIf { it > 0 }, st.epoch),
                id = newId(),
            )
        var attempts = 0
        while (attempts < 3) {
            when (requestWs(req)) {
                is WsReply.Ok -> {
                    cancelSyncTimeout()
                    if (isSyncing()) {
                        syncTimeoutJob =
                            scope.launch {
                                delayMs(6_000L)
                                setSyncing(false)
                            }
                    }
                    return
                }

                is WsReply.Error -> {
                    cancelSyncTimeout()
                    setSyncing(false)
                    return // p. ej. not_found: nada que reintentar
                }

                else -> {
                    attempts++
                    delayMs(1500L * attempts)
                }
            }
        }
        cancelSyncTimeout()
        setSyncing(false)
    }

    fun onHistoryReceived() {
        cancelSyncTimeout()
        setSyncing(false)
    }

    fun cancelSyncTimeout() {
        syncTimeoutJob?.cancel()
        syncTimeoutJob = null
    }
}
