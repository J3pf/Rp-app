package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.UserMessage

data class OutboxItem(
    val clientMsgId: String,
    val message: UserMessage?,
)

interface ChatPlatform {
    fun currentSession(): Session?

    suspend fun touchAgent(
        relayId: String,
        agentId: String,
        timestamp: Long,
    )

    suspend fun putOutbox(
        relayId: String,
        msg: UserMessage,
        createdAt: Long,
    )

    suspend fun deleteOutbox(clientMsgId: String)

    suspend fun isOutboxPending(
        relayId: String,
        clientMsgId: String,
    ): Boolean

    suspend fun findOutboxMessage(
        relayId: String,
        clientMsgId: String,
    ): UserMessage?

    suspend fun allPendingOutbox(relayId: String): List<OutboxItem>

    suspend fun clearOutbox()

    suspend fun loadInitialState(agentId: String): ChatState?

    suspend fun persistHistory(
        agentId: String,
        history: AgentHistory,
        epochChanged: Boolean,
    )

    suspend fun persistEvent(
        agentId: String,
        event: AgentEvent,
    )

    suspend fun clearChat(relayId: String)

    fun enqueueBackgroundSend()
}
