package dev.j3pf.rp.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.formatCompactTokens
import dev.j3pf.rp.protocol.parseHerdrTabMeta
import dev.j3pf.rp.ui.components.StatusChip
import dev.j3pf.rp.ui.theme.RpColors
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
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    hideToolCalls: Boolean = false,
    onToggleHideToolCalls: () -> Unit = {},
    onSendTerminalCommand: ((String) -> Unit)? = null,
    otherActiveAgents: List<AgentSummary> = emptyList(),
    onSwitchAgent: (String) -> Unit = {},
) {
    var showQuitDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showTerminalBar by remember { mutableStateOf(false) }
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
                            val rawBranch = agent.gitBranch
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
                            val ctxPct = agent.contextPercent ?: tabMeta.contextPercent
                            val ctxTokens = agent.contextTokens ?: tabMeta.contextTokens
                            if (ctxPct != null || ctxTokens != null) {
                                val pillColor =
                                    when {
                                        (ctxPct ?: 0) >= 85 -> MaterialTheme.colorScheme.error
                                        (ctxPct ?: 0) >= 60 -> RpColors.warning
                                        else -> RpColors.success
                                    }
                                val pillLabel =
                                    if (ctxPct != null) {
                                        "🧠 ${ctxPct}%"
                                    } else {
                                        "🧠 ${formatCompactTokens(ctxTokens!!)}"
                                    }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = pillColor.copy(alpha = 0.14f),
                                    border = BorderStroke(0.5.dp, pillColor.copy(alpha = 0.45f)),
                                    modifier = Modifier.clickable { onInfo() },
                                ) {
                                    Text(
                                        pillLabel,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pillColor,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            val activeTaskText = if (working) agent.activeTask?.takeIf { it.isNotBlank() } else null
                            val modelText = (model ?: agent.model?.label)?.takeIf { it.isNotBlank() }
                            val thinkingText = thinkingLevel?.takeIf { it.isNotBlank() }
                            val subtitle =
                                listOfNotNull(
                                    activeTaskText,
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
        if (topBarCtxPct != null && topBarCtxPct > 0) {
            val pct = topBarCtxPct.coerceIn(0, 100)
            val barColor =
                when {
                    pct >= 85 -> MaterialTheme.colorScheme.error
                    pct >= 60 -> RpColors.warning
                    else -> RpColors.success.copy(alpha = 0.78f)
                }
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            )
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
                        val siblingTitle = agentTitle(sibling.sessionName, sibling.cwd, sibling.agentId)
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
