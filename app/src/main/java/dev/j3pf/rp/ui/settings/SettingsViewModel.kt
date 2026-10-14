package dev.j3pf.rp.ui.settings

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.SessionRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachinePrefs
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.QuietHours
import dev.j3pf.rp.push.NotificationChannels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de las condiciones que hacen fiables las notificaciones (pantalla de Ajustes). */
data class HealthState(
    val notificationsEnabled: Boolean = true,
    val approvalsMuted: Boolean = false,
    val batteryExcluded: Boolean = false,
)

data class SettingsUiState(
    val session: Session? = null,
    val conn: ConnState = ConnState.Disconnected,
    val settings: AppSettings = AppSettings(),
    val machines: List<MachineSummary> = emptyList(),
    val health: HealthState = HealthState(),
    val agents: List<AgentSummary> = emptyList(),
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val secrets: SecretStore,
        private val settingsRepo: SettingsRepository,
        private val machinesRepo: MachinesRepository,
        private val agentsRepo: AgentsRepository,
        private val sessionRepo: SessionRepository,
        private val api: RelayApi,
        private val channels: NotificationChannels,
        connection: RelayConnection,
    ) : ViewModel() {
        private val health = MutableStateFlow(HealthState())

        val state: StateFlow<SettingsUiState> =
            combine(
                combine(secrets.session, connection.state, settingsRepo.settings, machinesRepo.machines, health) { s, c, st, ms, h ->
                    SettingsUiState(s, c, st, ms, h)
                },
                agentsRepo.agents,
            ) { ui, ags -> ui.copy(agents = ags) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

        private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
        val devices: StateFlow<List<DeviceInfo>> = _devices
        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message
        private val _pairCode = MutableStateFlow<String?>(null)
        val pairCode: StateFlow<String?> = _pairCode

        fun consumeMessage() = _message.update { null }

        fun refreshHealth() {
            val pm = context.getSystemService(PowerManager::class.java)
            health.value =
                HealthState(
                    notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    approvalsMuted = channels.approvalsMuted(state.value.machines.map { it.machineId }),
                    batteryExcluded = pm.isIgnoringBatteryOptimizations(context.packageName),
                )
        }

        // ───────────────────────────── Dispositivos / máquinas ─────────────────────────────

        fun loadDevices() =
            viewModelScope.launch {
                when (val r = api.devices()) {
                    is ApiResult.Ok -> {
                        _devices.value =
                            r.value.map { it.copy(current = it.current || it.deviceId == secrets.current()?.deviceId) }
                    }

                    else -> {
                        _message.value = "No se pudo cargar la lista de dispositivos"
                    }
                }
            }

        fun revokeDevice(id: String) =
            viewModelScope.launch {
                if (api.revokeDevice(id) is ApiResult.Ok) {
                    _devices.update { l -> l.filterNot { it.deviceId == id } }
                    _message.value = "Dispositivo revocado"
                } else {
                    _message.value = "No se pudo revocar"
                }
            }

        fun refreshMachines() = viewModelScope.launch { machinesRepo.refresh() }

        fun editMachine(
            id: String,
            name: String,
            context: String,
            color: String,
        ) = viewModelScope.launch {
            val r =
                machinesRepo.patch(
                    id,
                    MachinePatch(name = name.trim().ifEmpty { null }, context = context.trim().ifEmpty { null }, color = color),
                )
            _message.value = if (r is ApiResult.Ok) "PC actualizada" else "No se pudo guardar"
        }

        fun revokeMachine(id: String) =
            viewModelScope.launch {
                _message.value = if (machinesRepo.revoke(id)) "PC revocada" else "No se pudo revocar"
            }

        fun createMachineCode() =
            viewModelScope.launch {
                _pairCode.value = null
                when (val r = api.createMachineCode()) {
                    is ApiResult.Ok -> _pairCode.value = r.value.code
                    else -> _message.value = "No se pudo generar el código"
                }
            }

        fun clearPairCode() = _pairCode.update { null }

        fun setAppTheme(theme: String) = viewModelScope.launch { settingsRepo.setAppTheme(theme) }

        // ───────────────────────────── Notificaciones ─────────────────────────────

        fun setDirectPush(v: Boolean) = viewModelScope.launch { sessionRepo.applyPushChannels(v) }

        fun setCategory(
            category: String,
            enabled: Boolean,
        ) = updatePrefs { p ->
            p.copy(categories = p.categories + (category to enabled))
        }

        fun unmuteAgent(agentId: String) = viewModelScope.launch { settingsRepo.setAgentMuted(agentId, false) }

        fun setCriticalAlways(v: Boolean) =
            viewModelScope.launch {
                settingsRepo.setCriticalAlways(v)
                pushPrefs()
            }

        fun setMachineMute(
            machineId: String,
            mute: Boolean,
        ) = updatePrefs { p ->
            val m = p.machines[machineId] ?: MachinePrefs()
            p.copy(machines = p.machines + (machineId to m.copy(mute = mute)))
        }

        fun setMachineCategory(
            machineId: String,
            category: String,
            enabled: Boolean,
        ) = updatePrefs { p ->
            val m = p.machines[machineId] ?: MachinePrefs()
            p.copy(machines = p.machines + (machineId to m.copy(categories = m.categories + (category to enabled))))
        }

        /** Horas silenciosas por PC ("HH:mm"); `from`/`to` vacíos las desactivan. */
        fun setQuietHours(
            machineId: String,
            from: String,
            to: String,
            tz: String,
        ) = updatePrefs { p ->
            val m = p.machines[machineId] ?: MachinePrefs()
            val q = if (from.isBlank() || to.isBlank()) null else QuietHours(from, to, tz.ifBlank { null })
            p.copy(machines = p.machines + (machineId to m.copy(quietHours = q)))
        }

        private fun updatePrefs(f: (NotifyPrefs) -> NotifyPrefs) =
            viewModelScope.launch {
                settingsRepo.setNotifyPrefs(f(settingsRepo.current().notifyPrefs))
                pushPrefs()
            }

        /** `PUT /v1/devices/me/prefs`: el relay decide si manda el push. */
        private suspend fun pushPrefs() {
            val s = settingsRepo.current()
            // "Autorizaciones críticas siempre" se guarda como categoría especial que ignora silencios/horas.
            val prefs = s.notifyPrefs.copy(categories = s.notifyPrefs.categories + ("criticalAlways" to s.criticalAlways))
            val r = api.putPrefs(prefs)
            if (r !is ApiResult.Ok) _message.value = "No se pudieron guardar las preferencias en el relay"
        }

        fun sendTestNotification() =
            viewModelScope.launch {
                _message.value =
                    when (api.pushTest()) {
                        is ApiResult.Ok -> "Notificación de prueba enviada"
                        else -> "No se pudo enviar la notificación de prueba"
                    }
            }

        // ───────────────────────────── Seguridad ─────────────────────────────

        fun setRequireUnlock(v: Boolean) = viewModelScope.launch { settingsRepo.setRequireUnlock(v) }

        fun setAppLock(v: Boolean) = viewModelScope.launch { settingsRepo.setAppLock(v) }

        fun setFlagSecure(v: Boolean) = viewModelScope.launch { settingsRepo.setFlagSecure(v) }

        fun logout(onDone: () -> Unit) =
            viewModelScope.launch {
                sessionRepo.logout()
                onDone()
            }
    }
