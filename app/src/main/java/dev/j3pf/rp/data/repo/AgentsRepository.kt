package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.db.AgentDao
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.domain.model.toModel
import dev.j3pf.rp.protocol.AgentRef
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Ulid
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AgentsRepository @Inject constructor(
    private val dao: AgentDao,
    private val secrets: SecretStore,
    private val bus: MessageBus,
    private val connection: RelayConnection,
    private val settings: SettingsRepository,
    @AppScope private val scope: CoroutineScope,
) {
    /** Todos los agentes con su contador de prompts abiertos. */
    val agents: Flow<List<AgentSummary>> = secrets.session.flatMapLatest { s ->
        if (s == null) emptyFlow() else dao.observe(s.relayId).map { list -> list.map { it.agent.toModel(it.openPrompts) } }
    }

    /** Agentes filtrados por el selector de PC (`"all"` o un `context`). */
    val filtered: Flow<List<AgentSummary>> = combine(agents, settings.settings.map { it.selectedContext }) { list, ctx ->
        if (ctx == AppSettings.ALL) list else list.filter { it.machineContext == ctx }
    }

    fun observe(agentId: String): Flow<AgentSummary?> = secrets.session.flatMapLatest { s ->
        if (s == null) emptyFlow() else dao.observeOne(s.relayId, agentId).map { it?.agent?.toModel(it.openPrompts) }
    }

    fun start() {
        bus.messages.onEach { env ->
            val relayId = secrets.current()?.relayId ?: return@onEach
            when (env.t) {
                MsgType.APP_WELCOME -> {
                    val w = runCatching { env.body<AppWelcome>() }.getOrNull() ?: return@onEach
                    dao.syncWelcome(relayId, w.agents.map { it.toEntity(relayId) }, w.agents.map { it.agentId }.ifEmpty { listOf("") })
                }
                MsgType.AGENTS_UPDATE -> {
                    val u = runCatching { env.body<AgentsUpdate>() }.getOrNull() ?: return@onEach
                    if (u.upsert.isNotEmpty()) dao.upsert(u.upsert.map { it.toEntity(relayId) })
                    if (u.remove.isNotEmpty()) dao.delete(relayId, u.remove)
                }
            }
        }.launchIn(scope)
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
