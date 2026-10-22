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
import dev.j3pf.rp.chat.TerminalWaitInfo
import dev.j3pf.rp.data.db.AgentDao
import dev.j3pf.rp.data.db.ChatDao
import dev.j3pf.rp.data.db.ChatRecordEntity
import dev.j3pf.rp.data.db.OutboxDao
import dev.j3pf.rp.data.db.OutboxEntity
import dev.j3pf.rp.data.db.RecordKind
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSubscribe
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.Entry
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.TerminalWait
import dev.j3pf.rp.protocol.Ulid
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.protocol.UserMessageAck
import dev.j3pf.rp.protocol.obj
import dev.j3pf.rp.protocol.str
import dev.j3pf.rp.push.SendMessageWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de enviar un mensaje al agente. */
sealed interface SendResult {
    data class Delivered(
        val ack: UserMessageAck?,
    ) : SendResult

    /** Sin red: queda en la bandeja de salida y WorkManager lo envía al volver la conexión. */
    data object Queued : SendResult

    data class Failed(
        val reason: String,
    ) : SendResult
}

/** Eventos que se persisten (el resto —deltas, parciales— sólo vive en memoria). */
private val PERSISTED_EVENTS =
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
        private class SessionEntry(
            val session: ChatSession,
            var refs: Int = 0,
        )

        private val sessions = HashMap<String, SessionEntry>()

        /** Sesión de chat de un agente; la primera llamada la crea y la arranca. */
        fun open(agentId: String): ChatSession =
            synchronized(sessions) {
                val entry =
                    sessions.getOrPut(agentId) {
                        SessionEntry(ChatSession(agentId).also { it.start() }, 0)
                    }
                entry.refs++
                entry.session
            }

        fun release(agentId: String) {
            val toStop =
                synchronized(sessions) {
                    val entry = sessions[agentId] ?: return
                    entry.refs--
                    if (entry.refs <= 0) {
                        sessions.remove(agentId)
                        entry.session
                    } else {
                        null
                    }
                }
            toStop?.stop()
        }

        inner class ChatSession(
            val agentId: String,
        ) {
            private val _state = MutableStateFlow(ChatState())
            val state: StateFlow<ChatState> = _state.asStateFlow()
            private val _syncing = MutableStateFlow(true)
            val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
            private var job: Job? = null
            private var syncTimeoutJob: Job? = null
            private var persisted = 0

            fun start() {
                job =
                    scope.launch {
                        loadPersisted()
                        launch { bus.messages.collect { onMessage(it) } }
                        launch { prompts.openFor(agentId).collect { list -> list.forEach(::onPromptOpened) } }
                        launch {
                            connection.state.collectLatest { st ->
                                if (st == ConnState.Connected) subscribe()
                            }
                        }
                    }
            }

            fun stop() {
                job?.cancel()
                job = null
                if (connection.isConnected) {
                    connection.send(Envelope.of(MsgType.AGENT_UNSUBSCRIBE, AgentRef(agentId)))
                }
            }

            private suspend fun loadPersisted() {
                val relayId = secrets.current()?.relayId ?: return
                val records = chatDao.records(relayId, agentId)
                if (records.isEmpty()) return
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
                // Los deltas no se persisten, así que los huecos de seq son esperables aquí.
                _state.value = s.copy(needsResync = false, working = false, streaming = null)
                if (s.items.isNotEmpty()) _syncing.value = false
            }

            /** `agent.subscribe{sinceSeq, epoch}`: el relay responde con `agent.history` y sigue con eventos en vivo. */
            suspend fun subscribe() {
                val st = _state.value
                if (st.items.isEmpty()) _syncing.value = true
                syncTimeoutJob?.cancel()
                val req =
                    Envelope.of(
                        MsgType.AGENT_SUBSCRIBE,
                        AgentSubscribe(agentId, st.lastSeq.takeIf { it > 0 }, st.epoch),
                        id = Ulid.new(),
                    )
                var attempts = 0
                while (attempts < 3) {
                    when (connection.request(req)) {
                        is WsReply.Ok -> {
                            syncTimeoutJob?.cancel()
                            syncTimeoutJob = null
                            if (_syncing.value) {
                                syncTimeoutJob =
                                    scope.launch {
                                        delay(6_000L)
                                        _syncing.value = false
                                    }
                            }
                            return
                        }

                        is WsReply.Error -> {
                            syncTimeoutJob?.cancel()
                            _syncing.value = false
                            return // p. ej. not_found: nada que reintentar
                        }

                        else -> {
                            attempts++
                            delay(1500L * attempts)
                        }
                    }
                }
                syncTimeoutJob?.cancel()
                _syncing.value = false
            }

            private suspend fun onMessage(env: Envelope) {
                when (env.t) {
                    MsgType.AGENT_HISTORY -> {
                        if (env.d.obj()?.str("agentId") != agentId) return
                        val h = runCatching { env.body<AgentHistory>() }.getOrNull() ?: return
                        val epochChanged = h.epoch != null && h.epoch != _state.value.epoch
                        _state.update { ChatReducer.applyHistory(it, h, System.currentTimeMillis()) }
                        syncTimeoutJob?.cancel()
                        syncTimeoutJob = null
                        _syncing.value = false
                        persistHistory(h, epochChanged)
                    }

                    MsgType.AGENT_EVENT -> {
                        if (env.d.obj()?.str("agentId") != agentId) return
                        val ev = runCatching { env.body<AgentEvent>() }.getOrNull() ?: return
                        val before = _state.value
                        val after = ChatReducer.reduce(before, ev, System.currentTimeMillis())
                        _state.value = after
                        if (after.items.isNotEmpty()) _syncing.value = false
                        if (after.lastSeq != before.lastSeq && ev.e.str("type") in PERSISTED_EVENTS) persistEvent(ev)
                        if (after.needsResync && !before.needsResync) scope.launch { subscribe() }
                    }

                    MsgType.PROMPT_OPENED -> {
                        if (env.d.obj()?.str("agentId") != agentId) return
                        val p = runCatching { env.body<Prompt>() }.getOrNull() ?: return
                        onPromptOpened(p)
                    }

                    MsgType.PROMPT_RESOLVED -> {
                        val r = runCatching { env.body<PromptResolved>() }.getOrNull() ?: return
                        _state.update { ChatReducer.applyPromptResolved(it, r) }
                    }

                    MsgType.USER_MESSAGE_ACK -> {
                        val ack = runCatching { env.body<UserMessageAck>() }.getOrNull() ?: return
                        _state.update { ChatReducer.applyUserMessageAck(it, ack) }
                    }

                    MsgType.TERMINAL_WAIT -> {
                        if (env.d.obj()?.str("agentId") != agentId) return
                        val w = runCatching { env.body<TerminalWait>() }.getOrNull() ?: return
                        _state.update { it.copy(terminalWait = if (w.active) TerminalWaitInfo(w.kind, w.title) else null) }
                    }
                }
            }

            private fun onPromptOpened(p: Prompt) {
                _state.update { ChatReducer.applyPromptOpened(it, p) }
            }

            private suspend fun persistHistory(
                h: AgentHistory,
                epochChanged: Boolean,
            ) {
                val relayId = secrets.current()?.relayId ?: return
                if (epochChanged) chatDao.clearAgent(relayId, agentId)
                if (h.entries.isNotEmpty()) {
                    chatDao.deleteKind(relayId, agentId, RecordKind.ENTRY)
                    chatDao.insert(
                        h.entries.map {
                            ChatRecordEntity(
                                relayId = relayId,
                                agentId = agentId,
                                kind = RecordKind.ENTRY,
                                epoch = h.epoch,
                                seq = 0,
                                json = RpJson.encodeToString(Entry.serializer(), it),
                            )
                        },
                    )
                    // Los eventos anteriores al snapshot ya están reflejados en las entradas.
                    chatDao.deleteEventsBefore(relayId, agentId, h.epoch, h.liveFromSeq ?: Long.MAX_VALUE)
                }
                val eventRows =
                    h.events
                        .filter { it.e.str("type") in PERSISTED_EVENTS }
                        .map { ev ->
                            val normalized = ev.copy(epoch = ev.epoch ?: h.epoch)
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

            private suspend fun persistEvent(ev: AgentEvent) {
                val relayId = secrets.current()?.relayId ?: return
                chatDao.insert(
                    listOf(
                        ChatRecordEntity(
                            relayId = relayId,
                            agentId = agentId,
                            kind = RecordKind.EVENT,
                            epoch = ev.epoch,
                            seq = ev.seq,
                            json = RpJson.encodeToString(AgentEvent.serializer(), ev),
                        ),
                    ),
                )
                if (++persisted % 100 == 0) chatDao.trim(relayId, agentId, 5000)
            }

            // ───────────────────────────── Envío ─────────────────────────────

            /**
             * Envía un mensaje (con adjuntos ya subidos). `clientMsgId` (ULID) es la clave de idempotencia:
             * se reintenta con el mismo id hasta recibir `ack` y el relay/plugin descartan repetidos.
             */
            suspend fun send(
                text: String,
                attachments: List<AttachmentRef> = emptyList(),
                delivery: String = Delivery.AUTO,
                method: String = DeliveryMethod.AUTO,
                asCommand: Boolean = false,
            ): SendResult {
                val s = secrets.current() ?: return SendResult.Failed("sin sesión")
                val msg =
                    UserMessage(
                        agentId = agentId,
                        clientMsgId = Ulid.new(),
                        text = text,
                        attachments = attachments,
                        delivery = delivery,
                        method = method,
                        asCommand = asCommand,
                        deviceId = s.deviceId,
                    )
                val now = System.currentTimeMillis()
                val imgCount = attachments.count { it.mime.startsWith("image/") }
                _state.update { ChatReducer.markLocalSent(it, text, clientMsgId = msg.clientMsgId, at = now, imageCount = imgCount) }
                agentDao.touch(s.relayId, agentId, now)
                outboxDao.put(
                    OutboxEntity(
                        msg.clientMsgId,
                        s.relayId,
                        agentId,
                        RpJson.encodeToString(UserMessage.serializer(), msg),
                        0,
                        now,
                    ),
                )
                scope.launch {
                    delay(30_000L)
                    _state.update { ChatReducer.expireOptimisticSends(it, now = System.currentTimeMillis()) }
                }
                return deliver(msg)
            }

            private suspend fun deliver(msg: UserMessage): SendResult {
                repeat(3) {
                    if (!connection.isConnected) return@repeat
                    val reply = connection.request(Envelope.of(MsgType.USER_MESSAGE, msg, id = Ulid.new()))
                    when (reply) {
                        is WsReply.Ok -> {
                            val ack = runCatching { reply.envelope.body<UserMessageAck>() }.getOrNull()
                            outboxDao.delete(msg.clientMsgId)
                            if (ack != null) {
                                _state.update { ChatReducer.applyUserMessageAck(it, ack) }
                                return if (ack.isFailure) SendResult.Failed(ack.error ?: "rechazado") else SendResult.Delivered(ack)
                            }
                            return SendResult.Delivered(null)
                        }

                        is WsReply.Error -> {
                            if (reply.code == ErrorCodes.AGENT_OFFLINE) {
                                outboxDao.delete(msg.clientMsgId)
                                _state.update { ChatReducer.markLocalFailed(it, msg.clientMsgId) }
                                return SendResult.Failed("El agente está desconectado")
                            }
                            if (reply.code == ErrorCodes.BAD_REQUEST || reply.code == ErrorCodes.TOO_LARGE) {
                                outboxDao.delete(msg.clientMsgId)
                                _state.update { ChatReducer.markLocalFailed(it, msg.clientMsgId) }
                                return SendResult.Failed(reply.message.ifEmpty { reply.code })
                            }
                        }

                        else -> {
                            Unit
                        }
                    }
                    delay(1000)
                }
                // Fallback HTTP (también cubre WS caído).
                return when (val r = api.sendMessage(msg)) {
                    is ApiResult.Ok -> {
                        outboxDao.delete(msg.clientMsgId)
                        SendResult.Delivered(null)
                    }

                    is ApiResult.Http -> {
                        if (r.code in 400..499 && r.code != 429 && r.code != 401 && r.code != 403) {
                            outboxDao.delete(msg.clientMsgId)
                            _state.update { ChatReducer.markLocalFailed(it, msg.clientMsgId) }
                            SendResult.Failed(if (r.code == 404) "El agente ya no existe" else "HTTP ${r.code}")
                        } else {
                            enqueueSend(context)
                            SendResult.Queued
                        }
                    }

                    is ApiResult.Network -> {
                        enqueueSend(context)
                        SendResult.Queued
                    }
                }
            }

            fun abort() {
                scope.launch {
                    connection.request(Envelope.of(MsgType.AGENT_ABORT, AgentRef(agentId), id = Ulid.new()))
                }
            }

            fun removeLocal(clientMsgId: String) {
                scope.launch { outboxDao.delete(clientMsgId) }
                _state.update { ChatReducer.removeLocal(it, clientMsgId) }
            }
        }

        /** Vuelca la bandeja de salida (lo llama el Worker). Devuelve `true` si no queda nada pendiente. */
        suspend fun flushOutbox(): Boolean {
            val relayId = secrets.current()?.relayId ?: return true
            var allDone = true
            for (o in outboxDao.all(relayId)) {
                val msg = runCatching { RpJson.decodeFromString(UserMessage.serializer(), o.json) }.getOrNull()
                if (msg == null) {
                    outboxDao.delete(o.clientMsgId)
                    continue
                }
                when (val r = api.sendMessage(msg)) {
                    is ApiResult.Ok -> {
                        outboxDao.delete(o.clientMsgId)
                    }

                    is ApiResult.Http -> {
                        if (r.code in 400..499 &&
                            r.code != 429 &&
                            r.code != 401 &&
                            r.code != 403
                        ) {
                            outboxDao.delete(o.clientMsgId)
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

        suspend fun clear() {
            val all =
                synchronized(sessions) {
                    sessions.values.map { it.session }.also { sessions.clear() }
                }
            all.forEach { it.stop() }
            secrets.current()?.relayId?.let { chatDao.clear(it) }
            outboxDao.clear()
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
