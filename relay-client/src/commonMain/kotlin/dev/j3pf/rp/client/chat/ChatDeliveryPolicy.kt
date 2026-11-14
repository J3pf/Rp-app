package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatReducer
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatDeliveryPolicy(
    private val scope: CoroutineScope,
    private val platform: ChatPlatform,
    private val isWsConnected: () -> Boolean,
    private val requestWs: suspend (Envelope) -> WsReply,
    private val httpSendMessage: suspend (UserMessage) -> ApiResult<*>,
    private val newId: () -> String,
    private val now: () -> Long,
    private val delayMs: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun send(
        agentId: String,
        getState: () -> ChatState,
        updateState: ((ChatState) -> ChatState) -> Unit,
        text: String,
        attachments: List<AttachmentRef> = emptyList(),
        delivery: String = Delivery.AUTO,
        method: String = DeliveryMethod.AUTO,
        asCommand: Boolean = false,
        clientMsgId: String? = null,
    ): SendResult {
        val s = platform.currentSession() ?: return SendResult.Failed("sin sesión")
        val cId = clientMsgId ?: newId()
        val msg =
            UserMessage(
                agentId = agentId,
                clientMsgId = cId,
                text = text,
                attachments = attachments,
                delivery = delivery,
                method = method,
                asCommand = asCommand,
                deviceId = s.deviceId,
            )
        val timestamp = now()
        val imgCount = attachments.count { it.mime.startsWith("image/") }
        updateState {
            if (clientMsgId != null) {
                val targetKey = if (cId.startsWith("local:")) cId else "local:$cId"
                val ackKey = "ack:${cId.removePrefix("local:")}"
                val nextItems =
                    it.items.map { item ->
                        if (item is UserMsg && (item.clientMsgId == cId || item.key == targetKey || item.key == ackKey)) {
                            item.copy(
                                key = targetKey,
                                at = timestamp,
                                failed = false,
                                attachments = attachments,
                                delivery = delivery,
                                method = method,
                                asCommand = asCommand,
                            )
                        } else {
                            item
                        }
                    }
                val trimmed = text.trim()
                val nextLocalSent = if (trimmed.isNotBlank()) it.localSent + trimmed else it.localSent
                it.copy(items = nextItems, localSent = nextLocalSent, working = true)
            } else {
                ChatReducer.markLocalSent(
                    it,
                    text,
                    clientMsgId = msg.clientMsgId,
                    at = timestamp,
                    imageCount = imgCount,
                    attachments = attachments,
                    delivery = delivery,
                    method = method,
                    asCommand = asCommand,
                )
            }
        }
        platform.touchAgent(s.relayId, agentId, timestamp)
        platform.putOutbox(s.relayId, msg, timestamp)

        scope.launch {
            delayMs(30_000L)
            updateState { ChatReducer.expireOptimisticSends(it, now = now()) }
        }
        return deliver(msg, updateState)
    }

    suspend fun deliver(
        msg: UserMessage,
        updateState: ((ChatState) -> ChatState) -> Unit,
    ): SendResult {
        repeat(3) {
            if (!isWsConnected()) return@repeat
            val reply = requestWs(Envelope.of(MsgType.USER_MESSAGE, msg, id = newId()))
            when (reply) {
                is WsReply.Ok -> {
                    val ack = runCatching { reply.envelope.body<UserMessageAck>() }.getOrNull()
                    platform.deleteOutbox(msg.clientMsgId)
                    if (ack != null) {
                        updateState { ChatReducer.applyUserMessageAck(it, ack) }
                        return if (ack.isFailure) SendResult.Failed(ack.error ?: "rechazado") else SendResult.Delivered(ack)
                    }
                    updateState {
                        ChatReducer.applyUserMessageAck(
                            it,
                            UserMessageAck(clientMsgId = msg.clientMsgId, disposition = "delivered"),
                        )
                    }
                    return SendResult.Delivered(null)
                }

                is WsReply.Error -> {
                    if (reply.code == ErrorCodes.AGENT_OFFLINE) {
                        platform.deleteOutbox(msg.clientMsgId)
                        updateState { ChatReducer.markLocalFailed(it, msg.clientMsgId) }
                        return SendResult.Failed("El agente está desconectado")
                    }
                    if (reply.code == ErrorCodes.BAD_REQUEST || reply.code == ErrorCodes.TOO_LARGE) {
                        platform.deleteOutbox(msg.clientMsgId)
                        updateState { ChatReducer.markLocalFailed(it, msg.clientMsgId) }
                        return SendResult.Failed(reply.message.ifEmpty { reply.code })
                    }
                }

                else -> {}
            }
            delayMs(1000)
        }

        // Fallback HTTP (también cubre WS caído).
        return when (val r = httpSendMessage(msg)) {
            is ApiResult.Ok -> {
                platform.deleteOutbox(msg.clientMsgId)
                updateState {
                    ChatReducer.applyUserMessageAck(
                        it,
                        UserMessageAck(clientMsgId = msg.clientMsgId, disposition = "delivered"),
                    )
                }
                SendResult.Delivered(null)
            }

            is ApiResult.Http -> {
                if (r.code in 400..499 && r.code != 429 && r.code != 401 && r.code != 403) {
                    platform.deleteOutbox(msg.clientMsgId)
                    updateState { ChatReducer.markLocalFailed(it, msg.clientMsgId) }
                    SendResult.Failed(if (r.code == 404) "El agente ya no existe" else "HTTP ${r.code}")
                } else {
                    platform.enqueueBackgroundSend()
                    updateState { state ->
                        val hasAcked = state.items.any { it is UserMsg && it.key.startsWith("ack:") && !it.failed }
                        state.copy(working = hasAcked)
                    }
                    SendResult.Queued
                }
            }

            is ApiResult.Network -> {
                platform.enqueueBackgroundSend()
                updateState { state ->
                    val hasAcked = state.items.any { it is UserMsg && it.key.startsWith("ack:") && !it.failed }
                    state.copy(working = hasAcked)
                }
                SendResult.Queued
            }
        }
    }

    suspend fun retry(
        agentId: String,
        clientMsgId: String,
        getState: () -> ChatState,
        updateState: ((ChatState) -> ChatState) -> Unit,
    ): SendResult {
        val s = platform.currentSession() ?: return SendResult.Failed("sin sesión")
        val msg = platform.findOutboxMessage(s.relayId, clientMsgId)
        if (msg != null) {
            val timestamp = now()
            updateState { state ->
                val targetKey = if (clientMsgId.startsWith("local:")) clientMsgId else "local:$clientMsgId"
                val ackKey = "ack:${clientMsgId.removePrefix("local:")}"
                val nextItems =
                    state.items.map { item ->
                        if (item is UserMsg && (item.clientMsgId == clientMsgId || item.key == targetKey || item.key == ackKey)) {
                            item.copy(key = targetKey, at = timestamp, failed = false)
                        } else {
                            item
                        }
                    }
                val trimmed = msg.text.trim()
                val nextLocalSent = if (trimmed.isNotBlank()) state.localSent + trimmed else state.localSent
                state.copy(items = nextItems, localSent = nextLocalSent, working = true)
            }
            scope.launch {
                delayMs(30_000L)
                updateState { ChatReducer.expireOptimisticSends(it, now = now()) }
            }
            return deliver(msg, updateState)
        }
        val localMsg =
            getState().items.filterIsInstance<UserMsg>().firstOrNull {
                it.clientMsgId == clientMsgId || it.key == "local:$clientMsgId" || it.key == "ack:$clientMsgId"
            }
        if (localMsg != null) {
            return send(
                agentId = agentId,
                getState = getState,
                updateState = updateState,
                text = localMsg.text,
                attachments = localMsg.attachments,
                delivery = localMsg.delivery,
                method = localMsg.method,
                asCommand = localMsg.asCommand,
                clientMsgId = clientMsgId,
            )
        }
        return SendResult.Failed("Mensaje no encontrado")
    }

    fun removeLocal(
        clientMsgId: String,
        updateState: ((ChatState) -> ChatState) -> Unit,
    ) {
        scope.launch { platform.deleteOutbox(clientMsgId) }
        updateState { ChatReducer.removeLocal(it, clientMsgId) }
    }

    fun abort(agentId: String) {
        scope.launch {
            requestWs(Envelope.of(MsgType.AGENT_ABORT, AgentRef(agentId), id = newId()))
        }
    }

    suspend fun flushOutbox(relayId: String? = null): Boolean {
        val rid = relayId ?: platform.currentSession()?.relayId ?: return true
        var allDone = true
        for (item in platform.allPendingOutbox(rid)) {
            val msg = item.message
            if (msg == null) {
                platform.deleteOutbox(item.clientMsgId)
                continue
            }
            when (val r = httpSendMessage(msg)) {
                is ApiResult.Ok -> {
                    platform.deleteOutbox(item.clientMsgId)
                }

                is ApiResult.Http -> {
                    if (r.code in 400..499 &&
                        r.code != 429 &&
                        r.code != 401 &&
                        r.code != 403
                    ) {
                        platform.deleteOutbox(item.clientMsgId)
                    } else {
                        allDone = false
                    }
                }

                is ApiResult.Network -> {
                    allDone = false
                }
            }
        }
        return allDone
    }
}
