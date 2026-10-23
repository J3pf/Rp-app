package dev.j3pf.rp.client.chat

import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shared chat session orchestration for an agent. */
class ChatSession(
    val agentId: String,
    private val scope: CoroutineScope,
    private val platform: ChatPlatform,
    private val connection: ChatSocketConnection,
    private val deliveryPolicy: ChatDeliveryPolicy,
    private val promptFlow: (agentId: String) -> Flow<List<Prompt>> = { emptyFlow() },
    newId: () -> String,
    now: () -> Long,
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private val _syncing = MutableStateFlow(true)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var job: Job? = null

    val syncPolicy =
        ChatSyncPolicy(
            agentId = agentId,
            scope = scope,
            requestWs = { connection.request(it) },
            newId = newId,
            getState = { _state.value },
            isSyncing = { _syncing.value },
            setSyncing = { _syncing.value = it },
        )

    val dispatcher =
        ChatMessageDispatcher(
            agentId = agentId,
            getState = { _state.value },
            updateState = { transform -> _state.update(transform) },
            onHistory = { h, epochChanged -> platform.persistHistory(agentId, h, epochChanged) },
            onPersistEvent = { ev -> platform.persistEvent(agentId, ev) },
            onResyncNeeded = { scope.launch { subscribe() } },
            onHistoryApplied = { syncPolicy.onHistoryReceived() },
            onSyncingResolved = { _syncing.value = false },
            now = now,
        )

    fun start() {
        job =
            scope.launch {
                loadPersisted()
                launch { connection.messages.collect { onMessage(it) } }
                launch { promptFlow(agentId).collect { list -> list.forEach(::onPromptOpened) } }
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
        syncPolicy.cancelSyncTimeout()
        if (connection.isConnected) {
            connection.send(Envelope.of(MsgType.AGENT_UNSUBSCRIBE, AgentRef(agentId)))
        }
    }

    suspend fun loadPersisted() {
        val s = platform.loadInitialState(agentId) ?: return
        _state.value = s.copy(needsResync = false, working = false, streaming = null)
        if (s.items.isNotEmpty()) _syncing.value = false
    }

    suspend fun subscribe() {
        syncPolicy.subscribe()
    }

    suspend fun onMessage(env: Envelope) {
        dispatcher.dispatch(env)
    }

    fun onPromptOpened(p: Prompt) {
        dispatcher.dispatchPrompt(p)
    }

    suspend fun send(
        text: String,
        attachments: List<AttachmentRef> = emptyList(),
        delivery: String = Delivery.AUTO,
        method: String = DeliveryMethod.AUTO,
        asCommand: Boolean = false,
        clientMsgId: String? = null,
    ): SendResult =
        deliveryPolicy.send(
            agentId = agentId,
            getState = { _state.value },
            updateState = { transform -> _state.update(transform) },
            text = text,
            attachments = attachments,
            delivery = delivery,
            method = method,
            asCommand = asCommand,
            clientMsgId = clientMsgId,
        )

    fun abort() {
        deliveryPolicy.abort(agentId)
    }

    fun removeLocal(clientMsgId: String) {
        deliveryPolicy.removeLocal(clientMsgId) { transform -> _state.update(transform) }
    }

    suspend fun retry(clientMsgId: String): SendResult =
        deliveryPolicy.retry(
            agentId = agentId,
            clientMsgId = clientMsgId,
            getState = { _state.value },
            updateState = { transform -> _state.update(transform) },
        )
}
