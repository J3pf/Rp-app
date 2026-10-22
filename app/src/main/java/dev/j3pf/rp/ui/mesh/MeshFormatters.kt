package dev.j3pf.rp.ui.mesh

import androidx.core.graphics.toColorInt
import dev.j3pf.rp.protocol.MeshGroup
import dev.j3pf.rp.protocol.MeshNode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val UUID_REGEX = Regex(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*")
private val TRUNCATED_UUID_REGEX = Regex(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{3,}.*")
private val HEX_SUBAGENT_REGEX = Regex("^(subagent-chat|agent|session)-[0-9a-fA-F]{4,}(-[0-9a-fA-F]+)*$", RegexOption.IGNORE_CASE)
private val LONG_HEX_REGEX = Regex("^[0-9a-fA-F]{16,}$")
private val ULID_REGEX = Regex("^[0-9A-HJ-KM-NP-TV-Z]{26}$", RegexOption.IGNORE_CASE)

fun isIdLooking(text: String?): Boolean {
    if (text.isNullOrBlank()) return true
    val trimmed = text.trim()
    if (UUID_REGEX.containsMatchIn(trimmed)) return true
    if (TRUNCATED_UUID_REGEX.containsMatchIn(trimmed)) return true
    if (HEX_SUBAGENT_REGEX.matches(trimmed)) return true
    if (LONG_HEX_REGEX.matches(trimmed)) return true
    if (ULID_REGEX.matches(trimmed)) return true
    if (trimmed.endsWith("…") || trimmed.endsWith("...")) {
        val withoutEllipsis = trimmed.trimEnd('…', '.').trim()
        if (withoutEllipsis.length >= 8 && Regex("^[0-9a-fA-F-]+$").matches(withoutEllipsis)) return true
    }
    return false
}

private val NUMBERED_SUFFIX_REGEX = Regex(".*\\(\\d+\\)$")

fun extractCwdBasename(cwd: String?): String? {
    if (cwd.isNullOrBlank()) return null
    val clean = cwd.replace('\\', '/').trimEnd('/')
    val lastSlash = clean.lastIndexOf('/')
    val name = if (lastSlash >= 0) clean.substring(lastSlash + 1) else clean
    return name.takeIf { it.isNotBlank() && it != "/" && !isIdLooking(it) && !it.equals("Agent", ignoreCase = true) }
}

fun formatMeshDisplayName(
    node: MeshNode,
    allNodes: List<MeshNode> = emptyList(),
): String {
    val baseName = resolveBaseDisplayName(node)
    if (allNodes.size <= 1 || NUMBERED_SUFFIX_REGEX.matches(baseName)) return baseName

    val duplicates = allNodes.filter { resolveBaseDisplayName(it) == baseName }
    if (duplicates.size <= 1) return baseName

    val machineGroups = duplicates.groupBy { it.machineName }
    val sameMachineNodes = machineGroups[node.machineName].orEmpty()
    return if (sameMachineNodes.size > 1) {
        val sorted = sameMachineNodes.sortedBy { it.agentId }
        val index = sorted.indexOfFirst { it.agentId == node.agentId }.coerceAtLeast(0)
        "$baseName (${node.machineName} · ${index + 1})"
    } else {
        "$baseName (${node.machineName})"
    }
}

private fun resolveBaseDisplayName(node: MeshNode): String {
    val candidate = node.name.trim()
    if (
        candidate.isNotBlank() &&
        !isIdLooking(candidate) &&
        !candidate.equals("Agent", ignoreCase = true) &&
        candidate != node.agentId &&
        candidate != node.machineId
    ) {
        return candidate
    }
    val fromCwd = extractCwdBasename(node.cwd)
    if (fromCwd != null) return fromCwd
    return "Agente"
}

fun formatMeshMachineName(machineName: String?): String {
    if (machineName.isNullOrBlank() || isIdLooking(machineName)) return "Máquina"
    return machineName.trim()
}

fun formatMeshSecondaryLine(node: MeshNode): String {
    val machine = formatMeshMachineName(node.machineName)
    val branchOrModel =
        node.gitBranch?.takeIf { it.isNotBlank() && !isIdLooking(it) }
            ?: node.model?.takeIf { it.isNotBlank() && !isIdLooking(it) }
    return if (branchOrModel != null) {
        "$machine · $branchOrModel"
    } else {
        machine
    }
}

fun formatMeshGroupChipLabel(
    group: MeshGroup,
    orchestratorNode: MeshNode?,
    allNodes: List<MeshNode> = emptyList(),
): String {
    if (group.groupId == "solo" || group.label == "Sin grupo") return "Sin grupo"
    if (group.label.isNotBlank() && !isIdLooking(group.label) && !group.label.equals("Mesh Group", ignoreCase = true)) {
        return group.label
    }
    if (orchestratorNode != null) {
        return formatMeshDisplayName(orchestratorNode, allNodes)
    }
    return "Grupo de agentes"
}

@androidx.compose.runtime.Composable
fun parseRelayColor(raw: String): androidx.compose.ui.graphics.Color =
    runCatching {
        androidx.compose.ui.graphics
            .Color(raw.toColorInt())
    }.getOrElse { androidx.compose.material3.MaterialTheme.colorScheme.primary }

fun formatMeshRole(role: String): String =
    when (role.lowercase()) {
        "orchestrator" -> "Orquestador"
        "subordinate" -> "Subordinado"
        "subagent" -> "Subagente"
        "solo" -> "Individual"
        else -> "Agente"
    }

fun formatMeshStatus(status: String): String =
    when (status.lowercase()) {
        "working" -> "Trabajando"
        "waiting_input" -> "Esperando respuesta"
        "idle" -> "Inactivo"
        "offline" -> "Desconectado"
        "error" -> "Error"
        "compacting" -> "Compactando"
        else -> status.replace('_', ' ')
    }

fun formatMeshSubagentStatus(status: String): String =
    when (status.lowercase()) {
        "running" -> "En ejecución"
        "working" -> "Trabajando"
        "done" -> "Terminado"
        "completed" -> "Completado"
        "error" -> "Error"
        "stopped" -> "Detenido"
        else -> status.replace('_', ' ')
    }

fun formatMeshCommAction(action: String): String =
    when (action.lowercase()) {
        "ask" -> "Pregunta"
        "reply" -> "Respuesta"
        "handover" -> "Pase"
        "send" -> "Envío"
        "message", "unknown" -> "Mensaje"
        else -> "Mensaje"
    }

fun formatMeshCommText(text: String?): String = text ?: "Contenido oculto"

fun formatMeshTodos(
    done: Int,
    total: Int,
): String = "$done de $total tareas"

fun formatMeshLeafCompactStatus(node: MeshNode): String {
    val status = formatMeshStatus(node.status)
    val todos = node.todos
    return if (todos != null && todos.total > 0 && node.status == "working") {
        "$status · ${todos.done}/${todos.total}"
    } else {
        status
    }
}

fun formatMeshNodeReportsTo(parentDisplayName: String): String = ", reporta a $parentDisplayName"

fun formatMeshNodeContentDescription(
    node: MeshNode,
    allNodes: List<MeshNode> = emptyList(),
    parentNode: MeshNode? = null,
): String {
    val role = formatMeshRole(node.role)
    val name = formatMeshDisplayName(node, allNodes)
    val status = formatMeshStatus(node.status)
    val reportsTo = parentNode?.let { formatMeshNodeReportsTo(formatMeshDisplayName(it, allNodes)) }.orEmpty()
    val todos = node.todos?.let { ", ${formatMeshTodos(it.done, it.total)}" }.orEmpty()
    return "$role $name, $status$reportsTo$todos"
}

fun formatMeshDateTime(
    at: Long,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(at))

fun formatMeshTime(
    at: Long,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(at))
