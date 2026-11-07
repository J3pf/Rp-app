package dev.j3pf.rp.ui.promptsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.ui.components.rememberActivity
import dev.j3pf.rp.util.AuthResult
import kotlinx.coroutines.launch

/**
 * Hoja de respuesta global (BottomSheet). Se cierra sola si el prompt se resuelve en otro sitio; si al responder
 * llega 409 muestra "Ya respondido en otro lugar" y se cierra. Las aprobaciones críticas piden desbloqueo si el
 * ajuste "Requerir desbloqueo para aprobar" está activo.
 */
@Composable
fun PromptSheet(
    promptId: String,
    onDismiss: () -> Unit,
    vm: PromptSheetViewModel = hiltViewModel(),
) {
    val promptFlow = remember(promptId) { vm.open(promptId) }
    val prompt by promptFlow.collectAsStateWithLifecycle(initialValue = null)
    val status by vm.status.collectAsStateWithLifecycle()
    val activity = rememberActivity()
    val scope = rememberCoroutineScope()
    var authMessage by remember { mutableStateOf<String?>(null) }
    var seen by remember { mutableStateOf(false) }

    LaunchedEffect(prompt) {
        if (prompt != null) {
            seen = true
        } else if (seen) {
            onDismiss()
        }
    }
    LaunchedEffect(status) {
        when (status) {
            AnswerUi.Done -> {
                vm.reset()
                onDismiss()
            }

            AnswerUi.AlreadyResolved -> {
                kotlinx.coroutines.delay(1400)
                vm.reset()
                onDismiss()
            }

            is AnswerUi.Queued -> {
                kotlinx.coroutines.delay(1600)
                vm.reset()
                onDismiss()
            }

            else -> {}
        }
    }

    val currentStatus = status
    val chromeState = when {
        currentStatus == AnswerUi.AlreadyResolved -> PromptSheetChromeState.AlreadyResolved
        currentStatus is AnswerUi.Queued -> PromptSheetChromeState.Queued(currentStatus.message)
        prompt == null -> PromptSheetChromeState.PromptMissing
        else -> PromptSheetChromeState.Content
    }

    PromptSheetChrome(
        state = chromeState,
        onDismissRequest = {
            vm.reset()
            onDismiss()
        },
        errorMessage = (currentStatus as? AnswerUi.Error)?.message,
        authMessage = authMessage,
    ) {
        val p = prompt
        if (p != null) {
            PromptContent(
                prompt = p,
                busy = currentStatus == AnswerUi.Busy,
                onAnswer = { answer: Answer ->
                    scope.launch {
                        if (vm.requiresAuth(p)) {
                            if (activity == null) {
                                authMessage = "No se pudo verificar tu identidad"
                                return@launch
                            }
                            when (vm.gate.authenticate(activity, "Confirma tu identidad", p.title.take(80))) {
                                AuthResult.OK -> vm.answer(p.promptId, answer)
                                AuthResult.FAILED -> authMessage = "Desbloqueo cancelado"
                                AuthResult.UNAVAILABLE -> authMessage = "Configura un bloqueo de pantalla para aprobar"
                            }
                        } else {
                            vm.answer(p.promptId, answer)
                        }
                    }
                },
            )
        }
    }
}
