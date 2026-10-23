package dev.j3pf.rp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatItem
import dev.j3pf.rp.chat.SubagentCardItem
import dev.j3pf.rp.chat.ToolRun
import dev.j3pf.rp.chat.ToolStatus
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.TodoItem
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.util.humanSize
import kotlinx.coroutines.launch

@Composable
fun AttachmentChip(
    name: String,
    size: Long,
    state: AttachmentUploadState,
    progress: Float,
    error: String?,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name + if (size > 0) " · ${humanSize(size)}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar", modifier = Modifier.size(14.dp))
                }
            }
            when (state) {
                AttachmentUploadState.UPLOADING -> {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }

                AttachmentUploadState.PREPARING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                AttachmentUploadState.ERROR -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(error ?: "Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
fun InfoDialog(
    agent: AgentSummary,
    liveModel: String? = null,
    liveThinking: String? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Información del agente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "PC" to agent.machineName,
                    "Carpeta" to agent.cwd,
                    "Rama" to agent.gitBranch,
                    "Modelo" to (liveModel ?: agent.model?.label),
                    "Razonamiento" to liveThinking,
                    "Lanzador" to agent.launcher,
                    "herdr" to agent.herdr?.let { listOfNotNull(it.workspaceId, it.tabId, it.paneId).joinToString(" / ") },
                    "Modo gentle" to agent.gentleMode,
                    "Id" to agent.agentId,
                ).forEach { (k, v) ->
                    if (!v.isNullOrBlank()) Text("$k: $v", style = MaterialTheme.typography.bodySmall)
                }
                if (agent.redacted) TagChip("Contenido oculto por la PC")
            }
        },
    )
}

@Composable
fun TodoBanner(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
) {
    val (expanded, setExpanded) = rememberSaveable { mutableStateOf(false) }
    val total = todos.size
    val done = todos.count { it.status == "done" }
    val inProgress = todos.firstOrNull { it.status == "in_progress" }
    val percent = if (total > 0) (done * 100 / total) else 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Tareas: $done/$total ($percent%)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                inProgress?.let { curr ->
                    Text(
                        text = "· ${curr.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { setExpanded(!expanded) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(if (expanded) "Ocultar" else "Ver lista")
                }
            }
            AnimatedVisibility(expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    todos.forEach { task ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            when (task.status) {
                                "done" -> Text("✓", color = RpColors.success, fontWeight = FontWeight.Bold)
                                "in_progress" -> Text("▶", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                "blocked" -> Text("⏸", color = RpColors.warning)
                                "dropped" -> Text("✕", color = MaterialTheme.colorScheme.outline)
                                else -> Text("○", color = MaterialTheme.colorScheme.outline)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (task.status == "in_progress") FontWeight.Bold else FontWeight.Normal,
                                    textDecoration = if (task.status == "dropped") TextDecoration.LineThrough else null,
                                    color =
                                        if (task.status == "dropped") {
                                            MaterialTheme.colorScheme.outline
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                task.note?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageList(
    displayItems: List<ChatItem>,
    tools: Map<String, ToolRun> = emptyMap(),
    sending: Boolean = false,
    isWorking: Boolean = false,
    syncingHistory: Boolean = false,
    refreshing: Boolean = false,
    conn: ConnState = ConnState.Disconnected,
    chatFontScale: Float = 1.0f,
    chatMessageSpacing: String = "normal",
    unreadCount: Int = 0,
    busyPromptId: String? = null,
    onAnswerPrompt: (Prompt, Answer) -> Unit = { _, _ -> },
    onRetryUserMessage: (UserMsg) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val items =
        remember(displayItems) {
            val inlineToolCallIds =
                displayItems
                    .asSequence()
                    .filterIsInstance<AssistantMsg>()
                    .flatMap { it.blocks.asSequence() }
                    .filterIsInstance<Block.ToolCall>()
                    .mapNotNull { it.toolCallId }
                    .toSet()
            if (inlineToolCallIds.isEmpty()) {
                displayItems
            } else {
                displayItems.filterNot { it is SubagentCardItem && it.info.taskId in inlineToolCallIds }
            }
        }

    if (items.isEmpty()) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (syncingHistory || refreshing || conn is ConnState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                )
                Text("Sincronizando historial…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Descargando los mensajes recientes de la sesión.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("Sin mensajes todavía", style = MaterialTheme.typography.titleMedium)
                Text("Escribe abajo para hablar con este agente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val reversedItems = remember(items) { items.asReversed() }

    val isScrolledUp by remember {
        derivedStateOf {
            val maxBottomIndex = if (isWorking) 1 else 0
            listState.firstVisibleItemIndex > maxBottomIndex ||
                (listState.firstVisibleItemIndex == maxBottomIndex && listState.firstVisibleItemScrollOffset > 48)
        }
    }

    val itemsCountWhenScrolledUp = remember { mutableIntStateOf(items.size) }

    LaunchedEffect(isScrolledUp) {
        if (!isScrolledUp) {
            itemsCountWhenScrolledUp.intValue = items.size
        }
    }

    val unreadNewItems by remember(items.size, unreadCount) {
        derivedStateOf {
            if (isScrolledUp) {
                if (unreadCount > 0) {
                    unreadCount
                } else {
                    (items.size - itemsCountWhenScrolledUp.intValue).coerceAtLeast(0)
                }
            } else {
                0
            }
        }
    }

    val lastItem = items.lastOrNull()
    val lastItemKey = lastItem?.key
    val streamingTextLength = (lastItem as? AssistantMsg)?.plainText?.length ?: 0

    val prevItemsSize =
        remember {
            object {
                var value = items.size
            }
        }

    LaunchedEffect(lastItemKey, items.size, streamingTextLength) {
        val isNewItem = items.size != prevItemsSize.value || lastItemKey?.startsWith("local:") == true
        prevItemsSize.value = items.size

        if (lastItemKey != null && (!isScrolledUp || lastItemKey.startsWith("local:"))) {
            if (!listState.isScrollInProgress) {
                if (isNewItem) {
                    listState.animateScrollToItem(0)
                } else if (!isScrolledUp) {
                    listState.scrollToItem(0)
                }
            }
        }
    }

    val baseDensity = LocalDensity.current
    val scaledDensity =
        remember(baseDensity, chatFontScale) {
            Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * chatFontScale,
            )
        }

    val itemSpacing =
        when (chatMessageSpacing) {
            "compact" -> 2.dp
            "spacious" -> 10.dp
            else -> 4.dp
        }
    val verticalListPadding =
        when (chatMessageSpacing) {
            "compact" -> 3.dp
            "spacious" -> 10.dp
            else -> 6.dp
        }

    Box(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = verticalListPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.Bottom),
            ) {
                if (isWorking) {
                    item(key = "working-indicator", contentType = "WorkingIndicator") {
                        val indHPad =
                            when (chatMessageSpacing) {
                                "compact" -> 8.dp
                                "spacious" -> 14.dp
                                else -> 10.dp
                            }
                        val indVPad =
                            when (chatMessageSpacing) {
                                "compact" -> 4.dp
                                "spacious" -> 9.dp
                                else -> 6.dp
                            }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RpShapes.bubbleAgent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = indHPad, vertical = indVPad),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val reducedMotion = LocalReducedMotion.current
                                    if (reducedMotion) {
                                        CircularProgressIndicator(
                                            progress = { 0.75f },
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    val runningTool = tools.values.lastOrNull { it.status == ToolStatus.RUNNING }
                                    val statusText =
                                        when {
                                            sending -> "Enviando mensaje…"
                                            runningTool != null -> "Ejecutando ${runningTool.name}…"
                                            else -> "Pensando…"
                                        }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
                items(
                    reversedItems,
                    key = { it.key },
                    contentType = { it::class.simpleName },
                ) { item ->
                    val reducedMotion = LocalReducedMotion.current
                    val itemModifier =
                        if (reducedMotion) {
                            Modifier
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            )
                        }
                    Box(modifier = itemModifier) {
                        ChatItemView(
                            item = item,
                            tools = tools,
                            busyPromptId = busyPromptId,
                            onAnswerPrompt = onAnswerPrompt,
                            spacingMode = chatMessageSpacing,
                            onRetryUserMessage = onRetryUserMessage,
                        )
                    }
                }
            }
        }

        val reducedMotion = LocalReducedMotion.current
        val enterTransition =
            if (reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn() + slideInVertically { it / 2 }
            }
        val exitTransition =
            if (reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut() + slideOutVertically { it / 2 }
            }

        AnimatedVisibility(
            visible = isScrolledUp,
            enter = enterTransition,
            exit = exitTransition,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
        ) {
            Surface(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                shape = RpShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = "Ir al más reciente",
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (unreadNewItems > 0) "Más recientes ($unreadNewItems)" else "Más recientes",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
