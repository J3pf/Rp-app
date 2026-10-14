package dev.j3pf.rp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.chat.AssistantMsg
import dev.j3pf.rp.chat.Block
import dev.j3pf.rp.chat.ChatItem
import dev.j3pf.rp.chat.DiffKind
import dev.j3pf.rp.chat.NoteKind
import dev.j3pf.rp.chat.PromptCard
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
import dev.j3pf.rp.ui.components.RpMarkdown
import dev.j3pf.rp.ui.promptsheet.PromptContent
import dev.j3pf.rp.ui.theme.RpColors

@Composable
fun ChatItemView(
    item: ChatItem,
    tools: Map<String, ToolRun>,
    busyPromptId: String?,
    onAnswerPrompt: (Prompt, Answer) -> Unit,
) {
    when (item) {
        is UserMsg -> UserBubble(item)
        is AssistantMsg -> AssistantBubble(item, tools)
        is PromptCard -> PromptCardView(item, busyPromptId == item.prompt.promptId, onAnswerPrompt)
        is SystemNote -> SystemNoteView(item)
        else -> Unit
    }
}

@Composable
private fun UserBubble(m: UserMsg) {
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (m.fromMobile) Text("📱", style = MaterialTheme.typography.labelSmall)
                if (m.text.isNotBlank()) Text(m.text)
                if (m.imageCount > 0) Text("🖼 ${m.imageCount} imagen(es) adjunta(s)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    m: AssistantMsg,
    tools: Map<String, ToolRun>,
) {
    Column(Modifier.fillMaxWidth().padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        m.blocks.forEachIndexed { i, b ->
            when (b) {
                is Block.Text -> {
                    if (b.text.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { RpMarkdown(b.text) }
                        }
                    }
                }

                is Block.Thinking -> {
                    if (b.text.isNotBlank()) ThinkingBlock(b.text, key = "${m.key}-$i")
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
                    ToolCard(run)
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
) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        onClick = { open = !open },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                if (open) "💭 Razonamiento ▾" else "💭 Razonamiento ▸",
                style = MaterialTheme.typography.labelMedium,
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
fun ToolCard(run: ToolRun) {
    var open by rememberSaveable(run.toolCallId) { mutableStateOf(false) }
    var more by rememberSaveable(run.toolCallId + "m") { mutableStateOf(false) }
    val summary = remember(run.name, run.args) { run.summary() }
    Card(
        onClick = { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                            TextButton(onClick = { more = !more }) { Text(if (more) "ver menos" else "ver más") }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (card.resolved) {
                Text(card.prompt.title.ifBlank { "Pregunta" }, style = MaterialTheme.typography.titleSmall)
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
private fun SystemNoteView(n: SystemNote) {
    var open by rememberSaveable(n.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val icon =
            when (n.kind) {
                NoteKind.COMPACTION -> "🗜"
                NoteKind.MODEL_CHANGE -> "🤖"
                NoteKind.SESSION_RENAMED -> "✏️"
                NoteKind.THINKING_LEVEL -> "💭"
                NoteKind.BASH -> "⌨️"
                else -> "•"
            }
        TextButton(onClick = { if (n.detail != null) open = !open }, enabled = n.detail != null) {
            Text("$icon ${n.text}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        AnimatedVisibility(open && n.detail != null) {
            Mono(n.detail.orEmpty(), maxHeight = 240)
        }
    }
}
