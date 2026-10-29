package dev.j3pf.rp.ui.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.R
import dev.j3pf.rp.protocol.ArchivedAgentSummary
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    vm: AgentsViewModel = hiltViewModel(),
) {
    val lifecycle by vm.lifecycleState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.loadArchived() }
    LaunchedEffect(lifecycle.message) {
        lifecycle.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeLifecycleMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archived_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ArchivedAgentsContent(
            agents = lifecycle.archivedAgents,
            loading = lifecycle.archivedLoading,
            error = lifecycle.archivedError,
            nextBefore = lifecycle.archivedNextBefore,
            onOpenChat = onOpenChat,
            onPurge = vm::purgeArchived,
            onRetry = vm::loadArchived,
            onLoadMore = vm::loadMoreArchived,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun ArchivedAgentsContent(
    agents: List<ArchivedAgentSummary>,
    loading: Boolean,
    error: String?,
    onOpenChat: (String) -> Unit,
    onPurge: (String) -> Unit,
    modifier: Modifier = Modifier,
    nextBefore: Long? = null,
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        when {
            loading && agents.isEmpty() -> CircularProgressIndicator(Modifier.padding(24.dp))
            agents.isEmpty() && error == null -> Text(stringResource(R.string.archived_empty), Modifier.padding(24.dp))
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(agents, key = { it.agentId }) { archived ->
                        ArchivedAgentRow(archived, onOpenChat, onPurge)
                    }
                }
        }
        if (nextBefore != null && error == null) {
            TextButton(onClick = onLoadMore, enabled = !loading) { Text(stringResource(R.string.archived_load_more)) }
        }
    }
}

@Composable
private fun ArchivedAgentRow(
    archived: ArchivedAgentSummary,
    onOpenChat: (String) -> Unit,
    onPurge: (String) -> Unit,
) {
    val name = agentTitle(archived.sessionName, archived.cwd, archived.agentId)
    var confirmPurge by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable { onOpenChat(archived.agentId) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.archived_machine, archived.machineName), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.archived_last_seen, relativeTime(archived.lastSeenAt)), style = MaterialTheme.typography.bodySmall)
            Text(
                pluralStringResource(R.plurals.archived_message_count, archived.messageCount, archived.messageCount),
                style = MaterialTheme.typography.bodySmall,
            )
            archived.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    confirmPurge = true
                }) { Text(stringResource(R.string.archived_purge_action), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text(stringResource(R.string.archived_purge_title, name)) },
            text = { Text(stringResource(R.string.archived_purge_confirmation, name)) },
            confirmButton = {
                TextButton(onClick = { confirmPurge = false }) { Text(stringResource(R.string.archived_keep_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmPurge = false
                    onPurge(archived.agentId)
                }) { Text(stringResource(R.string.archived_purge_confirm), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}
