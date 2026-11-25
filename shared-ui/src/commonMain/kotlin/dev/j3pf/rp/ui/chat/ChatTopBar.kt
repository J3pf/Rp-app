package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.formatCompactTokens
import dev.j3pf.rp.protocol.parseHerdrTabMeta
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.util.agentTitle
import dev.j3pf.rp.ui.util.lastSegment

internal data class ChatHeaderTitleParts(
    val project: String?,
    val topic: String?,
    val fallback: String,
)

internal fun resolveChatHeaderTitle(
    rawTitle: String,
    folder: String,
): ChatHeaderTitleParts {
    val cleanRaw = rawTitle.trim()
    val cleanFolder = folder.trim()
    if (cleanFolder.isEmpty()) {
        return ChatHeaderTitleParts(project = null, topic = null, fallback = cleanRaw.ifEmpty { "Agente" })
    }
    if (cleanRaw == cleanFolder || cleanRaw.startsWith("$cleanFolder (") || cleanRaw == "[redacted]") {
        return ChatHeaderTitleParts(project = null, topic = null, fallback = cleanRaw)
    }
    if (cleanRaw.startsWith("$cleanFolder · ")) {
        val topic = cleanRaw.removePrefix("$cleanFolder · ").trim()
        return if (topic.isNotEmpty()) {
            ChatHeaderTitleParts(project = cleanFolder, topic = topic, fallback = cleanRaw)
        } else {
            ChatHeaderTitleParts(project = null, topic = null, fallback = cleanFolder)
        }
    }
    return ChatHeaderTitleParts(project = cleanFolder, topic = cleanRaw, fallback = "$cleanFolder · $cleanRaw")
}

@Immutable
data class ChatTopBarState(
    val agent: AgentSummary?,
    val sessionName: String?,
    val model: String?,
    val thinkingLevel: String?,
    val working: Boolean,
    val diffFileCount: Int,
    val chatFontScale: Float,
    val chatMessageSpacing: String,
    val muted: Boolean,
    val readOnly: Boolean,
    val searchQuery: String = "",
    val hideToolCalls: Boolean = false,
    val otherActiveAgents: List<AgentSummary> = emptyList(),
    val showQuickShortcuts: Boolean = false,
)

@Immutable
data class ChatTopBarActions(
    val onBack: () -> Unit,
    val onOpenDiff: () -> Unit,
    val onFontScaleChange: (Float) -> Unit,
    val onMessageSpacingChange: (String) -> Unit,
    val onInfo: () -> Unit,
    val onToggleMute: () -> Unit,
    val onQuit: () -> Unit,
    val onForget: () -> Unit,
    val onSearchQueryChange: (String) -> Unit = {},
    val onToggleHideToolCalls: () -> Unit = {},
    val onSendTerminalCommand: ((String) -> Unit)? = null,
    val onSwitchAgent: (String) -> Unit = {},
    val onToggleQuickShortcuts: (() -> Unit)? = null,
)

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
    @Suppress("UNUSED_PARAMETER") onAbort: () -> Unit = {},
    onOpenDiff: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onMessageSpacingChange: (String) -> Unit,
    onInfo: () -> Unit,
    onToggleMute: () -> Unit,
    onQuit: () -> Unit,
    onForget: () -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    hideToolCalls: Boolean = false,
    onToggleHideToolCalls: () -> Unit = {},
    onSendTerminalCommand: ((String) -> Unit)? = null,
    otherActiveAgents: List<AgentSummary> = emptyList(),
    onSwitchAgent: (String) -> Unit = {},
    showQuickShortcuts: Boolean = false,
    onToggleQuickShortcuts: (() -> Unit)? = null,
) {
    ChatTopBar(
        state =
            ChatTopBarState(
                agent = agent,
                sessionName = sessionName,
                model = model,
                thinkingLevel = thinkingLevel,
                working = working,
                diffFileCount = diffFileCount,
                chatFontScale = chatFontScale,
                chatMessageSpacing = chatMessageSpacing,
                muted = muted,
                readOnly = readOnly,
                searchQuery = searchQuery,
                hideToolCalls = hideToolCalls,
                otherActiveAgents = otherActiveAgents,
                showQuickShortcuts = showQuickShortcuts,
            ),
        actions =
            ChatTopBarActions(
                onBack = onBack,
                onOpenDiff = onOpenDiff,
                onFontScaleChange = onFontScaleChange,
                onMessageSpacingChange = onMessageSpacingChange,
                onInfo = onInfo,
                onToggleMute = onToggleMute,
                onQuit = onQuit,
                onForget = onForget,
                onSearchQueryChange = onSearchQueryChange,
                onToggleHideToolCalls = onToggleHideToolCalls,
                onSendTerminalCommand = onSendTerminalCommand,
                onSwitchAgent = onSwitchAgent,
                onToggleQuickShortcuts = onToggleQuickShortcuts,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    state: ChatTopBarState,
    actions: ChatTopBarActions,
) {
    val agent = state.agent
    val sessionName = state.sessionName
    val model = state.model
    val thinkingLevel = state.thinkingLevel
    val working = state.working
    val diffFileCount = state.diffFileCount
    val chatFontScale = state.chatFontScale
    val chatMessageSpacing = state.chatMessageSpacing
    val muted = state.muted
    val readOnly = state.readOnly
    val searchQuery = state.searchQuery
    val hideToolCalls = state.hideToolCalls
    val otherActiveAgents = state.otherActiveAgents
    val showQuickShortcuts = state.showQuickShortcuts
    val onBack = actions.onBack
    val onOpenDiff = actions.onOpenDiff
    val onFontScaleChange = actions.onFontScaleChange
    val onMessageSpacingChange = actions.onMessageSpacingChange
    val onInfo = actions.onInfo
    val onToggleMute = actions.onToggleMute
    val onQuit = actions.onQuit
    val onForget = actions.onForget
    val onSearchQueryChange = actions.onSearchQueryChange
    val onToggleHideToolCalls = actions.onToggleHideToolCalls
    val onSendTerminalCommand = actions.onSendTerminalCommand
    val onSwitchAgent = actions.onSwitchAgent
    val onToggleQuickShortcuts = actions.onToggleQuickShortcuts

    var showQuitDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showTerminalBar by remember { mutableStateOf(false) }
    val catalog =
        remember(agent?.launcher, agent?.model?.id, agent?.model?.provider) {
            SlashCommandCatalogs.resolve(agent)
        }
    val canPeekTerminal =
        !readOnly &&
            onSendTerminalCommand != null &&
            agent?.online == true &&
            (agent.herdr != null || agent.launcher != "pi")

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            windowInsets =
                androidx.compose.foundation.layout
                    .WindowInsets(0, 0, 0, 0),
            title = {
                val isRedacted = agent?.redacted == true
                val rawTitle =
                    if (isRedacted) {
                        "[redacted]"
                    } else {
                        agent?.let { agentTitle(it.sessionName, it.cwd, it.agentId) } ?: sessionName ?: "Agente"
                    }
                val folder = if (isRedacted) "" else agent?.cwd?.let { lastSegment(it) }.orEmpty()
                val headerParts = resolveChatHeaderTitle(rawTitle = rawTitle, folder = folder)
                val headerText =
                    if (headerParts.project != null && headerParts.topic != null) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                append(headerParts.project)
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(" · ${headerParts.topic}")
                            }
                        }
                    } else {
                        AnnotatedString(headerParts.fallback)
                    }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = headerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (agent != null) {
                        val effectiveAgent =
                            if (working && agent.online && (agent.status == "idle" || agent.status == "done")) {
                                agent.copy(
                                    status = "working",
                                    herdr =
                                        agent.herdr?.copy(
                                            tabId =
                                                agent.herdr
                                                    ?.tabId
                                                    ?.replace("|done=1", "")
                                                    ?.replace("~done", ""),
                                        ),
                                )
                            } else {
                                agent
                            }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(effectiveAgent)
                            val rawBranch = if (isRedacted) null else agent.gitBranch
                            val hasChanges = diffFileCount > 0 || rawBranch?.endsWith(" *") == true
                            if (!rawBranch.isNullOrBlank()) {
                                val cleanBranch = rawBranch.removeSuffix(" *").trim()
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
                                        "⎇ $cleanBranch${if (hasChanges) " *" else ""}",
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
                            val tabMeta = parseHerdrTabMeta(agent.herdr?.tabId)
                            val hasCtxTelemetry =
                                (agent.contextPercent ?: tabMeta.contextPercent) != null ||
                                    (agent.contextTokens ?: tabMeta.contextTokens) != null
                            val activeTaskText = if (working && !isRedacted) agent.activeTask?.takeIf { it.isNotBlank() } else null
                            val modelText = if (!hasCtxTelemetry) (model ?: agent.model?.label)?.takeIf { it.isNotBlank() } else null
                            val thinkingText = if (!hasCtxTelemetry) thinkingLevel?.takeIf { it.isNotBlank() } else null
                            val subtitle =
                                listOfNotNull(
                                    activeTaskText,
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
                IconButton(
                    onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar && searchQuery.isNotEmpty()) {
                            onSearchQueryChange("")
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Buscar en el chat",
                        tint =
                            if (showSearchBar || searchQuery.isNotBlank() || hideToolCalls) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
                    if (canPeekTerminal) {
                        DropdownMenuItem(
                            text = { Text("Terminal en vivo (/term)") },
                            onClick = {
                                menu = false
                                showTerminalBar = true
                                onSendTerminalCommand("/term")
                            },
                        )
                    }
                    if (!readOnly && onToggleQuickShortcuts != null && catalog.quickShortcuts.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (showQuickShortcuts) {
                                        "Ocultar comandos (/)"
                                    } else {
                                        "Comandos rápidos (/)"
                                    },
                                )
                            },
                            onClick = {
                                menu = false
                                onToggleQuickShortcuts()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (hideToolCalls) {
                                    "Mostrar bloques de herramientas"
                                } else {
                                    "Ocultar bloques de herramientas"
                                },
                            )
                        },
                        onClick = {
                            menu = false
                            onToggleHideToolCalls()
                        },
                    )
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

        val topBarMeta = parseHerdrTabMeta(agent?.herdr?.tabId)
        val topBarCtxPct = agent?.contextPercent ?: topBarMeta.contextPercent
        val topBarCtxTokens = agent?.contextTokens ?: topBarMeta.contextTokens
        val topBarCtxWindow = agent?.contextWindow ?: topBarMeta.contextWindow
        val topBarModelText = (model ?: agent?.model?.label)?.takeIf { it.isNotBlank() && it != "default" }
        val topBarThinking = thinkingLevel?.takeIf { it.isNotBlank() }
        val hasTelemetryRow = topBarCtxPct != null || topBarCtxTokens != null
        if (hasTelemetryRow) {
            val pct = (topBarCtxPct ?: 0).coerceIn(0, 100)
            val barColor =
                when {
                    pct >= 85 -> MaterialTheme.colorScheme.error
                    pct >= 60 -> RpColors.warning
                    else -> RpColors.success
                }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onInfo() },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val launcherTag = agent?.launcher?.takeIf { it.isNotBlank() }
                        val leftLabel =
                            buildString {
                                if (topBarModelText != null) {
                                    append("✦ ")
                                    append(topBarModelText)
                                    if (topBarThinking != null) {
                                        append(" · ")
                                        append(topBarThinking)
                                    }
                                } else if (launcherTag != null) {
                                    append("✦ ")
                                    append(launcherTag)
                                }
                            }
                        Text(
                            text = leftLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (topBarCtxPct != null || topBarCtxTokens != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val tokensLabel =
                                    when {
                                        topBarCtxTokens != null && topBarCtxWindow != null ->
                                            "🧠 ${formatCompactTokens(topBarCtxTokens)} / ${formatCompactTokens(topBarCtxWindow)}"
                                        topBarCtxTokens != null ->
                                            "🧠 ${formatCompactTokens(topBarCtxTokens)}"
                                        else -> "🧠 Contexto"
                                    }
                                Text(
                                    text = tokensLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                if (topBarCtxPct != null) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(42.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth((pct / 100f).coerceIn(0.06f, 1f))
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(barColor),
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = barColor.copy(alpha = 0.18f),
                                        border = BorderStroke(0.8.dp, barColor.copy(alpha = 0.55f)),
                                    ) {
                                        Text(
                                            text = "$topBarCtxPct%",
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = barColor,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (topBarCtxPct != null && topBarCtxPct > 0) {
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                            color = barColor.copy(alpha = 0.85f),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }

        if (otherActiveAgents.isNotEmpty()) {
            val activeScroll = rememberScrollState()
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(activeScroll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Activos:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    otherActiveAgents.forEach { sibling ->
                        val isWaiting = sibling.status == AgentStatus.WAITING_INPUT
                        val siblingTitle =
                            if (sibling.redacted) {
                                "[redacted]"
                            } else {
                                agentTitle(sibling.sessionName, sibling.cwd, sibling.agentId)
                            }
                        val badgePrefix = if (isWaiting) "⏳ " else "● "
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color =
                                if (isWaiting) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isWaiting) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    },
                                ),
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onSwitchAgent(sibling.agentId) },
                        ) {
                            Text(
                                text = "$badgePrefix$siblingTitle",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color =
                                    if (isWaiting) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        if (showSearchBar || searchQuery.isNotBlank() || hideToolCalls) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSearchBar || searchQuery.isNotBlank()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Buscar en este chat…", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(999.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Close, "Limpiar búsqueda", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    FilterChip(
                        selected = hideToolCalls,
                        onClick = onToggleHideToolCalls,
                        label = { Text("Solo mensajes", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(999.dp),
                    )
                }
            }
        }

        if (showTerminalBar && canPeekTerminal) {
            val termScroll = rememberScrollState()
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(termScroll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val termActions =
                        listOf(
                            "📺 Leer" to "/term",
                            "⏎ Enter" to "/term enter",
                            "⎋ Esc" to "/term esc",
                            "^C Ctrl+C" to "/term ctrl+c",
                            "⇥ Tab" to "/term tab",
                            "y ⏎" to "/term y",
                            "n ⏎" to "/term n",
                        )
                    termActions.forEach { (label, cmd) ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onSendTerminalCommand(cmd) },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = { showTerminalBar = false },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Filled.Close, "Cerrar barra de terminal", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

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
