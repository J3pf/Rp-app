package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.secure.SecretStore
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
class MeshRepository
    @Inject
    constructor(
        private val connection: RelayConnection,
        private val bus: MessageBus,
        private val api: RelayApi,
        @Suppress("unused") private val secrets: SecretStore,
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

        val isSubscribed: Boolean
            get() = synchronized(subscriptionLock) { activeSubscribers.isNotEmpty() }

        val subscribedMachineId: String?
            get() = synchronized(subscriptionLock) { activeSubscribers.values.lastOrNull() }

        private val subscriptionLock = Any()

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
                            val subId = lastSubscribeEnvelopeId
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
                .onEach { state ->
                    if (state == ConnState.Connected) {
                        synchronized(subscriptionLock) {
                            if (activeSubscribers.isNotEmpty()) sendSubscribe(activeSubscribers.values.lastOrNull())
                        }
                    }
                }.launchIn(scope)
        }

        fun subscribe(machineId: String? = null) {
            subscribe(DEFAULT_OWNER, machineId)
        }

        fun subscribe(
            owner: Any,
            machineId: String?,
        ) {
            synchronized(subscriptionLock) {
                val wasEmpty = activeSubscribers.isEmpty()
                val previousMachineId = activeSubscribers.values.lastOrNull()
                activeSubscribers[owner] = machineId
                if (wasEmpty || previousMachineId != machineId) {
                    sendSubscribe(machineId)
                }
            }
        }

        fun unsubscribe() {
            unsubscribe(DEFAULT_OWNER)
        }

        fun unsubscribe(owner: Any) {
            synchronized(subscriptionLock) {
                val previousMachineId = activeSubscribers.values.lastOrNull()
                activeSubscribers.remove(owner)
                if (activeSubscribers.isEmpty()) {
                    connection.send(Envelope.of(MsgType.MESH_UNSUBSCRIBE, MeshUnsubscribe()))
                } else {
                    val remainingMachineId = activeSubscribers.values.lastOrNull()
                    if (remainingMachineId != previousMachineId) {
                        sendSubscribe(remainingMachineId)
                    }
                }
            }
        }

        fun clear() {
            synchronized(subscriptionLock) {
                activeSubscribers.clear()
                lastSubscribeEnvelopeId = null
            }
            _snapshot.value = null
        }

        private fun sendSubscribe(machineId: String?) {
            val env = Envelope.of(MsgType.MESH_SUBSCRIBE, MeshSubscribe(machineId))
            lastSubscribeEnvelopeId = env.id
            connection.send(env)
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
