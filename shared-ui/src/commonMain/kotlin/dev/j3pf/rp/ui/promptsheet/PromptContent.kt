package dev.j3pf.rp.ui.promptsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.chat.prettyArgs
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.QuestionAnswer
import dev.j3pf.rp.protocol.QuestionDef
import dev.j3pf.rp.ui.components.RpMarkdown
import dev.j3pf.rp.ui.components.TagChip
import dev.j3pf.rp.ui.theme.RpColors

/**
 * Contenido de un prompt; lo usan la hoja de respuesta (BottomSheet) y las tarjetas en línea del chat.
 *
 *  - permission: herramienta + comando completo en monoespaciado, peligro, [Permitir] [Permitir siempre (sesión)]
 *    [Denegar] [Denegar con motivo]
 *  - question: opciones como botones (con descripción) y texto libre si `allowFreeText`
 *  - questionnaire: 1–4 preguntas en páginas con progreso, opción única/múltiple y `preview` en markdown
 *  - dialog: confirm (Sí/No), select (lista), input (texto)
 */
@Composable
fun PromptContent(
    prompt: Prompt,
    busy: Boolean,
    onAnswer: (Answer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptHeader(prompt)
        when {
            prompt.kind == PromptKind.QUESTIONNAIRE && !prompt.questions.isNullOrEmpty() ->
                QuestionnaireBody(prompt.promptId, prompt.questions!!, busy, onAnswer)
            prompt.category == PromptCategory.PERMISSION -> PermissionBody(prompt, busy, onAnswer)
            prompt.kind == PromptKind.CONFIRM && prompt.options.isEmpty() -> ConfirmBody(busy, onAnswer)
            prompt.kind == PromptKind.INPUT -> InputBody(prompt.placeholder, busy, onAnswer)
            else -> SelectBody(prompt, busy, onAnswer)
        }
    }
}

@Composable
private fun PromptHeader(p: Prompt) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val (label, color) =
            when (p.category) {
                PromptCategory.PERMISSION -> "Autorización" to RpColors.warning
                PromptCategory.QUESTION -> "Pregunta" to MaterialTheme.colorScheme.primary
                else -> "Diálogo" to MaterialTheme.colorScheme.secondary
            }
        TagChip(label, color = color)
        if (p.source?.subagent == true) TagChip("❀ subagente", MaterialTheme.colorScheme.tertiary)
        when (p.danger) {
            Danger.HIGH -> TagChip("Peligro alto", RpColors.danger)
            Danger.MEDIUM -> TagChip("Peligro medio", RpColors.warning)
        }
    }
    if (p.title.isNotBlank()) Text(p.title, style = MaterialTheme.typography.titleMedium)
    if (!p.message.isNullOrBlank() &&
        p.category != PromptCategory.PERMISSION
    ) {
        Text(p.message!!, style = MaterialTheme.typography.bodyMedium)
    }
}

// ───────────────────────────── Autorización ─────────────────────────────

@Composable
private fun PermissionBody(
    p: Prompt,
    busy: Boolean,
    onAnswer: (Answer) -> Unit,
) {
    val command =
        p.tool
            ?.args
            ?.let { prettyArgs(it) }
            ?.takeIf { it.isNotBlank() } ?: p.message.orEmpty()
    if (p.tool != null || command.isNotBlank()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp)) {
                p.tool?.name?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Text(
                    command,
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    if (!p.message.isNullOrBlank() && p.tool == null && command != p.message) {
        Text(p.message!!, style = MaterialTheme.typography.bodyMedium)
    }

    var reasonMode by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }

    val opts = p.options
    val allow = opts.firstOrNull { isAllow(it) } ?: opts.firstOrNull()
    val always = opts.firstOrNull { isAlways(it) }
    val deny = opts.lastOrNull { isDeny(it) } ?: opts.takeIf { it.size > 1 }?.last()

    fun allowAnswer() = if (allow == null) Answer.confirm(true) else Answer.option(allow.id)

    fun denyAnswer(text: String? = null) =
        if (deny == null) Answer(confirmed = false, text = text) else Answer(optionIds = listOf(deny.id), text = text)

    if (reasonMode) {
        OutlinedTextField(
            reason,
            { reason = it },
            Modifier.fillMaxWidth(),
            label = { Text("Motivo de la denegación") },
            minLines = 2,
            enabled = !busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { reasonMode = false }, enabled = !busy) { Text("Volver") }
            Button(
                onClick = { onAnswer(denyAnswer(reason.trim().ifEmpty { null })) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = RpColors.danger),
            ) { Text("Denegar") }
        }
        return
    }

    Button(onClick = { onAnswer(allowAnswer()) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(allow?.label ?: "Permitir")
    }
    if (always != null && always != allow) {
        FilledTonalButton(onClick = {
            onAnswer(Answer.option(always.id))
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(always.label)
        }
    }
    OutlinedButton(
        onClick = { onAnswer(denyAnswer()) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(deny?.label ?: "Denegar", color = RpColors.danger) }
    TextButton(onClick = { reasonMode = true }, enabled = !busy) { Text("Denegar con motivo…") }
}

private fun isDeny(o: PromptOption) =
    o.label.lowercase().let {
        it.startsWith("deneg") ||
            it.startsWith("deny") ||
            it.startsWith("rechaz") ||
            it == "no"
    }

private fun isAlways(o: PromptOption) = o.label.lowercase().let { "siempre" in it || "always" in it }

private fun isAllow(o: PromptOption) =
    o.label.lowercase().let {
        (
            it.startsWith("permit") ||
                it.startsWith("allow") ||
                it.startsWith("acept") ||
                it == "sí" ||
                it == "si"
        ) &&
            !isAlways(o)
    }

// ───────────────────────────── Confirmación / entrada / selección ─────────────────────────────

@Composable
private fun ConfirmBody(
    busy: Boolean,
    onAnswer: (Answer) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = {
            onAnswer(Answer.confirm(false))
        }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("No") }
        Button(
            onClick = { onAnswer(Answer.confirm(true)) },
            enabled = !busy,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) { Text("Sí") }
    }
}

@Composable
private fun InputBody(
    placeholder: String?,
    busy: Boolean,
    onAnswer: (Answer) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        text,
        { text = it },
        Modifier.fillMaxWidth(),
        placeholder = placeholder?.let { { Text(it) } },
        minLines = 2,
        enabled = !busy,
        keyboardOptions = KeyboardOptions.Default,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onAnswer(Answer.Cancelled) }, enabled = !busy) { Text("Omitir") }
        Button(onClick = { onAnswer(Answer.text(text.trim())) }, enabled = !busy && text.isNotBlank()) { Text("Enviar") }
    }
}

@Composable
private fun SelectBody(
    p: Prompt,
    busy: Boolean,
    onAnswer: (Answer) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    p.options.forEach { o ->
        FilledTonalButton(
            onClick = { onAnswer(Answer.option(o.id)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(o.label)
                if (!o.description.isNullOrBlank()) {
                    Text(o.description!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (p.allowFreeText) {
        OutlinedTextField(
            text,
            { text = it },
            Modifier.fillMaxWidth(),
            label = { Text(p.placeholder ?: "Otra respuesta") },
            minLines = 1,
            enabled = !busy,
        )
        Button(onClick = { onAnswer(Answer.text(text.trim())) }, enabled = !busy && text.isNotBlank()) { Text("Enviar texto") }
    }
    TextButton(onClick = { onAnswer(Answer.Cancelled) }, enabled = !busy) { Text("Omitir") }
}

// ───────────────────────────── Cuestionario (ask_user_question) ─────────────────────────────

@Composable
private fun QuestionnaireBody(
    promptId: String,
    questions: List<QuestionDef>,
    busy: Boolean,
    onAnswer: (Answer) -> Unit,
) {
    if (questions.isEmpty()) return
    var page by rememberSaveable(promptId, questions.size) { mutableIntStateOf(0) }
    val selected = remember(promptId, questions) { mutableStateMapOf<Int, List<String>>() }
    val texts = remember(promptId, questions) { mutableStateMapOf<Int, String>() }
    var focused by remember(promptId, questions) { mutableStateOf<PromptOption?>(null) }

    val safePage = page.coerceIn(0, questions.lastIndex)
    val q = questions[safePage]
    val sel = selected[safePage].orEmpty()
    val answered = sel.isNotEmpty() || !texts[safePage].isNullOrBlank()

    LinearProgressIndicator(progress = { (safePage + 1f) / questions.size }, modifier = Modifier.fillMaxWidth())
    Text(
        "Pregunta ${safePage + 1} de ${questions.size}" + (q.header?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(q.question, style = MaterialTheme.typography.titleSmall)

    q.options.forEach { o ->
        val checked = o.id in sel
        Card(
            onClick = {
                focused = o
                selected[safePage] =
                    when {
                        q.multiSelect -> if (checked) sel - o.id else sel + o.id
                        else -> listOf(o.id)
                    }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (q.multiSelect) Checkbox(checked, onCheckedChange = null) else RadioButton(checked, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(o.label, style = MaterialTheme.typography.bodyLarge)
                    if (!o.description.isNullOrBlank()) {
                        Text(
                            o.description!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    // Vista previa en markdown de la opción enfocada.
    val preview = (focused ?: q.options.firstOrNull { it.id in sel })?.preview
    if (!preview.isNullOrBlank() && q.options.any { it.preview == preview }) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                RpMarkdown(preview)
            }
        }
    }
    if (q.allowFreeText) {
        OutlinedTextField(
            texts[safePage].orEmpty(),
            { texts[safePage] = it },
            Modifier.fillMaxWidth(),
            label = { Text("Otra respuesta") },
            enabled = !busy,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                if (safePage > 0) {
                    page = safePage - 1
                    focused = null
                } else {
                    onAnswer(Answer.Cancelled)
                }
            },
            enabled = !busy,
        ) { Text(if (safePage > 0) "Atrás" else "Omitir") }
        if (safePage < questions.size - 1) {
            Button(onClick = {
                page = safePage + 1
                focused = null
            }, enabled = !busy && answered) { Text("Siguiente") }
        } else {
            Button(
                onClick = {
                    onAnswer(
                        Answer.questionnaire(
                            questions.indices.map { i ->
                                val ids = selected[i].orEmpty()
                                if (ids.isNotEmpty()) QuestionAnswer(i, optionIds = ids) else QuestionAnswer(i, text = texts[i].orEmpty())
                            },
                        ),
                    )
                },
                enabled = !busy && answered && questions.indices.all { i -> !selected[i].isNullOrEmpty() || !texts[i].isNullOrBlank() },
            ) { Text("Enviar") }
        }
    }
}
