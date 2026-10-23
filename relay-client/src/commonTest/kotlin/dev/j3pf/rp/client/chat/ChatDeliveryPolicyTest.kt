package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatDeliveryPolicyTest {
    private class TestChatPlatform(
        var session: Session? =
            Session(
                relayId = "relay-1",
                relayUrl = "https://relay.test",
                relayName = "Test Relay",
                deviceId = "dev-1",
                deviceToken = "tok-1",
                pushKey = "pk-1",
            ),
    ) : ChatPlatform {
        val outbox = mutableMapOf<String, UserMessage>()
        val touchedAgents = mutableListOf<String>()
        var backgroundEnqueued = false

        override fun currentSession(): Session? = session

        override suspend fun touchAgent(
            relayId: String,
            agentId: String,
            timestamp: Long,
        ) {
            touchedAgents.add("$relayId:$agentId")
        }

        override suspend fun putOutbox(
            relayId: String,
            msg: UserMessage,
            createdAt: Long,
        ) {
            outbox[msg.clientMsgId] = msg
        }

        override suspend fun deleteOutbox(clientMsgId: String) {
            outbox.remove(clientMsgId)
        }

        override suspend fun isOutboxPending(
            relayId: String,
            clientMsgId: String,
        ): Boolean = outbox.containsKey(clientMsgId)

        override suspend fun findOutboxMessage(
            relayId: String,
            clientMsgId: String,
        ): UserMessage? = outbox[clientMsgId]

        override suspend fun allPendingOutbox(relayId: String): List<OutboxItem> = outbox.map { OutboxItem(it.key, it.value) }

        override suspend fun clearOutbox() {
            outbox.clear()
        }

        override suspend fun loadInitialState(agentId: String): ChatState? = null

        override suspend fun persistHistory(
            agentId: String,
            history: AgentHistory,
            epochChanged: Boolean,
        ) {}

        override suspend fun persistEvent(
            agentId: String,
            event: AgentEvent,
        ) {}

        override suspend fun clearChat(relayId: String) {}

        override fun enqueueBackgroundSend() {
            backgroundEnqueued = true
        }
    }

    @Test
    fun failsSendIfNoActiveSession() =
        runTest {
            val platform = TestChatPlatform(session = null)
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { WsReply.NotConnected },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Failed)
            assertEquals("sin sesión", result.reason)
        }

    @Test
    fun successfulWsDeliveryDeletesOutboxAndReturnsDelivered() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { env ->
                        val ack = UserMessageAck(clientMsgId = "msg-1", disposition = "accepted")
                        WsReply.Ok(Envelope.of("ack", ack, re = env.id))
                    },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Delivered)
            assertFalse(platform.outbox.containsKey("msg-1"), "Outbox must be deleted upon delivery")
        }

    @Test
    fun wsDeliveryWithAckFailureReturnsFailedAndDeletesOutbox() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { env ->
                        val ack = UserMessageAck(clientMsgId = "msg-1", disposition = "rejected", error = "agent_blocked")
                        WsReply.Ok(Envelope.of("ack", ack, re = env.id))
                    },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Failed)
            assertEquals("agent_blocked", result.reason)
            assertFalse(platform.outbox.containsKey("msg-1"), "Outbox must be deleted on rejection ack")
        }

    @Test
    fun wsErrorAgentOfflineDeletesOutboxMarksLocalFailedAndReturnsFailed() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { WsReply.Error(ErrorCodes.AGENT_OFFLINE, "agent offline") },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Failed)
            assertEquals("El agente está desconectado", result.reason)
            assertFalse(platform.outbox.containsKey("msg-1"))
            val failedItem =
                state.value.items
                    .filterIsInstance<UserMsg>()
                    .firstOrNull()
            assertTrue(failedItem?.failed == true, "Local message should be marked failed")
        }

    @Test
    fun wsErrorBadRequestDeletesOutboxMarksLocalFailedAndReturnsFailed() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { WsReply.Error(ErrorCodes.BAD_REQUEST, "invalid payload") },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Failed)
            assertEquals("invalid payload", result.reason)
            assertFalse(platform.outbox.containsKey("msg-1"))
        }

    @Test
    fun wsFailureFallsBackToHttpAndDeliversOnOk() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())
            var httpCalled = false

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { false }, // WS disconnected
                    requestWs = { WsReply.NotConnected },
                    httpSendMessage = {
                        httpCalled = true
                        ApiResult.Ok(JsonObject(emptyMap()))
                    },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(httpCalled, "HTTP fallback must be called when WS is not connected")
            assertTrue(result is SendResult.Delivered)
            assertFalse(platform.outbox.containsKey("msg-1"), "Outbox deleted after HTTP 200")
        }

    @Test
    fun httpFallback404DeletesOutboxMarksLocalFailedAndReturnsFailed() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { false },
                    requestWs = { WsReply.NotConnected },
                    httpSendMessage = { ApiResult.Http(404, "not found") },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Failed)
            assertEquals("El agente ya no existe", result.reason)
            assertFalse(platform.outbox.containsKey("msg-1"))
        }

    @Test
    fun httpFallback500OrNetworkEnqueuesBackgroundAndReturnsQueued() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { false },
                    requestWs = { WsReply.NotConnected },
                    httpSendMessage = { ApiResult.Http(500, "server error") },
                    newId = { "msg-1" },
                    now = { 1000L },
                )

            val result =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "hello",
                )

            assertTrue(result is SendResult.Queued)
            assertTrue(platform.backgroundEnqueued, "Should enqueue background work on 500")
            assertTrue(platform.outbox.containsKey("msg-1"), "Outbox must NOT be deleted on 500")
        }

    @Test
    fun retryReusesStableClientMsgIdFromOutbox() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())
            val sentIds = mutableListOf<String>()

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { env ->
                        val userMsg = env.body<UserMessage>()
                        sentIds.add(userMsg.clientMsgId)
                        val ack = UserMessageAck(clientMsgId = userMsg.clientMsgId, disposition = "accepted")
                        WsReply.Ok(Envelope.of("ack", ack, re = env.id))
                    },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "original-id" },
                    now = { 1000L },
                )

            // Seed outbox
            val origMsg = UserMessage(agentId = "agent-1", clientMsgId = "original-id", text = "retry me")
            platform.putOutbox("relay-1", origMsg, 1000L)

            val result =
                policy.retry(
                    agentId = "agent-1",
                    clientMsgId = "original-id",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                )

            assertTrue(result is SendResult.Delivered)
            assertEquals(listOf("original-id"), sentIds, "Retry must reuse original clientMsgId")
        }

    @Test
    fun flushOutboxDeletesSucceededMessagesAndLeavesFailedOnes() =
        runTest {
            val platform = TestChatPlatform()
            val msg1 = UserMessage(agentId = "agent-1", clientMsgId = "m-1", text = "ok")
            val msg2 = UserMessage(agentId = "agent-1", clientMsgId = "m-2", text = "fail-500")
            platform.putOutbox("relay-1", msg1, 1000L)
            platform.putOutbox("relay-1", msg2, 2000L)

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { false },
                    requestWs = { WsReply.NotConnected },
                    httpSendMessage = { msg ->
                        if (msg.clientMsgId == "m-1") {
                            ApiResult.Ok(JsonObject(emptyMap()))
                        } else {
                            ApiResult.Http(500, "error")
                        }
                    },
                    newId = { "id" },
                    now = { 1000L },
                )

            val allDone = policy.flushOutbox("relay-1")

            assertFalse(allDone, "flushOutbox returns false if any message failed to send")
            assertFalse(platform.outbox.containsKey("m-1"), "Succeeded message m-1 must be deleted")
            assertTrue(platform.outbox.containsKey("m-2"), "Failed message m-2 must remain in outbox")
        }

    @Test
    fun retryAfterFailedSendPreservesAttachmentsAndDeliveryOptions() =
        runTest {
            val platform = TestChatPlatform()
            val state = MutableStateFlow(ChatState())
            val sentMessages = mutableListOf<UserMessage>()
            var failFirst = true

            val policy =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { true },
                    requestWs = { env ->
                        val userMsg = env.body<UserMessage>()
                        sentMessages.add(userMsg)
                        if (failFirst) {
                            failFirst = false
                            WsReply.Error(ErrorCodes.AGENT_OFFLINE, "offline")
                        } else {
                            val ack = UserMessageAck(clientMsgId = userMsg.clientMsgId, disposition = "accepted")
                            WsReply.Ok(Envelope.of("ack", ack, re = env.id))
                        }
                    },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "retry-att-id" },
                    now = { 1000L },
                )

            val att = AttachmentRef(blobId = "b-1", name = "photo.png", mime = "image/png", size = 1234L)
            val firstRes =
                policy.send(
                    agentId = "agent-1",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                    text = "/deploy",
                    attachments = listOf(att),
                    delivery = Delivery.STEER,
                    method = DeliveryMethod.INTERACTIVE,
                    asCommand = true,
                )
            assertTrue(firstRes is SendResult.Failed)
            assertFalse(platform.outbox.containsKey("retry-att-id"))

            val retryRes =
                policy.retry(
                    agentId = "agent-1",
                    clientMsgId = "retry-att-id",
                    getState = { state.value },
                    updateState = { transform -> state.update(transform) },
                )
            assertTrue(retryRes is SendResult.Delivered)
            assertEquals(2, sentMessages.size)
            val retried = sentMessages.last()
            assertEquals("retry-att-id", retried.clientMsgId)
            assertEquals(listOf(att), retried.attachments)
            assertEquals(Delivery.STEER, retried.delivery)
            assertEquals(DeliveryMethod.INTERACTIVE, retried.method)
            assertTrue(retried.asCommand)
        }
}
