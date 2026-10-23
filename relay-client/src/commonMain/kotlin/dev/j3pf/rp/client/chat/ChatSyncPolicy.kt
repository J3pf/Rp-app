package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.client.ws.RelayLock
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
    private val lock = RelayLock()
    private var syncTimeoutJob: Job? = null
    private var subscribeGeneration: Long = 0L

    suspend fun subscribe() {
        val generation =
            lock.withLock {
                subscribeGeneration += 1L
                syncTimeoutJob?.cancel()
                syncTimeoutJob = null
                subscribeGeneration
            }
        val st = getState()
        if (st.items.isEmpty()) setSyncing(true)
        val req =
            Envelope.of(
                MsgType.AGENT_SUBSCRIBE,
                AgentSubscribe(agentId, st.lastSeq.takeIf { it > 0 }, st.epoch),
                id = newId(),
            )
        var attempts = 0
        while (attempts < 3) {
            if (lock.withLock { subscribeGeneration != generation }) return
            when (requestWs(req)) {
                is WsReply.Ok -> {
                    lock.withLock {
                        if (subscribeGeneration != generation) return@withLock
                        syncTimeoutJob?.cancel()
                        syncTimeoutJob =
                            if (isSyncing()) {
                                scope.launch {
                                    delayMs(6_000L)
                                    val isCurrent = lock.withLock { subscribeGeneration == generation }
                                    if (isCurrent) setSyncing(false)
                                }
                            } else {
                                null
                            }
                    }
                    return
                }

                is WsReply.Error -> {
                    val isCurrent =
                        lock.withLock {
                            if (subscribeGeneration != generation) {
                                false
                            } else {
                                syncTimeoutJob?.cancel()
                                syncTimeoutJob = null
                                true
                            }
                        }
                    if (isCurrent) setSyncing(false)
                    return // p. ej. not_found: nada que reintentar
                }

                else -> {
                    attempts++
                    delayMs(1500L * attempts)
                }
            }
        }
        val isCurrent =
            lock.withLock {
                if (subscribeGeneration != generation) {
                    false
                } else {
                    syncTimeoutJob?.cancel()
                    syncTimeoutJob = null
                    true
                }
            }
        if (isCurrent) setSyncing(false)
    }

    fun onHistoryReceived() {
        cancelSyncTimeout()
        setSyncing(false)
    }

    fun cancelSyncTimeout() {
        lock.withLock {
            syncTimeoutJob?.cancel()
            syncTimeoutJob = null
        }
    }
}
