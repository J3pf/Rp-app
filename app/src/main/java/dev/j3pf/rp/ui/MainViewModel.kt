package dev.j3pf.rp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Destino pedido desde fuera de la UI (notificación, deep link). */
sealed interface NavTarget {
    data class Prompt(val promptId: String, val agentId: String?) : NavTarget
    data class Agent(val agentId: String) : NavTarget
    data class Pair(val url: String, val code: String) : NavTarget
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val secrets: SecretStore,
    private val settingsRepo: SettingsRepository,
    connection: RelayConnection,
) : ViewModel() {
    val session: StateFlow<Session?> = secrets.session
    val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val connState: StateFlow<ConnState> = connection.state

    private val _target = MutableStateFlow<NavTarget?>(null)
    val target: StateFlow<NavTarget?> = _target.asStateFlow()

    /** Prompt abierto en la hoja de respuesta global. */
    private val _sheetPrompt = MutableStateFlow<String?>(null)
    val sheetPrompt: StateFlow<String?> = _sheetPrompt.asStateFlow()

    /** Bloqueo biométrico de la app (fase 4). */
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = 0L

    fun post(t: NavTarget) = _target.update { t }
    fun consumeTarget() = _target.update { null }

    fun openPrompt(id: String) = _sheetPrompt.update { id }
    fun closePrompt() = _sheetPrompt.update { null }

    fun onAppStopped() { backgroundedAt = System.currentTimeMillis() }

    /** Se bloquea tras 30 s en segundo plano (o en arranque en frío) si el ajuste está activo. */
    fun onAppStarted() {
        val away = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt == 0L || away > LOCK_AFTER_MS) _locked.update { true }
    }

    fun unlock() = _locked.update { false }

    fun markOnboardingDone() = viewModelScope.launch { settingsRepo.setOnboardingDone(true) }

    private companion object { const val LOCK_AFTER_MS = 30_000L }
}
