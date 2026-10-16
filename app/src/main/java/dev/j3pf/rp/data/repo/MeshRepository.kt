package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.Envelope
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

        val connState: StateFlow<ConnState> get() = connection.state

        @Volatile
        var isSubscribed: Boolean = false
            private set

        @Volatile
        var subscribedMachineId: String? = null
            private set

        private val subscriptionLock = Any()

        init {
            bus.messages
                .onEach { envelope ->
                    when (envelope.t) {
                        MsgType.MESH_SNAPSHOT, MsgType.MESH_UPDATE -> {
                            runCatching { envelope.body<MeshSnapshot>() }.getOrNull()?.let { _snapshot.value = it }
                        }

                        MsgType.MESH_COMM -> {
                            runCatching { envelope.body<MeshCommEvent>() }.getOrNull()?.let { _commEvents.tryEmit(it.comm) }
                        }
                    }
                }.launchIn(scope)

            connection.state
                .onEach { state ->
                    if (state == ConnState.Connected) {
                        synchronized(subscriptionLock) {
                            if (isSubscribed) sendSubscribe(subscribedMachineId)
                        }
                    }
                }.launchIn(scope)
        }

        fun subscribe(machineId: String? = null) {
            synchronized(subscriptionLock) {
                isSubscribed = true
                subscribedMachineId = machineId
                sendSubscribe(machineId)
            }
        }

        fun unsubscribe() {
            synchronized(subscriptionLock) {
                isSubscribed = false
                subscribedMachineId = null
                connection.send(Envelope.of(MsgType.MESH_UNSUBSCRIBE, MeshUnsubscribe()))
            }
        }

        private fun sendSubscribe(machineId: String?) {
            connection.send(Envelope.of(MsgType.MESH_SUBSCRIBE, MeshSubscribe(machineId)))
        }

        suspend fun getMesh(machineId: String? = null): ApiResult<MeshSnapshot> = api.getMesh(machineId)

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
    }
