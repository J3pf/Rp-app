package dev.j3pf.rp.ui.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.ui.components.ConnectionBanner
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.components.rememberActivity
import dev.j3pf.rp.ui.promptsheet.AnswerUi
import dev.j3pf.rp.ui.promptsheet.PromptSheetViewModel
import dev.j3pf.rp.util.AuthResult
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.humanSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = hiltViewModel(),
    promptVm: PromptSheetViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val promptStatus by promptVm.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = rememberActivity()
    var text by rememberSaveable { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }
    var showQuitDialog by remember { mutableStateOf(false) }
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

            else -> {
                Unit
            }
        }
    }

    val agent = ui.agent
    val working = agent?.status == "working" || ui.chat.working

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            agent?.let { agentTitle(it.sessionName, it.cwd, it.agentId) } ?: ui.chat.sessionName ?: "Agente",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (agent != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(agent)
                                (ui.chat.model ?: agent.model?.label)?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if (working) {
                        IconButton(onClick = vm::abort) { Icon(Icons.Filled.Stop, "Interrumpir", tint = MaterialTheme.colorScheme.error) }
                    }
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Información") }, onClick = {
                            menu = false
                            showInfo = true
                        })
                        DropdownMenuItem(
                            text = { Text(if (ui.muted) "Activar notificaciones del agente" else "Silenciar notificaciones del agente") },
                            onClick = {
                                menu = false
                                vm.toggleMute()
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
                                vm.forget(onBack)
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.imePadding(),
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
                        TextButton(onClick = vm::switchToNormal) { Text("Pasar a Normal") }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.weight(1f),
            ) {
                MessageList(
                    ui = ui,
                    modifier = Modifier.fillMaxSize(),
                    busyPromptId = busyPrompt,
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
                )
            }

            Composer(
                ui = ui,
                working = working,
                text = text,
                onText = { text = it },
                onChoice = vm::setChoice,
                onWorkingDelivery = vm::setWorkingDelivery,
                onPicked = vm::addAttachments,
                onRemoveAttachment = vm::removeAttachment,
                onRetryAttachment = vm::retryAttachment,
                newCameraTarget = vm::newCameraTarget,
                onSend = {
                    scope.launch {
                        if (vm.needsAuth()) {
                            if (activity == null) return@launch
                            when (vm.gate.authenticate(activity, "Enviar como tecleado", "Este mensaje autoriza acciones en modo Auto")) {
                                AuthResult.OK -> {
                                    Unit
                                }

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
            )
        }
    }

    if (showInfo && agent != null) InfoDialog(agent) { showInfo = false }
    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("Cerrar sesión de Pi") },
            text = { Text("¿Deseas enviar /quit al agente para cerrar su proceso en la terminal de la PC?") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitDialog = false
                    vm.quit(onBack)
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

@Composable
private fun MessageList(
    ui: ChatUiState,
    modifier: Modifier,
    busyPromptId: String?,
    onAnswerPrompt: (dev.j3pf.rp.protocol.Prompt, dev.j3pf.rp.protocol.Answer) -> Unit,
) {
    val listState = rememberLazyListState()
    val items = ui.chat.display
    val streamLen =
        ui.chat.streaming?.blocks?.sumOf { b ->
            when (b) {
                is Block.Text -> b.text.length
                is Block.Thinking -> b.text.length
                is Block.ToolCall -> b.rawArgs.length
            }
        } ?: 0
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 2
        }
    }
    var first by remember { mutableStateOf(true) }
    var prevItemsSize by remember { mutableIntStateOf(0) }
    LaunchedEffect(items.size, streamLen, ui.chat.tools.size) {
        if (items.isEmpty()) {
            prevItemsSize = 0
            return@LaunchedEffect
        }
        val sizeChanged = items.size != prevItemsSize
        prevItemsSize = items.size
        if (first) {
            listState.scrollToItem(items.lastIndex)
            first = false
        } else if (nearBottom) {
            if (sizeChanged) {
                listState.animateScrollToItem(items.lastIndex)
            } else {
                listState.scrollToItem(items.lastIndex)
            }
        }
    }
    if (items.isEmpty()) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sin mensajes todavía", style = MaterialTheme.typography.titleMedium)
            Text("Escribe abajo para hablar con este agente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }, contentType = { it::class.simpleName }) { item ->
            ChatItemView(item, ui.chat.tools, busyPromptId, onAnswerPrompt)
        }
    }
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

    Surface(
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ui.attachments.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ui.attachments.forEach { a ->
                        AttachmentChip(a, onRemove = { onRemoveAttachment(a.id) }, onRetry = { onRetryAttachment(a.id) })
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    AssistChip(
                        onClick = { choiceMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Entrega: ${ui.choice.label}") },
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
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Encolar (follow‑up)") },
                    )
                    FilterChip(
                        selected = ui.workingDelivery == Delivery.STEER,
                        onClick = { onWorkingDelivery(Delivery.STEER) },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Interrumpir y dirigir (steer)") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column {
                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Filled.AttachFile, "Adjuntar") }
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
                    shape = RoundedCornerShape(20.dp),
                    placeholder = { Text("Escribe a tu agente…") },
                    maxLines = 6,
                )
                IconButton(
                    onClick = onSend,
                    enabled = ui.canSend && (text.isNotBlank() || ui.attachments.isNotEmpty()),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
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
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.name + if (a.size > 0) " · ${humanSize(a.size)}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) { Icon(Icons.Filled.Close, "Quitar", Modifier.size(14.dp)) }
            }
            when (a.state) {
                UploadState.UPLOADING -> {
                    LinearProgressIndicator(progress = { a.progress }, modifier = Modifier.fillMaxWidth())
                }

                UploadState.PREPARING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                UploadState.ERROR -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.error ?: "Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                }

                else -> {
                    Unit
                }
            }
        }
    }
}

@Composable
private fun InfoDialog(
    a: AgentSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Información del agente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "PC" to a.machineName,
                    "Carpeta" to a.cwd,
                    "Rama" to a.gitBranch,
                    "Modelo" to a.model?.label,
                    "Lanzador" to a.launcher,
                    "herdr" to a.herdr?.let { listOfNotNull(it.workspaceId, it.tabId, it.paneId).joinToString(" / ") },
                    "Modo gentle" to a.gentleMode,
                    "Id" to a.agentId,
                ).forEach { (k, v) -> if (!v.isNullOrBlank()) Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                if (a.redacted) TagChip("Contenido oculto por la PC")
            }
        },
    )
}
