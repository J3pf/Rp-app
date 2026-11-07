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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface PromptSheetChromeState {
    data object Content : PromptSheetChromeState

    data object AlreadyResolved : PromptSheetChromeState

    data class Queued(
        val message: String,
    ) : PromptSheetChromeState

    data object PromptMissing : PromptSheetChromeState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSheetChrome(
    state: PromptSheetChromeState,
    onDismissRequest: () -> Unit,
    errorMessage: String? = null,
    authMessage: String? = null,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
            when (state) {
                PromptSheetChromeState.AlreadyResolved -> {
                    Text("Ya respondido en otro lugar", style = MaterialTheme.typography.titleMedium)
                }

                PromptSheetChromeState.Content -> {
                    content()
                    errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    authMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }

                is PromptSheetChromeState.Queued -> {
                    Text(state.message, style = MaterialTheme.typography.titleMedium)
                }

                PromptSheetChromeState.PromptMissing -> {
                    Text("Esta pregunta ya no está abierta", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
