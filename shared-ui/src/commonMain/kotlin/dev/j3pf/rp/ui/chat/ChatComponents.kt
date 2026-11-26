package dev.j3pf.rp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import dev.j3pf.rp.chat.PromptCard
import dev.j3pf.rp.chat.SubagentCardItem
import dev.j3pf.rp.chat.SystemNote
import dev.j3pf.rp.chat.ToolRun
import dev.j3pf.rp.chat.ToolStatus
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.TodoItem
import dev.j3pf.rp.protocol.formatCompactTokens
import dev.j3pf.rp.protocol.parseHerdrTabMeta
import dev.j3pf.rp.ui.agents.cleanHerdrTabId
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpShapes
import dev.j3pf.rp.ui.util.humanSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

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
    thumbnail: ImageBitmap? = null,
    onPreview: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        if (onPreview != null) {
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onPreview)
                                .padding(end = 4.dp)
                        } else {
                            Modifier.padding(end = 4.dp)
                        },
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = "Vista previa de $name",
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    Text(
                        text = name + if (size > 0) " · ${humanSize(size)}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onPreview != null) {
                        Icon(
                            Icons.Filled.Crop,
                            contentDescription = "Ver o recortar imagen",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 6.dp).size(14.dp),
                        )
                    }
                }
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

internal fun infoDialogRows(
    agent: AgentSummary,
    liveModel: String? = null,
    liveThinking: String? = null,
    contextSummary: String? = null,
): List<Pair<String, String?>> =
    listOf(
        "PC" to agent.machineName,
        "Carpeta" to if (agent.redacted) null else agent.cwd,
        "Rama" to if (agent.redacted) null else agent.gitBranch,
        "Modelo" to (liveModel ?: agent.model?.label),
        "Razonamiento" to liveThinking,
        "Contexto" to contextSummary,
        "Lanzador" to agent.launcher,
        "herdr" to agent.herdr?.let { listOfNotNull(it.workspaceId, cleanHerdrTabId(it.tabId), it.paneId).joinToString(" / ") },
        "Modo gentle" to agent.gentleMode,
        "Id" to agent.agentId,
    )

@Composable
fun InfoDialog(
    agent: AgentSummary,
    liveModel: String? = null,
    liveThinking: String? = null,
    onDismiss: () -> Unit,
) {
    val tabMeta = parseHerdrTabMeta(agent.herdr?.tabId)
    val ctxTokens = agent.contextTokens ?: tabMeta.contextTokens
    val ctxWindow = agent.contextWindow ?: tabMeta.contextWindow
    val ctxPercent = agent.contextPercent ?: tabMeta.contextPercent
    val windowNote = if (agent.launcher == "agy" && ctxWindow == 256_000) " · límite compactación" else ""
    val contextSummary =
        when {
            ctxTokens != null && ctxWindow != null && ctxPercent != null ->
                "${formatCompactTokens(ctxTokens)} / ${formatCompactTokens(ctxWindow)} tokens ($ctxPercent%$windowNote)"
            ctxTokens != null && ctxPercent != null ->
                "${formatCompactTokens(ctxTokens)} tokens ($ctxPercent%)"
            ctxPercent != null -> "$ctxPercent%"
            ctxTokens != null -> "${formatCompactTokens(ctxTokens)} tokens"
            else -> null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Información del agente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                infoDialogRows(
                    agent = agent,
                    liveModel = liveModel,
                    liveThinking = liveThinking,
                    contextSummary = contextSummary,
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

fun filterChatItems(
    displayItems: List<ChatItem>,
    tools: Map<String, ToolRun> = emptyMap(),
    searchQuery: String = "",
    hideToolCalls: Boolean = false,
): List<ChatItem> {
    val inlineToolCallIds =
        displayItems
            .asSequence()
            .filterIsInstance<AssistantMsg>()
            .flatMap { it.blocks.asSequence() }
            .filterIsInstance<Block.ToolCall>()
            .mapNotNull { it.toolCallId }
            .toSet()
    val deduped =
        if (inlineToolCallIds.isEmpty()) {
            displayItems
        } else {
            displayItems.filterNot { it is SubagentCardItem && it.info.taskId in inlineToolCallIds }
        }
    val afterToolFilter =
        if (!hideToolCalls) {
            deduped
        } else {
            deduped.mapNotNull { item ->
                when (item) {
                    is SubagentCardItem -> null
                    is AssistantMsg -> {
                        val nonToolBlocks = item.blocks.filterNot { it is Block.ToolCall }
                        if (nonToolBlocks.isEmpty() && !item.streaming && item.error == null) {
                            null
                        } else {
                            item.copy(blocks = nonToolBlocks)
                        }
                    }
                    else -> item
                }
            }
        }
    val q = searchQuery.trim()
    if (q.isEmpty()) return afterToolFilter
    return afterToolFilter.filter { item ->
        when (item) {
            is UserMsg -> item.text.contains(q, ignoreCase = true)
            is AssistantMsg -> {
                val blockMatches =
                    item.blocks.any { block ->
                        when (block) {
                            is Block.Text -> block.text.contains(q, ignoreCase = true)
                            is Block.Thinking -> block.text.contains(q, ignoreCase = true)
                            is Block.ToolCall -> {
                                val run = block.toolCallId?.let { tools[it] }
                                block.name?.contains(q, ignoreCase = true) == true ||
                                    run?.result?.contains(q, ignoreCase = true) == true
                            }
                        }
                    }
                blockMatches || item.error?.contains(q, ignoreCase = true) == true
            }
            is PromptCard ->
                item.prompt.title.contains(q, ignoreCase = true) ||
                    item.prompt.message?.contains(q, ignoreCase = true) == true
            is SystemNote ->
                item.text.contains(q, ignoreCase = true) ||
                    item.detail?.contains(q, ignoreCase = true) == true
            is SubagentCardItem ->
                item.info.agent.contains(q, ignoreCase = true) ||
                    item.info.taskId.contains(q, ignoreCase = true)
        }
    }
}

internal fun shouldAutoPinOnLocalOrAck(
    lastItemKey: String?,
    settledLastKey: String?,
): Boolean =
    lastItemKey != settledLastKey &&
        (lastItemKey?.startsWith("local:") == true || lastItemKey?.startsWith("ack:") == true)

private fun isBottomScrolledUp(
    index: Int,
    offset: Int,
    working: Boolean,
): Boolean {
    val maxBottomIndex = if (working) 1 else 0
    return index > maxBottomIndex || (index == maxBottomIndex && offset > 48)
}

private class ScrollAnchorTracker(
    initialSize: Int,
    initialLastKey: String?,
    initialStreamingLength: Int,
    initialIsWorking: Boolean,
) {
    var settledSize = initialSize
    var settledLastKey = initialLastKey
    var settledStreamingLength = initialStreamingLength
    var settledIsWorking = initialIsWorking
    var autoScrolling by mutableStateOf(false)
    var pendingForce = false
    var pendingAnimate = false
    var activeScrollGeneration = 0
    val scrollSignals = Channel<Unit>(Channel.CONFLATED)

    fun scheduleScroll(
        animate: Boolean,
        force: Boolean,
    ) {
        activeScrollGeneration++
        pendingForce = pendingForce || force
        pendingAnimate = pendingAnimate || animate
        autoScrolling = true
        scrollSignals.trySend(Unit)
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
    searchQuery: String = "",
    hideToolCalls: Boolean = false,
    onAnswerPrompt: (Prompt, Answer) -> Unit = { _, _ -> },
    onRetryUserMessage: (UserMsg) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    streaming: AssistantMsg? = null,
    resolveAttachmentThumbnail: (String) -> ImageBitmap? = { null },
    onOpenAttachmentImage: ((AttachmentRef) -> Unit)? = null,
) {
    val searchToolsKey = if (searchQuery.isNotBlank()) tools else null
    val committedItems =
        remember(displayItems, searchToolsKey, searchQuery, hideToolCalls) {
            filterChatItems(displayItems, tools, searchQuery, hideToolCalls)
        }
    val filteredStreaming =
        remember(streaming, searchToolsKey, searchQuery, hideToolCalls) {
            streaming?.let { streamItem ->
                filterChatItems(listOf(streamItem), tools, searchQuery, hideToolCalls).firstOrNull() as? AssistantMsg
            }
        }
    val streamingToolCallIds =
        remember(filteredStreaming) {
            filteredStreaming
                ?.blocks
                ?.asSequence()
                ?.filterIsInstance<Block.ToolCall>()
                ?.mapNotNull { it.toolCallId }
                ?.toSet()
                .orEmpty()
        }
    val items =
        remember(committedItems, streamingToolCallIds) {
            if (streamingToolCallIds.isEmpty() ||
                committedItems.none { it is SubagentCardItem && it.info.taskId in streamingToolCallIds }
            ) {
                committedItems
            } else {
                committedItems.filterNot { it is SubagentCardItem && it.info.taskId in streamingToolCallIds }
            }
        }
    val totalFilteredCount = items.size + if (filteredStreaming != null) 1 else 0
    val hasRawItems = displayItems.isNotEmpty() || streaming != null

    if (totalFilteredCount == 0) {
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
                if (LocalReducedMotion.current) {
                    CircularProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
                Text("Sincronizando historial…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Descargando los mensajes recientes de la sesión.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (hasRawItems && (searchQuery.isNotBlank() || hideToolCalls)) {
                Text("Sin coincidencias", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (searchQuery.isNotBlank()) {
                        "No se encontraron mensajes con \"${searchQuery.trim()}\"."
                    } else {
                        "Todos los elementos actuales son llamadas a herramientas."
                    },
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
    val currentIsWorking by rememberUpdatedState(isWorking)
    val currentToolsState = rememberUpdatedState(tools)
    val toolLookup: (String) -> ToolRun? = remember(currentToolsState) { { id -> currentToolsState.value[id] } }

    val isScrolledUp by remember {
        derivedStateOf {
            isBottomScrolledUp(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
                working = currentIsWorking,
            )
        }
    }

    val lastItem: ChatItem? = filteredStreaming ?: items.lastOrNull()
    val lastItemKey = lastItem?.key
    val streamingTextLength = (lastItem as? AssistantMsg)?.plainTextLength ?: 0

    var pinnedToBottom by remember(listState) {
        val initiallyScrolledUp =
            Snapshot.withoutReadObservation {
                isBottomScrolledUp(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                    working = isWorking,
                )
            }
        mutableStateOf(!initiallyScrolledUp)
    }
    val itemsCountWhenScrolledUp = remember(listState) { mutableIntStateOf(totalFilteredCount) }
    val tracker =
        remember(listState) {
            ScrollAnchorTracker(
                initialSize = totalFilteredCount,
                initialLastKey = lastItemKey,
                initialStreamingLength = streamingTextLength,
                initialIsWorking = isWorking,
            )
        }

    val contentChanged =
        totalFilteredCount != tracker.settledSize ||
            lastItemKey != tracker.settledLastKey ||
            streamingTextLength != tracker.settledStreamingLength ||
            isWorking != tracker.settledIsWorking

    SideEffect {
        if (contentChanged) {
            // Read pre-layout scroll position outside composition snapshot observation before
            // LazyColumn measures newly prepended items or a toggled working indicator.
            val preLayoutScrolledUp =
                isBottomScrolledUp(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                    working = tracker.settledIsWorking,
                )
            val userScrolling = !tracker.autoScrolling && listState.isScrollInProgress
            if (!tracker.autoScrolling && !userScrolling) {
                pinnedToBottom = !preLayoutScrolledUp
            }
            val isNewLocalOrAck = shouldAutoPinOnLocalOrAck(lastItemKey, tracker.settledLastKey)
            if (isNewLocalOrAck) {
                pinnedToBottom = true
            }
            if (pinnedToBottom && !userScrolling) {
                itemsCountWhenScrolledUp.intValue = totalFilteredCount
                val isNewItem = totalFilteredCount != tracker.settledSize || isNewLocalOrAck
                val workingChanged = isWorking != tracker.settledIsWorking
                tracker.scheduleScroll(
                    animate = isNewItem || workingChanged,
                    force = false,
                )
            }
            tracker.settledSize = totalFilteredCount
            tracker.settledLastKey = lastItemKey
            tracker.settledStreamingLength = streamingTextLength
            tracker.settledIsWorking = isWorking
        }
    }

    LaunchedEffect(listState, tracker) {
        snapshotFlow {
            Triple(isScrolledUp, listState.isScrollInProgress, tracker.autoScrolling)
        }.collect { (scrolledUp, scrolling, isAutoScrolling) ->
            if (isAutoScrolling) return@collect
            if (scrolledUp) {
                pinnedToBottom = false
            } else {
                pinnedToBottom = true
                if (!scrolling) {
                    itemsCountWhenScrolledUp.intValue = tracker.settledSize
                }
            }
        }
    }

    LaunchedEffect(tracker) {
        for (signal in tracker.scrollSignals) {
            val force = tracker.pendingForce
            val animate = tracker.pendingAnimate
            val generation = tracker.activeScrollGeneration
            tracker.pendingForce = false
            tracker.pendingAnimate = false

            if (!force && listState.isScrollInProgress) {
                if (tracker.activeScrollGeneration == generation) {
                    tracker.autoScrolling = false
                }
                continue
            }
            tracker.autoScrolling = true
            try {
                if (animate) {
                    listState.animateScrollToItem(0)
                } else if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                    listState.scrollToItem(0)
                }
            } catch (_: CancellationException) {
                // Distinguish Compose user-drag interruption (MutationInterruptedException) from scope teardown.
                coroutineContext.ensureActive()
            } finally {
                if (tracker.activeScrollGeneration == generation) {
                    tracker.autoScrolling = false
                }
            }
        }
    }

    val unreadNewItems =
        if (!pinnedToBottom) {
            if (unreadCount > 0) {
                unreadCount
            } else {
                (totalFilteredCount - itemsCountWhenScrolledUp.intValue).coerceAtLeast(0)
            }
        } else {
            0
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
            SelectionContainer {
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
                                        val runningTool = currentToolsState.value.values.lastOrNull { it.status == ToolStatus.RUNNING }
                                        val statusText =
                                            when {
                                                sending -> "Enviando mensaje…"
                                                runningTool != null -> "Ejecutando: ${toolDisplayTitle(runningTool.name)}…"
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
                    if (filteredStreaming != null) {
                        item(key = filteredStreaming.key, contentType = "AssistantMsg") {
                            Box {
                                ChatItemView(
                                    item = filteredStreaming,
                                    toolLookup = toolLookup,
                                    busyPromptId = busyPromptId,
                                    onAnswerPrompt = onAnswerPrompt,
                                    spacingMode = chatMessageSpacing,
                                    onRetryUserMessage = onRetryUserMessage,
                                )
                            }
                        }
                    }
                    items(
                        reversedItems,
                        key = { it.key },
                        contentType = { it::class.simpleName },
                    ) { item ->
                        val reducedMotion = LocalReducedMotion.current
                        val isStreamingItem = item is AssistantMsg && item.streaming
                        val itemModifier =
                            if (reducedMotion || isStreamingItem) {
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
                                toolLookup = toolLookup,
                                busyPromptId = busyPromptId,
                                onAnswerPrompt = onAnswerPrompt,
                                spacingMode = chatMessageSpacing,
                                onRetryUserMessage = onRetryUserMessage,
                                resolveAttachmentThumbnail = resolveAttachmentThumbnail,
                                onOpenAttachmentImage = onOpenAttachmentImage,
                            )
                        }
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
            visible = !pinnedToBottom && isScrolledUp,
            enter = enterTransition,
            exit = exitTransition,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
        ) {
            Surface(
                onClick = {
                    pinnedToBottom = true
                    itemsCountWhenScrolledUp.intValue = tracker.settledSize
                    tracker.scheduleScroll(animate = true, force = true)
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
