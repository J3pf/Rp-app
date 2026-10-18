package dev.j3pf.rp.ui.promptsheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSheet(
    promptId: String,
    onDismiss: () -> Unit,
    vm: PromptSheetViewModel = hiltViewModel(),
) {
    val promptFlow = remember(promptId) { vm.open(promptId) }
    val prompt by promptFlow.collectAsStateWithLifecycle(initialValue = null)
    val status by vm.status.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

            else -> {
                Unit
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            vm.reset()
            onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            val p = prompt
            when {
                status == AnswerUi.AlreadyResolved -> {
                    Text("Ya respondido en otro lugar", style = MaterialTheme.typography.titleMedium)
                }

                status is AnswerUi.Queued -> {
                    Text((status as AnswerUi.Queued).message, style = MaterialTheme.typography.titleMedium)
                }

                p == null -> {
                    Text("Esta pregunta ya no está abierta", style = MaterialTheme.typography.titleMedium)
                }

                else -> {
                    PromptContent(
                        prompt = p,
                        busy = status == AnswerUi.Busy,
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
                    (status as? AnswerUi.Error)?.let {
                        Text(
                            it.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    authMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}
