package dev.j3pf.rp.data.repo

import dev.j3pf.rp.data.db.MachineDao
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.domain.model.toModel
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MachinesUpdate
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.push.NotificationChannels
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MachinesRepository @Inject constructor(
    private val dao: MachineDao,
    private val secrets: SecretStore,
    private val bus: MessageBus,
    private val api: RelayApi,
    private val channels: NotificationChannels,
    @AppScope private val scope: CoroutineScope,
) {
    val machines: Flow<List<MachineSummary>> = secrets.session.flatMapLatest { s ->
        if (s == null) emptyFlow() else dao.observe(s.relayId).map { list -> list.map { it.toModel() } }
    }

    val machinesState: StateFlow<List<MachineSummary>> =
        machines.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Escucha el bus: `app.welcome` reemplaza; `machines.update` aplica upserts/borrados. */
    fun start() {
        bus.messages.onEach { env ->
            val relayId = secrets.current()?.relayId ?: return@onEach
            when (env.t) {
                MsgType.APP_WELCOME -> {
                    val w = env.body<AppWelcome>()
                    dao.upsert(w.machines.map { it.toEntity(relayId) })
                    dao.deleteNotIn(relayId, w.machines.map { it.machineId }.ifEmpty { listOf("") })
                    channels.sync(w.machines)
                }
                MsgType.MACHINES_UPDATE -> {
                    val u = env.body<MachinesUpdate>()
                    if (u.upsert.isNotEmpty()) dao.upsert(u.upsert.map { it.toEntity(relayId) })
                    if (u.remove.isNotEmpty()) {
                        dao.delete(relayId, u.remove)
                        u.remove.forEach(channels::removeMachine)
                    }
                    channels.sync(dao.all(relayId).map { it.toModel() })
                }
            }
        }.launchIn(scope)
    }

    /** Edición vía `PATCH /v1/machines/{id}` (el relay también emite `machines.update`). */
    suspend fun patch(id: String, patch: MachinePatch): ApiResult<MachineSummary> {
        val res = api.patchMachine(id, patch)
        val relayId = secrets.current()?.relayId
        if (res is ApiResult.Ok && relayId != null) dao.upsert(listOf(res.value.toEntity(relayId)))
        return res
    }

    suspend fun revoke(id: String): Boolean {
        val res = api.revokeMachine(id)
        val relayId = secrets.current()?.relayId
        if (res is ApiResult.Ok && relayId != null) {
            dao.delete(relayId, listOf(id))
            channels.removeMachine(id)
        }
        return res is ApiResult.Ok
    }

    suspend fun refresh() {
        val relayId = secrets.current()?.relayId ?: return
        val res = api.machines()
        if (res is ApiResult.Ok) {
            dao.upsert(res.value.map { it.toEntity(relayId) })
            channels.sync(res.value)
        }
    }

    suspend fun clear() {
        secrets.current()?.relayId?.let { dao.clear(it) }
    }
}
