package dev.j3pf.rp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatItem
import dev.j3pf.rp.chat.DiffKind
import dev.j3pf.rp.chat.PromptCard
import dev.j3pf.rp.chat.SubagentCardItem
import dev.j3pf.rp.chat.SystemNote
import dev.j3pf.rp.chat.ToolRun
import dev.j3pf.rp.chat.ToolStatus
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.chat.editDiff
import dev.j3pf.rp.chat.prettyArgs
import dev.j3pf.rp.chat.simpleDiff
import dev.j3pf.rp.chat.summary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.SubagentInfo
import dev.j3pf.rp.ui.components.RpMarkdown
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.components.horizontalEdgeFade
import dev.j3pf.rp.ui.promptsheet.PromptContent
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpShapes

@Composable
fun ChatItemView(
    item: ChatItem,
    tools: Map<String, ToolRun>,
    busyPromptId: String?,
    onAnswerPrompt: (Prompt, Answer) -> Unit,
    spacingMode: String = "normal",
) {
    when (item) {
        is UserMsg -> UserBubble(item, spacingMode)
        is AssistantMsg -> AssistantBubble(item, tools, spacingMode)
        is PromptCard -> PromptCardView(item, busyPromptId == item.prompt.promptId, onAnswerPrompt)
        is SystemNote -> SystemNoteView(item, spacingMode)
        is SubagentCardItem -> SubagentCard(item.info)
    }
}

@Composable
private fun UserBubble(
    m: UserMsg,
    spacingMode: String = "normal",
) {
    val hPad =
        when (spacingMode) {
            "compact" -> 8.dp
            "spacious" -> 14.dp
            else -> 10.dp
        }
    val vPad =
        when (spacingMode) {
            "compact" -> 4.dp
            "spacious" -> 9.dp
            else -> 6.dp
        }
    val textLineHeight =
        when (spacingMode) {
            "compact" -> 17.sp
            "spacious" -> 21.sp
            else -> 19.sp
        }
    Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RpShapes.bubbleUser,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        ) {
            Column(
                Modifier.padding(horizontal = hPad, vertical = vPad),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val isOptimistic = m.key.startsWith("local:")
                if (isOptimistic) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            "enviando…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                } else if (m.fromMobile) {
                    Text(
                        "móvil",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                if (m.text.isNotBlank()) {
                    Text(
                        text = m.text,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = textLineHeight),
                    )
                }
                if (m.imageCount > 0) Text("${m.imageCount} imagen(es) adjunta(s)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    m: AssistantMsg,
    tools: Map<String, ToolRun>,
    spacingMode: String = "normal",
) {
    val blockSpacing =
        when (spacingMode) {
            "compact" -> 2.dp
            "spacious" -> 8.dp
            else -> 4.dp
        }
    val hPad =
        when (spacingMode) {
            "compact" -> 8.dp
            "spacious" -> 14.dp
            else -> 10.dp
        }
    val vPad =
        when (spacingMode) {
            "compact" -> 4.dp
            "spacious" -> 9.dp
            else -> 6.dp
        }
    Column(Modifier.fillMaxWidth().padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(blockSpacing)) {
        m.blocks.forEachIndexed { i, b ->
            when (b) {
                is Block.Text -> {
                    if (b.text.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RpShapes.bubbleAgent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        ) {
                            Box(Modifier.padding(horizontal = hPad, vertical = vPad)) {
                                RpMarkdown(b.text, spacingMode = spacingMode)
                            }
                        }
                    }
                }

                is Block.Thinking -> {
                    if (b.text.isNotBlank()) ThinkingBlock(b.text, key = "${m.key}-$i", spacingMode = spacingMode)
                }

                is Block.ToolCall -> {
                    val run =
                        b.toolCallId?.let { tools[it] }
                            ?: ToolRun(
                                b.toolCallId ?: "${m.key}-$i",
                                b.name ?: "…",
                                b.args,
                                if (m.streaming) ToolStatus.RUNNING else ToolStatus.OK,
                            )
                    val sub = run.subagent
                    if (sub != null) {
                        SubagentCard(sub)
                    } else {
                        ToolCard(run, spacingMode = spacingMode)
                    }
                }
            }
        }
        if (m.streaming && m.blocks.isEmpty()) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        m.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (m.stopReason ==
            "aborted"
        ) {
            Text("⏹ Interrumpido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ThinkingBlock(
    text: String,
    key: String,
    spacingMode: String = "normal",
) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val minH =
        when (spacingMode) {
            "compact" -> 28.dp
            "spacious" -> 44.dp
            else -> 36.dp
        }
    val hPad =
        when (spacingMode) {
            "compact" -> 10.dp
            "spacious" -> 14.dp
            else -> 12.dp
        }
    val vPad =
        when (spacingMode) {
            "compact" -> 5.dp
            "spacious" -> 10.dp
            else -> 7.dp
        }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RpShapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 1.dp,
        modifier = Modifier.heightIn(min = minH),
        onClick = { open = !open },
    ) {
        Column(Modifier.padding(horizontal = hPad, vertical = vPad)) {
            Text(
                if (open) "Razonamiento ▾" else "Razonamiento ▸",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
            )
            AnimatedVisibility(open) {
                Text(
                    text,
                    Modifier.padding(top = 4.dp),
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Llamada a herramienta plegable: bash muestra el comando, edit muestra el diff, resultado truncado con "ver más". */
@Composable
fun ToolCard(
    run: ToolRun,
    spacingMode: String = "normal",
) {
    val (open, setOpen) = rememberSaveable(run.toolCallId) { mutableStateOf(false) }
    val (more, setMore) = rememberSaveable(run.toolCallId + "m") { mutableStateOf(false) }
    val summary = remember(run.name, run.args) { run.summary() }
    val hPad =
        when (spacingMode) {
            "compact" -> 10.dp
            "spacious" -> 14.dp
            else -> 12.dp
        }
    val vPad =
        when (spacingMode) {
            "compact" -> 5.dp
            "spacious" -> 10.dp
            else -> 7.dp
        }
    SurfaceCard(
        onClick = { setOpen(!open) },
        shape = RpShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = hPad, vertical = vPad)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (run.status) {
                    ToolStatus.RUNNING -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    ToolStatus.OK -> Text("✓", color = RpColors.success)
                    ToolStatus.ERROR -> Text("✕", color = RpColors.danger)
                }
                Text(run.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    summary,
                    Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                run.durationMs?.let {
                    Text(
                        "${it / 100 / 10.0}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val diff = remember(run.name, run.args) { run.editDiff() }
                    if (diff != null) {
                        DiffView(diff.first, diff.second)
                    } else {
                        val args = remember(run.name, run.args) { prettyArgs(run.args) }
                        if (args.isNotBlank()) Mono(args, maxHeight = 160)
                    }
                    val out = run.result ?: run.partial
                    if (!out.isNullOrEmpty()) {
                        val shown = if (more || out.length <= LIMIT) out else out.take(LIMIT) + "…"
                        Mono(
                            shown,
                            color = if (run.isError) MaterialTheme.colorScheme.error else Color.Unspecified,
                            maxHeight = if (more) 400 else 200,
                        )
                        if (out.length > LIMIT) {
                            TextButton(onClick = { setMore(!more) }) { Text(if (more) "ver menos" else "ver más") }
                        }
                        if (run.truncated) {
                            Text(
                                "(resultado truncado por el relay)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val LIMIT = 1500

@Composable
private fun Mono(
    text: String,
    color: Color = Color.Unspecified,
    maxHeight: Int = 200,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color,
        )
    }
}

@Composable
private fun DiffView(
    old: String,
    new: String,
) {
    val lines = remember(old, new) { simpleDiff(old, new) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            lines.forEach { l ->
                val (bg, mark) =
                    when (l.kind) {
                        DiffKind.ADDED -> Color(0x3300C853) to "+ "
                        DiffKind.REMOVED -> Color(0x33D50000) to "- "
                        DiffKind.SAME -> Color.Transparent to "  "
                    }
                Text(
                    mark + l.text,
                    Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PromptCardView(
    card: PromptCard,
    busy: Boolean,
    onAnswer: (Prompt, Answer) -> Unit,
) {
    SurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (card.resolved) {
                Text(
                    card.prompt.title.ifBlank { "Pregunta" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Respondida" +
                        when (card.resolvedBy) {
                            "local" -> " en la terminal"
                            "remote" -> " desde el móvil"
                            "timeout" -> " (expiró)"
                            "cancelled" -> " (cancelada)"
                            else -> ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                PromptContent(prompt = card.prompt, busy = busy, onAnswer = { onAnswer(card.prompt, it) })
            }
        }
    }
}

@Composable
private fun SystemNoteView(
    n: SystemNote,
    spacingMode: String = "normal",
) {
    val vPad =
        when (spacingMode) {
            "compact" -> 1.dp
            "spacious" -> 4.dp
            else -> 2.dp
        }
    var open by rememberSaveable(n.key) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = vPad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (n.detail != null) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = !open },
            ) {
                Text(
                    "• ${n.text}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            AnimatedVisibility(open) {
                Mono(n.detail.orEmpty(), maxHeight = 240)
            }
        } else {
            Text(
                "• ${n.text}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Tarjeta de subagente enriquecida estilo Gentle Shell (modelo, tiempo, thinking, tokens, status, steps). */
@Composable
fun SubagentCard(info: SubagentInfo) {
    val (openThinking, setOpenThinking) = rememberSaveable(info.taskId + "-think") { mutableStateOf(false) }
    val (openResult, setOpenResult) = rememberSaveable(info.taskId + "-res") { mutableStateOf(false) }

    SurfaceCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Icon + Agent Name + Task Label + Model + Status + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            info.agent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        info.model?.let { modelName ->
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    modelName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    info.label?.takeIf { it.isNotBlank() }?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Estado e indicador de duración
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (info.status) {
                        "running" -> {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                "en ejecución",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        "done", "ok", "completed" -> {
                            Text("✓", color = RpColors.success, fontWeight = FontWeight.Bold)
                            Text(
                                "completado",
                                style = MaterialTheme.typography.labelSmall,
                                color = RpColors.success,
                            )
                        }

                        "error" -> {
                            Text("✕", color = RpColors.danger, fontWeight = FontWeight.Bold)
                            Text(
                                "error",
                                style = MaterialTheme.typography.labelSmall,
                                color = RpColors.danger,
                            )
                        }

                        else -> {
                            Text(
                                info.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    info.durationMs?.let { ms ->
                        Text(
                            "${ms / 100 / 10.0}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // Métricas (turns, tool calls, tokens, cost)
            val metricsList = mutableListOf<String>()
            if (info.turns > 0) metricsList.add("${info.turns} turnos")
            if (info.toolCalls > 0) metricsList.add("${info.toolCalls} herramientas")
            info.tokens?.let { tok ->
                if (tok.total > 0) metricsList.add("${tok.total} tokens")
            }
            info.cost?.let { c ->
                metricsList.add("$${String.format(java.util.Locale.US, "%.3f", c)}")
            }

            if (metricsList.isNotEmpty()) {
                Text(
                    metricsList.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Herramientas internas (steps)
            if (info.tools.isNotEmpty()) {
                val toolsScroll = rememberScrollState()
                Row(
                    modifier =
                        Modifier
                            .horizontalEdgeFade(toolsScroll, edgeColor = MaterialTheme.colorScheme.surfaceVariant)
                            .horizontalScroll(toolsScroll),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    info.tools.forEach { t ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                t,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Thinking colapsable
            info.thinking?.takeIf { it.isNotBlank() }?.let { think ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 1.dp,
                    onClick = { setOpenThinking(!openThinking) },
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            if (openThinking) "Razonamiento del subagente ▾" else "Razonamiento del subagente ▸",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        AnimatedVisibility(openThinking) {
                            Text(
                                think,
                                modifier = Modifier.padding(top = 4.dp),
                                fontStyle = FontStyle.Italic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Error si existe
            info.error?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Resultado final si existe
            info.result?.takeIf { it.isNotBlank() }?.let { res ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { setOpenResult(!openResult) }) {
                        Text(if (openResult) "Ocultar resultado" else "Ver resultado del subagente")
                    }
                    AnimatedVisibility(openResult) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Box(Modifier.padding(10.dp)) {
                                RpMarkdown(res)
                            }
                        }
                    }
                }
            }
        }
    }
}
