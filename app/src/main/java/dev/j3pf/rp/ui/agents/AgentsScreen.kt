package dev.j3pf.rp.ui.agents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.R
import dev.j3pf.rp.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun AgentsScreen(
    onOpenChat: (String) -> Unit,
    onNewAgent: () -> Unit,
    onOpenArchived: () -> Unit = {},
    mainVm: MainViewModel,
    vm: AgentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val conn by mainVm.connState.collectAsStateWithLifecycle()
    val lifecycle by vm.lifecycleState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lifecycle.message) {
        lifecycle.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeLifecycleMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onOpenArchived) {
            Text(stringResource(R.string.archived_title), color = MaterialTheme.colorScheme.primary)
        }
        AgentsContent(
            state = state,
            conn = conn,
            snackbarHostState = snackbar,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onOpenChat = onOpenChat,
            onNewAgent = onNewAgent,
            onSelect = vm::select,
            onRefresh = vm::refresh,
            onResume = { a, machineName ->
                if (state.machines.find { it.name == machineName }?.hostOnline == true) {
                    vm.resumeAgent(a) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                } else {
                    scope.launch { snackbar.showSnackbar("Para reanudar agentes, ejecuta rp-host en $machineName") }
                }
            },
            onForget = vm::forget,
        )
    }

    lifecycle.forgetAgentId?.let {
        AgentCloseConfirmationDialog(onConfirm = vm::confirmForget, onDismiss = vm::cancelForget)
    }
}

@Composable
fun AgentCloseConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_close_title)) },
        text = { Text(stringResource(R.string.agent_close_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.agent_close_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
