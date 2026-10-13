package dev.j3pf.rp.ui.promptsheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.util.BiometricGate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Resultado de intentar responder desde la UI. */
sealed interface AnswerUi {
    data object Idle : AnswerUi
    data object Busy : AnswerUi
    data object Done : AnswerUi
    data object AlreadyResolved : AnswerUi
    data class Queued(val message: String) : AnswerUi
    data class Error(val message: String) : AnswerUi
}

@HiltViewModel
class PromptSheetViewModel @Inject constructor(
    private val prompts: PromptsRepository,
    settings: SettingsRepository,
    val gate: BiometricGate,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _status = MutableStateFlow<AnswerUi>(AnswerUi.Idle)
    val status: StateFlow<AnswerUi> = _status

    /** Prompt abierto con ese id; `null` cuando se resuelve (en la terminal, otro dispositivo o aquí). */
    fun open(promptId: String): Flow<Prompt?> = prompts.open.map { list -> list.firstOrNull { it.promptId == promptId } }

    /** ¿Debe pedirse desbloqueo antes de enviar esta respuesta? (aprobaciones críticas) */
    fun requiresAuth(p: Prompt): Boolean =
        settings.value.requireUnlockToApprove && (p.category == PromptCategory.PERMISSION || p.danger == Danger.HIGH)

    fun reset() = _status.update { AnswerUi.Idle }

    fun answer(promptId: String, answer: Answer) {
        if (_status.value == AnswerUi.Busy) return
        _status.value = AnswerUi.Busy
        viewModelScope.launch {
            _status.value = when (val r = prompts.answer(promptId, answer)) {
                AnswerOutcome.Sent -> AnswerUi.Done
                AnswerOutcome.AlreadyResolved -> AnswerUi.AlreadyResolved
                AnswerOutcome.Queued -> AnswerUi.Queued("Sin conexión · se enviará al volver la red")
                is AnswerOutcome.Failed -> AnswerUi.Error(r.message)
            }
        }
    }
}
