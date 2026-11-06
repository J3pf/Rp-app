package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.lastSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    agent: AgentSummary?,
    sessionName: String?,
    model: String?,
    thinkingLevel: String?,
    working: Boolean,
    diffFileCount: Int,
    chatFontScale: Float,
    chatMessageSpacing: String,
    muted: Boolean,
    readOnly: Boolean,
    onBack: () -> Unit,
    onAbort: () -> Unit,
    onOpenDiff: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onMessageSpacingChange: (String) -> Unit,
    onInfo: () -> Unit,
    onToggleMute: () -> Unit,
    onQuit: () -> Unit,
    onForget: () -> Unit,
) {
    var showQuitDialog by remember { mutableStateOf(false) }

    TopAppBar(
        windowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        title = {
            val rawTitle = agent?.let { agentTitle(it.sessionName, it.cwd, it.agentId) } ?: sessionName ?: "Agente"
            val folder = agent?.cwd?.let { lastSegment(it) }.orEmpty()
            val displayTopic =
                if (folder.isNotEmpty() && rawTitle.startsWith("$folder · ")) {
                    rawTitle.removePrefix("$folder · ").trim()
                } else {
                    rawTitle
                }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    displayTopic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (agent != null) {
                    val effectiveAgent =
                        if (working && agent.online && agent.status == "idle") {
                            agent.copy(status = "working")
                        } else {
                            agent
                        }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(effectiveAgent)
                        val hasChanges = diffFileCount > 0
                        if (!agent.gitBranch.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color =
                                    if (hasChanges) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                modifier = Modifier.clickable { onOpenDiff() },
                            ) {
                                Text(
                                    "⎇ ${agent.gitBranch}${if (hasChanges) " *" else ""}",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (hasChanges) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        val modelText = (model ?: agent.model?.label)?.takeIf { it.isNotBlank() }
                        val thinkingText = thinkingLevel?.takeIf { it.isNotBlank() }
                        val subtitle =
                            listOfNotNull(
                                folder.takeIf { it.isNotBlank() },
                                modelText,
                                thinkingText,
                            ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
        actions = {
            if (working) {
                IconButton(onClick = onAbort) { Icon(Icons.Filled.Stop, "Interrumpir", tint = MaterialTheme.colorScheme.error) }
            }
            if (diffFileCount > 0) {
                AssistChip(
                    onClick = onOpenDiff,
                    label = { Text("Diff ($diffFileCount)", style = MaterialTheme.typography.labelSmall) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    border = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
            DropdownMenu(menu, { menu = false }) {
                if (agent != null) {
                    DropdownMenuItem(
                        text = { Text("Revisar cambios Git (Diff)") },
                        onClick = {
                            menu = false
                            onOpenDiff()
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Letra (${(chatFontScale * 100).toInt()}%)")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { onFontScaleChange((chatFontScale - 0.10f).coerceAtLeast(0.80f)) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text("A-", fontWeight = FontWeight.Bold)
                                }
                                if (kotlin.math.abs(chatFontScale - 1.0f) > 0.03f) {
                                    TextButton(
                                        onClick = { onFontScaleChange(1.0f) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("100%", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                TextButton(
                                    onClick = { onFontScaleChange((chatFontScale + 0.10f).coerceAtMost(1.45f)) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text("A+", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    onClick = {},
                )
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Espaciado de mensajes", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    "compact" to "Compacto",
                                    "normal" to "Normal",
                                    "spacious" to "Amplio",
                                ).forEach { (id, label) ->
                                    val selected = chatMessageSpacing == id
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onMessageSpacingChange(id) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(999.dp),
                                    )
                                }
                            }
                        }
                    },
                    onClick = {},
                )
                DropdownMenuItem(text = { Text("Información") }, onClick = {
                    menu = false
                    onInfo()
                })
                DropdownMenuItem(
                    text = {
                        Text(if (muted) "Activar notificaciones del agente" else "Silenciar notificaciones del agente")
                    },
                    onClick = {
                        menu = false
                        onToggleMute()
                    },
                )
                if (!readOnly && agent != null && agent.online) {
                    DropdownMenuItem(
                        text = { Text("Cerrar agente (/quit)", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            showQuitDialog = true
                        },
                    )
                }
                if (!readOnly && agent != null && !agent.online) {
                    DropdownMenuItem(text = { Text("Olvidar agente") }, onClick = {
                        menu = false
                        onForget()
                    })
                }
            }
        },
    )

    if (showQuitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("Cerrar sesión de Pi") },
            text = { Text("¿Deseas enviar /quit al agente para cerrar su proceso en la terminal de la PC?") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitDialog = false
                    onQuit()
                }) {
                    Text("Cerrar agente", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Cancelar") }
            },
        )
    }
}
