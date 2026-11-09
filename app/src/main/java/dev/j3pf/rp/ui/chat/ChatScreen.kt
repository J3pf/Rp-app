package dev.j3pf.rp.ui.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.j3pf.rp.R
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.ports.CloseOutcome
import dev.j3pf.rp.data.ports.VoiceInputHost
import dev.j3pf.rp.data.ports.VoiceListener
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.platform.AndroidVoiceInputHost
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.ui.agents.AgentCloseConfirmationDialog
import dev.j3pf.rp.ui.components.ConnectionBanner
import dev.j3pf.rp.ui.components.rememberActivity
import dev.j3pf.rp.ui.promptsheet.AnswerUi
import dev.j3pf.rp.ui.promptsheet.PromptSheetViewModel
import kotlinx.coroutines.launch
import dev.j3pf.rp.data.ports.BiometricAuthResult as AuthResult

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
    onSendTerminalCommand: ((String) -> Unit)? = null,
    onSwitchAgent: (String) -> Unit = {},
    busyPromptId: String? = null,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    voiceInputHost: VoiceInputHost? = null,
    isVoiceInputAvailable: Boolean = false,
) {
    val agent = ui.agent
    val working = ui.sending || agent?.status == "working" || ui.chat.working
    var showInfo by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hideToolCalls by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatTopBar(
                agent = agent,
                sessionName = ui.chat.sessionName,
                model = ui.chat.model,
                thinkingLevel = ui.chat.thinkingLevel,
                working = working,
                diffFileCount = ui.diffResult?.files?.size ?: 0,
                chatFontScale = ui.chatFontScale,
                chatMessageSpacing = ui.chatMessageSpacing,
                muted = ui.muted,
                readOnly = ui.readOnly,
                onBack = onBack,
                onAbort = onAbort,
                onOpenDiff = onOpenDiff,
                onFontScaleChange = onFontScaleChange,
                onMessageSpacingChange = onMessageSpacingChange,
                onInfo = { showInfo = true },
                onToggleMute = onToggleMute,
                onQuit = onQuit,
                onForget = onForget,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                hideToolCalls = hideToolCalls,
                onToggleHideToolCalls = { hideToolCalls = !hideToolCalls },
                onSendTerminalCommand = onSendTerminalCommand,
                otherActiveAgents = ui.otherActiveAgents,
                onSwitchAgent = onSwitchAgent,
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
                AutoDeliveryBanner()
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
                    searchQuery = searchQuery,
                    hideToolCalls = hideToolCalls,
                    onAnswerPrompt = onAnswerPrompt,
                    onRetryUserMessage = onRetryUserMessage,
                    listState = listState,
                )
            }

            if (ui.readOnly) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.archived_read_only),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
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
                    voiceInputHost = voiceInputHost,
                    isVoiceInputAvailable = isVoiceInputAvailable,
                )
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onSwitchAgent: (String) -> Unit = {},
    readOnly: Boolean = false,
    vm: ChatViewModel = hiltViewModel(),
    promptVm: PromptSheetViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val archivedNotice = stringResource(R.string.archived_fallback_notice)
    val promptStatus by promptVm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = rememberActivity()
    val applicationContext = LocalContext.current.applicationContext
    val voiceInputHost =
        remember(applicationContext) {
            EntryPointAccessors
                .fromApplication(applicationContext, ChatVoiceInputEntryPoint::class.java)
                .voiceInputHost()
        }
    var text by rememberSaveable(vm.agentId) { mutableStateOf(vm.getDraft()) }
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
        ui = ui.copy(readOnly = readOnly),
        text = text,
        onText = {
            text = it
            vm.setDraft(it)
        },
        onBack = onBack,
        onSend = {
            scope.launch {
                if (vm.needsAuth()) {
                    if (activity == null) return@launch
                    when (vm.gate.authenticate("Enviar como tecleado", "Este mensaje autoriza acciones en modo Auto")) {
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
                vm.send(text) {
                    text = ""
                    vm.setDraft("")
                }
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
                    val r = promptVm.gate.authenticate("Confirma tu identidad", p.title.take(80))
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
        onSendTerminalCommand = vm::sendTerminalCommand,
        onSwitchAgent = onSwitchAgent,
        busyPromptId = busyPrompt,
        snackbarHostState = snackbar,
        voiceInputHost = voiceInputHost,
        isVoiceInputAvailable = voiceInputHost.isAvailable,
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
                vm.setDraft(prefix)
                vm.closeDiffSheet()
            },
            onDismiss = vm::closeDiffSheet,
            onQuickPrompt = { quickText ->
                text = quickText
                vm.setDraft(quickText)
                vm.closeDiffSheet()
            },
        )
    }
}

@Composable
internal fun MessageList(
    ui: ChatUiState,
    modifier: Modifier,
    busyPromptId: String?,
    searchQuery: String = "",
    hideToolCalls: Boolean = false,
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
        searchQuery = searchQuery,
        hideToolCalls = hideToolCalls,
        onAnswerPrompt = onAnswerPrompt,
        onRetryUserMessage = onRetryUserMessage,
        listState = listState,
        modifier = modifier,
    )
}

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
    voiceInputHost: VoiceInputHost? = null,
    isVoiceInputAvailable: Boolean = false,
) {
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
        rememberVoiceInputHostState(
            hostKey = voiceInputHost,
            isAvailable = isVoiceInputAvailable,
            listeningFlow = voiceInputHost?.listening,
            start = { onPartial, onFinal, onError ->
                voiceInputHost?.start(
                    object : VoiceListener {
                        override fun onPartial(text: String) = onPartial(text)

                        override fun onFinal(text: String) = onFinal(text)

                        override fun onError(code: Int) = onError()
                    },
                )
            },
            stop = { voiceInputHost?.stop() },
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

    ChatComposer(
        agent = ui.agent,
        working = working,
        text = text,
        onTextChange = onText,
        attachments =
            ui.attachments.map { attachment ->
                ComposerAttachment(
                    id = attachment.id,
                    name = attachment.name,
                    size = attachment.size,
                    state =
                        when (attachment.state) {
                            UploadState.PREPARING -> AttachmentUploadState.PREPARING
                            UploadState.READY -> AttachmentUploadState.READY
                            UploadState.UPLOADING -> AttachmentUploadState.UPLOADING
                            UploadState.DONE -> AttachmentUploadState.DONE
                            UploadState.ERROR -> AttachmentUploadState.ERROR
                        },
                    progress = attachment.progress,
                    error = attachment.error,
                )
            },
        canSend = ui.canSend,
        sending = ui.sending,
        choice = ui.choice,
        onChoice = onChoice,
        workingDelivery = ui.workingDelivery,
        onWorkingDelivery = onWorkingDelivery,
        onPickDocuments = { files.launch(arrayOf("*/*")) },
        onPickImages = {
            photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onCameraCapture = { cameraPermission.launch(Manifest.permission.CAMERA) },
        onRemoveAttachment = onRemoveAttachment,
        onRetryAttachment = onRetryAttachment,
        onSend = onSend,
        isVoiceAvailable = isVoiceInputAvailable,
        isListening = voiceInput.isListening,
        onVoiceToggle = {
            baseVoiceText = text
            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onVoiceStop = voiceInput::stopListening,
        voiceListeningText = stringResource(R.string.voice_listening),
        voiceInputDescription = stringResource(R.string.action_voice_input),
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChatVoiceInputEntryPoint {
    fun voiceInputHost(): AndroidVoiceInputHost
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
