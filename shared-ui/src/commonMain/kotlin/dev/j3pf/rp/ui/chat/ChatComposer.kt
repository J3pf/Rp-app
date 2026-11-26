package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.ui.components.horizontalEdgeFade

/** Platform-neutral attachment state shown by the shared composer. */
data class ComposerAttachment(
    val id: String,
    val name: String,
    val size: Long,
    val state: AttachmentUploadState,
    val progress: Float,
    val error: String?,
    val isImage: Boolean = false,
    val thumbnail: ImageBitmap? = null,
)

@Composable
fun AutoDeliveryBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AUTO · tus mensajes se envían como tecleados", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Immutable
data class ChatComposerState(
    val agent: AgentSummary?,
    val working: Boolean,
    val text: String,
    val attachments: List<ComposerAttachment>,
    val canSend: Boolean,
    val sending: Boolean,
    val choice: DeliveryChoice,
    val workingDelivery: String,
    val isVoiceAvailable: Boolean,
    val isListening: Boolean,
    val voiceListeningText: String,
    val voiceInputDescription: String,
    val showShortcuts: Boolean = false,
)

@Immutable
data class ChatComposerActions(
    val onTextChange: (String) -> Unit,
    val onChoice: (DeliveryChoice) -> Unit,
    val onWorkingDelivery: (String) -> Unit,
    val onPickDocuments: () -> Unit,
    val onPickImages: () -> Unit,
    val onCameraCapture: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onRetryAttachment: (String) -> Unit,
    val onSend: () -> Unit,
    val onVoiceToggle: () -> Unit,
    val onVoiceStop: () -> Unit,
    val onAbort: () -> Unit = {},
    val onDismissShortcuts: () -> Unit = {},
    val onPreviewAttachment: (String) -> Unit = {},
)

@Composable
fun ChatComposer(
    agent: AgentSummary?,
    working: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<ComposerAttachment>,
    canSend: Boolean,
    sending: Boolean,
    choice: DeliveryChoice,
    onChoice: (DeliveryChoice) -> Unit,
    workingDelivery: String,
    onWorkingDelivery: (String) -> Unit,
    onPickDocuments: () -> Unit,
    onPickImages: () -> Unit,
    onCameraCapture: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onSend: () -> Unit,
    isVoiceAvailable: Boolean,
    isListening: Boolean,
    onVoiceToggle: () -> Unit,
    onVoiceStop: () -> Unit,
    voiceListeningText: String,
    voiceInputDescription: String,
    onAbort: () -> Unit = {},
    showShortcuts: Boolean = false,
    onDismissShortcuts: () -> Unit = {},
) {
    ChatComposer(
        state =
            ChatComposerState(
                agent = agent,
                working = working,
                text = text,
                attachments = attachments,
                canSend = canSend,
                sending = sending,
                choice = choice,
                workingDelivery = workingDelivery,
                isVoiceAvailable = isVoiceAvailable,
                isListening = isListening,
                voiceListeningText = voiceListeningText,
                voiceInputDescription = voiceInputDescription,
                showShortcuts = showShortcuts,
            ),
        actions =
            ChatComposerActions(
                onTextChange = onTextChange,
                onChoice = onChoice,
                onWorkingDelivery = onWorkingDelivery,
                onPickDocuments = onPickDocuments,
                onPickImages = onPickImages,
                onCameraCapture = onCameraCapture,
                onRemoveAttachment = onRemoveAttachment,
                onRetryAttachment = onRetryAttachment,
                onSend = onSend,
                onVoiceToggle = onVoiceToggle,
                onVoiceStop = onVoiceStop,
                onAbort = onAbort,
                onDismissShortcuts = onDismissShortcuts,
            ),
    )
}

@Composable
fun ChatComposer(
    state: ChatComposerState,
    actions: ChatComposerActions,
) {
    val agent = state.agent
    val working = state.working
    val text = state.text
    val attachments = state.attachments
    val canSend = state.canSend
    val sending = state.sending
    val choice = state.choice
    val workingDelivery = state.workingDelivery
    val isVoiceAvailable = state.isVoiceAvailable
    val isListening = state.isListening
    val voiceListeningText = state.voiceListeningText
    val voiceInputDescription = state.voiceInputDescription
    val showShortcuts = state.showShortcuts
    val onTextChange = actions.onTextChange
    val onChoice = actions.onChoice
    val onWorkingDelivery = actions.onWorkingDelivery
    val onPickDocuments = actions.onPickDocuments
    val onPickImages = actions.onPickImages
    val onCameraCapture = actions.onCameraCapture
    val onRemoveAttachment = actions.onRemoveAttachment
    val onRetryAttachment = actions.onRetryAttachment
    val onPreviewAttachment = actions.onPreviewAttachment
    val onSend = actions.onSend
    val onVoiceToggle = actions.onVoiceToggle
    val onVoiceStop = actions.onVoiceStop
    val onAbort = actions.onAbort
    val onDismissShortcuts = actions.onDismissShortcuts

    var attachMenu by remember { mutableStateOf(false) }
    var choiceMenu by remember { mutableStateOf(false) }

    val canSendNow = canSend && (text.isNotBlank() || attachments.isNotEmpty())

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (attachments.isNotEmpty()) {
                val attachmentsScroll = rememberScrollState()
                Row(
                    Modifier
                        .horizontalEdgeFade(attachmentsScroll, edgeColor = MaterialTheme.colorScheme.surface)
                        .horizontalScroll(attachmentsScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEach { attachment ->
                        AttachmentChip(
                            name = attachment.name,
                            size = attachment.size,
                            state = attachment.state,
                            progress = attachment.progress,
                            error = attachment.error,
                            thumbnail = attachment.thumbnail,
                            onPreview = if (attachment.isImage) ({ onPreviewAttachment(attachment.id) }) else null,
                            onRemove = { onRemoveAttachment(attachment.id) },
                            onRetry = { onRetryAttachment(attachment.id) },
                        )
                    }
                }
            }
            val isSlashText = text.trimStart().startsWith("/")
            val catalog =
                remember(agent?.launcher, agent?.model?.id, agent?.model?.provider) {
                    SlashCommandCatalogs.resolve(agent)
                }
            val suggestions: List<Pair<String, String>> =
                remember(text, catalog) {
                    val trimmed = text.trimStart()
                    val lowerTrimmed = trimmed.lowercase()
                    if (!lowerTrimmed.startsWith("/")) {
                        emptyList()
                    } else {
                        val subEntry =
                            catalog.subSuggestions.entries.firstOrNull { (cmd, _) ->
                                lowerTrimmed == cmd || lowerTrimmed.startsWith("$cmd ")
                            }
                        if (subEntry != null) {
                            val (cmd, levels) = subEntry
                            val arg = trimmed.substring(cmd.length).trimStart()
                            val matchingLevels =
                                if (arg.isEmpty()) {
                                    levels
                                } else {
                                    levels.filter { it.startsWith(arg.lowercase()) && it != arg.lowercase() }
                                }
                            matchingLevels.map { level -> level to "$cmd $level" }
                        } else if (!lowerTrimmed.contains(" ")) {
                            catalog.commands
                                .filter { it.startsWith(lowerTrimmed) && it != lowerTrimmed }
                                .map { cmd ->
                                    val desc = catalog.descriptions[cmd]
                                    val label = if (desc != null) "$cmd · $desc" else cmd
                                    val replacement = if (cmd == "/term") cmd else "$cmd "
                                    label to replacement
                                }
                        } else {
                            emptyList()
                        }
                    }
                }

            if (suggestions.isNotEmpty()) {
                val suggestionsScroll = rememberScrollState()
                Row(
                    Modifier
                        .horizontalEdgeFade(suggestionsScroll, edgeColor = MaterialTheme.colorScheme.surface)
                        .horizontalScroll(suggestionsScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    suggestions.forEach { (label, replacement) ->
                        SuggestionChip(
                            onClick = { onTextChange(replacement) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
            }
            val isHerdrAgent = agent?.herdr != null || (agent != null && agent.launcher != "pi")
            val showQuickShortcuts = showShortcuts && !isSlashText && attachments.isEmpty()
            if (!isHerdrAgent || isSlashText || showQuickShortcuts) {
                val composerScrollState = rememberScrollState()
                Row(
                    Modifier
                        .horizontalEdgeFade(composerScrollState, edgeColor = MaterialTheme.colorScheme.surface)
                        .horizontalScroll(composerScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isHerdrAgent) {
                        Column {
                            AssistChip(
                                onClick = { choiceMenu = true },
                                shape = RoundedCornerShape(999.dp),
                                colors =
                                    if (isSlashText) {
                                        AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    } else {
                                        AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        )
                                    },
                                label = {
                                    Text(
                                        text = if (isSlashText) "⚡ Comando ${catalog.launcherLabel} (/)" else "Entrega: ${choice.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                            DropdownMenu(choiceMenu, { choiceMenu = false }) {
                                DeliveryChoice.entries.forEach { deliveryChoice ->
                                    val enabled = deliveryChoice != DeliveryChoice.COMMAND || text.trimStart().startsWith("/")
                                    DropdownMenuItem(
                                        text = { Text(deliveryChoice.label) },
                                        enabled = enabled,
                                        onClick = {
                                            choiceMenu = false
                                            onChoice(deliveryChoice)
                                        },
                                    )
                                }
                            }
                        }
                        if (working) {
                            FilterChip(
                                selected = workingDelivery == Delivery.FOLLOW_UP,
                                onClick = { onWorkingDelivery(Delivery.FOLLOW_UP) },
                                shape = RoundedCornerShape(999.dp),
                                label = { Text("Encolar (follow‑up)", style = MaterialTheme.typography.labelSmall) },
                            )
                            FilterChip(
                                selected = workingDelivery == Delivery.STEER,
                                onClick = { onWorkingDelivery(Delivery.STEER) },
                                shape = RoundedCornerShape(999.dp),
                                label = { Text("Interrumpir y dirigir (steer)", style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    } else if (isSlashText) {
                        AssistChip(
                            onClick = {},
                            shape = RoundedCornerShape(999.dp),
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            label = {
                                Text(
                                    text = "⚡ Comando ${catalog.launcherLabel} (/)",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                    if (showQuickShortcuts) {
                        catalog.quickShortcuts.forEach { cmd ->
                            val desc = catalog.descriptions[cmd]
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable {
                                            onDismissShortcuts()
                                            val replacement = if (cmd == "/term") cmd else "$cmd "
                                            onTextChange(replacement)
                                        },
                            ) {
                                Text(
                                    text = if (desc != null) "$cmd · $desc" else cmd,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.clip(CircleShape).clickable { onDismissShortcuts() },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Ocultar comandos rápidos",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(5.dp).size(14.dp),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        IconButton(onClick = { attachMenu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.AttachFile, "Adjuntar", modifier = Modifier.size(19.dp))
                        }
                    }
                    DropdownMenu(attachMenu, { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Archivo") }, onClick = {
                            attachMenu = false
                            onPickDocuments()
                        })
                        DropdownMenuItem(text = { Text("Foto de la galería") }, onClick = {
                            attachMenu = false
                            onPickImages()
                        })
                        DropdownMenuItem(text = { Text("Cámara") }, onClick = {
                            attachMenu = false
                            onCameraCapture()
                        })
                    }
                }
                val haptic = LocalHapticFeedback.current
                val hasDraft = text.isNotBlank() || attachments.isNotEmpty()
                val isAbortMode = working && !hasDraft && !sending && !isListening
                val isVoiceMode = !working && !hasDraft && !sending && isVoiceAvailable
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    placeholder = {
                        Text(
                            if (isListening) {
                                voiceListeningText
                            } else {
                                "Escribe a tu agente…"
                            },
                        )
                    },
                    trailingIcon =
                        if (isAbortMode && isVoiceAvailable) {
                            {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onVoiceToggle()
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = voiceInputDescription,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    maxLines = 6,
                )
                Surface(
                    shape = CircleShape,
                    color =
                        when {
                            isListening || isAbortMode -> MaterialTheme.colorScheme.errorContainer
                            canSendNow -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        },
                    contentColor =
                        when {
                            isListening || isAbortMode -> MaterialTheme.colorScheme.onErrorContainer
                            canSendNow -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.outline
                        },
                    border =
                        if (!canSendNow && !isListening && !isAbortMode) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        } else {
                            null
                        },
                ) {
                    when {
                        isListening -> {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onVoiceStop()
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Filled.MicOff, contentDescription = voiceInputDescription, modifier = Modifier.size(20.dp))
                            }
                        }
                        hasDraft || sending -> {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSend()
                                },
                                enabled = canSendNow && !sending,
                                modifier = Modifier.size(44.dp),
                            ) {
                                if (sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        isAbortMode -> {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAbort()
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Interrumpir", modifier = Modifier.size(20.dp))
                            }
                        }
                        isVoiceMode -> {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onVoiceToggle()
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = voiceInputDescription, modifier = Modifier.size(20.dp))
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
