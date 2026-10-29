package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.db.AgentDao
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.domain.model.toModel
import dev.j3pf.rp.protocol.AgentArchivedList
import dev.j3pf.rp.protocol.AgentArchivedRequest
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Ulid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CloseOutcome {
    data object Closed : CloseOutcome

    data object Archived : CloseOutcome

    data class Failed(
        val message: String,
    ) : CloseOutcome
}

sealed interface ArchivedAgentsResult {
    data class Ok(
        val agents: List<ArchivedAgentSummary>,
        val nextBefore: Long?,
    ) : ArchivedAgentsResult

    data class Failed(
        val message: String,
    ) : ArchivedAgentsResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AgentsRepository
    @Inject
    constructor(
        private val dao: AgentDao,
        private val secrets: SecretStore,
        private val bus: MessageBus,
        private val connection: RelayConnection,
        private val settings: SettingsRepository,
        @AppScope private val scope: CoroutineScope,
    ) {
        /** Todos los agentes con su contador de prompts abiertos. */
        val agents: Flow<List<AgentSummary>> =
            secrets.session
                .flatMapLatest { s ->
                    if (s == null) emptyFlow() else dao.observe(s.relayId).map { list -> list.map { it.agent.toModel(it.openPrompts) } }
                }.distinctUntilChanged()

        /** Agentes filtrados por el selector de PC (`"all"` o un `context`). */
        val filtered: Flow<List<AgentSummary>> =
            combine(agents, settings.settings.map { it.selectedContext }.distinctUntilChanged()) { list, ctx ->
                if (ctx == AppSettings.ALL) list else list.filter { it.machineContext == ctx }
            }.distinctUntilChanged()

        fun observe(agentId: String): Flow<AgentSummary?> =
            secrets.session
                .flatMapLatest { s ->
                    if (s == null) emptyFlow() else dao.observeOne(s.relayId, agentId).map { it?.agent?.toModel(it.openPrompts) }
                }.distinctUntilChanged()

        fun start() {
            bus.messages
                .onEach { env ->
                    val relayId = secrets.current()?.relayId ?: return@onEach
                    when (env.t) {
                        MsgType.APP_WELCOME -> {
                            val w = runCatching { env.body<AppWelcome>() }.getOrNull() ?: return@onEach
                            dao.syncWelcome(
                                relayId,
                                w.agents.map { it.toEntity(relayId) },
                                w.agents.map { it.agentId }.ifEmpty { listOf("") },
                            )
                        }
                        MsgType.AGENTS_UPDATE -> {
                            val u = runCatching { env.body<AgentsUpdate>() }.getOrNull() ?: return@onEach
                            if (u.upsert.isNotEmpty()) dao.upsert(u.upsert.map { it.toEntity(relayId) })
                            if (u.remove.isNotEmpty()) dao.delete(relayId, u.remove)
                        }
                        // `agent.closed` no se escucha a propósito: es un frame del puente hacia el relay
                        // (rp-relay registry.ts, dirección plugin→relay) y no lleva agentId propio, así que
                        // la app nunca lo recibe. La retirada llega como `agents.update.remove`.
                    }
                }.launchIn(scope)
        }

        /** Cierra el pane de Herdr; cuando no hay pane vinculado, archiva el historial con `agent.forget`. */
        suspend fun close(agentId: String): CloseOutcome {
            val relayId = secrets.current()?.relayId ?: return CloseOutcome.Failed("Sin sesión con el relay")
            val ref = AgentRef(agentId)
            return when (val reply = connection.request(Envelope.of(MsgType.AGENT_CLOSE, ref, id = Ulid.new()))) {
                is WsReply.Ok -> {
                    dao.delete(relayId, listOf(agentId))
                    CloseOutcome.Closed
                }
                is WsReply.Error -> {
                    // El pane ya no existía: el plugin responde con un fallo tipado y el agente se ha ido
                    // igual. No es un error que merezca banner, es el mismo resultado que cerrarlo.
                    if (reply.code == ErrorCodes.NOT_FOUND) {
                        dao.delete(relayId, listOf(agentId))
                        CloseOutcome.Closed
                    } else if (reply.code != ErrorCodes.AGENT_CLOSE_UNSUPPORTED) {
                        // Ni `not_found` ni `agent_close_unsupported`: el cierre falló de verdad y el panel
                        // puede seguir vivo, así que NO se archiva. Pero la razón del relay se pinta tal
                        // cual: descartarla convertía cualquier fallo en el mismo banner mudo.
                        CloseOutcome.Failed(closeFailureMessage(reply.message))
                    } else {
                        when (connection.request(Envelope.of(MsgType.AGENT_FORGET, ref, id = Ulid.new()))) {
                            is WsReply.Ok -> {
                                dao.delete(relayId, listOf(agentId))
                                CloseOutcome.Archived
                            }
                            else -> CloseOutcome.Failed("No se pudo archivar el agente")
                        }
                    }
                }
                WsReply.Timeout -> CloseOutcome.Failed("Tiempo de espera agotado al cerrar el agente")
                WsReply.NotConnected -> CloseOutcome.Failed("Sin conexión con el relay")
            }
        }

        /**
         * Mensaje del banner de cierre fallido: lleva la razón del relay cuando existe, porque es el único
         * dato que permite diagnosticar desde el teléfono. Sin ella se queda el texto de siempre, sin dos
         * puntos colgando al final.
         */
        internal fun closeFailureMessage(relayMessage: String): String =
            if (relayMessage.isBlank()) "No se pudo cerrar el agente" else "No se pudo cerrar el agente: $relayMessage"

        /** Lista archivados desde el relay; el cursor pertenece al estado de pantalla, no a Room. */
        suspend fun archived(
            machineId: String? = null,
            before: Long? = null,
        ): ArchivedAgentsResult =
            when (
                val reply =
                    connection.request(
                        Envelope.of(MsgType.AGENT_ARCHIVED, AgentArchivedRequest(machineId = machineId, before = before), id = Ulid.new()),
                    )
            ) {
                is WsReply.Ok ->
                    runCatching { reply.envelope.body<AgentArchivedList>() }
                        .fold(
                            onSuccess = { ArchivedAgentsResult.Ok(it.agents, it.nextBefore) },
                            onFailure = { ArchivedAgentsResult.Failed("No se pudieron cargar los agentes archivados") },
                        )
                is WsReply.Error -> ArchivedAgentsResult.Failed("No se pudieron cargar los agentes archivados")
                WsReply.Timeout -> ArchivedAgentsResult.Failed("Se agotó el tiempo al cargar los agentes archivados")
                WsReply.NotConnected -> ArchivedAgentsResult.Failed("Sin conexión para cargar los agentes archivados")
            }

        /** Elimina irreversiblemente el historial archivado en el relay. */
        suspend fun purge(agentId: String): Boolean {
            val reply = connection.request(Envelope.of(MsgType.AGENT_PURGE, AgentRef(agentId), id = Ulid.new()))
            return reply is WsReply.Ok
        }

        /** `agent.forget`: borra un agente offline de la lista. */
        suspend fun forget(agentId: String): Boolean {
            val relayId = secrets.current()?.relayId ?: return false
            val reply = connection.request(Envelope.of(MsgType.AGENT_FORGET, AgentRef(agentId), id = Ulid.new()))
            if (reply is dev.j3pf.rp.data.net.WsReply.Ok) {
                dao.delete(relayId, listOf(agentId))
                return true
            }
            return false
        }

        suspend fun clear() {
            secrets.current()?.relayId?.let { dao.clear(it) }
        }
    }
