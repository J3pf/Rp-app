package dev.j3pf.rp.data.repo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.chat.ChatReducer
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.client.chat.ChatDeliveryPolicy
import dev.j3pf.rp.client.chat.ChatPlatform
import dev.j3pf.rp.client.chat.ChatSessionRegistry
import dev.j3pf.rp.client.chat.ChatSocketConnection
import dev.j3pf.rp.client.chat.OutboxItem
import dev.j3pf.rp.client.chat.PERSISTED_EVENTS
import dev.j3pf.rp.data.db.AgentDao
import dev.j3pf.rp.data.db.ChatDao
import dev.j3pf.rp.data.db.ChatRecordEntity
import dev.j3pf.rp.data.db.OutboxDao
import dev.j3pf.rp.data.db.OutboxEntity
import dev.j3pf.rp.data.db.RecordKind
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.Entry
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.Ulid
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.str
import dev.j3pf.rp.push.SendMessageWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dev.j3pf.rp.client.chat.ChatSession as SharedChatSession

@Singleton
class ChatRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val chatDao: ChatDao,
        private val outboxDao: OutboxDao,
        private val connection: RelayConnection,
        private val bus: MessageBus,
        private val secrets: SecretStore,
        private val prompts: PromptsRepository,
        private val api: RelayApi,
        @AppScope private val scope: CoroutineScope,
        private val agentDao: AgentDao,
    ) {
        private var persisted = 0

        private val chatPlatform =
            object : ChatPlatform {
                override fun currentSession(): Session? = secrets.current()

                override suspend fun touchAgent(
                    relayId: String,
                    agentId: String,
                    timestamp: Long,
                ) {
                    agentDao.touch(relayId, agentId, timestamp)
                }

                override suspend fun putOutbox(
                    relayId: String,
                    msg: UserMessage,
                    createdAt: Long,
                ) {
                    outboxDao.put(
                        OutboxEntity(
                            clientMsgId = msg.clientMsgId,
                            relayId = relayId,
                            agentId = msg.agentId,
                            json = RpJson.encodeToString(UserMessage.serializer(), msg),
                            attempts = 0,
                            createdAt = createdAt,
                        ),
                    )
                }

                override suspend fun deleteOutbox(clientMsgId: String) {
                    outboxDao.delete(clientMsgId)
                }

                override suspend fun isOutboxPending(
                    relayId: String,
                    clientMsgId: String,
                ): Boolean = outboxDao.all(relayId).any { it.clientMsgId == clientMsgId }

                override suspend fun findOutboxMessage(
                    relayId: String,
                    clientMsgId: String,
                ): UserMessage? {
                    val item = outboxDao.all(relayId).firstOrNull { it.clientMsgId == clientMsgId } ?: return null
                    return runCatching { RpJson.decodeFromString(UserMessage.serializer(), item.json) }.getOrNull()
                }

                override suspend fun allPendingOutbox(relayId: String): List<OutboxItem> =
                    outboxDao.all(relayId).map { o ->
                        OutboxItem(
                            clientMsgId = o.clientMsgId,
                            message = runCatching { RpJson.decodeFromString(UserMessage.serializer(), o.json) }.getOrNull(),
                        )
                    }

                override suspend fun clearOutbox() {
                    outboxDao.clear()
                }

                override suspend fun loadInitialState(agentId: String): ChatState? {
                    val relayId = secrets.current()?.relayId ?: return null
                    val records = chatDao.records(relayId, agentId)
                    if (records.isEmpty()) return null
                    var s = ChatState()
                    val entries =
                        records
                            .filter { it.kind == RecordKind.ENTRY }
                            .mapNotNull { runCatching { RpJson.decodeFromString(Entry.serializer(), it.json) }.getOrNull() }
                    val epoch = records.lastOrNull { it.epoch != null }?.epoch
                    if (entries.isNotEmpty()) s = ChatReducer.applyEntries(s, entries, epoch)
                    for (r in records.filter { it.kind == RecordKind.EVENT }.sortedBy { it.seq }) {
                        val ev = runCatching { RpJson.decodeFromString(AgentEvent.serializer(), r.json) }.getOrNull() ?: continue
                        s = ChatReducer.reduce(s, ev)
                    }
                    return s
                }

                override suspend fun persistHistory(
                    agentId: String,
                    history: AgentHistory,
                    epochChanged: Boolean,
                ) {
                    val relayId = secrets.current()?.relayId ?: return
                    if (epochChanged) chatDao.clearAgent(relayId, agentId)
                    if (history.entries.isNotEmpty()) {
                        chatDao.deleteKind(relayId, agentId, RecordKind.ENTRY)
                        chatDao.insert(
                            history.entries.map {
                                ChatRecordEntity(
                                    relayId = relayId,
                                    agentId = agentId,
                                    kind = RecordKind.ENTRY,
                                    epoch = history.epoch,
                                    seq = 0,
                                    json = RpJson.encodeToString(Entry.serializer(), it),
                                )
                            },
                        )
                        chatDao.deleteEventsBefore(relayId, agentId, history.epoch, history.liveFromSeq ?: Long.MAX_VALUE)
                    }
                    val eventRows =
                        history.events
                            .filter { it.e.str("type") in PERSISTED_EVENTS }
                            .map { ev ->
                                val normalized = ev.copy(epoch = ev.epoch ?: history.epoch)
                                ChatRecordEntity(
                                    relayId = relayId,
                                    agentId = agentId,
                                    kind = RecordKind.EVENT,
                                    epoch = normalized.epoch,
                                    seq = normalized.seq,
                                    json = RpJson.encodeToString(AgentEvent.serializer(), normalized),
                                )
                            }
                    if (eventRows.isNotEmpty()) {
                        chatDao.insert(eventRows)
                        chatDao.trim(relayId, agentId, 5000)
                    }
                }

                override suspend fun persistEvent(
                    agentId: String,
                    event: AgentEvent,
                ) {
                    val relayId = secrets.current()?.relayId ?: return
                    chatDao.insert(
                        listOf(
                            ChatRecordEntity(
                                relayId = relayId,
                                agentId = agentId,
                                kind = RecordKind.EVENT,
                                epoch = event.epoch,
                                seq = event.seq,
                                json = RpJson.encodeToString(AgentEvent.serializer(), event),
                            ),
                        ),
                    )
                    if (++persisted % 100 == 0) chatDao.trim(relayId, agentId, 5000)
                }

                override suspend fun clearChat(relayId: String) {
                    chatDao.clear(relayId)
                }

                override fun enqueueBackgroundSend() {
                    enqueueSend(context)
                }
            }

        private val chatConnection =
            object : ChatSocketConnection {
                override val isConnected: Boolean get() = connection.isConnected
                override val state: StateFlow<ConnState> get() = connection.state
                override val messages: Flow<Envelope> get() = bus.messages

                override fun send(env: Envelope): Boolean = connection.send(env)

                override suspend fun request(
                    env: Envelope,
                    timeoutMs: Long,
                ): WsReply = connection.request(env, timeoutMs)
            }

        private val deliveryPolicy =
            ChatDeliveryPolicy(
                scope = scope,
                platform = chatPlatform,
                isWsConnected = { connection.isConnected },
                requestWs = { connection.request(it) },
                httpSendMessage = { api.sendMessage(it) },
                newId = { Ulid.new() },
                now = { System.currentTimeMillis() },
            )

        private val registry =
            ChatSessionRegistry<ChatSession>(
                create = { agentId -> ChatSession(agentId).also { it.start() } },
                onStop = { it.stop() },
            )

        /** Sesión de chat de un agente; la primera llamada la crea y la arranca. */
        fun open(agentId: String): ChatSession = registry.open(agentId)

        fun release(agentId: String) {
            registry.release(agentId)
        }

        inner class ChatSession(
            val agentId: String,
        ) {
            private val core =
                SharedChatSession(
                    agentId = agentId,
                    scope = scope,
                    platform = chatPlatform,
                    connection = chatConnection,
                    deliveryPolicy = deliveryPolicy,
                    promptFlow = { id -> prompts.openFor(id) },
                    newId = { Ulid.new() },
                    now = { System.currentTimeMillis() },
                )

            val state: StateFlow<ChatState> get() = core.state
            val syncing: StateFlow<Boolean> get() = core.syncing

            fun start() = core.start()

            fun stop() = core.stop()

            suspend fun subscribe() = core.subscribe()

            suspend fun send(
                text: String,
                attachments: List<AttachmentRef> = emptyList(),
                delivery: String = Delivery.AUTO,
                method: String = DeliveryMethod.AUTO,
                asCommand: Boolean = false,
                clientMsgId: String? = null,
            ): SendResult = core.send(text, attachments, delivery, method, asCommand, clientMsgId)

            fun abort() = core.abort()

            fun removeLocal(clientMsgId: String) = core.removeLocal(clientMsgId)

            suspend fun retry(clientMsgId: String): SendResult = core.retry(clientMsgId)
        }

        /** Vuelca la bandeja de salida (lo llama el Worker). Devuelve `true` si no queda nada pendiente. */
        suspend fun flushOutbox(): Boolean = deliveryPolicy.flushOutbox()

        suspend fun clear() {
            registry.clear()
            secrets.current()?.relayId?.let { chatPlatform.clearChat(it) }
            chatPlatform.clearOutbox()
        }

        companion object {
            fun enqueueSend(context: Context) {
                val req =
                    OneTimeWorkRequestBuilder<SendMessageWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork("send-outbox", ExistingWorkPolicy.REPLACE, req)
            }
        }
    }
