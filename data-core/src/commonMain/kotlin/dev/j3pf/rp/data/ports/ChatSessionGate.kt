package dev.j3pf.rp.data.ports

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-agent interactive chat session contract (Unit P1).
 *
 * Exposes the reducer-driven [state] and history-resync [syncing] flows alongside message delivery,
 * retry, abort, and subscription lifecycle without leaking Android or transport types.
 */
interface ChatSession {
    val agentId: String
    val state: StateFlow<ChatState>
    val syncing: StateFlow<Boolean>

    fun start()

    fun stop()

    suspend fun subscribe()

    suspend fun send(
        text: String,
        attachments: List<AttachmentRef> = emptyList(),
        delivery: String = Delivery.AUTO,
        method: String = DeliveryMethod.AUTO,
        asCommand: Boolean = false,
        clientMsgId: String? = null,
    ): SendResult

    fun abort()

    fun removeLocal(clientMsgId: String)

    suspend fun retry(clientMsgId: String): SendResult
}

/**
 * Port contract for opening/releasing per-agent [ChatSession] instances and managing outbox/local
 * chat state (Unit P1).
 */
interface ChatSessionGate {
    fun open(agentId: String): ChatSession

    fun release(agentId: String)

    suspend fun flushOutbox(): Boolean

    suspend fun clear()
}
