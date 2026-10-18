package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.HerdrLayout
import dev.j3pf.rp.protocol.HerdrTarget
import dev.j3pf.rp.protocol.HostDirs
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.HostListDirs
import dev.j3pf.rp.protocol.HostMachineRef
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.HostSpawned
import dev.j3pf.rp.protocol.HostsUpdate
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Ulid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HostResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : HostResult<T>

    data class Failed(
        val message: String,
    ) : HostResult<Nothing>
}

/** Cliente de `rp-host` (PROTOCOLO §9) a través del relay: listar carpetas, layout de herdr y crear agentes. */
@Singleton
class HostRepository
    @Inject
    constructor(
        private val connection: RelayConnection,
        private val bus: MessageBus,
        @AppScope private val scope: CoroutineScope,
    ) {
        private val _hosts = MutableStateFlow<Map<String, HostInfo>>(emptyMap())
        val hosts: StateFlow<Map<String, HostInfo>> = _hosts.asStateFlow()

        fun start() {
            bus.messages
                .onEach { env ->
                    if (env.t == MsgType.HOSTS_UPDATE) {
                        val u = runCatching { env.body<HostsUpdate>() }.getOrNull() ?: return@onEach
                        _hosts.update { cur -> cur - u.remove.toSet() + u.upsert.associateBy { it.machineId } }
                    }
                }.launchIn(scope)
        }

        fun clear() {
            _hosts.value = emptyMap()
        }

        suspend fun listDirs(
            machineId: String,
            path: String,
        ): HostResult<HostDirs> = ask(MsgType.HOST_LIST_DIRS, HostListDirs(machineId, path)) { it.body<HostDirs>() }

        suspend fun herdrLayout(machineId: String): HostResult<HerdrLayout> =
            ask(MsgType.HOST_HERDR_LAYOUT, HostMachineRef(machineId)) { it.body<HerdrLayout>() }

        suspend fun spawn(req: HostSpawn): HostResult<HostSpawned> =
            ask(MsgType.HOST_SPAWN, req, timeoutMs = 30_000) { it.body<HostSpawned>() }

        suspend fun resumeAgent(agent: AgentSummary): HostResult<HostSpawned> {
            val sessionId = agent.agentId.substringAfter(':')
            val host = _hosts.value[agent.machineId]
            val effectiveBackend = host?.defaultBackend ?: if (host?.backends?.herdr != null) "herdr" else null
            val target = if (effectiveBackend == "herdr") HerdrTarget(type = "workspace") else null
            return spawn(
                HostSpawn(
                    machineId = agent.machineId,
                    cwd = agent.cwd.orEmpty(),
                    name = agent.sessionName,
                    launcher = agent.launcher ?: host?.defaultLauncher,
                    backend = effectiveBackend,
                    herdrTarget = target,
                    sessionId = sessionId,
                ),
            )
        }

        private suspend inline fun <reified B, T> ask(
            type: String,
            body: B,
            timeoutMs: Long = 12_000,
            crossinline parse: (Envelope) -> T,
        ): HostResult<T> =
            when (val r = connection.request(Envelope.of(type, body, id = Ulid.new()), timeoutMs)) {
                is WsReply.Ok ->
                    runCatching { HostResult.Ok(parse(r.envelope)) }
                        .getOrElse { HostResult.Failed("Respuesta inválida") }
                is WsReply.Error -> HostResult.Failed(r.message.ifEmpty { r.code })
                WsReply.Timeout -> HostResult.Failed("Tiempo de espera agotado")
                WsReply.NotConnected -> HostResult.Failed("Sin conexión con el relay")
            }
    }
