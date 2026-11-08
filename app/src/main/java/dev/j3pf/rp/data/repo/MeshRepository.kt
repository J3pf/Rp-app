package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.MeshAgentCommsResponse
import dev.j3pf.rp.protocol.MeshComm
import dev.j3pf.rp.protocol.MeshCommEvent
import dev.j3pf.rp.protocol.MeshConversationResponse
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.MeshSubscribe
import dev.j3pf.rp.protocol.MeshUnsubscribe
import dev.j3pf.rp.protocol.MsgType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class MeshRepository
    @Inject
    constructor(
        private val connection: RelayConnection,
        private val bus: MessageBus,
        private val api: RelayApi,
        @AppScope private val scope: CoroutineScope,
    ) {
        private val _snapshot = MutableStateFlow<MeshSnapshot?>(null)
        val snapshot: StateFlow<MeshSnapshot?> = _snapshot.asStateFlow()

        private val _commEvents =
            MutableSharedFlow<MeshComm>(
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val commEvents: SharedFlow<MeshComm> = _commEvents.asSharedFlow()

        private val _meshErrors =
            MutableSharedFlow<String>(
                extraBufferCapacity = 16,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val meshErrors: SharedFlow<String> = _meshErrors.asSharedFlow()

        val connState: StateFlow<ConnState> get() = connection.state

        private val activeSubscribers = mutableMapOf<Any, String?>()
        private var lastSubscribeEnvelopeId: String? = null
        private var lastSubscribedMachineId: String? = null

        val isSubscribed: Boolean
            get() = synchronized(subscriptionLock) { activeSubscribers.isNotEmpty() }

        val subscribedMachineId: String?
            get() = synchronized(subscriptionLock) { activeSubscribers.values.lastOrNull() }

        private val subscriptionLock = Any()
        private val sendLock = Any()
        private var decisionSeq = 0L
        private var lastSentSeq = 0L

        internal val lastSentSeqForTest: Long
            get() = synchronized(sendLock) { lastSentSeq }

        private data class SequencedEnvelope(
            val seq: Long,
            val envelope: Envelope,
        )

        private fun sequenceEnvelope(envelope: Envelope) = SequencedEnvelope(++decisionSeq, envelope)

        /** `deliverEnvelope` test seam: subclasses may pause before the send-order gate to pin decision-order delivery. */
        internal open fun deliverEnvelope(
            env: Envelope,
            seq: Long,
        ): Boolean =
            synchronized(sendLock) {
                if (seq > lastSentSeq) {
                    lastSentSeq = seq
                    connection.send(env)
                    true
                } else {
                    false
                }
            }

        init {
            bus.messages
                .onEach { envelope ->
                    when (envelope.t) {
                        MsgType.MESH_SNAPSHOT, MsgType.MESH_UPDATE -> {
                            try {
                                _snapshot.value = envelope.body<MeshSnapshot>()
                            } catch (e: Exception) {
                                android.util.Log.e("MeshRepository", "Fallo al decodificar MESH_SNAPSHOT", e)
                                _meshErrors.tryEmit("Error al decodificar la red de agentes: ${e.message ?: "formato inválido"}")
                            }
                        }

                        MsgType.MESH_COMM -> {
                            runCatching { envelope.body<MeshCommEvent>() }.getOrNull()?.let { _commEvents.tryEmit(it.comm) }
                        }

                        MsgType.ERROR -> {
                            val subId = synchronized(subscriptionLock) { lastSubscribeEnvelopeId }
                            if (subId != null && envelope.re == subId) {
                                val msg =
                                    runCatching { envelope.body<ErrorBody>().message }.getOrNull()
                                        ?: "Error al suscribir a la red de agentes"
                                _meshErrors.tryEmit(msg)
                            }
                        }
                    }
                }.launchIn(scope)

            connection.state
                .onEach(::handleConnectionState)
                .launchIn(scope)
        }

        // Shared production state handler; called directly by wire-order tests for deterministic observer scheduling.
        internal fun handleConnectionState(state: ConnState) {
            val envelope =
                synchronized(subscriptionLock) {
                    if (state == ConnState.Connected) {
                        val targetMachineId = activeSubscribers.values.lastOrNull()
                        if (activeSubscribers.isNotEmpty() &&
                            (lastSubscribedMachineId != targetMachineId || lastSubscribeEnvelopeId == null)
                        ) {
                            sequenceEnvelope(createSubscribeEnvelope(targetMachineId))
                        } else {
                            null
                        }
                    } else {
                        lastSubscribedMachineId = null
                        lastSubscribeEnvelopeId = null
                        null
                    }
                }
            if (envelope != null) deliverEnvelope(envelope.envelope, envelope.seq)
        }

        fun subscribe(machineId: String? = null) {
            subscribe(DEFAULT_OWNER, machineId)
        }

        fun subscribe(
            owner: Any,
            machineId: String?,
        ) {
            val envelope =
                synchronized(subscriptionLock) {
                    val wasEmpty = activeSubscribers.isEmpty()
                    val previousMachineId = activeSubscribers.values.lastOrNull()
                    activeSubscribers[owner] = machineId
                    if (wasEmpty || previousMachineId != machineId) sequenceEnvelope(createSubscribeEnvelope(machineId)) else null
                }
            if (envelope != null) deliverEnvelope(envelope.envelope, envelope.seq)
        }

        fun unsubscribe() {
            unsubscribe(DEFAULT_OWNER)
        }

        fun unsubscribe(owner: Any) {
            val envelope =
                synchronized(subscriptionLock) {
                    val previousMachineId = activeSubscribers.values.lastOrNull()
                    activeSubscribers.remove(owner)
                    if (activeSubscribers.isEmpty()) {
                        lastSubscribeEnvelopeId = null
                        lastSubscribedMachineId = null
                        sequenceEnvelope(Envelope.of(MsgType.MESH_UNSUBSCRIBE, MeshUnsubscribe()))
                    } else {
                        val remainingMachineId = activeSubscribers.values.lastOrNull()
                        if (remainingMachineId != previousMachineId) sequenceEnvelope(createSubscribeEnvelope(remainingMachineId)) else null
                    }
                }
            if (envelope != null) deliverEnvelope(envelope.envelope, envelope.seq)
        }

        fun clear() {
            val fence =
                synchronized(subscriptionLock) {
                    activeSubscribers.clear()
                    lastSubscribeEnvelopeId = null
                    lastSubscribedMachineId = null
                    ++decisionSeq
                }
            synchronized(sendLock) {
                if (fence > lastSentSeq) lastSentSeq = fence
            }
            _snapshot.value = null
        }

        private fun createSubscribeEnvelope(machineId: String?): Envelope {
            val env = Envelope.of(MsgType.MESH_SUBSCRIBE, MeshSubscribe(machineId))
            if (connection.state.value == ConnState.Connected) {
                lastSubscribeEnvelopeId = env.id
                lastSubscribedMachineId = machineId
            } else {
                lastSubscribeEnvelopeId = null
                lastSubscribedMachineId = null
            }
            return env
        }

        suspend fun getMesh(machineId: String? = null): ApiResult<MeshSnapshot> {
            val res = api.getMesh(machineId)
            if (res is ApiResult.Ok) {
                _snapshot.value = res.value
            }
            return res
        }

        suspend fun getMeshConversation(
            a: String,
            b: String,
            before: Long? = null,
            limit: Int = 50,
        ): ApiResult<MeshConversationResponse> = api.getMeshConversation(a, b, before, limit)

        suspend fun getMeshAgentComms(
            agentId: String,
            limit: Int = 50,
        ): ApiResult<MeshAgentCommsResponse> = api.getMeshAgentComms(agentId, limit)

        fun retryConnection() = connection.retryNow()

        companion object {
            private val DEFAULT_OWNER = Any()
        }
    }
