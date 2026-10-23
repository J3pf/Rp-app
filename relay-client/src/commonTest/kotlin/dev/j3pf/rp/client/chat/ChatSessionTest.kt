package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {
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
        var initialState: ChatState? = null,
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

        override suspend fun loadInitialState(agentId: String): ChatState? = initialState

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

    private class TestChatSocketConnection : ChatSocketConnection {
        override var isConnected: Boolean = true
        override val state = MutableStateFlow<ConnState>(ConnState.Connected)
        override val messages = MutableSharedFlow<Envelope>(extraBufferCapacity = 64)
        val sent = mutableListOf<Envelope>()
        var replyHandler: suspend (Envelope) -> WsReply = { WsReply.NotConnected }

        override fun send(env: Envelope): Boolean {
            sent.add(env)
            return true
        }

        override suspend fun request(
            env: Envelope,
            timeoutMs: Long,
        ): WsReply {
            sent.add(env)
            return replyHandler(env)
        }
    }

    @Test
    fun startingSessionLoadsPersistedState() =
        runTest {
            val platform =
                TestChatPlatform(
                    initialState =
                        ChatState(
                            items = listOf(UserMsg(key = "1", text = "hello persisted")),
                        ),
                )
            val conn = TestChatSocketConnection()
            val delivery =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { conn.isConnected },
                    requestWs = { conn.request(it) },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "id-1" },
                    now = { 1000L },
                )

            val session =
                ChatSession(
                    agentId = "agent-1",
                    scope = this,
                    platform = platform,
                    connection = conn,
                    deliveryPolicy = delivery,
                    newId = { "id-1" },
                    now = { 1000L },
                )

            try {
                session.start()
                withTimeout(2000) {
                    session.syncing.first { !it }
                }
                assertEquals(1, session.state.value.items.size)
                assertEquals("hello persisted", (session.state.value.items[0] as UserMsg).text)
            } finally {
                session.stop()
                coroutineContext.cancelChildren()
            }
        }

    @Test
    fun sendDeliversOverWsAndRemovesLocalCleansUp() =
        runTest {
            val platform = TestChatPlatform()
            val conn = TestChatSocketConnection()
            conn.replyHandler = { env ->
                if (env.t == MsgType.USER_MESSAGE) {
                    val userMsg = env.body<UserMessage>()
                    val ack = UserMessageAck(clientMsgId = userMsg.clientMsgId, disposition = "accepted")
                    WsReply.Ok(Envelope.of("ack", ack, re = env.id))
                } else {
                    WsReply.Ok(Envelope(t = "ok", re = env.id))
                }
            }

            val delivery =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { conn.isConnected },
                    requestWs = { conn.request(it) },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "msg-123" },
                    now = { 1000L },
                )

            val session =
                ChatSession(
                    agentId = "agent-1",
                    scope = this,
                    platform = platform,
                    connection = conn,
                    deliveryPolicy = delivery,
                    newId = { "msg-123" },
                    now = { 1000L },
                )

            try {
                session.start()
                val res = session.send("hello world")
                assertTrue(res is SendResult.Delivered)

                session.removeLocal("msg-123")
                advanceUntilIdle()
                assertFalse(
                    session.state.value.items
                        .any { it.key == "local:msg-123" },
                )
            } finally {
                session.stop()
                coroutineContext.cancelChildren()
            }
        }

    @Test
    fun stopUnsubscribesWhenConnected() =
        runTest {
            val platform = TestChatPlatform()
            val conn = TestChatSocketConnection()
            val delivery =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { conn.isConnected },
                    requestWs = { conn.request(it) },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "id-1" },
                    now = { 1000L },
                )

            val session =
                ChatSession(
                    agentId = "agent-1",
                    scope = this,
                    platform = platform,
                    connection = conn,
                    deliveryPolicy = delivery,
                    newId = { "id-1" },
                    now = { 1000L },
                )

            try {
                session.start()
                session.stop()
                assertTrue(conn.sent.any { it.t == MsgType.AGENT_UNSUBSCRIBE })
            } finally {
                coroutineContext.cancelChildren()
            }
        }

    @Test
    fun abortSendsAgentAbortEnvelope() =
        runTest {
            val platform = TestChatPlatform()
            val conn = TestChatSocketConnection()
            conn.replyHandler = { WsReply.Ok(Envelope(t = "ok", re = it.id)) }
            val delivery =
                ChatDeliveryPolicy(
                    scope = this,
                    platform = platform,
                    isWsConnected = { conn.isConnected },
                    requestWs = { conn.request(it) },
                    httpSendMessage = { ApiResult.Ok(JsonObject(emptyMap())) },
                    newId = { "abort-id" },
                    now = { 1000L },
                )

            val session =
                ChatSession(
                    agentId = "agent-1",
                    scope = this,
                    platform = platform,
                    connection = conn,
                    deliveryPolicy = delivery,
                    newId = { "abort-id" },
                    now = { 1000L },
                )

            try {
                session.abort()
                advanceUntilIdle()
                assertTrue(conn.sent.any { it.t == MsgType.AGENT_ABORT })
            } finally {
                session.stop()
                coroutineContext.cancelChildren()
            }
        }
}
