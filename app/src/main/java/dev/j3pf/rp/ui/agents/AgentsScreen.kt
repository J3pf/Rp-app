package dev.j3pf.rp.ui.agents

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun AgentsScreen(
    onOpenChat: (String) -> Unit,
    onNewAgent: () -> Unit,
    mainVm: MainViewModel,
    vm: AgentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val conn by mainVm.connState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AgentsContent(
        state = state,
        conn = conn,
        snackbarHostState = snackbar,
        onOpenChat = onOpenChat,
        onNewAgent = onNewAgent,
        onSelect = vm::select,
        onRefresh = vm::refresh,
        onResume = { a, machineName ->
            if (state.machines.find { it.name == machineName }?.hostOnline == true) {
                vm.resumeAgent(a) { msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            } else {
                scope.launch {
                    snackbar.showSnackbar("Para reanudar agentes, ejecuta rp-host en $machineName")
                }
            }
        },
        onForget = vm::forget,
    )
}
