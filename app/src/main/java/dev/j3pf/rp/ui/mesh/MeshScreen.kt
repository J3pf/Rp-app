package dev.j3pf.rp.ui.mesh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.R

@Composable
fun MeshScreen(
    onOpenChat: (String) -> Unit,
    viewModel: MeshViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onScreenVisible()
                    Lifecycle.Event.ON_STOP -> viewModel.onScreenInvisible()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenInvisible()
        }
    }

    MeshScreenContent(
        uiState = state,
        onOpenChat = onOpenChat,
        title = stringResource(R.string.mesh_title),
        recenterDesc = stringResource(R.string.mesh_recenter_desc),
        onToggleListView = viewModel::toggleListView,
        onSelectMachine = viewModel::selectMachine,
        onSelectGroup = viewModel::selectGroup,
        onNodeClick = viewModel::onNodeClick,
        onEdgeClick = viewModel::onEdgeClick,
        onViewportSizeChanged = viewModel::onViewportSizeChanged,
        onDismissNodeDetail = viewModel::dismissNodeDetail,
        onViewConversation = { peerId ->
            state.nodeDetail?.node?.agentId?.let { nodeId ->
                viewModel.onOpenConversation(nodeId, peerId)
            }
        },
        onDismissConversation = viewModel::dismissConversationDetail,
        onLoadMoreConversation = viewModel::loadMoreConversation,
        onRetry = viewModel::retry,
    )
}
