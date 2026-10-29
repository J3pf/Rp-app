package dev.j3pf.rp.ui.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.R
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.CloseOutcome
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.ui.agents.AgentCloseConfirmationDialog
import dev.j3pf.rp.ui.components.ConnectionBanner
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.horizontalEdgeFade
import dev.j3pf.rp.ui.components.rememberActivity
import dev.j3pf.rp.ui.promptsheet.AnswerUi
import dev.j3pf.rp.ui.promptsheet.PromptSheetViewModel
import dev.j3pf.rp.util.AuthResult
import dev.j3pf.rp.util.agentTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    ui: ChatUiState,
    modifier: Modifier = Modifier,
    text: String = "",
    onText: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onSend: () -> Unit = {},
    onChoice: (DeliveryChoice) -> Unit = {},
    onWorkingDelivery: (String) -> Unit = {},
    onPicked: (List<Uri>) -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    newCameraTarget: () -> Uri = { Uri.EMPTY },
    onAbort: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onAnswerPrompt: (Prompt, Answer) -> Unit = { _, _ -> },
    onToggleMute: () -> Unit = {},
    onFontScaleChange: (Float) -> Unit = {},
    onMessageSpacingChange: (String) -> Unit = {},
    onQuit: () -> Unit = {},
    onForget: () -> Unit = {},
    onConfirmForget: () -> Unit = {},
    onCancelForget: () -> Unit = {},
    onSwitchToNormal: () -> Unit = {},
    onRetryUserMessage: (UserMsg) -> Unit = {},
    onOpenDiff: () -> Unit = {},
    busyPromptId: String? = null,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val agent = ui.agent
    val working = ui.sending || agent?.status == "working" || ui.chat.working
    var showInfo by remember { mutableStateOf(false) }
    var showQuitDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets =
                    androidx.compose.foundation.layout
                        .WindowInsets(0, 0, 0, 0),
                title = {
                    val rawTitle = agent?.let { agentTitle(it.sessionName, it.cwd, it.agentId) } ?: ui.chat.sessionName ?: "Agente"
                    val folder =
                        agent
                            ?.cwd
                            ?.let {
                                dev.j3pf.rp.util
                                    .lastSegment(it)
                            }.orEmpty()
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
                                val hasChanges = ui.diffResult?.files?.isNotEmpty() == true
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
                                val modelText = (ui.chat.model ?: agent.model?.label)?.takeIf { it.isNotBlank() }
                                val thinkingText = ui.chat.thinkingLevel?.takeIf { it.isNotBlank() }
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
                    if (ui.diffResult?.files?.isNotEmpty() == true) {
                        AssistChip(
                            onClick = onOpenDiff,
                            label = { Text("Diff (${ui.diffResult.files.size})", style = MaterialTheme.typography.labelSmall) },
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
                                    Text("Letra (${(ui.chatFontScale * 100).toInt()}%)")
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = { onFontScaleChange((ui.chatFontScale - 0.10f).coerceAtLeast(0.80f)) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("A-", fontWeight = FontWeight.Bold)
                                        }
                                        if (kotlin.math.abs(ui.chatFontScale - 1.0f) > 0.03f) {
                                            TextButton(
                                                onClick = { onFontScaleChange(1.0f) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text("100%", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        TextButton(
                                            onClick = { onFontScaleChange((ui.chatFontScale + 0.10f).coerceAtMost(1.45f)) },
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
                                            val selected = ui.chatMessageSpacing == id
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
                            showInfo = true
                        })
                        DropdownMenuItem(
                            text = { Text(if (ui.muted) "Activar notificaciones del agente" else "Silenciar notificaciones del agente") },
                            onClick = {
                                menu = false
                                onToggleMute()
                            },
                        )
                        if (agent != null && agent.online) {
                            DropdownMenuItem(
                                text = { Text("Cerrar agente (/quit)", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    showQuitDialog = true
                                },
                            )
                        }
                        if (agent != null && !agent.online) {
                            DropdownMenuItem(text = { Text("Olvidar agente") }, onClick = {
                                menu = false
                                onForget()
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.imePadding(),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ConnectionBanner(ui.conn)
            ui.chat.terminalWait?.let { w ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Esperando en la terminal (diálogo no remoto)" + (w.title?.let { ": $it" } ?: ""),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (ui.isAuto) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("AUTO · tus mensajes se envían como tecleados", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (ui.interactiveUnavailable) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("En Auto este mensaje no autoriza acciones", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onSwitchToNormal) { Text("Pasar a Normal") }
                    }
                }
            }
            if (ui.chat.todos.isNotEmpty()) {
                TodoBanner(todos = ui.chat.todos)
            }

            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                MessageList(
                    ui = ui,
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = busyPromptId,
                    onAnswerPrompt = onAnswerPrompt,
                    onRetryUserMessage = onRetryUserMessage,
                    listState = listState,
                )
            }

            Composer(
                ui = ui,
                working = working,
                text = text,
                onText = onText,
                onChoice = onChoice,
                onWorkingDelivery = onWorkingDelivery,
                onPicked = onPicked,
                onRemoveAttachment = onRemoveAttachment,
                onRetryAttachment = onRetryAttachment,
                newCameraTarget = newCameraTarget,
                onSend = onSend,
            )
        }
    }

    if (showInfo && agent != null) {
        InfoDialog(
            agent = agent,
            liveModel = ui.chat.model,
            liveThinking = ui.chat.thinkingLevel,
            onDismiss = { showInfo = false },
        )
    }
    if (ui.showForgetConfirmation) {
        AgentCloseConfirmationDialog(onConfirm = onConfirmForget, onDismiss = onCancelForget)
    }
    if (showQuitDialog) {
        AlertDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = hiltViewModel(),
    promptVm: PromptSheetViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val archivedNotice = stringResource(R.string.archived_fallback_notice)
    val promptStatus by promptVm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = rememberActivity()
    var text by rememberSaveable { mutableStateOf("") }
    var busyPrompt by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(promptStatus) {
        when (promptStatus) {
            AnswerUi.AlreadyResolved -> {
                snackbar.showSnackbar("Ya respondido en otro lugar")
                promptVm.reset()
                busyPrompt = null
            }

            AnswerUi.Done, is AnswerUi.Queued -> {
                promptVm.reset()
                busyPrompt = null
            }

            is AnswerUi.Error -> {
                snackbar.showSnackbar((promptStatus as AnswerUi.Error).message)
                promptVm.reset()
                busyPrompt = null
            }

            else -> {}
        }
    }

    ChatContent(
        ui = ui,
        text = text,
        onText = { text = it },
        onBack = onBack,
        onSend = {
            scope.launch {
                if (vm.needsAuth()) {
                    if (activity == null) return@launch
                    when (vm.gate.authenticate(activity, "Enviar como tecleado", "Este mensaje autoriza acciones en modo Auto")) {
                        AuthResult.OK -> {}

                        AuthResult.UNAVAILABLE -> {
                            snackbar.showSnackbar("Configura un bloqueo de pantalla para enviar como tecleado")
                            return@launch
                        }

                        AuthResult.FAILED -> {
                            snackbar.showSnackbar("Desbloqueo cancelado")
                            return@launch
                        }
                    }
                }
                vm.send(text) { text = "" }
            }
        },
        onChoice = vm::setChoice,
        onWorkingDelivery = vm::setWorkingDelivery,
        onPicked = vm::addAttachments,
        onRemoveAttachment = vm::removeAttachment,
        onRetryAttachment = vm::retryAttachment,
        newCameraTarget = vm::newCameraTarget,
        onAbort = vm::abort,
        onRefresh = vm::refresh,
        onAnswerPrompt = { p, answer ->
            scope.launch {
                if (promptVm.requiresAuth(p)) {
                    if (activity == null) {
                        snackbar.showSnackbar("No se pudo verificar tu identidad")
                        return@launch
                    }
                    val r = promptVm.gate.authenticate(activity, "Confirma tu identidad", p.title.take(80))
                    if (r != AuthResult.OK) {
                        snackbar.showSnackbar(
                            if (r ==
                                AuthResult.UNAVAILABLE
                            ) {
                                "Configura un bloqueo de pantalla para aprobar"
                            } else {
                                "Desbloqueo cancelado"
                            },
                        )
                        return@launch
                    }
                }
                busyPrompt = p.promptId
                promptVm.answer(p.promptId, answer)
            }
        },
        onToggleMute = vm::toggleMute,
        onFontScaleChange = vm::setChatFontScale,
        onMessageSpacingChange = vm::setChatMessageSpacing,
        onQuit = { vm.quit(onBack) },
        onForget = {
            vm.forget { outcome ->
                scope.launch {
                    if (outcome == CloseOutcome.Archived) snackbar.showSnackbar(archivedNotice)
                    onBack()
                }
            }
        },
        onConfirmForget = vm::confirmForget,
        onCancelForget = vm::cancelForget,
        onSwitchToNormal = vm::switchToNormal,
        onRetryUserMessage = vm::retryUserMessage,
        onOpenDiff = vm::openDiffSheet,
        busyPromptId = busyPrompt,
        snackbarHostState = snackbar,
    )

    if (ui.showDiffSheet) {
        DiffSheet(
            diff = ui.diffResult,
            selectedFile = ui.selectedDiffFile,
            isLoading = ui.loadingDiff,
            onSelectFile = vm::selectDiffFile,
            onDiscardFile = vm::discardDiffFile,
            onRequestAdjustment = { filePath ->
                val prefix = if (text.isBlank()) "Ajusta en $filePath: " else "$text\nAjusta en $filePath: "
                text = prefix
                vm.closeDiffSheet()
            },
            onDismiss = vm::closeDiffSheet,
        )
    }
}

@Composable
internal fun MessageList(
    ui: ChatUiState,
    modifier: Modifier,
    busyPromptId: String?,
    onAnswerPrompt: (dev.j3pf.rp.protocol.Prompt, dev.j3pf.rp.protocol.Answer) -> Unit,
    onRetryUserMessage: (UserMsg) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
) {
    dev.j3pf.rp.ui.chat.MessageList(
        displayItems = ui.chat.display,
        tools = ui.chat.tools,
        sending = ui.sending,
        isWorking =
            (ui.sending || ui.agent?.status == "working" || ui.chat.working) &&
                (
                    ui.chat.streaming == null ||
                        ui.chat.streaming!!
                            .blocks
                            .isEmpty()
                ),
        syncingHistory = ui.syncingHistory,
        refreshing = ui.refreshing,
        conn = ui.conn,
        chatFontScale = ui.chatFontScale,
        chatMessageSpacing = ui.chatMessageSpacing,
        unreadCount = ui.unreadCount,
        busyPromptId = busyPromptId,
        onAnswerPrompt = onAnswerPrompt,
        onRetryUserMessage = onRetryUserMessage,
        listState = listState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Composer(
    ui: ChatUiState,
    working: Boolean,
    text: String,
    onText: (String) -> Unit,
    onChoice: (DeliveryChoice) -> Unit,
    onWorkingDelivery: (String) -> Unit,
    onPicked: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit = {},
    newCameraTarget: () -> Uri,
    onSend: () -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    var choiceMenu by remember { mutableStateOf(false) }
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onPicked(it) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { onPicked(it) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) cameraTarget?.let { onPicked(listOf(it)) }
        }
    // La app declara CAMERA (escáner QR): sin el permiso concedido, ACTION_IMAGE_CAPTURE lanza SecurityException.
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val target = newCameraTarget().also { cameraTarget = it }
                takePicture.launch(target)
            }
        }

    var baseVoiceText by remember { mutableStateOf("") }
    val voiceInput =
        rememberVoiceInputState(
            onPartialResult = { partial ->
                val prefix = if (baseVoiceText.isNotEmpty() && !baseVoiceText.endsWith(" ")) "$baseVoiceText " else baseVoiceText
                onText(prefix + partial)
            },
            onFinalResult = { final ->
                val prefix = if (baseVoiceText.isNotEmpty() && !baseVoiceText.endsWith(" ")) "$baseVoiceText " else baseVoiceText
                onText(prefix + final)
            },
        )
    val recordAudioPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                baseVoiceText = text
                voiceInput.startListening()
            }
        }

    val canSendNow = ui.canSend && (text.isNotBlank() || ui.attachments.isNotEmpty())

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
            if (ui.attachments.isNotEmpty()) {
                val attachmentsScroll = rememberScrollState()
                Row(
                    Modifier
                        .horizontalEdgeFade(attachmentsScroll, edgeColor = MaterialTheme.colorScheme.surface)
                        .horizontalScroll(attachmentsScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.attachments.forEach { a ->
                        AttachmentChip(a, onRemove = { onRemoveAttachment(a.id) }, onRetry = { onRetryAttachment(a.id) })
                    }
                }
            }
            val isSlashText = text.trimStart().startsWith("/")
            val catalog =
                remember(ui.agent?.launcher, ui.agent?.model?.id, ui.agent?.model?.provider) {
                    SlashCommandCatalogs.resolve(ui.agent)
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
                                .map { cmd -> cmd to "$cmd " }
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
                            onClick = { onText(replacement) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
            }
            val composerScrollState = rememberScrollState()
            Row(
                Modifier
                    .horizontalEdgeFade(composerScrollState, edgeColor = MaterialTheme.colorScheme.surface)
                    .horizontalScroll(composerScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isHerdrAgent = ui.agent?.herdr != null || (ui.agent != null && ui.agent.launcher != "pi")
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
                                    text = if (isSlashText) "⚡ Comando ${catalog.launcherLabel} (/)" else "Entrega: ${ui.choice.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        DropdownMenu(choiceMenu, { choiceMenu = false }) {
                            DeliveryChoice.entries.forEach { c ->
                                val enabled = c != DeliveryChoice.COMMAND || text.trimStart().startsWith("/")
                                DropdownMenuItem(
                                    text = { Text(c.label) },
                                    enabled = enabled,
                                    onClick = {
                                        choiceMenu = false
                                        onChoice(c)
                                    },
                                )
                            }
                        }
                    }
                    if (working) {
                        FilterChip(
                            selected = ui.workingDelivery == Delivery.FOLLOW_UP,
                            onClick = { onWorkingDelivery(Delivery.FOLLOW_UP) },
                            shape = RoundedCornerShape(999.dp),
                            label = { Text("Encolar (follow‑up)", style = MaterialTheme.typography.labelSmall) },
                        )
                        FilterChip(
                            selected = ui.workingDelivery == Delivery.STEER,
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
                if (text.isEmpty() && ui.attachments.isEmpty()) {
                    catalog.quickShortcuts.forEach { cmd ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onText("$cmd ") },
                        ) {
                            Text(
                                text = cmd,
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
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        IconButton(onClick = { attachMenu = true }, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Filled.AttachFile, "Adjuntar", modifier = Modifier.size(20.dp))
                        }
                    }
                    DropdownMenu(attachMenu, { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Archivo") }, onClick = {
                            attachMenu = false
                            files.launch(arrayOf("*/*"))
                        })
                        DropdownMenuItem(text = { Text("Foto de la galería") }, onClick = {
                            attachMenu = false
                            photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text("Cámara") }, onClick = {
                            attachMenu = false
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    placeholder = {
                        Text(
                            if (voiceInput.isListening) {
                                stringResource(R.string.voice_listening)
                            } else {
                                "Escribe a tu agente…"
                            },
                        )
                    },
                    maxLines = 6,
                )
                val isVoiceMode = !canSendNow && !ui.sending && voiceInput.isAvailable
                Surface(
                    shape = CircleShape,
                    color =
                        when {
                            voiceInput.isListening -> MaterialTheme.colorScheme.errorContainer
                            canSendNow -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        },
                    contentColor =
                        when {
                            voiceInput.isListening -> MaterialTheme.colorScheme.onErrorContainer
                            canSendNow -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.outline
                        },
                    border =
                        if (!canSendNow && !voiceInput.isListening) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        } else {
                            null
                        },
                ) {
                    val haptic = LocalHapticFeedback.current
                    if (isVoiceMode || voiceInput.isListening) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (voiceInput.isListening) {
                                    voiceInput.stopListening()
                                } else {
                                    baseVoiceText = text
                                    recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                if (voiceInput.isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.action_voice_input),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSend()
                            },
                            enabled = canSendNow && !ui.sending,
                            modifier = Modifier.size(44.dp),
                        ) {
                            if (ui.sending) {
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
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    a: AttachmentUi,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val state =
        when (a.state) {
            UploadState.PREPARING -> AttachmentUploadState.PREPARING
            UploadState.READY -> AttachmentUploadState.READY
            UploadState.UPLOADING -> AttachmentUploadState.UPLOADING
            UploadState.DONE -> AttachmentUploadState.DONE
            UploadState.ERROR -> AttachmentUploadState.ERROR
        }
    dev.j3pf.rp.ui.chat.AttachmentChip(
        name = a.name,
        size = a.size,
        state = state,
        progress = a.progress,
        error = a.error,
        onRemove = onRemove,
        onRetry = onRetry,
    )
}
