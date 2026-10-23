package dev.j3pf.rp.ui.newagent

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun NewAgentScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    vm: NewAgentViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.form.created) { if (ui.form.created) onCreated() }

    NewAgentContent(
        ui = ui,
        onBack = onBack,
        onPickMachine = vm::pickMachine,
        onOpenDir = vm::openDir,
        onEnterDir = vm::enter,
        onUpDir = vm::up,
        canGoUp = vm.canGoUp,
        onSetLauncher = vm::setLauncher,
        onSetBackend = vm::setBackend,
        onSetName = vm::setName,
        onSetPrompt = vm::setPrompt,
        onCreate = vm::create,
        herdrSection = {
            HerdrPlacementSection(
                ui = ui,
                onSetPlacement = vm::setPlacement,
                onSetWorkspace = vm::setWorkspace,
                onSetPane = vm::setPane,
                onSetDirection = vm::setDirection,
                onRefreshLayout = vm::loadLayout,
            )
        },
        snackbarHostState = snackbar,
    )
}
