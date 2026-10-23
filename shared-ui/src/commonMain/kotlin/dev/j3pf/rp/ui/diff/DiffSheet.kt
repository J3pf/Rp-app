package dev.j3pf.rp.ui.diff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.protocol.HostGitDiffFile
import dev.j3pf.rp.protocol.HostGitDiffResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffSheet(
    diff: HostGitDiffResult?,
    selectedFile: String?,
    isLoading: Boolean,
    onSelectFile: (String) -> Unit,
    onDiscardFile: (String) -> Unit,
    onRequestAdjustment: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Revisión de cambios",
    discardLabel: String = "Descartar",
    requestAdjustmentLabel: String = "Pedir ajuste",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDiscardFile by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    diff?.branch?.let { br ->
                        Text(
                            text = "Rama: $br",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    diff?.let { d ->
                        if (d.totalAdditions > 0 || d.totalDeletions > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (d.totalAdditions > 0) {
                                        Text(
                                            text = "+${d.totalAdditions}",
                                            color = Color(0xFF2E7D32),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (d.totalDeletions > 0) {
                                        Text(
                                            text = "-${d.totalDeletions}",
                                            color = Color(0xFFC62828),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                return@ModalBottomSheet
            }

            val files = diff?.files ?: emptyList()
            if (files.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "El árbol de trabajo está limpio (sin cambios pendientes).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@ModalBottomSheet
            }

            // File Chips
            val activePath = selectedFile ?: files.firstOrNull()?.path
            val scrollState = rememberScrollState()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                files.forEach { file ->
                    val isSelected = file.path == activePath
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFile(file.path) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = file.path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(4.dp))
                                if (file.additions > 0) {
                                    Text(
                                        text = "+${file.additions}",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 10.sp,
                                    )
                                    Spacer(Modifier.width(2.dp))
                                }
                                if (file.deletions > 0) {
                                    Text(
                                        text = "-${file.deletions}",
                                        color = Color(0xFFC62828),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val activeFile = files.find { it.path == activePath } ?: files.firstOrNull()
            if (activeFile != null) {
                // File action bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = activeFile.path,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onRequestAdjustment(activeFile.path) },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                requestAdjustmentLabel,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        OutlinedButton(
                            onClick = { confirmDiscardFile = activeFile.path },
                            shape = RoundedCornerShape(999.dp),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                discardLabel,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Diff viewer
                DiffFileContent(activeFile)
            }
        }
    }

    // Confirmation dialog
    confirmDiscardFile?.let { path ->
        AlertDialog(
            onDismissRequest = { confirmDiscardFile = null },
            title = { Text(discardLabel) },
            text = { Text("¿Seguro que deseas descartar los cambios en '$path'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscardFile = null
                        onDiscardFile(path)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(discardLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardFile = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun DiffFileContent(file: HostGitDiffFile) {
    val lines = remember(file.patch) { file.patch.split("\n") }
    val horizontalScroll = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 460.dp),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScroll)
                    .padding(vertical = 4.dp),
        ) {
            items(lines) { line ->
                val bg =
                    when {
                        line.startsWith("+") && !line.startsWith("+++") -> Color(0x224CAF50)
                        line.startsWith("-") && !line.startsWith("---") -> Color(0x22F44336)
                        line.startsWith("@@") -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        else -> Color.Transparent
                    }
                val textColor =
                    when {
                        line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2E7D32)
                        line.startsWith("-") && !line.startsWith("---") -> Color(0xFFC62828)
                        line.startsWith("@@") -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = line,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                        color = textColor,
                    )
                }
            }
        }
    }
}
