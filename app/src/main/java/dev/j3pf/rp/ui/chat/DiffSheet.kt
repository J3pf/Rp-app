package dev.j3pf.rp.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.j3pf.rp.R
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.ui.diff.DiffSheet as SharedDiffSheet

@Composable
fun DiffSheet(
    diff: HostGitDiffResult?,
    selectedFile: String?,
    isLoading: Boolean,
    onSelectFile: (String) -> Unit,
    onDiscardFile: (String) -> Unit,
    onRequestAdjustment: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SharedDiffSheet(
        diff = diff,
        selectedFile = selectedFile,
        isLoading = isLoading,
        onSelectFile = onSelectFile,
        onDiscardFile = onDiscardFile,
        onRequestAdjustment = onRequestAdjustment,
        onDismiss = onDismiss,
        title = stringResource(R.string.diff_title),
        discardLabel = stringResource(R.string.diff_discard),
        requestAdjustmentLabel = "Pedir ajuste",
    )
}
